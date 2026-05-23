# MDC Structured Logging and ELK Stack -- Design Document

**Project:** HR Portal Backend (`demo-app-backend`)  
**Date:** 2026-05-23  
**Status:** Draft  
**Spring Boot:** 3.3.5 | **Java:** 21 | **Virtual Threads:** Enabled

---

## Table of Contents

1. [MDC Field Catalogue](#1-mdc-field-catalogue)
2. [Virtual Threads and MDC -- The Problem and Solution](#2-virtual-threads-and-mdc----the-problem-and-solution)
3. [Implementation Plan -- MDC Filters](#3-implementation-plan----mdc-filters)
4. [Logback Configuration](#4-logback-configuration)
5. [Maven Dependency](#5-maven-dependency)
6. [ELK Stack -- Docker Compose Addition](#6-elk-stack----docker-compose-addition)
7. [Application Config Additions](#7-application-config-additions)
8. [Kibana Setup Guide](#8-kibana-setup-guide)
9. [Implementation Checklist](#9-implementation-checklist)

---

## 1. MDC Field Catalogue

Every HTTP request passing through the filter chain will have the following fields
injected into Logback's MDC. These fields appear automatically in every log line
emitted during that request's lifetime.

| MDC Key | Source | Set By | Lifecycle | Example |
|------------|------------------------------------------------------|----------------------|------------------------------|-------------------------------|
| `requestId` | Generated `UUID.randomUUID()`, or `X-Request-ID` header if present | `MdcRequestFilter` | Start of filter chain | `a3f1c9e2-7b4d-4e8a-9f12-3c5d6e7f8a9b` |
| `traceId` | Same value as `requestId` (single-service correlation) | `MdcRequestFilter` | Start of filter chain | `a3f1c9e2-7b4d-4e8a-9f12-3c5d6e7f8a9b` |
| `method` | `HttpServletRequest.getMethod()` | `MdcRequestFilter` | Start of filter chain | `POST` |
| `path` | `HttpServletRequest.getRequestURI()` | `MdcRequestFilter` | Start of filter chain | `/api/v1/users` |
| `clientIp` | `X-Forwarded-For` header, fallback to `getRemoteAddr()` | `MdcRequestFilter` | Start of filter chain | `192.168.1.100` |
| `userId` | `SecurityContextHolder.getContext().getAuthentication().getPrincipal()` (JWT subject) | `MdcUserFilter` | After authentication filters | `d4e5f6a7-8b9c-0d1e-2f3a-4b5c6d7e8f9a` |
| `roleId` | JWT `roleId` claim (requires re-parsing the token or extracting from a custom `Authentication` detail) | `MdcUserFilter` | After authentication filters | `c1d2e3f4-5a6b-7c8d-9e0f-a1b2c3d4e5f6` |
| `statusCode` | `HttpServletResponse.getStatus()` | `MdcRequestFilter` | After `chain.doFilter()` returns | `201` |
| `durationMs` | `System.nanoTime()` delta, converted to milliseconds | `MdcRequestFilter` | After `chain.doFilter()` returns | `142` |

### Design Notes

- **`requestId` vs `traceId`:** In a single-service architecture these are identical.
  If the system later adopts distributed tracing (Micrometer Tracing / OpenTelemetry),
  `traceId` should be replaced by the propagated trace context. Using a separate MDC
  key now avoids a breaking change later.

- **`roleId` extraction challenge:** The current `JwtCookieAuthFilter` stores only the
  `userId` (as `Authentication.principal`) and `permissions` (as authorities). The
  `roleId` claim is present in the JWT but is not placed into the `Authentication`
  object. The `MdcUserFilter` has two options:
  1. **Re-parse the JWT cookie** to extract `roleId` -- adds CPU cost but is
     self-contained.
  2. **Modify `JwtCookieAuthFilter`** to store `roleId` in
     `Authentication.details` (a `Map<String, Object>`) -- cleaner, but requires
     changing an existing filter.

  **Recommendation:** Option 2. Modify `JwtCookieAuthFilter` to set
  `auth.setDetails(Map.of("roleId", roleId))` so that `MdcUserFilter` can read
  it from `SecurityContextHolder` without re-parsing the token. This is a one-line
  change to the existing filter.

- **Internal API key requests:** When `InternalApiKeyFilter` authenticates a request,
  the principal is the string `"internal-service"`. `MdcUserFilter` should set
  `userId=internal-service` and `roleId=SERVICE` for these requests.

---

## 2. Virtual Threads and MDC -- The Problem and Solution

### The Problem

Spring Boot 3.3.5 with `spring.threads.virtual.enabled: true` serves every request
on a **virtual thread**. The Logback MDC implementation (`LogbackMDCAdapter`) stores
context in a `ThreadLocal<Map>`. This works correctly for the request-handling thread
itself, but breaks in two specific scenarios:

1. **`@Async` methods:** Spring's `@Async` dispatches work to an executor. With
   virtual threads enabled, the default `SimpleAsyncTaskExecutor` creates a new
   virtual thread per task. Virtual threads created via `Thread.ofVirtual()` do
   **not** inherit `ThreadLocal` values from the parent thread (unlike platform
   threads, which can use `InheritableThreadLocal`). The MDC map in the `@Async`
   method is therefore empty.

2. **`CompletableFuture.supplyAsync()` and manual executor submissions:** Same
   root cause. Any code that submits a `Runnable` or `Callable` to an executor
   will lose the MDC context if the executor creates a new virtual thread.

**Concrete impact in this codebase:** `AuditService.log()` is annotated with
`@Async`. When this method runs on a new virtual thread, every `log.error()` or
`log.info()` call inside it will have an empty MDC -- no `requestId`, no `userId`,
no `traceId`. This makes it impossible to correlate audit failures with the
originating HTTP request in Kibana.

### Solution: TaskDecorator for MDC Propagation

The recommended approach for Spring Boot 3.3.x is a **`TaskDecorator`** that
captures the MDC snapshot on the calling thread and restores it on the executing
thread. This is the most targeted fix: it does not require replacing Logback's
`MDCAdapter`, does not introduce Micrometer Tracing as a new dependency, and works
with any executor including `SimpleAsyncTaskExecutor`.

#### Why not the alternatives?

| Alternative | Reason to reject |
|-------------|-----------------|
| `InheritableThreadLocal`-based MDC adapter | Virtual threads created via `Thread.ofVirtual().start()` do **not** support `InheritableThreadLocal` propagation. This is a JVM design decision (JEP 444). The adapter would only help platform threads, which we are not using. |
| Micrometer Tracing + Brave/OTel | Adds significant new dependencies and complexity (trace exporters, span management). Appropriate when adopting full distributed tracing, but over-engineered for the current single-service MDC requirement. Can be adopted later without conflict. |
| ScopedValue (Java 21 preview) | Still a preview feature as of Java 21. Not supported by Logback MDC. Not production-ready. |

### MdcTaskDecorator Implementation

```java
package com.demo.app.platform.logging;

import org.slf4j.MDC;
import org.springframework.core.task.TaskDecorator;

import java.util.Map;

/**
 * Captures the MDC context map from the calling thread and restores it
 * on the executing thread. This ensures that @Async methods running on
 * virtual threads retain the requestId, userId, and other MDC fields
 * set by the MdcRequestFilter and MdcUserFilter.
 *
 * <p>The MDC is cleared in the finally block to prevent leaking context
 * to subsequent tasks that may reuse the same thread (relevant for
 * platform thread pools; virtual threads are not pooled, but defensive
 * cleanup is still correct practice).</p>
 */
public class MdcTaskDecorator implements TaskDecorator {

    @Override
    public Runnable decorate(Runnable runnable) {
        // Capture MDC snapshot on the CALLING thread (the request thread).
        Map<String, String> contextMap = MDC.getCopyOfContextMap();

        return () -> {
            if (contextMap != null) {
                MDC.setContextMap(contextMap);
            }
            try {
                runnable.run();
            } finally {
                MDC.clear();
            }
        };
    }
}
```

### AsyncConfig -- Wiring the TaskDecorator

```java
package com.demo.app.platform.logging;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;

import java.util.concurrent.Executor;

/**
 * Configures the async executor used by all @Async methods in the application.
 *
 * <p>With virtual threads enabled, Spring Boot auto-configures a
 * SimpleAsyncTaskExecutor that creates virtual threads. This configuration
 * wraps that executor with the MdcTaskDecorator so that MDC context
 * propagates from the calling thread to the async virtual thread.</p>
 */
@Configuration
@EnableAsync
public class AsyncConfig implements AsyncConfigurer {

    @Override
    public Executor getAsyncExecutor() {
        SimpleAsyncTaskExecutor executor = new SimpleAsyncTaskExecutor();
        executor.setVirtualThreads(true);
        executor.setTaskDecorator(new MdcTaskDecorator());
        executor.setThreadNamePrefix("async-mdc-");
        return executor;
    }
}
```

### Verification

After implementation, verify MDC propagation in `AuditService` by:

1. Setting a breakpoint or adding a temporary log line in `AuditService.log()`:
   ```java
   log.info("Audit event: action={}, requestId={}", action, MDC.get("requestId"));
   ```
2. Triggering an operation that calls `AuditService.log()`.
3. Confirming that `requestId` appears in the log output (not null/empty).

---

## 3. Implementation Plan -- MDC Filters

### Architecture: Two-Filter Design

A single filter cannot both set pre-authentication fields (requestId, method, path)
and post-authentication fields (userId, roleId) because the authentication filters
run between these two concerns in the Spring Security filter chain.

```
Request
  |
  v
[MdcRequestFilter]         <-- order = HIGHEST_PRECEDENCE
  | sets: requestId, traceId, method, path, clientIp
  | starts timer
  |
  v
[Spring Security Filters]
  |-- CorsFilter
  |-- CsrfFilter (disabled)
  |-- InternalApiKeyFilter  <-- before UsernamePasswordAuthenticationFilter
  |-- JwtCookieAuthFilter   <-- before UsernamePasswordAuthenticationFilter
  |-- AuthorizationFilter
  |
  v
[MdcUserFilter]             <-- registered via FilterRegistrationBean, order chosen
  | sets: userId, roleId      to run after security filters have populated
  |                            SecurityContextHolder
  v
[DispatcherServlet]
  |-- Controller -> Service -> Repository
  |
  v
[MdcRequestFilter returns]
  | sets: statusCode, durationMs
  | adds X-Request-ID response header
  | clears MDC
```

### Important: Filter Registration Strategy

The `MdcRequestFilter` must run **outside** the Spring Security filter chain (before
it), and the `MdcUserFilter` must run **after** the security filters have populated
`SecurityContextHolder`. There are two ways to achieve this:

- **Option A:** Register both as `FilterRegistrationBean` with explicit order values.
  `MdcRequestFilter` at `Ordered.HIGHEST_PRECEDENCE`, `MdcUserFilter` at
  `Ordered.HIGHEST_PRECEDENCE + 20`. Since Spring Security's
  `DelegatingFilterProxy`/`FilterChainProxy` typically runs at order -100
  (`SpringBootWebSecurityConfiguration`), the MdcUserFilter at
  `HIGHEST_PRECEDENCE + 20` would still run before security. This does not work.

- **Option B (recommended):** Register `MdcRequestFilter` as a
  `FilterRegistrationBean` at `Ordered.HIGHEST_PRECEDENCE` (runs first, wraps
  everything). Register `MdcUserFilter` **inside** the Spring Security filter
  chain using `http.addFilterAfter(mdcUserFilter, AuthorizationFilter.class)` in
  `SecurityConfig`. This guarantees it runs after JWT/API-key authentication.

**Recommendation: Option B.** This is the only reliable way to ensure that
`SecurityContextHolder` is populated before `MdcUserFilter` reads it.

### MdcRequestFilter

```java
package com.demo.app.platform.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * First filter in the chain. Sets request-scoped MDC fields that do not
 * depend on authentication: requestId, traceId, method, path, clientIp.
 *
 * <p>After the filter chain completes, sets statusCode and durationMs,
 * adds the X-Request-ID response header, and clears the entire MDC.</p>
 *
 * <p>Registered as a FilterRegistrationBean at Ordered.HIGHEST_PRECEDENCE
 * to ensure it wraps the entire request lifecycle, including Spring Security.</p>
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class MdcRequestFilter extends OncePerRequestFilter {

    private static final String REQUEST_ID_HEADER = "X-Request-ID";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain)
            throws ServletException, IOException {

        long startNanos = System.nanoTime();

        try {
            // Generate or accept request ID
            String requestId = request.getHeader(REQUEST_ID_HEADER);
            if (requestId == null || requestId.isBlank()) {
                requestId = UUID.randomUUID().toString();
            }

            // Set MDC fields available before authentication
            MDC.put("requestId", requestId);
            MDC.put("traceId", requestId);
            MDC.put("method", request.getMethod());
            MDC.put("path", request.getRequestURI());
            MDC.put("clientIp", resolveClientIp(request));

            // Set response header so clients/load-balancers can correlate
            response.setHeader(REQUEST_ID_HEADER, requestId);

            // Proceed through the rest of the filter chain
            chain.doFilter(request, response);

        } finally {
            // Set post-processing fields
            long durationMs = (System.nanoTime() - startNanos) / 1_000_000;
            MDC.put("statusCode", String.valueOf(response.getStatus()));
            MDC.put("durationMs", String.valueOf(durationMs));

            // Log the completed request at INFO level (optional -- see note below)
            // logger.info("Request completed");

            // Clear MDC to prevent leaking to the next request on this thread.
            // Virtual threads are not pooled, but this is defensive best practice.
            MDC.clear();
        }
    }

    /**
     * Resolves the client IP address, preferring the X-Forwarded-For header
     * (first IP in the chain) when behind a reverse proxy.
     */
    private String resolveClientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            // X-Forwarded-For may contain multiple IPs: client, proxy1, proxy2
            // The first one is the original client IP.
            return xff.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
```

### MdcUserFilter

```java
package com.demo.app.platform.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;

/**
 * Runs inside the Spring Security filter chain, after JwtCookieAuthFilter
 * and InternalApiKeyFilter have populated SecurityContextHolder.
 *
 * <p>Reads the authenticated principal and sets userId and roleId into MDC.
 * This filter does NOT clear MDC -- that responsibility belongs to
 * MdcRequestFilter which wraps the entire chain.</p>
 *
 * <p>Registered in SecurityConfig via:
 * {@code http.addFilterAfter(mdcUserFilter, AuthorizationFilter.class)}</p>
 */
@Component
public class MdcUserFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain)
            throws ServletException, IOException {

        try {
            populateUserMdc();
            chain.doFilter(request, response);
        } finally {
            // Do NOT clear MDC here. MdcRequestFilter owns the full lifecycle.
            // Only remove the keys this filter set, so they don't persist
            // if a downstream filter somehow re-enters this one.
            MDC.remove("userId");
            MDC.remove("roleId");
        }
    }

    private void populateUserMdc() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            MDC.put("userId", "anonymous");
            MDC.put("roleId", "NONE");
            return;
        }

        Object principal = auth.getPrincipal();
        if (principal instanceof String userId) {
            MDC.put("userId", userId);
        } else {
            MDC.put("userId", principal.toString());
        }

        // Extract roleId from Authentication.details if present.
        // Requires JwtCookieAuthFilter to set: auth.setDetails(Map.of("roleId", roleId))
        Object details = auth.getDetails();
        if (details instanceof Map<?, ?> detailsMap) {
            Object roleId = detailsMap.get("roleId");
            if (roleId != null) {
                MDC.put("roleId", roleId.toString());
            } else {
                MDC.put("roleId", "UNKNOWN");
            }
        } else {
            // Internal API key requests or other auth mechanisms
            MDC.put("roleId", "UNKNOWN");
        }
    }
}
```

### Required Change to JwtCookieAuthFilter

To make `roleId` available in MDC without re-parsing the JWT, add one line to
`JwtCookieAuthFilter.doFilterInternal()`:

```java
// In JwtCookieAuthFilter.java, after line 44 (where auth is created):
var roleId = claims.get("roleId", String.class);
auth.setDetails(Map.of("roleId", roleId != null ? roleId : "NONE"));

// The full block becomes:
var auth = new UsernamePasswordAuthenticationToken(userId, null, authorities);
var roleId = claims.get("roleId", String.class);
auth.setDetails(Map.of("roleId", roleId != null ? roleId : "NONE"));
SecurityContextHolder.getContext().setAuthentication(auth);
```

### Required Change to SecurityConfig

Register `MdcUserFilter` inside the Spring Security filter chain:

```java
// In SecurityConfig.java, add field:
private final MdcUserFilter mdcUserFilter;

// In filterChain() method, after the existing addFilterBefore calls:
.addFilterAfter(mdcUserFilter, org.springframework.security.web.access.intercept.AuthorizationFilter.class);
```

The full `SecurityConfig.filterChain()` method becomes:

```java
@Bean
public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
    http
        .csrf(AbstractHttpConfigurer::disable)
        .cors(cors -> cors.configurationSource(corsConfigurationSource))
        .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(auth -> auth
            .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
            .requestMatchers("/api/v1/auth/**").permitAll()
            .requestMatchers("/actuator/health/**").permitAll()
            .anyRequest().authenticated()
        )
        .addFilterBefore(internalApiKeyFilter, UsernamePasswordAuthenticationFilter.class)
        .addFilterBefore(jwtCookieAuthFilter, UsernamePasswordAuthenticationFilter.class)
        .addFilterAfter(mdcUserFilter, AuthorizationFilter.class);
    return http.build();
}
```

### FilterRegistrationBean for MdcRequestFilter

Since `MdcRequestFilter` is annotated with `@Component` and `@Order(HIGHEST_PRECEDENCE)`,
Spring Boot will auto-register it as a servlet filter outside the security chain.
However, to be explicit and avoid double-registration (once by component scan, once
by the security chain), use a `FilterRegistrationBean`:

```java
package com.demo.app.platform.logging;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

@Configuration
public class MdcFilterConfig {

    @Bean
    public FilterRegistrationBean<MdcRequestFilter> mdcRequestFilterRegistration(
            MdcRequestFilter filter) {
        FilterRegistrationBean<MdcRequestFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        registration.addUrlPatterns("/*");
        registration.setName("mdcRequestFilter");
        return registration;
    }

    /**
     * Prevent MdcUserFilter from being auto-registered as a servlet filter.
     * It is registered manually inside the Spring Security filter chain via
     * SecurityConfig.addFilterAfter().
     */
    @Bean
    public FilterRegistrationBean<MdcUserFilter> mdcUserFilterRegistration(
            MdcUserFilter filter) {
        FilterRegistrationBean<MdcUserFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }
}
```

---

## 4. Logback Configuration

Create the file `src/main/resources/logback-spring.xml`. This replaces the default
Logback configuration provided by Spring Boot.

```xml
<?xml version="1.0" encoding="UTF-8"?>
<configuration scan="true" scanPeriod="30 seconds">

    <!-- ============================================================= -->
    <!-- Properties                                                     -->
    <!-- ============================================================= -->
    <springProperty scope="context" name="APP_NAME" source="spring.application.name"
                    defaultValue="demo-app-backend"/>
    <property name="LOGSTASH_HOST" value="${LOGSTASH_HOST:-logstash}"/>
    <property name="LOGSTASH_PORT" value="${LOGSTASH_PORT:-5000}"/>

    <!-- ============================================================= -->
    <!-- CONSOLE Appender (human-readable, for development)             -->
    <!-- ============================================================= -->
    <appender name="CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
        <encoder>
            <pattern>
                %d{yyyy-MM-dd HH:mm:ss.SSS} %highlight(%-5level) [%thread] %cyan(%logger{36}) - %msg | requestId=%X{requestId} userId=%X{userId} method=%X{method} path=%X{path} clientIp=%X{clientIp} status=%X{statusCode} duration=%X{durationMs}ms%n
            </pattern>
        </encoder>
    </appender>

    <!-- ============================================================= -->
    <!-- LOGSTASH Appender (JSON, for production ELK stack)             -->
    <!-- Uses logstash-logback-encoder to produce structured JSON       -->
    <!-- and sends it over TCP to Logstash.                             -->
    <!-- ============================================================= -->
    <appender name="LOGSTASH" class="net.logstash.logback.appender.LogstashTcpSocketAppender">
        <destination>${LOGSTASH_HOST}:${LOGSTASH_PORT}</destination>

        <!-- Reconnect if Logstash is temporarily unavailable -->
        <reconnectionDelay>5 seconds</reconnectionDelay>

        <!-- Ring buffer to prevent blocking the application thread -->
        <ringBufferSize>16384</ringBufferSize>

        <encoder class="net.logstash.logback.encoder.LogstashEncoder">
            <!-- Include all MDC fields automatically -->
            <includeMdcKeyName>requestId</includeMdcKeyName>
            <includeMdcKeyName>traceId</includeMdcKeyName>
            <includeMdcKeyName>userId</includeMdcKeyName>
            <includeMdcKeyName>roleId</includeMdcKeyName>
            <includeMdcKeyName>method</includeMdcKeyName>
            <includeMdcKeyName>path</includeMdcKeyName>
            <includeMdcKeyName>clientIp</includeMdcKeyName>
            <includeMdcKeyName>statusCode</includeMdcKeyName>
            <includeMdcKeyName>durationMs</includeMdcKeyName>

            <!-- Add static fields -->
            <customFields>{"application":"${APP_NAME}"}</customFields>

            <!-- Include stack traces for exceptions -->
            <throwableConverter class="net.logstash.logback.stacktrace.ShortenedThrowableConverter">
                <maxDepthPerThrowable>30</maxDepthPerThrowable>
                <maxLength>2048</maxLength>
                <shortenedClassNameLength>20</shortenedClassNameLength>
                <rootCauseFirst>true</rootCauseFirst>
            </throwableConverter>
        </encoder>
    </appender>

    <!-- ============================================================= -->
    <!-- JSON_CONSOLE Appender (structured JSON to stdout, for          -->
    <!-- container environments without Logstash TCP connectivity)      -->
    <!-- ============================================================= -->
    <appender name="JSON_CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
        <encoder class="net.logstash.logback.encoder.LogstashEncoder">
            <includeMdcKeyName>requestId</includeMdcKeyName>
            <includeMdcKeyName>traceId</includeMdcKeyName>
            <includeMdcKeyName>userId</includeMdcKeyName>
            <includeMdcKeyName>roleId</includeMdcKeyName>
            <includeMdcKeyName>method</includeMdcKeyName>
            <includeMdcKeyName>path</includeMdcKeyName>
            <includeMdcKeyName>clientIp</includeMdcKeyName>
            <includeMdcKeyName>statusCode</includeMdcKeyName>
            <includeMdcKeyName>durationMs</includeMdcKeyName>
            <customFields>{"application":"${APP_NAME}"}</customFields>
            <throwableConverter class="net.logstash.logback.stacktrace.ShortenedThrowableConverter">
                <maxDepthPerThrowable>30</maxDepthPerThrowable>
                <maxLength>2048</maxLength>
                <shortenedClassNameLength>20</shortenedClassNameLength>
                <rootCauseFirst>true</rootCauseFirst>
            </throwableConverter>
        </encoder>
    </appender>

    <!-- ============================================================= -->
    <!-- Profile-specific configuration                                 -->
    <!-- ============================================================= -->

    <!-- DEV profile: human-readable console output -->
    <springProfile name="dev">
        <logger name="com.demo" level="DEBUG" additivity="false">
            <appender-ref ref="CONSOLE"/>
        </logger>
        <logger name="org.springframework.security" level="DEBUG"/>
        <logger name="org.hibernate.SQL" level="DEBUG"/>

        <root level="INFO">
            <appender-ref ref="CONSOLE"/>
        </root>
    </springProfile>

    <!-- PROD profile: structured JSON to Logstash TCP + JSON to stdout -->
    <springProfile name="prod">
        <logger name="com.demo" level="INFO" additivity="false">
            <appender-ref ref="LOGSTASH"/>
            <appender-ref ref="JSON_CONSOLE"/>
        </logger>
        <logger name="org.springframework.security" level="WARN"/>
        <logger name="org.hibernate.SQL" level="WARN"/>
        <logger name="org.hibernate.type.descriptor.sql" level="WARN"/>

        <root level="WARN">
            <appender-ref ref="LOGSTASH"/>
            <appender-ref ref="JSON_CONSOLE"/>
        </root>
    </springProfile>

    <!-- DEFAULT (no profile active): console output like dev -->
    <springProfile name="default">
        <logger name="com.demo" level="INFO" additivity="false">
            <appender-ref ref="CONSOLE"/>
        </logger>

        <root level="INFO">
            <appender-ref ref="CONSOLE"/>
        </root>
    </springProfile>

</configuration>
```

### Appender Details

| Appender | Purpose | Output | When used |
|----------|---------|--------|-----------|
| `CONSOLE` | Human-readable development logs | stdout (pattern layout) | `dev` profile |
| `LOGSTASH` | Structured JSON shipped to Logstash over TCP | TCP socket to `logstash:5000` | `prod` profile |
| `JSON_CONSOLE` | Structured JSON to stdout (for `docker logs` / container log collectors) | stdout (JSON) | `prod` profile |

### Sample Output

**Dev profile (CONSOLE):**
```
2026-05-23 14:30:22.456 INFO  [virtual-42] c.d.a.i.service.AuthService - User logged in | requestId=a3f1c9e2 userId=d4e5f6a7 method=POST path=/api/v1/auth/login clientIp=192.168.1.100 status= duration=ms
```

**Prod profile (LOGSTASH/JSON_CONSOLE):**
```json
{
  "@timestamp": "2026-05-23T14:30:22.456Z",
  "@version": "1",
  "message": "User logged in",
  "logger_name": "com.demo.app.iam.service.AuthService",
  "thread_name": "virtual-42",
  "level": "INFO",
  "application": "demo-app-backend",
  "requestId": "a3f1c9e2-7b4d-4e8a-9f12-3c5d6e7f8a9b",
  "traceId": "a3f1c9e2-7b4d-4e8a-9f12-3c5d6e7f8a9b",
  "userId": "d4e5f6a7-8b9c-0d1e-2f3a-4b5c6d7e8f9a",
  "roleId": "c1d2e3f4-5a6b-7c8d-9e0f-a1b2c3d4e5f6",
  "method": "POST",
  "path": "/api/v1/auth/login",
  "clientIp": "192.168.1.100",
  "statusCode": "200",
  "durationMs": "142"
}
```

---

## 5. Maven Dependency

Add to `pom.xml` inside the `<dependencies>` block:

```xml
<!-- Logstash Logback Encoder — structured JSON logging for ELK -->
<dependency>
    <groupId>net.logstash.logback</groupId>
    <artifactId>logstash-logback-encoder</artifactId>
    <version>7.4</version>
</dependency>
```

This is the only new dependency required. It provides:
- `LogstashEncoder` -- produces JSON log events compatible with Logstash/Elasticsearch
- `LogstashTcpSocketAppender` -- ships log events over TCP to Logstash
- Automatic MDC field inclusion in JSON output

No transitive conflicts with the existing dependency tree. The encoder depends on
`logback-core` and `jackson-databind`, both of which are already provided by
Spring Boot 3.3.5.

---

## 6. ELK Stack -- Docker Compose Addition

### Service Blocks

Add the following services to the existing `docker-compose.yml`:

```yaml
  # ─── ELK Stack ─────────────────────────────────────────────────────

  elasticsearch:
    image: docker.elastic.co/elasticsearch/elasticsearch:8.13.0
    environment:
      - discovery.type=single-node
      - xpack.security.enabled=false
      - xpack.security.enrollment.enabled=false
      - ES_JAVA_OPTS=-Xms512m -Xmx512m
      - cluster.name=demo-app-cluster
    ports:
      - "9200:9200"
    volumes:
      - elasticsearch_data:/usr/share/elasticsearch/data
    healthcheck:
      test: ["CMD-SHELL", "curl -s http://localhost:9200/_cluster/health | grep -q '\"status\":\"green\"\\|\"status\":\"yellow\"'"]
      interval: 10s
      timeout: 5s
      retries: 12
      start_period: 30s

  logstash:
    image: docker.elastic.co/logstash/logstash:8.13.0
    environment:
      - LS_JAVA_OPTS=-Xms256m -Xmx256m
      - xpack.monitoring.enabled=false
    ports:
      - "5000:5000/tcp"
      - "9600:9600"
    volumes:
      - ./logstash/pipeline/logstash.conf:/usr/share/logstash/pipeline/logstash.conf:ro
    depends_on:
      elasticsearch:
        condition: service_healthy
    healthcheck:
      test: ["CMD-SHELL", "curl -s http://localhost:9600/_node/stats | grep -q '\"status\":\"green\"'"]
      interval: 10s
      timeout: 5s
      retries: 12
      start_period: 30s

  kibana:
    image: docker.elastic.co/kibana/kibana:8.13.0
    environment:
      - ELASTICSEARCH_HOSTS=http://elasticsearch:9200
      - xpack.security.enabled=false
    ports:
      - "5601:5601"
    depends_on:
      elasticsearch:
        condition: service_healthy
    healthcheck:
      test: ["CMD-SHELL", "curl -s http://localhost:5601/api/status | grep -q '\"overall\":{\"level\":\"available\"'"]
      interval: 15s
      timeout: 10s
      retries: 12
      start_period: 60s
```

### Volume Addition

Add to the existing `volumes:` section:

```yaml
volumes:
  postgres_data:
  uploads_data:
  cv_output_data:
  elasticsearch_data:
```

### App Service Environment Update

Add the `LOGSTASH_HOST` environment variable to the `app` service and set the
Spring profile:

```yaml
  app:
    # ... existing config ...
    environment:
      # ... existing env vars ...
      LOGSTASH_HOST: logstash
      LOGSTASH_PORT: "5000"
      SPRING_PROFILES_ACTIVE: prod
    depends_on:
      postgres:
        condition: service_healthy
      redis:
        condition: service_healthy
      # Note: do NOT add logstash as a dependency here.
      # The LogstashTcpSocketAppender handles reconnection gracefully.
      # Making logstash a hard dependency would prevent the app from starting
      # if ELK is temporarily down.
```

### Logstash Pipeline Configuration

Create the file `logstash/pipeline/logstash.conf` in the backend project root:

```
input {
  tcp {
    port => 5000
    codec => json_lines
    type => "spring-boot"
  }
}

filter {
  # Ensure @timestamp is set (logstash-logback-encoder already sets it,
  # but this handles edge cases with malformed events)
  if ![\"@timestamp\"] {
    date {
      match => ["timestamp", "ISO8601"]
      target => "@timestamp"
      remove_field => ["timestamp"]
    }
  }

  # Add environment field from env var (set via Logstash environment config)
  mutate {
    add_field => {
      "environment" => "${ENVIRONMENT:development}"
    }
  }

  # Parse durationMs as integer for range queries in Kibana
  if [durationMs] {
    mutate {
      convert => { "durationMs" => "integer" }
    }
  }

  # Parse statusCode as integer for aggregation in Kibana
  if [statusCode] {
    mutate {
      convert => { "statusCode" => "integer" }
    }
  }

  # Remove redundant fields to save storage
  mutate {
    remove_field => ["host", "port"]
  }
}

output {
  elasticsearch {
    hosts => ["http://elasticsearch:9200"]
    index => "demo-app-%{+YYYY.MM.dd}"
    manage_template => true
    template_name => "demo-app"
    template_overwrite => true
  }

  # Optional: also output to stdout for debugging Logstash itself
  # Uncomment the following block to debug pipeline issues:
  # stdout {
  #   codec => rubydebug
  # }
}
```

### Directory Structure

Create this directory structure in the backend project root:

```
demo-app-backend/
  logstash/
    pipeline/
      logstash.conf
```

---

## 7. Application Config Additions

### application.yml

Add these entries to the existing `application.yml`:

```yaml
# Under the existing management: block, update to:
management:
  endpoints:
    web:
      exposure:
        include: health,info,loggers
  endpoint:
    health:
      probes:
        enabled: true
    loggers:
      enabled: true

# Add logging section:
logging:
  level:
    com.demo: INFO
    org.springframework.security: WARN
    net.logstash.logback: WARN
```

The `loggers` actuator endpoint enables runtime log level changes via:

```bash
# View current log level for a logger
curl http://localhost:8080/actuator/loggers/com.demo

# Change log level at runtime (no restart needed)
curl -X POST http://localhost:8080/actuator/loggers/com.demo \
  -H "Content-Type: application/json" \
  -d '{"configuredLevel": "DEBUG"}'
```

**Security note:** The `loggers` endpoint is protected by Spring Security
(`anyRequest().authenticated()`), so only authenticated users can change log
levels. In production, consider further restricting this to admin roles only
via a custom `WebSecurityCustomizer` or method security.

### application-dev.yml

Update the existing `application-dev.yml` to:

```yaml
spring:
  jpa:
    properties:
      hibernate:
        show_sql: true

logging:
  level:
    com.demo: DEBUG
    org.springframework.security: DEBUG
    net.logstash.logback: INFO
```

No changes needed here beyond what already exists. The `logback-spring.xml` uses
`<springProfile name="dev">` to select the CONSOLE appender and DEBUG levels.

### Environment Variables Reference

| Variable | Default | Description |
|----------|---------|-------------|
| `LOGSTASH_HOST` | `logstash` | Hostname of the Logstash TCP input |
| `LOGSTASH_PORT` | `5000` | Port of the Logstash TCP input |
| `SPRING_PROFILES_ACTIVE` | (none) | Set to `prod` in Docker Compose to activate JSON/Logstash logging |

---

## 8. Kibana Setup Guide

### First-Time Setup

After running `docker compose up`, wait for all services to become healthy (approximately
60-90 seconds for Elasticsearch, 30 seconds more for Kibana).

#### Step 1 -- Open Kibana

Navigate to [http://localhost:5601](http://localhost:5601) in a browser.

#### Step 2 -- Create a Data View

1. Open the hamburger menu (top left).
2. Navigate to **Stack Management** > **Data Views** (under Kibana section).
3. Click **Create data view**.
4. Configure:
   - **Name:** `HR Portal Logs`
   - **Index pattern:** `demo-app-*`
   - **Timestamp field:** `@timestamp`
5. Click **Save data view to Kibana**.

#### Step 3 -- Verify Data Ingestion

1. Navigate to **Discover** (hamburger menu > Analytics > Discover).
2. Select the `HR Portal Logs` data view from the dropdown.
3. Set the time range to **Last 15 minutes**.
4. You should see log events flowing in. If not:
   - Check that the `app` service is running with `SPRING_PROFILES_ACTIVE=prod`.
   - Check Logstash logs: `docker compose logs logstash`.
   - Check that port 5000 is reachable from the app container:
     `docker compose exec app wget -qO- http://logstash:5000 || echo "expected connection refused for non-HTTP"`.

#### Step 4 -- Pin Key Fields

In the **Discover** view, add the following columns to the table (click the **+**
icon next to each field in the available fields list on the left sidebar):

| Field | Purpose |
|-------|---------|
| `requestId` | Correlate all log lines for a single request |
| `userId` | Filter logs by user |
| `method` | HTTP method (GET, POST, etc.) |
| `path` | API endpoint hit |
| `statusCode` | HTTP response status |
| `durationMs` | Request processing time |
| `level` | Log level (INFO, WARN, ERROR) |
| `message` | Log message text |
| `logger_name` | Java class that produced the log |
| `clientIp` | Source IP address |

#### Step 5 -- Create Saved Searches

Create and save these searches for quick access:

**1. Errors Only**
- Filter: `level: "ERROR"`
- Name: `[HR Portal] Errors`
- Purpose: Quick view of all application errors

**2. Slow Requests (> 1000ms)**
- Filter: `durationMs > 1000`
- Name: `[HR Portal] Slow Requests (>1s)`
- Purpose: Identify performance bottlenecks

**3. Requests by User ID**
- Filter: `userId: "<paste-specific-uuid>"`
- Name: (use ad-hoc, not saved -- change userId each time)
- Purpose: Debug user-specific issues

**4. Authentication Failures**
- Filter: `path: "/api/v1/auth/*" AND statusCode >= 400`
- Name: `[HR Portal] Auth Failures`
- Purpose: Monitor brute-force attempts or integration issues

**5. Request Trace (single request)**
- Filter: `requestId: "<paste-specific-request-id>"`
- Name: (use ad-hoc)
- Purpose: See every log line for a single request, including async audit events

#### Step 6 -- Optional: Create a Dashboard

1. Navigate to **Dashboard** (hamburger menu > Analytics > Dashboard).
2. Click **Create dashboard**.
3. Add the following visualizations:
   - **Request volume over time:** Line chart, count by `@timestamp` (1-minute intervals)
   - **Error rate:** TSVB metric, count where `level=ERROR` / total count * 100
   - **Top 10 slowest endpoints:** Data table, `path` field, average `durationMs`, sorted descending
   - **Status code distribution:** Pie chart, terms aggregation on `statusCode`
   - **Active users:** Unique count of `userId` over time

---

## 9. Implementation Checklist

Ordered task list for the implementing developer. Each task is independent enough
to be a single commit. Tasks must be completed in this order because later tasks
depend on earlier ones.

### Phase A -- Dependencies and Configuration

- [ ] **A1.** Add `logstash-logback-encoder` 7.4 dependency to `pom.xml`
  - File: `pom.xml`
  - Add the `<dependency>` block from [Section 5](#5-maven-dependency)
  - Run `mvn dependency:resolve` to verify no conflicts

- [ ] **A2.** Create `src/main/resources/logback-spring.xml`
  - File: `src/main/resources/logback-spring.xml`
  - Content: full XML from [Section 4](#4-logback-configuration)
  - This replaces Spring Boot's default Logback auto-configuration

- [ ] **A3.** Update `application.yml` -- expose `loggers` actuator endpoint
  - File: `src/main/resources/application.yml`
  - Change `include: health,info` to `include: health,info,loggers`
  - Add `logging.level` block from [Section 7](#7-application-config-additions)

### Phase B -- MDC Filters and Context Propagation

- [ ] **B1.** Create `MdcRequestFilter.java`
  - File: `src/main/java/com/demo/app/platform/logging/MdcRequestFilter.java`
  - Content: full class from [Section 3](#mdcrequestfilter)

- [ ] **B2.** Create `MdcUserFilter.java`
  - File: `src/main/java/com/demo/app/platform/logging/MdcUserFilter.java`
  - Content: full class from [Section 3](#mdcuserfilter)

- [ ] **B3.** Create `MdcFilterConfig.java`
  - File: `src/main/java/com/demo/app/platform/logging/MdcFilterConfig.java`
  - Content: `FilterRegistrationBean` configuration from [Section 3](#filterregistrationbean-for-mdcrequestfilter)

- [ ] **B4.** Modify `JwtCookieAuthFilter.java` -- store roleId in Authentication.details
  - File: `src/main/java/com/demo/app/iam/security/JwtCookieAuthFilter.java`
  - Add two lines after `UsernamePasswordAuthenticationToken` creation:
    ```java
    var roleId = claims.get("roleId", String.class);
    auth.setDetails(Map.of("roleId", roleId != null ? roleId : "NONE"));
    ```
  - Add import: `java.util.Map`

- [ ] **B5.** Modify `SecurityConfig.java` -- register MdcUserFilter in security chain
  - File: `src/main/java/com/demo/app/config/SecurityConfig.java`
  - Add `MdcUserFilter` field
  - Add `.addFilterAfter(mdcUserFilter, AuthorizationFilter.class)` to the chain
  - Add import: `org.springframework.security.web.access.intercept.AuthorizationFilter`
  - Add import: `com.demo.app.platform.logging.MdcUserFilter`

### Phase C -- Virtual Thread MDC Propagation

- [ ] **C1.** Create `MdcTaskDecorator.java`
  - File: `src/main/java/com/demo/app/platform/logging/MdcTaskDecorator.java`
  - Content: full class from [Section 2](#mdctaskdecorator-implementation)

- [ ] **C2.** Create `AsyncConfig.java`
  - File: `src/main/java/com/demo/app/platform/logging/AsyncConfig.java`
  - Content: full class from [Section 2](#asyncconfig----wiring-the-taskdecorator)
  - Note: if `@EnableAsync` is already present elsewhere, remove the duplicate

### Phase D -- ELK Stack Infrastructure

- [ ] **D1.** Create `logstash/pipeline/logstash.conf`
  - File: `logstash/pipeline/logstash.conf` (relative to project root)
  - Content: full pipeline from [Section 6](#logstash-pipeline-configuration)

- [ ] **D2.** Update `docker-compose.yml` -- add ELK services
  - File: `docker-compose.yml`
  - Add `elasticsearch`, `logstash`, `kibana` service blocks from [Section 6](#service-blocks)
  - Add `elasticsearch_data` to the `volumes` section
  - Add `LOGSTASH_HOST`, `LOGSTASH_PORT` environment variables to the `app` service

### Phase E -- Verification

- [ ] **E1.** Run `mvn clean verify` -- all existing tests must pass
  - The new filters, config classes, and logback config must not break any existing tests
  - The `logstash-logback-encoder` dependency must resolve without conflicts
  - JaCoCo coverage thresholds must still be met (new filter/config classes should be
    added to JaCoCo excludes if they are infrastructure-only, or covered by tests)

- [ ] **E2.** Run `docker compose up` and verify ELK stack starts
  - Elasticsearch responds at `http://localhost:9200`
  - Kibana loads at `http://localhost:5601`
  - App logs appear in Kibana after creating the data view

- [ ] **E3.** Verify MDC fields appear in logs
  - Make a request: `curl -c cookies.txt http://localhost:8080/api/v1/auth/login ...`
  - Check `docker compose logs app` for MDC fields in log output
  - Verify `X-Request-ID` response header is returned

- [ ] **E4.** Verify async MDC propagation
  - Trigger an operation that invokes `AuditService.log()` (any create/update operation)
  - Search Kibana for logs from `AuditService` and verify `requestId` is present (not empty)

- [ ] **E5.** Update JaCoCo excludes (if needed)
  - If the new filter classes lower overall coverage below 95%, add them to JaCoCo excludes:
    ```xml
    <exclude>**/platform/logging/**</exclude>
    ```
  - Alternatively, write unit tests for `MdcRequestFilter` and `MdcUserFilter`

---

## Appendix A: Complete File Inventory

| File | Action | Description |
|------|--------|-------------|
| `pom.xml` | Modify | Add `logstash-logback-encoder` 7.4 dependency |
| `src/main/resources/logback-spring.xml` | Create | Logback config with CONSOLE, LOGSTASH, JSON_CONSOLE appenders |
| `src/main/resources/application.yml` | Modify | Add loggers actuator endpoint, logging levels |
| `src/main/java/com/demo/app/platform/logging/MdcRequestFilter.java` | Create | Pre-auth MDC fields, timing, X-Request-ID header |
| `src/main/java/com/demo/app/platform/logging/MdcUserFilter.java` | Create | Post-auth MDC fields (userId, roleId) |
| `src/main/java/com/demo/app/platform/logging/MdcFilterConfig.java` | Create | Filter registration beans |
| `src/main/java/com/demo/app/platform/logging/MdcTaskDecorator.java` | Create | MDC propagation for @Async virtual threads |
| `src/main/java/com/demo/app/platform/logging/AsyncConfig.java` | Create | Async executor with TaskDecorator |
| `src/main/java/com/demo/app/iam/security/JwtCookieAuthFilter.java` | Modify | Store roleId in Authentication.details |
| `src/main/java/com/demo/app/config/SecurityConfig.java` | Modify | Register MdcUserFilter after AuthorizationFilter |
| `docker-compose.yml` | Modify | Add elasticsearch, logstash, kibana services |
| `logstash/pipeline/logstash.conf` | Create | Logstash TCP input, filter, Elasticsearch output |

## Appendix B: Troubleshooting

### App starts but no logs in Kibana

1. Check that `SPRING_PROFILES_ACTIVE=prod` is set (otherwise LOGSTASH appender is not active).
2. Check Logstash is receiving data: `docker compose logs logstash` -- look for `Pipeline started`.
3. Check Elasticsearch has indices: `curl http://localhost:9200/_cat/indices?v` -- look for `demo-app-*`.
4. Check the data view in Kibana uses the correct index pattern (`demo-app-*`).

### App fails to start with ClassNotFoundException for LogstashEncoder

The `logstash-logback-encoder` dependency is missing from `pom.xml`, or Maven has not
resolved it yet. Run `mvn dependency:resolve` and rebuild.

### MDC fields are empty in @Async methods

The `AsyncConfig` is not being picked up. Verify:
- The class is in a package scanned by Spring (`com.demo.app.platform.logging`).
- `@EnableAsync` is present (on `AsyncConfig` or another config class).
- There is no competing `AsyncConfigurer` bean elsewhere in the codebase.

### Elasticsearch runs out of memory

The default `ES_JAVA_OPTS=-Xms512m -Xmx512m` is sized for development. For local
development with limited RAM, reduce to `-Xms256m -Xmx256m`. For staging/production,
increase to at least `-Xms1g -Xmx1g`.

### Virtual threads and MDC: context still empty

If MDC context is still empty after implementing `MdcTaskDecorator`, check whether
the `@Async` method is called from within the same bean (self-invocation). Spring
AOP proxies do not intercept self-calls, so `@Async` is silently ignored and the
method runs synchronously on the calling thread. Extract the async call to a
separate Spring bean if this is the case.
