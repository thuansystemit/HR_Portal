# Architecture — Demo App (Angular 21 Enterprise)

> Status: Proposed (greenfield backend)
> Audience: Engineering, IT/DevOps, Security review
> Frontend: Angular 21 standalone, signals-based, zoneless (fixed)
> Backend: Greenfield — this document defines it
> Scope: Production-ready full-stack design that backs the existing demo UI

> **Scope boundary:** This document covers the Angular 21 frontend and Spring Boot backend only.
> The CV batch extraction service (`cv-batch-extractor`) is a separate Python microservice with its own dedicated documentation:
> - [Architecture](cv_batch_extractor/ARCHITECTURE.md) — enterprise module structure, 10-stage pipeline, OCR/LLM adapters, worker pool
> - [Domain Model](cv_batch_extractor/DOMAIN_MODEL.md) — PipelineContext, CvExtraction schema, settings reference
> - [Guardrails Spec](cv_batch_extractor/GUARDRAILS_SPEC.md) — per-stage validators, status aggregation rules

---

## Phase 1 — Requirements & Context

### 1.1 Functional Requirements (FRs)

| ID    | Capability        | Functional Requirement                                                                                               | Source UI Feature         |
|-------|-------------------|----------------------------------------------------------------------------------------------------------------------|---------------------------|
| FR-1  | Authentication    | Validate email/password, issue session tokens via HttpOnly cookies, expose `whoami`, support logout/revocation       | §1 Authentication         |
| FR-2  | Authentication    | Session persists across browser tabs and reloads via HttpOnly cookie; no token in JavaScript memory                 | §1                        |
| FR-3  | Authorization     | Resolve effective permissions server-side; enforce `usersView/Create/Edit/Delete`, `rolesView/Edit`                  | §3 RBAC, §13 Guards       |
| FR-4  | Users             | CRUD users; list with sort/filter/pagination matching `app-data-table`                                              | §5 User Management        |
| FR-5  | Users             | Forbid `usersDelete` for non-Admin — server enforced; UI hides the button                                           | §5                        |
| FR-6  | Roles             | CRUD roles, attach permission set; protect built-in roles from edit/delete                                          | §6 Role Management        |
| FR-7  | Documents         | Manage document categories with per-role visibility (`canView`, `canUpload`, `canDelete`)                           | §7 Document Mgmt, §4 Dash |
| FR-8  | Documents         | Upload (multipart), list paginated, preview, delete; enforce per-category permissions                               | §7                        |
| FR-9  | Reports           | Aggregate: upload trend (12 months), docs per category, role distribution, storage by category                      | §8 Reports                |
| FR-10 | Settings          | Persist per-user appearance/localization/table/notification settings; expose defaults; reset                        | §9 Settings               |
| FR-11 | Profile           | Update display name; change password (current verification, ≥6 chars, confirm)                                      | §10 Profile               |
| FR-12 | Audit             | Log auth events, user/role mutations, document upload/delete, settings reset                                        | Implicit (enterprise)     |
| FR-13 | Access Control UX | Return 401 / 403 with machine-readable code so UI can route to `/access-denied`                                     | §11                       |
| FR-14 | Theming           | Server returns user theme preference at session bootstrap so Shell renders correct theme pre-paint                  | §14                       |

### 1.2 Non-Functional Requirements (NFRs)

| Category        | Target                                                                                          | Rationale                                                            |
|-----------------|-------------------------------------------------------------------------------------------------|----------------------------------------------------------------------|
| Availability    | 99.5% monthly (≈ 3h 39min downtime/month)                                                       | Internal tool, no external customers                                 |
| Concurrency     | **10,000 simultaneous in-flight requests**; steady ~2,000 RPS, burst up to 5,000 RPS            | Horizontal scale + Java 21 Virtual Threads as core enabler           |
| Latency (p95)   | API reads ≤ 200 ms, writes ≤ 400 ms, document upload TTFB ≤ 500 ms (50 MB max)                 | Achievable with virtual threads + PgBouncer at stated concurrency    |
| DB connections  | PostgreSQL `max_connections=200`; all app connections via PgBouncer transaction pooling          | Postgres cannot sustain 10k direct connections                       |
| Throughput      | 50 MB/s aggregate document I/O burst                                                            | Enterprise document workflows; bytes bypass API via presigned URLs   |
| Storage         | 1 TB documents Y1, growing 50 GB/month; Postgres ≤ 50 GB                                       | ~5k docs, avg 200 MB; metadata is small                              |
| Security        | OWASP ASVS L2; PII at-rest encrypted; TLS 1.3 in transit; session ≤ 8h sliding                 | Standard enterprise baseline                                         |
| Compliance      | GDPR-ready (right to erasure for User PII; audit log retention 1 year)                         | EU staff; not regulated industry                                     |
| RPO / RTO       | RPO ≤ 15 min, RTO ≤ 4 h                                                                        | Matches 99.5% with single-region recovery                            |
| Browser         | Latest 2 versions of Chrome/Edge/Firefox/Safari; no IE                                         | Angular 21 baseline; also guarantees SameSite=Strict support         |
| Accessibility   | WCAG 2.1 AA on all routes                                                                       | Internal tool policy                                                 |

### 1.3 Stakeholders

| Stakeholder   | Concerns                                                              |
|---------------|-----------------------------------------------------------------------|
| Administrator | Full control of users, roles, settings; reliable audit trail          |
| Manager       | Manage users (no delete), view roles, run reports                     |
| Viewer        | Read-only access to documents and dashboard                           |
| IT / DevOps   | Deployability, observability, PgBouncer ops, backup/restore           |
| Security      | RBAC correctness, PII handling, cookie hygiene, CORS origin control   |
| Product Owner | Feature parity with current UI; predictable delivery                  |

### 1.4 System Boundaries

In scope (backend):
- Authentication (credentials, cookie-based sessions, password change).
- Authorization (server-side permission resolution; UI guards are advisory).
- User and Role lifecycle (CRUD, role-permission mapping, built-in protection).
- Document Categories and Documents (metadata, ACL).
- Object storage for binary document blobs (S3).
- Aggregations powering the four Reports.
- Per-user settings store.
- Audit log.
- Email/push/desktop notification dispatch.
- CV ingest endpoint (`POST /api/v1/cv-candidates`) — receives structured extraction results from the CV batch extractor and updates `document.extractionStatus`.

Out of scope (frontend continues to own):
- Highcharts rendering, `data-theme` application, dialog/UI choreography, route guards (UX), avatar rendering.

Out of scope for this document (see linked docs):
- CV text extraction pipeline, OCR adapters, LLM providers, guardrail validation — owned by the `cv-batch-extractor` Python microservice. Full specification in [`docs/cv_batch_extractor/`](cv_batch_extractor/ARCHITECTURE.md).

External systems: SMTP relay, Web Push gateway (VAPID), Identity Provider (future OIDC seam).

### 1.5 Assumptions & Constraints

- Angular 21 frontend is **fixed**; backend serves a REST API consumable by Angular `HttpClient`.
- Single-region deployment is acceptable (NFR allows 4h RTO).
- Angular SPA and API **may be on different origins** (e.g., `app.company.com` vs `api.company.com`). CORS is configured at the application layer to handle this explicitly.
- No multi-tenancy in v1 — single organisation.
- No real-time collaboration; documents are read-mostly.
- "Built-in" roles are seeded and immutable.
- **All auth tokens delivered via HttpOnly cookies only** — no tokens ever written to `sessionStorage`, `localStorage`, or readable JavaScript memory.
- **CSRF token mechanism is disabled.** `SameSite=Strict` on cookies and a strict CORS origin allowlist together provide equivalent protection without per-request token overhead. All target browsers support `SameSite=Strict` (Chrome 80+, Firefox 79+, Safari 13.1+).
- **10,000 concurrent connections** require Java 21 Virtual Threads (Project Loom) and PgBouncer connection pooling. These are first-class constraints, not afterthoughts.

### 1.6 Capability Map

```
+----------------------------------------------------------------------+
|                        Demo App Capabilities                         |
+----------------------------------------------------------------------+
| Identity & Access     | Content                  | Insights          |
|  - AuthN              |  - Document Categories   |  - Reports        |
|  - AuthZ (RBAC)       |  - Documents (blobs)     |  - Aggregations   |
|  - Users              |  - Preview rendering     |                   |
|  - Roles              |                          |                   |
+-----------------------+--------------------------+-------------------+
| Personalization       | Platform                 | Compliance        |
|  - Settings (per-user)|  - Notifications         |  - Audit Log      |
|  - Profile            |  - Object storage        |  - PII handling   |
|  - Theme bootstrap    |  - Cache / Pooling       |                   |
+----------------------------------------------------------------------+
```

---

## Phase 2 — Architecture Design

### 2.1 Style Selection — Modular Monolith

**Decision:** Modular Monolith (single deployable, internally segmented packages) scaled horizontally.

| Option           | Pros                                                          | Cons                                                         | Verdict               |
|------------------|---------------------------------------------------------------|--------------------------------------------------------------|-----------------------|
| Single monolith  | Simplest, fastest to ship                                     | Hard to enforce module boundaries; refactor cost grows       | Too loose             |
| Modular monolith | One deploy unit, clear package seams, horizontal scale, splittable | Requires ArchUnit boundary enforcement                  | **Selected**          |
| Microservices    | Independent scaling per domain                                | Overkill at this team size; distributed transaction cost     | Rejected              |
| Serverless       | Auto-scale, pay-per-use                                       | Cold starts hurt p95; document streaming awkward; VPC latency | Rejected            |

Justification: 10k concurrent is a load target, not a complexity signal. A well-configured modular monolith on virtual threads with PgBouncer handles it comfortably. Horizontal scaling (4–20 ECS tasks) provides both capacity and availability.

### 2.2 Component Registry

| Component                | Responsibility                                                                                   | Package             |
|--------------------------|--------------------------------------------------------------------------------------------------|---------------------|
| Nginx (Edge)             | TLS 1.3, gzip/br, Angular bundle, CORS preflight passthrough, rate limit                         | Infra               |
| Auth Module              | `POST /auth/login`, `/auth/logout`, `/auth/refresh`, `/auth/password`                            | `iam.auth`          |
| Authorization Service    | Permission resolution, `@PreAuthorize` policy, JWT cookie filter                                 | `iam.authz`         |
| User Module              | `/users` CRUD, search, pagination, status                                                        | `iam.users`         |
| Role Module              | `/roles` CRUD, permission set, built-in role protection                                          | `iam.roles`         |
| Document Category Module | `/categories` CRUD, per-role visibility matrix                                                   | `content.cats`      |
| Document Module          | `/documents` metadata, ACL check, presigned URL minting                                          | `content.docs`      |
| Storage Adapter          | S3/MinIO client, streaming upload, SSE-KMS                                                       | `content.storage`   |
| Reports Module           | Aggregation queries (Redis-cached), 4 endpoints powering Highcharts                              | `insights`          |
| Settings Module          | `/settings/me` GET/PUT; defaults; reset                                                          | `personal.settings` |
| Profile Module           | `/profile` (display name) — thin wrapper over Auth + User                                        | `personal.profile`  |
| Audit Module             | Append-only event log; queried by Admin only                                                     | `compliance`        |
| Notification Dispatcher  | Email (SMTP), Web Push (VAPID), in-app — fan-out from domain events                              | `platform.notif`    |
| PgBouncer                | Connection pooler in transaction mode between API pods and PostgreSQL                            | Infra sidecar       |
| Cache (Redis Cluster)    | Permission cache, report cache, rate-limit counters, event Streams                               | `platform.cache`    |
| CV Batch Extractor       | Python microservice — watches upload volume, OCR → LLM → structured JSON → notifies backend via `POST /api/v1/cv-candidates`. See [cv_batch_extractor/ARCHITECTURE.md](cv_batch_extractor/ARCHITECTURE.md) | External sidecar |

### 2.3 Data Ownership Map

| Owner               | Tables                                                            |
|---------------------|-------------------------------------------------------------------|
| `iam.auth`          | `credential`, `refresh_token`, `password_history`                |
| `iam.users`         | `user`, `user_status`                                            |
| `iam.roles`         | `role`, `permission`, `role_permission`, `user_role`             |
| `content.cats`      | `document_category`, `category_role_visibility`                  |
| `content.docs`      | `document`, `document_acl`                                       |
| `content.storage`   | (blobs — object store, not relational)                           |
| `insights`          | (read-only views over `content.*`, `iam.*`)                      |
| `personal.settings` | `user_setting`                                                   |
| `compliance`        | `audit_event`                                                    |
| `platform.notif`    | `notification`, `push_subscription`                              |

### 2.4 Integration Map

REST (synchronous): all UI-facing calls. Cross-module calls within the monolith are direct Spring service method calls inside a single transaction boundary.

Events (in-process `ApplicationEvent` + Redis Streams outbox):
- `UserCreated`, `UserDeleted`, `RoleChanged` → invalidate `perm:{userId}` in Redis, emit audit.
- `DocumentUploaded`, `DocumentDeleted` → invalidate report cache, emit audit, fan-out notifications.
- `PasswordChanged`, `LoginSucceeded`, `LoginFailed` → audit only.

### 2.5 C4 — Level 1: System Context

```
                +----------------------+
                |      End User        |
                | (Admin/Mgr/Viewer)   |
                +----------+-----------+
                           | HTTPS
                           v
                +----------------------+        +-------------------+
                |   Demo App System    |------> |   SMTP Relay      |
                |  (Angular SPA + API) |        +-------------------+
                |                      |------> +-------------------+
                |                      |        |  Web Push (VAPID) |
                |                      |        +-------------------+
                |                      |------> +-------------------+
                |                      |        |  ui-avatars.com   |
                +----------+-----------+        +-------------------+
                           |
                           v
                +----------------------+
                |  IT / DevOps         |
                |  (CI/CD, monitoring) |
                +----------------------+
```

### 2.6 C4 — Level 2: Container Diagram

```
                   Browser (Angular 21 SPA)
                   - All auth via HttpOnly cookies
                   - HttpClient: withCredentials: true
                   - No XSRF token needed
                   - CORS: credentials + explicit origin
                            |
                            | HTTPS / JSON / multipart
                            v
+--------------------------------------------------------------+
|          Edge / Reverse Proxy (Nginx)                        |
|  TLS 1.3, gzip/br, static SPA, rate limit 200 r/m per IP    |
|  CORS preflight passed through to API                        |
+-----+---------------------------+--------------------------+--+
      |                           |
      v                           v
+------------------+    +--------------------------------------------+
| Static Asset     |    |  API Pods ×4–20 (Spring Boot 3 / Java 21) |
| (Angular bundle) |    |  Virtual Threads enabled                    |
+------------------+    |  Packages: iam.*, content.*, insights,      |
                        |   personal.*, compliance, platform.*        |
                        +-----+----------+-----------+---------------+
                              |          |           |
                              v          |           v
                       +------------+   |   +-----------+
                       | PgBouncer  |   |   | Object    |
                       | (txn mode) |   |   | Store     |
                       | pool=50    |   |   | (S3/MinIO)|
                       +-----+------+   |   +-----------+
                             |          v
                             v   +------------------+
                       +---------+  Redis 7 Cluster |
                       |Postgres |  3 primary       |
                       |  16     |  3 replica       |
                       | RDS     |  (cache+streams) |
                       | r6g.xl  +------------------+
                       +---------+
```

### 2.7 Sequence — Login (HttpOnly Cookie, No CSRF Token)

```
User      Angular SPA              Nginx       Spring Boot (auth)   PgBouncer  Postgres    Redis
 |  click  |                         |                |                 |          |           |
 |-------->|                         |                |                 |          |           |
 |         | POST /auth/login        |                |                 |          |           |
 |         | (withCredentials:true)  |                |                 |          |           |
 |         |------------------------>|                |                 |          |           |
 |         |              CORS check: Origin allowed? |                 |          |           |
 |         |                         | forward        |                 |          |           |
 |         |                         |--------------->|                 |          |           |
 |         |                         |                | SELECT by email |          |           |
 |         |                         |                |---------------->|--------->|           |
 |         |                         |                |<----------------|<---------|           |
 |         |                         |                | BCrypt verify   |          |           |
 |         |                         |                | sign JWT (RS256, 15m)      |           |
 |         |                         |                | gen refresh (opaque 256-bit)           |
 |         |                         |                | INSERT refresh_token        |          |
 |         |                         |                |---------------->|--------->|           |
 |         |                         |                | cache perm:{userId} 10m    |---------->|
 |         |                         |                | load user_setting.theme    |           |
 |         |                         |<- 200 + cookies|                |          |           |
 |         |                         |  Set-Cookie: access-token=<JWT> |          |           |
 |         |                         |    HttpOnly; Secure; SameSite=Strict; Max-Age=900       |
 |         |                         |  Set-Cookie: refresh-token=<opaque>        |           |
 |         |                         |    HttpOnly; Secure; SameSite=Strict; Path=/auth/refresh
 |         |                         |  Access-Control-Allow-Origin: https://app.company.com  |
 |         |                         |  Access-Control-Allow-Credentials: true                |
 |         |<------------------------|  body: { user, perms, theme }  |           |           |
 |         | writes theme to         |                |                |           |           |
 |         | localStorage            |                |                |           |           |
 |         | sets <html data-theme>  |                |                |           |           |
 |         | NO token in JS memory   |                |                |           |           |
```

**Cookie strategy — two cookies, no CSRF token:**

| Cookie          | Attributes                                           | Purpose                                             |
|-----------------|------------------------------------------------------|-----------------------------------------------------|
| `access-token`  | HttpOnly; Secure; SameSite=Strict; Max-Age=900       | JWT (RS256, 15 min). Opaque to JS.                  |
| `refresh-token` | HttpOnly; Secure; SameSite=Strict; Path=/auth/refresh| Opaque 256-bit; scoped to refresh endpoint only.    |

No `XSRF-TOKEN` cookie. CSRF protection comes from `SameSite=Strict` (leg 1: browser refuses cross-site cookie sending) and strict CORS origin allowlist (leg 2: cross-origin requests from unauthorized origins rejected at preflight).

### 2.8 Sequence — Document Upload

```
User    SPA              API (docs)        AuthZ        PgBouncer  Postgres   Object Store   Event Bus
 | pick |                  |                 |             |          |             |              |
 |----> |                  |                 |             |          |             |              |
 |      | POST /documents/presign            |             |          |             |              |
 |      | (access-token cookie sent; CORS)   |             |          |             |              |
 |      |----------------->|                 |             |          |             |              |
 |      |                  | canUpload(cat,role)           |          |             |              |
 |      |                  |---------------->|             |          |             |              |
 |      |                  |<--allow---------|             |          |             |              |
 |      |                  | INSERT document(status=pending)          |             |              |
 |      |                  |-------------------------------->-------->|             |              |
 |      |                  | mint S3 PUT URL (5 min, size cap)        |             |              |
 |      |                  |--------------------------------------------------->(SDK)            |
 |      |<--{url, docId}---|                 |             |          |             |              |
 |      | PUT bytes → S3 directly (presigned, no auth cookie needed)  |             |              |
 |      |-----------------------------------------------------------------> store  |              |
 |      |<----------------------------------------------------------------------------------      |
 |      | POST /documents/{id}/commit        |             |          |             |              |
 |      |----------------->|                 |             |          |             |              |
 |      |                  | HEAD object — verify size/etag           |             |              |
 |      |                  | UPDATE document SET status=ready         |             |              |
 |      |                  |-------------------------------->-------->|             |              |
 |      |                  | publish DocumentUploaded                 |             |              |
 |      |                  |-------------------------------------------------------------------------
 |      |<--201 doc--------|                 |             |          |  cache inval + audit + notif|
```

### 2.9 Trade-offs Accepted

- **Modular monolith** — accepts future split cost in exchange for current simplicity and low ops overhead.
- **CSRF disabled; CORS + SameSite=Strict as protection** — removes per-request token overhead (significant at 10k concurrent); relies on `SameSite=Strict` and strict origin allowlist. Requires strict CORS configuration discipline: any misconfiguration (`allowedOrigins: *`) re-opens the attack surface.
- **PgBouncer transaction pooling** — enables 10k concurrent without overwhelming Postgres; accepted ops complexity of an additional component; Hibernate prepared statement cache disabled (`prepareThreshold=0`), slightly higher SQL parse overhead.
- **Virtual threads** — eliminates need for reactive WebFlux at 10k concurrent; trade-off is no backpressure propagation (virtual threads block silently under load rather than rejecting early). Mitigated by ALB request queuing + auto-scaling.
- **Presigned S3 URLs** — extra round-trip per upload/download; document bytes never pass through the API, which is essential at 10k concurrent (avoids I/O monopolising API pods).
- **Server + `localStorage` for settings** — server is source of truth; `localStorage` is a pre-paint theme cache only (never holds tokens).
- **Single region** — accepts longer RTO in exchange for ⅓ infra cost.

---

## Phase 3 — Technology Stack

### 3.1 Stack Summary

| Layer            | Choice                                          | Notes                                                              |
|------------------|-------------------------------------------------|--------------------------------------------------------------------|
| Frontend         | Angular 21 (fixed)                              | Standalone, signals, zoneless, `withCredentials: true` globally    |
| API runtime      | Spring Boot 3.3 / Java 21                       | Virtual Threads (`spring.threads.virtual.enabled=true`), Spring MVC |
| Concurrency      | Java 21 Virtual Threads (Project Loom)          | 10k concurrent without reactive complexity; ~few KB per vthread    |
| Auth             | Spring Security + JWT RS256 (jjwt)              | HttpOnly cookies, CSRF disabled, CORS enabled, OIDC seam reserved  |
| Password hash    | `DelegatingPasswordEncoder` (BCrypt strength=12)| Argon2 registered as upgrade path                                  |
| Database         | PostgreSQL 16 (RDS r6g.xlarge)                  | max_connections=200; all app traffic via PgBouncer                 |
| Connection pool  | PgBouncer 1.22 (transaction mode) + HikariCP    | PgBouncer: pool=50 real PG connections; HikariCP: max=20 per pod   |
| ORM              | Spring Data JPA + Hibernate 6.4                 | `prepareThreshold=0` JDBC option for PgBouncer compatibility       |
| Migrations       | Flyway 10                                        | Forward-only reviewed SQL scripts; never `ddl-auto`                |
| Cache / queue    | Redis 7 Cluster (3 primary + 3 replica)         | Permission cache, report cache, rate-limit counters, Streams outbox |
| Object storage   | S3 (prod) / MinIO (dev)                         | SSE-KMS; presigned PUT/GET; bytes never pass through API           |
| Edge             | Nginx                                            | TLS termination, static SPA, rate limit, preflight passthrough     |
| IaC              | Terraform                                        | Single AWS account, one VPC                                        |
| Container        | Docker; ECS Fargate (4 min → 20 max tasks)      | Auto-scale on `ALBRequestCountPerTarget > 2500`                    |
| CI/CD            | GitHub Actions → ECR → ECS                      | Maven → fat JAR → Docker image; blue/green deploy                  |
| Observability    | Micrometer → Prometheus / Grafana + OTel → Tempo| Spring Boot Actuator; virtual thread metrics; PgBouncer pool metrics|
| Logging          | SLF4J + Logback (logstash-logback-encoder)       | JSON → Loki; deny-list for PII fields                              |
| Email            | Spring Mail + Amazon SES                         | Transactional only                                                 |
| Web Push         | `com.zerodeplibs:webpush-lib` + VAPID            | Keys in AWS Secrets Manager                                        |

### 3.2 Approved Library Registry (Backend — Java / Spring)

| Concern               | Library / Version                                   | Why                                                      |
|-----------------------|-----------------------------------------------------|----------------------------------------------------------|
| Framework             | `spring-boot-starter-web` 3.3                       | MVC, embedded Tomcat, virtual threads                    |
| Security              | `spring-boot-starter-security`                      | Filter chain, CORS config, CSRF disabled, method security |
| JWT                   | `io.jsonwebtoken:jjwt-api` 0.12                     | RS256 sign/verify; JWKS rotation-ready                   |
| Password hashing      | `spring-security-crypto`                            | `DelegatingPasswordEncoder` (BCrypt + Argon2 upgrade)    |
| ORM                   | `spring-boot-starter-data-jpa` + Hibernate 6.4      | Type-safe queries; PgBouncer-compatible config           |
| DB migration          | `flyway-core` 10                                    | Forward-only SQL; reviewed per PR                        |
| Validation            | `spring-boot-starter-validation`                    | Jakarta Bean Validation 3 on DTOs; automatic 400s        |
| Boilerplate           | `lombok`                                            | `@Value`, `@Builder`, `@Slf4j`                           |
| Mapping               | `mapstruct` 1.6                                     | Compile-time, no reflection                              |
| Object storage        | `software.amazon.awssdk:s3` 2.x                     | Presigned PUT/GET, multipart, SSE-KMS                    |
| Caching               | `spring-boot-starter-data-redis` + Lettuce          | Cluster-aware; `@Cacheable`, `@CacheEvict`, Streams      |
| Observability         | `spring-boot-starter-actuator` + Micrometer         | `/actuator/health`, Prometheus scrape, virtual thread metrics |
| Tracing               | `io.opentelemetry:opentelemetry-spring-boot-starter`| OTLP → Tempo; W3C TraceContext propagation               |
| Logging format        | `logstash-logback-encoder` 7.x                      | JSON logs with traceId, spanId, userId                   |
| Testing — unit        | JUnit 5 + Mockito                                   | Standard Spring Boot test slices                         |
| Testing — integration | Testcontainers + `@SpringBootTest`                  | Real Postgres + Redis; no H2                             |
| Testing — E2E         | Playwright (frontend-driven)                        | Full login → action → assert flows                       |
| Architecture lint     | ArchUnit 1.x                                        | Package boundary enforcement in CI                       |
| API docs              | `springdoc-openapi-starter-webmvc-ui`               | Swagger UI at `/swagger-ui.html`                         |

### 3.3 Trade-off Matrices

#### 3.3.1 Database

| Option        | Perf     | Ops cost | Schema fit                        | 10k concurrent   | Verdict      |
|---------------|----------|----------|-----------------------------------|------------------|--------------|
| PostgreSQL 16 | High     | Low      | Excellent (JSONB, partitioning)   | Via PgBouncer    | **Selected** |
| MySQL 8       | High     | Low      | Adequate; weaker JSON             | Via ProxySQL     | Rejected     |
| MongoDB       | High     | Medium   | Poor — strong relational model    | Native            | Rejected     |
| DynamoDB      | High     | Low      | Poor for ad-hoc reports           | Native            | Rejected     |

Decisive: Reports need SQL aggregations across joins. PgBouncer solves the concurrency limit.

#### 3.3.2 API Runtime / Concurrency Model

| Option                       | 10k concurrent | DX    | Ops    | Team fit         | Verdict      |
|------------------------------|----------------|-------|--------|------------------|--------------|
| Spring Boot 3 + Virtual Threads | Yes (Loom)  | High  | Low    | Java-first team  | **Selected** |
| Spring Boot + WebFlux (Reactor) | Yes          | Low   | Low    | Steep learning curve | Rejected |
| Node + NestJS + cluster mode  | Partial        | High  | Medium | N/A (Java shop)  | Rejected     |
| Go + goroutines               | Yes            | Med   | Low    | New language     | Rejected     |

Decisive: Virtual Threads deliver 10k concurrent with blocking-style code — no reactive operators, no `Mono`/`Flux`. The team writes standard Spring MVC code and gets the concurrency for free. Enabling: `spring.threads.virtual.enabled=true`.

#### 3.3.3 CSRF Protection Strategy

| Option                              | XSS-safe | CSRF-safe | Request overhead | Complexity | Verdict      |
|-------------------------------------|----------|-----------|------------------|------------|--------------|
| CSRF token (double-submit cookie)   | Yes      | Yes       | Extra header/cookie per mutation | High | Rejected — overhead at 10k |
| SameSite=Strict + strict CORS       | Yes      | Yes (modern browsers) | Zero     | Low  | **Selected** |
| No protection                       | Yes      | No        | Zero             | Zero       | Rejected     |

Decisive: Target browsers (NFR) all support `SameSite=Strict`. CORS preflight is a natural gate for cross-origin mutations. Removing the CSRF token eliminates per-request overhead that multiplies at 10k concurrent.

#### 3.3.4 CORS Origin Strategy

| Option                     | Security | Dev DX     | Credential cookies | Verdict      |
|----------------------------|----------|------------|--------------------|--------------|
| `allowedOrigins("*")`      | None     | Easy       | **Illegal** with credentials | Rejected |
| Origin allowlist (explicit)| Strong   | Config discipline | Yes         | **Selected** |
| Same-origin (no CORS)      | Strong   | Simple     | Yes (implicit)     | Only if monodomain deploy |

Decisive: `allowCredentials=true` is required for HttpOnly cookies across origins; browsers reject `*` in this case. Explicit allowlist is mandatory.

#### 3.3.5 Object Storage

| Option      | Cost  | Durability | Presigned URLs | Verdict      |
|-------------|-------|------------|----------------|--------------|
| AWS S3      | Low   | 11×9       | Yes            | **Selected** |
| MinIO       | Free  | Cluster    | Yes            | Dev/local    |
| Postgres LO | High  | DB-bound   | No             | Rejected — blocks API at 10k |

---

## Phase 4 — Non-Functional Architecture

### 4.1 Scalability Strategy

#### Virtual Threads — the 10k concurrent enabler

```
# application.properties
spring.threads.virtual.enabled=true

# This single property switches Tomcat from platform threads (pool of 200)
# to virtual threads (one per request, JVM-scheduled, ~few KB each).
# 10,000 concurrent requests = ~10,000 virtual threads = ~100 MB extra heap.
# No reactive code required.
```

Virtual threads block on I/O (JDBC, Redis, S3 SDK calls) without holding an OS thread. The JVM scheduler parks them and uses the carrier thread (= 1 per CPU core) for other work. Throughput scales with I/O wait time, not thread pool size.

#### PgBouncer — mandatory connection multiplexer

```
# pgbouncer.ini (per pod sidecar or shared service)
[pgbouncer]
pool_mode        = transaction        # connection held only during a DB transaction
max_client_conn  = 500               # accepts 500 connections from this pod's HikariCP
default_pool_size = 50               # actual Postgres backend connections
min_pool_size    = 10
reserve_pool_size = 5
listen_port      = 5432

# JDBC URL — disable prepared statement caching (incompatible with txn pooling)
spring.datasource.url=jdbc:postgresql://pgbouncer:5432/demo?prepareThreshold=0
spring.datasource.hikari.maximum-pool-size=20   # per pod → to PgBouncer, not Postgres
spring.datasource.hikari.minimum-idle=5
spring.datasource.hikari.connection-timeout=3000
```

**Connection math at 10k concurrent (8 pods):**
```
8 pods × 20 HikariCP connections = 160 app→PgBouncer connections
PgBouncer server_pool_size=50    = 50 PgBouncer→Postgres connections
Postgres max_connections=200     = headroom for tooling, migrations, DBA
```

Transaction pooling mode holds a Postgres connection only for the duration of a `@Transactional` method. Most API requests complete their DB work in < 5 ms, releasing the connection immediately.

#### Redis Cluster topology

```yaml
# application.yml
spring:
  data:
    redis:
      cluster:
        nodes:
          - redis-primary-1:6379
          - redis-primary-2:6379
          - redis-primary-3:6379
        max-redirects: 3
      lettuce:
        cluster:
          refresh:
            adaptive: true       # topology refresh when slot moves detected
            period: 30s
```

3 primary nodes (hash-slot sharded) + 3 replicas. Permission cache, report cache, rate-limit counters are hot keys — Cluster distributes them across nodes.

#### Horizontal scaling (ECS)

```
Minimum tasks:  4   (baseline; ≥ 2,500 concurrent each at 10k total)
Maximum tasks: 20   (50,000 concurrent headroom)

Auto-scale policy: Target Tracking
  Metric:    ALBRequestCountPerTarget
  Target:    2,500 requests/task
  Scale-out cooldown: 60s
  Scale-in  cooldown: 300s    (prevent thrashing on bursty load)

Task spec:  2 vCPU, 4 GB RAM
  (virtual threads are memory-efficient; GC pressure is the ceiling, not thread count)
```

#### Stateless contract (required for horizontal scale)

- No in-memory session state — identity is in the JWT cookie; any pod can serve any request.
- No local file temp storage — upload bytes go directly to S3 via presigned URL.
- No sticky sessions — ALB round-robins freely.
- Redis holds all ephemeral shared state (permission cache, report cache, rate counters).

### 4.2 High Availability / Fault Tolerance

| Concern               | Mitigation                                                                                    |
|-----------------------|-----------------------------------------------------------------------------------------------|
| API pod crash         | ECS auto-replaces; ALB health check `GET /actuator/health/liveness` removes unhealthy task    |
| DB primary failover   | RDS Multi-AZ; ≤60s auto-failover; HikariCP reconnects on next request                        |
| PgBouncer failure     | Run as sidecar per pod (failure scoped to that pod, not all pods)                             |
| Redis Cluster failure | ElastiCache managed Cluster; node failure triggers automatic slot rebalance; cache miss → DB  |
| S3 outage             | Read-only mode; upload attempts return 503 with `Retry-After` header                         |
| Bad deploy            | Blue/green via ECS; auto-rollback on 5xx-rate alarm within 2 min                             |
| Poison event          | Redis Streams DLQ after 3 redeliveries; PagerDuty alert on DLQ depth                         |

Minimum pod count is 4 (not 2) to absorb a pod failure without falling below 10k concurrent capacity.

### 4.3 Performance Targets

| Endpoint                        | p95 budget | Dominant cost                                |
|---------------------------------|------------|----------------------------------------------|
| `POST /auth/login`              | 300 ms     | BCrypt (~200 ms at strength=12)              |
| `GET  /users` (paginated)       | 150 ms     | Index `(status, created_at)`; PgBouncer hop  |
| `GET  /documents?categoryId=`   | 150 ms     | Index `(category_id, created_at desc)`        |
| `POST /documents/presign`       | 100 ms     | DB insert + S3 presign                       |
| `GET  /reports/upload-trend`    | 200 ms     | Redis cache hit (<5 ms); miss → matview query|
| `GET  /settings/me`             | 80 ms      | Single row PK lookup via PgBouncer           |
| `POST /auth/refresh`            | 100 ms     | token_hash lookup + JWT sign                 |

At 10k concurrent, latency is dominated by queue wait in PgBouncer when all 50 Postgres connections are busy. At 10k concurrent × 5 ms avg DB time = 50k DB-ms demand per second; 50 connections × 1000 ms/s = 50k DB-ms supply. The system is at its limit at exactly 10k. Scale to 20 pods (100 PgBouncer connections to Postgres) for 2× headroom.

### 4.4 Security Architecture

#### 4.4.1 Identity & Session — HttpOnly Cookie Model

```
┌─────────────────────────────────────────────────────────────────────┐
│  Cookie: access-token=<JWT>                                         │
│    Attributes: HttpOnly; Secure; SameSite=Strict; Max-Age=900       │
│    Content:    RS256 JWT — { sub, roles, perms, theme, jti }        │
│    JS access:  NONE — browser never exposes this to JavaScript      │
├─────────────────────────────────────────────────────────────────────┤
│  Cookie: refresh-token=<opaque 256-bit>                             │
│    Attributes: HttpOnly; Secure; SameSite=Strict; Path=/auth/refresh│
│    Purpose:    Silent re-issue of access-token on expiry            │
│    Scoped:     Path=/auth/refresh — not sent on any other request   │
│    Revocation: token_hash in DB; reuse detection revokes family     │
└─────────────────────────────────────────────────────────────────────┘
```

**No CSRF cookie.** `SameSite=Strict` prevents the browser from sending either cookie on cross-site requests. The CORS preflight independently blocks unauthorized cross-origin mutations.

Spring Security `SecurityFilterChain`:

```java
@Bean
SecurityFilterChain filterChain(HttpSecurity http,
                                 CorsConfigurationSource corsSource) throws Exception {
    return http
        .cors(cors -> cors.configurationSource(corsSource))
        .csrf(AbstractHttpConfigurer::disable)          // SameSite=Strict + CORS replaces this
        .sessionManagement(s ->
            s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .addFilterBefore(jwtCookieAuthFilter,
            UsernamePasswordAuthenticationFilter.class)
        .authorizeHttpRequests(auth -> auth
            .requestMatchers("/auth/login", "/auth/refresh").permitAll()
            .requestMatchers("/actuator/health/**").permitAll()
            .anyRequest().authenticated())
        .build();
}
```

#### 4.4.2 CORS Configuration

```java
@Bean
CorsConfigurationSource corsConfigurationSource(
        @Value("${app.cors.allowed-origins}") List<String> allowedOrigins) {

    var config = new CorsConfiguration();
    config.setAllowedOrigins(allowedOrigins);   // e.g. ["https://app.company.com","http://localhost:4200"]
                                                // NEVER "*" when allowCredentials=true
    config.setAllowedMethods(List.of(
        "GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
    config.setAllowedHeaders(List.of(
        "Content-Type", "Accept", "Authorization"));
    config.setExposedHeaders(List.of(
        "Content-Disposition",   // file download filename
        "X-Total-Count"));       // pagination total for app-data-table
    config.setAllowCredentials(true);   // required for HttpOnly cookies to cross origins
    config.setMaxAge(3600L);            // cache preflight 1h — critical at 10k concurrent
                                        // without this: every cross-origin mutation = extra OPTIONS round-trip

    var source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", config);
    return source;
}
```

`app.cors.allowed-origins` is environment-specific:
- Production: `https://app.company.com`
- Staging: `https://staging.company.com`
- Development: `http://localhost:4200`

**Why `allowCredentials=true` is required:** HttpOnly cookies are only sent with cross-origin requests when the server responds with `Access-Control-Allow-Credentials: true` AND the browser request sets `credentials: 'include'`. Angular `HttpClient` sets this via `withCredentials: true` (configured globally in `app.config.ts`).

#### 4.4.3 Token Refresh Flow

```
SPA (access-token cookie expired → 401 from API)
  → Angular HTTP interceptor catches 401
  → POST /auth/refresh  (only refresh-token cookie sent, scoped by Path)
     → Spring: lookup token_hash in Postgres
     → verify: not revoked, not expired, family not compromised
     → rotate: revoke old token (UPDATE), issue new refresh (INSERT)
     → sign new JWT (RS256, 15m)
  ← 200 + Set-Cookie: access-token (new, 15m)
         Set-Cookie: refresh-token (new, 7d)
  → Angular interceptor retries the original failed request (now succeeds)
```

#### 4.4.4 Authorization — Server-Side RBAC

Frontend `auth.can(permission)` is advisory UX only. Server enforces via `@EnableMethodSecurity` and `@PreAuthorize`:

| Endpoint group                    | Guard                                               |
|-----------------------------------|-----------------------------------------------------|
| `GET    /users`                   | `@PreAuthorize("hasPermission(null,'usersView')")`  |
| `POST   /users`                   | `@PreAuthorize("hasPermission(null,'usersCreate')")` |
| `PATCH  /users/:id`               | `@PreAuthorize("hasPermission(null,'usersEdit')")`  |
| `DELETE /users/:id`               | `@PreAuthorize("hasPermission(null,'usersDelete')")` |
| `GET/POST/PATCH/DELETE /roles`    | `@PreAuthorize("hasPermission(null,'rolesView/Edit')")` |
| `GET    /reports/*`               | `@PreAuthorize("hasPermission(null,'rolesView')")`  |
| `GET/PUT /settings/me`            | `@PreAuthorize("isAuthenticated()")`                |
| `GET    /audit`                   | `@PreAuthorize("hasPermission(null,'rolesEdit')")`  |
| `/documents` (category-scoped)    | Authenticated + `CategoryAclService` runtime check  |

Custom `PermissionEvaluator` reads permission list from JWT claims; falls back to `perm:{userId}` Redis cache on miss.

Built-in role protection: `@PreAuthorize("@roleGuard.notBuiltIn(#id)")` + DB CHECK `WHERE is_builtin = false`.

#### 4.4.5 Document Access Control

ACL evaluated in order:
1. Document owner → always `canView` + `canDelete`.
2. `category_role_visibility[role]` → category-level flags.
3. `document_acl[user]` → explicit per-user override.

Presigned URL minted only after this check passes. URL TTL = 5 min; content-length cap enforced by S3 bucket policy.

#### 4.4.6 Transport & Hardening

- TLS 1.3 at Nginx; HTTP/2 enabled; HSTS `max-age=31536000; includeSubDomains; preload`.
- CSP: `default-src 'self'; img-src 'self' https://ui-avatars.com data:; script-src 'self'; style-src 'self' 'unsafe-inline'; connect-src 'self'; frame-ancestors 'none'`.
- Rate limits: 200 req/min/IP at Nginx; 10 login attempts/min/IP at Spring (`HandlerInterceptor` + Redis counter `ratelimit:login:{ip}`, TTL 60s); account lockout after 10 failures in 15 min.
- Secrets: AWS Secrets Manager; JWT signing key (RSA-2048) rotated every 180 days.
- Encryption at rest: RDS KMS, S3 SSE-KMS, Redis encryption-in-transit/at-rest.

#### 4.4.7 PII Handling

`user.email` and `user.display_name` are PII. Audit log records `actor_user_id` (UUID), never email. Logback deny-list pattern rejects log events containing `password`, `token`, `email`, `displayName`. GDPR erasure: tombstone the row (`email = 'deleted+{uuid}@invalid'`, `display_name = '[deleted]'`); FK rows preserved for audit integrity.

### 4.5 Observability

| Signal   | Tool                               | Detail                                                                    |
|----------|------------------------------------|---------------------------------------------------------------------------|
| Logs     | SLF4J + Logback → Loki             | JSON; traceId, spanId, userId, route, HTTP status, latency_ms             |
| Metrics  | Micrometer → Prometheus → Grafana  | RED per route; virtual thread count; PgBouncer pool utilisation; cache hit% |
| Traces   | OTel (auto) → Tempo                | 10% sampling, 100% on 5xx; traces span API → PgBouncer → Postgres        |
| Audit    | `audit_event` table (Postgres)     | Append-only, partitioned monthly, 1-year retention                        |
| Alerts   | Grafana → PagerDuty                | 5xx > 1%/5min (P1); p95 > 2× budget/10min (P1); PgBouncer pool > 80% (P2)|

PgBouncer-specific metrics to monitor:
- `pgbouncer_pools_cl_active` — active client connections (alert if > 400 per PgBouncer).
- `pgbouncer_pools_sv_active` — active server connections (alert if > 45/50).
- `pgbouncer_pools_cl_waiting` — clients waiting for a server connection (alert if > 0 sustained for > 30s).

### 4.6 Disaster Recovery and Backup

| Asset         | Backup                                              | RPO    | RTO   |
|---------------|-----------------------------------------------------|--------|-------|
| Postgres      | RDS automated daily snapshot + 5-min PITR (WAL)     | 5 min  | 1 h   |
| S3 documents  | S3 versioning + cross-region replication            | 15 min | 1 h   |
| Redis Cluster | Not backed up (cache only; cold start = degraded perf, not data loss) | n/a | 5 min |
| Audit log     | Postgres backup + monthly export to S3 Glacier      | 5 min  | 4 h   |

DR drill: quarterly restore-to-staging from snapshots; documented runbook in IT/DevOps wiki.

---

## Phase 5 — Data Architecture

### 5.1 Entity Catalogue

| Entity                     | Key fields                                                                                                               | PII | Retention              |
|----------------------------|--------------------------------------------------------------------------------------------------------------------------|-----|------------------------|
| `user`                     | id (uuid pk), email (uniq, citext), display_name, status, role_id (fk), created_at, updated_at                          | Yes | While active + 1y      |
| `credential`               | user_id (pk/fk), password_hash, algo, updated_at                                                                        | No  | While user active      |
| `password_history`         | user_id, password_hash, created_at                                                                                      | No  | Last 5 entries         |
| `refresh_token`            | id (uuid), user_id (fk), token_hash, family_id, issued_at, expires_at, revoked_at                                      | No  | 30 days post-expiry    |
| `role`                     | id (uuid pk), name (uniq), description, is_builtin (bool)                                                               | No  | Lifetime               |
| `permission`               | id, code (uniq, e.g. `usersDelete`), description                                                                        | No  | Lifetime               |
| `role_permission`          | role_id (fk), permission_id (fk), pk(role_id, permission_id)                                                            | No  | Lifetime               |
| `user_role`                | user_id (fk), role_id (fk), pk(user_id, role_id)                                                                        | No  | Lifetime               |
| `document_category`        | id (uuid pk), name, slug, sort_order, created_at                                                                        | No  | Lifetime               |
| `category_role_visibility` | category_id (fk), role_id (fk), can_view, can_upload, can_delete                                                       | No  | Lifetime               |
| `document`                 | id (uuid pk), category_id (fk), owner_id (fk), filename, mime, size_bytes, object_key, status, created_at              | No  | Per category policy    |
| `document_acl`             | document_id (fk), user_id (fk), can_view, can_delete                                                                   | No  | While document active  |
| `user_setting`             | user_id (pk/fk), theme, language, date_format, page_size, notif_email, notif_push, notif_desktop                        | No  | While user active      |
| `audit_event`              | id (uuid), occurred_at, actor_user_id (fk), action, resource_type, resource_id, ip, user_agent, payload (jsonb)        | No (IP borderline) | 1 year then archive |
| `notification`             | id, user_id (fk), channel, payload, status, created_at                                                                  | No  | 90 days                |
| `push_subscription`        | id, user_id (fk), endpoint, p256dh, auth, created_at                                                                   | No  | While valid            |

### 5.2 ERD Shorthand

```
user (1) --< user_role >-- (N) role (1) --< role_permission >-- (N) permission
user (1) --- (1) credential
user (1) --- (0..1) user_setting
user (1) --< document    (as owner)
user (1) --< audit_event (as actor)
user (1) --< push_subscription
user (1) --< refresh_token

document_category (1) --< document
document_category (1) --< category_role_visibility >-- (N) role
document (1) --< document_acl >-- (N) user
```

Notable indexes:
- `user (email)` — unique, `citext`.
- `document (category_id, created_at desc)` — list view.
- `document (owner_id, created_at desc)` — my-uploads.
- `refresh_token (token_hash)` — lookup; `(user_id, family_id)` for family revocation.
- `audit_event (occurred_at desc)` — partitioned monthly by `PARTITION BY RANGE`.
- Materialized view `mv_upload_trend(month, count)` — refreshed (debounced 60s) on `DocumentUploaded`.
- Materialized view `mv_storage_by_category(category_id, total_bytes)` — same trigger.

### 5.3 Data Flow Maps

#### 5.3.1 Login

```
SPA --(email, password)--> POST /auth/login
  → CORS preflight: OPTIONS validates origin → proceed
  → user lookup by email (Postgres via PgBouncer)
  → BCrypt verify (~200 ms)
  → load user_role + role_permission (Postgres)
  → cache perm:{userId} in Redis Cluster (TTL 10m)
  → load user_setting.theme
  → sign JWT (RS256, 15m), generate opaque refresh token
  → INSERT refresh_token (Postgres)
  → audit_event: LoginSucceeded | LoginFailed
  ← 200 body: { user, perms, theme }
  ← Set-Cookie: access-token (HttpOnly, SameSite=Strict, 15m)
  ← Set-Cookie: refresh-token (HttpOnly, SameSite=Strict, Path=/auth/refresh, 7d)
SPA writes theme to localStorage → sets <html data-theme>
No token in sessionStorage or JS memory
```

#### 5.3.2 Token Refresh

```
SPA (401 interceptor) --> POST /auth/refresh
  (only refresh-token cookie sent — Path=/auth/refresh scoping)
  → JWT cookie filter: no access-token → pass to refresh endpoint
  → lookup token_hash in Postgres; verify not revoked, not expired
  → reuse detection: if family_id already rotated → revoke entire family
  → rotate: revoke old token (UPDATE), issue new (INSERT)
  → sign new JWT (RS256, 15m)
  ← 200 + Set-Cookie: access-token (new) + Set-Cookie: refresh-token (new)
Angular interceptor retries original request with new access-token cookie
```

#### 5.3.3 Document Upload

```
SPA --> POST /documents/presign  (access-token cookie + CORS)
  → JWT filter validates access-token cookie
  → CategoryAclService.canUpload(category, role)
  → INSERT document(status=pending) (Postgres via PgBouncer)
  → mint S3 PUT URL (5 min, size cap via S3 bucket policy)
SPA --> S3 directly (presigned URL — no auth cookie involved)
SPA --> POST /documents/{id}/commit
  → S3 HEAD validates etag/size
  → UPDATE document SET status=ready (Postgres)
  → publish DocumentUploaded (Redis Streams)
    → invalidate report cache
    → audit_event
    → refresh mv_upload_trend (debounced 60s)
    → notify subscribers
```

#### 5.3.4 Settings Change

```
SPA --> PUT /settings/me  (access-token cookie + CORS)
  → upsert user_setting (Postgres)
  → return canonical settings
SPA writes theme to localStorage (write-through pre-paint cache)
SPA applies <html data-theme> immediately
Bootstrap: SPA reads localStorage theme first (pre-paint),
  then calls GET /settings/me after cookie resolves to reconcile
```

### 5.4 Storage Selection per Entity

| Entity                              | Store                    | Rationale                                                       |
|-------------------------------------|--------------------------|-----------------------------------------------------------------|
| user, credential, role, permission, user_role, role_permission | Postgres | ACID, relational joins, transactional |
| document (metadata)                 | Postgres                 | Joined with category/user; small rows                           |
| document (binary)                   | S3                       | Large blobs; bytes bypass API pods entirely                     |
| user_setting                        | Postgres                 | Single row per user, transactional with profile updates         |
| audit_event                         | Postgres (partitioned)   | SQL ad-hoc queries; monthly partition sweeps for retention      |
| refresh_token                       | Postgres                 | Must be revocable atomically; family-based revocation           |
| permission cache `perm:{userId}`    | Redis Cluster            | Sub-ms; evicted on RoleChanged / UserUpdated                    |
| report cache                        | Redis Cluster            | TTL 5m + event-driven invalidation                              |
| event outbox                        | Redis Streams            | At-least-once; consumer groups; simple ops                      |
| rate-limit counters                 | Redis Cluster            | TTL-based; atomic INCR                                          |
| notifications                       | Postgres                 | Joinable with user; low volume                                  |

### 5.5 Data Classification

| Class     | Examples                                                           | Handling                                                          |
|-----------|--------------------------------------------------------------------|-------------------------------------------------------------------|
| PII       | `user.email`, `user.display_name`                                  | RDS KMS at rest; never logged; tombstoned on erasure              |
| Secret    | `credential.password_hash`, `refresh_token.token_hash`, VAPID key | Never logged; accessed via Secrets Manager; rotation enforced     |
| Sensitive | `audit_event.ip`, `audit_event.user_agent`, document binaries     | Admin-only (audit); per-role ACL (documents)                      |
| Internal  | role/permission codes, category metadata, settings                 | Standard internal access control                                  |
| Public    | None                                                               | n/a                                                               |

Logback deny-list: `password`, `token`, `email`, `displayName`. PRs touching log call-sites require security gate in CI.

---

## Appendix A — REST API Surface

```
POST   /auth/login                     → Sets access-token + refresh-token cookies
POST   /auth/logout                    → Clears all auth cookies; revokes refresh token
POST   /auth/refresh                   → Rotates cookies (called by Angular interceptor on 401)
POST   /auth/password                  → { currentPassword, newPassword }

GET    /users                          ?q=&page=&pageSize=&sort=
POST   /users
PATCH  /users/:id
DELETE /users/:id

GET    /roles
POST   /roles
PATCH  /roles/:id                      (rejected if is_builtin)
DELETE /roles/:id                      (rejected if is_builtin)

GET    /categories
POST   /categories
PATCH  /categories/:id
PUT    /categories/:id/visibility

GET    /documents                      ?categoryId=&page=&pageSize=
POST   /documents/presign
POST   /documents/:id/commit
GET    /documents/:id/url              → returns presigned S3 GET URL (5 min)
DELETE /documents/:id

GET    /reports/upload-trend
GET    /reports/by-category
GET    /reports/role-distribution
GET    /reports/storage-by-category

GET    /settings/me
PUT    /settings/me
POST   /settings/me/reset

GET    /profile
PATCH  /profile                        { displayName }

GET    /audit                          (Admin only)

GET    /actuator/health/liveness       (internal port — ECS liveness probe)
GET    /actuator/health/readiness      (internal port — checks DB+Redis+S3)
```

No `X-XSRF-TOKEN` header required on any endpoint. Authentication is via `access-token` HttpOnly cookie (auto-sent by browser when `withCredentials: true`).

---

## Appendix B — Module-Boundary Lint Rules (ArchUnit)

```java
// src/test/java/architecture/BoundaryTest.java
noClasses().that().resideInPackage("..iam..")
    .should().accessClassesThat().resideInPackage("..content..");

noClasses().that().resideInPackage("..content.docs..")
    .should().accessClassesThat().resideInPackage("..content.cats..internal..");

noClasses().that().resideInPackage("..personal..")
    .should().accessClassesThat().resideInPackage("..content..");

noClasses().that().resideInPackage("..compliance..")
    .should().accessClassesThat()
    .resideInAPackage("..iam..").and().notResideInPackage("..iam.users..");

// Cross-module access via Service class only — no repository leakage
noClasses().that().resideOutsideOfPackage("..iam.users..")
    .should().accessClassesThat().resideInPackage("..iam.users..repository..");
```

---

## Appendix C — Mapping Frontend Today → Backend Tomorrow

| Frontend (current demo)                  | Backend (proposed)                                                   |
|------------------------------------------|----------------------------------------------------------------------|
| `AuthService` in-memory user list        | `iam.auth` + `iam.users` over PostgreSQL via PgBouncer               |
| `sessionStorage['demo_user']`            | **Removed** — identity in `access-token` HttpOnly cookie             |
| `localStorage['app_settings']`           | `user_setting` table; `localStorage` is pre-paint theme cache only  |
| `auth.can(permission)` route guards      | Advisory UX only; server enforces via `@PreAuthorize`                |
| Mock `DocumentCategoryStore`             | `content.cats` + `content.docs` Spring services                     |
| Highcharts data hard-coded               | `/reports/*` endpoints backed by materialized views + Redis cache    |
| `ui-avatars.com` avatars                 | Keep as-is in v1; replaceable later                                  |
| Built-in role hard-coding                | `role.is_builtin` DB column + ArchUnit guard + `@PreAuthorize`       |
| `HttpClient` (no credentials)            | Add `withCredentials: true` globally in `app.config.ts`             |
| No CORS handling                         | `CorsConfigurationSource` bean with explicit `allowedOrigins` list   |
| No CSRF handling needed                  | CSRF disabled in Spring Security; `SameSite=Strict` provides cover  |

---

## Appendix D — Adjacent Services

### CV Batch Extractor

A standalone Python microservice that handles CV document extraction outside the Spring Boot process. It watches the upload volume, runs OCR and LLM extraction, validates the result through a guardrail pipeline, and calls back the backend via `POST /api/v1/cv-candidates`.

**Integration point with this backend:**

| Direction | Contract |
|---|---|
| Extractor → Backend | `POST /api/v1/cv-candidates` with `{ documentId, documentCategoryId, jsonFile, extractionStatus, guardrailWarnings }` |
| Backend response | `201 Created` (PASS/DEGRADED with candidate) or `200 OK` (REJECTED/ERROR — document status set to FAILED) |
| Backend side-effect | `document.extractionStatus` updated: `PASS`/`DEGRADED` → `COMPLETED`; `REJECTED`/`ERROR` → `FAILED` |

**Dedicated documentation:**

| Document | Path | Contents |
|---|---|---|
| Architecture | [cv_batch_extractor/ARCHITECTURE.md](cv_batch_extractor/ARCHITECTURE.md) | Enterprise module structure, 10-stage pipeline flow, OCR adapters, LLM adapters with circuit breaker, worker pool, monitoring |
| Domain Model | [cv_batch_extractor/DOMAIN_MODEL.md](cv_batch_extractor/DOMAIN_MODEL.md) | `PipelineContext`, `ProcessingResult`, `CvExtraction` Pydantic schema, settings reference, OCR/LLM adapter contracts |
| Guardrails Spec | [cv_batch_extractor/GUARDRAILS_SPEC.md](cv_batch_extractor/GUARDRAILS_SPEC.md) | Per-stage validators (file size, MIME type, text length/quality, injection, JSON parse, schema, confidence, hallucination, normalisation), status aggregation rules |
