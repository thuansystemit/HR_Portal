# JaCoCo Code Coverage Setup -- Design Document

**Project:** `demo-app-backend`
**Path:** `/Users/pvthuan/work/HR_Portal/demo-app-backend`
**Build:** Maven, Spring Boot 3.3.5, Java 21
**JaCoCo version:** 0.8.12 (already declared in `pom.xml`)

---

## 1. Current State

### What is configured

The `pom.xml` at `/Users/pvthuan/work/HR_Portal/demo-app-backend/pom.xml` (lines 174-189) declares the JaCoCo Maven plugin with two executions:

```xml
<plugin>
    <groupId>org.jacoco</groupId>
    <artifactId>jacoco-maven-plugin</artifactId>
    <version>0.8.12</version>
    <executions>
        <execution>
            <id>prepare-agent</id>
            <goals><goal>prepare-agent</goal></goals>
        </execution>
        <execution>
            <id>report</id>
            <phase>verify</phase>
            <goals><goal>report</goal></goals>
        </execution>
    </executions>
</plugin>
```

### What is missing

| Gap | Impact |
|-----|--------|
| No `check` execution | Build never fails on low coverage -- thresholds are not enforced |
| No class/package exclusions | Coverage denominator includes generated code (MapStruct `UserMapperImpl`), Lombok-generated entity classes, DTOs (records), config classes, and exception classes -- all of which inflate the gap between measured and meaningful coverage |
| No CI integration guidance | `mvn verify` produces a report but no one reads it; HTML report is not surfaced as a build artifact |

### Existing tests

| Test file | Layer | What it covers |
|-----------|-------|---------------|
| `AuthServiceTest` | Unit (Mockito) | `AuthService.login` (5 scenarios), `sha256`, `logout` |
| `UserServiceTest` | Unit (Mockito) | `UserService.findById`, `create`, `delete`, `list` |
| `DocumentCategoryServiceTest` | Unit (Mockito) | `DocumentCategoryService.listForRole`, `create`, `update`, `delete`, `findById` |
| `RedisIdempotencyServiceTest` | Unit (Mockito) | `RedisIdempotencyService.findCached` (3 scenarios), `store` |
| `BaseIntegrationTest` | Base class | Provides Testcontainers PostgreSQL setup; no test classes extend it yet |

### Services without any test coverage

| Service | Package | Methods needing tests |
|---------|---------|----------------------|
| `RoleService` | `iam.service` | `list`, `create`, `findById`, `update`, `delete` |
| `JwtService` | `iam.service` | `generateAccessToken`, `validateAndParse` |
| `PermissionCacheService` | `iam.service` | `loadPermissions`, `evict` |
| `DocumentService` | `content.service` | `list`, `upload`, `download`, `delete`, `updateExtractionStatus` (both overloads) |
| `StorageService` | `content.service` | `store`, `load`, `delete` |
| `CvCandidateService` | `cv.service` | `ingest`, `create`, `findById`, `findByDocumentId`, `listByCategory`, `delete` |
| `CvExtractionResultMapper` | `cv.extraction` | `toRequest`, `normalizeConfidence`, `parseDate` |
| `AuditService` | `compliance.service` | `log` |
| `ReportService` | `insights.service` | `uploadTrend`, `categoryCount`, `storageByCategory`, `roleDistribution`, `refreshMaterializedViews` |
| `UserSettingsService` | `personal.service` | `findByUserId`, `update` |
| `GlobalExceptionHandler` | `platform.handler` | all `@ExceptionHandler` methods |

### Partially tested services

| Service | Missing coverage |
|---------|-----------------|
| `AuthService` | `refresh`, `getMe`, `changePassword`, `handleFailedLogin` (account lock at 5 failures) |
| `UserService` | `update` |

---

## 2. Recommended JaCoCo Maven Plugin Configuration

Replace the existing `jacoco-maven-plugin` block in `pom.xml` (lines 174-189) with the following. The `prepare-agent` and `report` executions are preserved; a `check` execution is added.

```xml
<plugin>
    <groupId>org.jacoco</groupId>
    <artifactId>jacoco-maven-plugin</artifactId>
    <version>0.8.12</version>
    <executions>
        <!-- 1. Instrument classes for coverage data collection -->
        <execution>
            <id>prepare-agent</id>
            <goals>
                <goal>prepare-agent</goal>
            </goals>
        </execution>

        <!-- 2. Generate HTML + XML + CSV reports after tests run -->
        <execution>
            <id>report</id>
            <phase>verify</phase>
            <goals>
                <goal>report</goal>
            </goals>
        </execution>

        <!-- 3. Enforce coverage thresholds -- fail build if not met -->
        <execution>
            <id>check</id>
            <phase>verify</phase>
            <goals>
                <goal>check</goal>
            </goals>
            <configuration>
                <rules>
                    <rule>
                        <element>BUNDLE</element>
                        <limits>
                            <limit>
                                <counter>LINE</counter>
                                <value>COVEREDRATIO</value>
                                <minimum>0.70</minimum>
                            </limit>
                            <limit>
                                <counter>BRANCH</counter>
                                <value>COVEREDRATIO</value>
                                <minimum>0.60</minimum>
                            </limit>
                        </limits>
                    </rule>
                </rules>
                <excludes>
                    <!--
                        EXCLUSION RATIONALE:
                        These classes are structural / generated / trivial.
                        Covering them adds noise without catching real bugs.
                    -->

                    <!-- Application entry point (just main() method) -->
                    <exclude>com/demo/app/DemoAppApplication.class</exclude>

                    <!-- Spring @Configuration classes (wiring only) -->
                    <exclude>com/demo/app/config/**</exclude>

                    <!-- DTO records / request / response objects (no logic) -->
                    <exclude>**/dto/**</exclude>

                    <!-- JPA entity classes (Lombok-generated, no logic) -->
                    <exclude>**/entity/**</exclude>

                    <!-- Exception classes (trivial constructors) -->
                    <exclude>**/exception/**</exclude>

                    <!-- MapStruct generated mapper implementations -->
                    <exclude>**/*MapperImpl.class</exclude>

                    <!-- Spring Data JPA repository interfaces (no implementation code) -->
                    <exclude>**/repository/**</exclude>
                </excludes>
            </configuration>
        </execution>
    </executions>
    <configuration>
        <excludes>
            <!--
                Global excludes applied to both agent instrumentation
                and report generation. Keeps the HTML report clean.
            -->
            <exclude>com/demo/app/DemoAppApplication.class</exclude>
            <exclude>com/demo/app/config/**</exclude>
            <exclude>**/dto/**</exclude>
            <exclude>**/entity/**</exclude>
            <exclude>**/exception/**</exclude>
            <exclude>**/*MapperImpl.class</exclude>
            <exclude>**/repository/**</exclude>
        </excludes>
    </configuration>
</plugin>
```

### Exclusion rationale summary

| Excluded pattern | Reason |
|-----------------|--------|
| `DemoAppApplication.class` | Contains only `main()` -- cannot unit test meaningfully |
| `com/demo/app/config/**` | `CorsConfig`, `SecurityConfig`, `JwtConfig`, `RedisConfig`, `StorageConfig` -- Spring wiring only; tested indirectly via integration tests |
| `**/dto/**` | Java records with no logic: `LoginRequest`, `AuthResponse`, `UserResponse`, `CategoryResponse`, etc. |
| `**/entity/**` | JPA entities with Lombok `@Data`/`@Builder` -- generated getters/setters/builders |
| `**/exception/**` | `ResourceNotFoundException`, `ConflictException`, etc. -- trivial constructors extending `RuntimeException` |
| `**/*MapperImpl.class` | MapStruct-generated implementation of `UserMapper` interface |
| `**/repository/**` | Spring Data JPA interfaces -- no hand-written code to cover |

### Classes intentionally NOT excluded

These contain real business logic and MUST be covered:

| Class | Package | Why it must be covered |
|-------|---------|----------------------|
| `AuthService` | `iam.service` | Authentication flow, cookie handling, token rotation, account locking |
| `UserService` | `iam.service` | User CRUD, role assignment, soft delete |
| `RoleService` | `iam.service` | Role CRUD, permission management, built-in role protection |
| `JwtService` | `iam.service` | Token generation and validation -- security critical |
| `PermissionCacheService` | `iam.service` | Permission loading and cache eviction |
| `DocumentCategoryService` | `content.service` | Category CRUD, role-based visibility filtering |
| `DocumentService` | `content.service` | Upload, download, delete, extraction status updates |
| `StorageService` | `content.service` | File I/O -- store, load, delete |
| `CvCandidateService` | `cv.service` | CV ingestion, candidate CRUD with child entities |
| `CvExtractionResultMapper` | `cv.extraction` | Data mapping with null handling, date parsing, confidence normalization |
| `AuditService` | `compliance.service` | Audit event persistence |
| `ReportService` | `insights.service` | Native queries against materialized views |
| `UserSettingsService` | `personal.service` | Settings CRUD with defaults |
| `GlobalExceptionHandler` | `platform.handler` | Exception-to-HTTP-status mapping |
| `RedisIdempotencyService` | `platform.idempotency` | Redis caching for idempotency |
| `JwtCookieAuthFilter` | `iam.security` | Security filter -- JWT extraction and auth context setup |
| `InternalApiKeyFilter` | `iam.security` | API key auth filter for internal services |

---

## 3. Test Strategy per Layer

### 3.1 Unit Tests (Service Layer) -- Mockito

**Pattern:** `@ExtendWith(MockitoExtension.class)`, `@Mock` for all dependencies, `@InjectMocks` for the class under test. Use AssertJ assertions. Follow the naming convention `{method}_{condition}_{expectedOutcome}`.

**File location:** Mirror the source package under `src/test/java`. Suffix with `Test`.

#### Tests that already exist (keep as-is)

- `AuthServiceTest` -- 7 test methods
- `UserServiceTest` -- 7 test methods
- `DocumentCategoryServiceTest` -- 7 test methods
- `RedisIdempotencyServiceTest` -- 4 test methods

#### Tests to create

##### 3.1.1 `RoleServiceTest`

**File:** `src/test/java/com/demo/app/iam/service/RoleServiceTest.java`
**Mocks:** `RoleRepository`, `PermissionRepository`, `RolePermissionRepository`, `UserRoleRepository`

| Test method | Scenario |
|------------|----------|
| `list_returnsPaged` | Returns paginated list of roles with permissions and user counts |
| `create_succeeds_whenNameAvailable` | Saves role, saves permissions, returns response |
| `create_throws_whenNameExists` | Throws `ConflictException` |
| `findById_returnsResponse_whenFound` | Returns role with permissions |
| `findById_throws_whenNotFound` | Throws `ResourceNotFoundException` |
| `update_succeeds_whenNotBuiltin` | Updates name/description, replaces permissions |
| `update_throws_whenBuiltin` | Throws `BuiltInRoleException` |
| `update_throws_whenNotFound` | Throws `ResourceNotFoundException` |
| `delete_succeeds_whenNoUsers` | Deletes permissions then role |
| `delete_throws_whenBuiltin` | Throws `BuiltInRoleException` |
| `delete_throws_whenHasAssignedUsers` | Throws `BusinessRuleException` |
| `delete_throws_whenNotFound` | Throws `ResourceNotFoundException` |

##### 3.1.2 `JwtServiceTest`

**File:** `src/test/java/com/demo/app/iam/service/JwtServiceTest.java`
**Setup:** Create a real `JwtConfig` with a test RSA key pair (generate in `@BeforeAll`). No mocks needed.

| Test method | Scenario |
|------------|----------|
| `generateAccessToken_containsSubjectAndClaims` | Token contains userId as subject, roleId, permissions |
| `validateAndParse_returnsClaimsForValidToken` | Round-trip: generate then validate |
| `validateAndParse_throwsForExpiredToken` | Expired token throws JwtException |
| `validateAndParse_throwsForTamperedToken` | Modified token throws JwtException |

##### 3.1.3 `PermissionCacheServiceTest`

**File:** `src/test/java/com/demo/app/iam/service/PermissionCacheServiceTest.java`
**Mocks:** `UserRoleRepository`, `RolePermissionRepository`, `PermissionRepository`

| Test method | Scenario |
|------------|----------|
| `loadPermissions_returnsCodesAndRoleId_whenRolesExist` | User has a role with permissions |
| `loadPermissions_returnsEmpty_whenNoRoles` | User has no roles -- returns null roleId and empty set |
| `evict_executesWithoutError` | Verify method completes (cache eviction is a Spring concern) |

##### 3.1.4 `DocumentServiceTest`

**File:** `src/test/java/com/demo/app/content/service/DocumentServiceTest.java`
**Mocks:** `DocumentRepository`, `DocumentCategoryRepository`, `StorageService`, `ReportService`

| Test method | Scenario |
|------------|----------|
| `list_returnsPaged` | Returns documents for category |
| `upload_succeeds_forNonCvType` | Creates document, stores file, increments count |
| `upload_setsPendingExtraction_forCvType` | Category has `DocumentType.CV`, sets `extractionStatus = "PENDING"` |
| `upload_throws_whenCategoryNotFound` | Throws `ResourceNotFoundException` |
| `download_returnsResource` | Returns `DownloadResult` with resource, mime type, filename |
| `download_throws_whenNotFound` | Throws `ResourceNotFoundException` |
| `delete_softDeletesAndDecrementsCount` | Sets `deletedAt`, calls `storageService.delete`, decrements category count |
| `delete_throws_whenNotFound` | Throws `ResourceNotFoundException` |
| `updateExtractionStatus_updatesStatus` | Two-arg overload sets status and clears error |
| `updateExtractionStatus_updatesStatusWithError` | Four-arg overload sets status and error message |

##### 3.1.5 `StorageServiceTest`

**File:** `src/test/java/com/demo/app/content/service/StorageServiceTest.java`
**Setup:** Use `@TempDir` for the `uploadRoot` path. No mocks -- test real file I/O against a temp directory.

| Test method | Scenario |
|------------|----------|
| `store_writesFileToDisk` | File exists at expected path after store |
| `store_createsParentDirectories` | Nested path directories are created |
| `load_returnsResource` | Returns `FileSystemResource` for existing file |
| `delete_removesFile` | File no longer exists after delete |
| `delete_succeedsWhenFileDoesNotExist` | No exception on missing file |

##### 3.1.6 `CvCandidateServiceTest`

**File:** `src/test/java/com/demo/app/cv/service/CvCandidateServiceTest.java`
**Mocks:** `CvCandidateRepository`, `CvWorkExperienceRepository`, `CvEducationRepository`, `CvTechnicalSkillRepository`, `CvLanguageRepository`, `CvCertificationRepository`, `CvExtractionResultMapper`, `DocumentService`, `ObjectMapper`

| Test method | Scenario |
|------------|----------|
| `create_savesCandidate_andChildEntities` | Candidate + work exp + edu + skills + languages + certifications all saved |
| `create_throws_whenDocumentAlreadyExtracted` | Throws `ConflictException` |
| `create_handlesNullChildLists` | Null work experiences / educations / etc. do not throw NPE |
| `findById_returnsResponse` | Returns fully populated response |
| `findById_throws_whenNotFound` | Throws `ResourceNotFoundException` |
| `findByDocumentId_returnsResponse` | Returns candidate by document ID |
| `findByDocumentId_throws_whenNotFound` | Throws `ResourceNotFoundException` |
| `listByCategory_returnsList` | Returns list for category |
| `delete_removesCandidate` | Calls `candidateRepository.delete` |
| `delete_throws_whenNotFound` | Throws `ResourceNotFoundException` |

##### 3.1.7 `CvExtractionResultMapperTest`

**File:** `src/test/java/com/demo/app/cv/extraction/CvExtractionResultMapperTest.java`
**Setup:** No mocks -- plain instantiation.

| Test method | Scenario |
|------------|----------|
| `toRequest_mapsAllFields` | All fields from `CvExtractionResult` appear in `CreateCvCandidateRequest` |
| `toRequest_defaultsFullNameToUnknown_whenNull` | Null fullName becomes "Unknown" |
| `normalizeConfidence_highMediumLow` | "HIGH" -> "HIGH", "MEDIUM" -> "MEDIUM", "low" -> "LOW", null -> "LOW" |
| `parseDate_validIsoDate` | "2024-01-15" parses to `LocalDate` |
| `parseDate_returnsNull_forInvalid` | "not-a-date" returns null (no exception) |
| `parseDate_returnsNull_forNull` | null returns null |
| `mapWorkExperiences_handlesNullList` | null input returns null |
| `mapWorkExperiences_defaultsNullCompanyAndTitle` | Null company/title become "Unknown" |
| `mapLanguages_filtersBlankLanguage` | Language with blank name is excluded |

##### 3.1.8 `AuditServiceTest`

**File:** `src/test/java/com/demo/app/compliance/service/AuditServiceTest.java`
**Mocks:** `AuditEventRepository`

| Test method | Scenario |
|------------|----------|
| `log_savesAuditEvent` | Saves an `AuditEvent` with correct fields |
| `log_doesNotThrow_whenRepositoryFails` | Repository throws exception -- method catches it silently |

##### 3.1.9 `ReportServiceTest`

**File:** `src/test/java/com/demo/app/insights/service/ReportServiceTest.java`
**Mocks:** `EntityManager`, `Query`

Note: `ReportService` uses native queries via `EntityManager`. Mock the `EntityManager` and the `Query` returned by `createNativeQuery`. This tests the mapping logic (Object[] -> DTO), not the SQL.

| Test method | Scenario |
|------------|----------|
| `uploadTrend_mapsRowsCorrectly` | Object[] rows map to `UploadTrendEntry` |
| `uploadTrend_returnsEmptyList` | Empty result set returns empty list |
| `categoryCount_mapsRowsCorrectly` | Object[] rows map to `CategoryCountEntry` |
| `storageByCategory_mapsRowsCorrectly` | Object[] rows map to `StorageEntry` |
| `roleDistribution_mapsRowsCorrectly` | Object[] rows map to `RoleDistributionEntry` |
| `refreshMaterializedViews_executesQueries` | Both `REFRESH MATERIALIZED VIEW` queries are executed |

##### 3.1.10 `UserSettingsServiceTest`

**File:** `src/test/java/com/demo/app/personal/service/UserSettingsServiceTest.java`
**Mocks:** `UserSettingsRepository`

| Test method | Scenario |
|------------|----------|
| `findByUserId_returnsSettings_whenExists` | Returns persisted settings |
| `findByUserId_returnsDefaults_whenNotExists` | Returns default settings (no entity in DB) |
| `update_savesAndReturnsUpdated` | All fields are updated and returned |
| `update_createsNew_whenNotExists` | Non-existing user gets new settings |

##### 3.1.11 `GlobalExceptionHandlerTest`

**File:** `src/test/java/com/demo/app/platform/handler/GlobalExceptionHandlerTest.java`
**Setup:** Instantiate `GlobalExceptionHandler` directly. Call each handler method and assert the returned `ResponseEntity` status and body.

| Test method | Scenario |
|------------|----------|
| `handleNotFound_returns404` | `ResourceNotFoundException` -> 404 |
| `handleConflict_returns409` | `ConflictException` -> 409 |
| `handleBuiltIn_returns409` | `BuiltInRoleException` -> 409 |
| `handleWrongPassword_returns422` | `WrongPasswordException` -> 422 |
| `handleForbidden_returns403` | `ForbiddenException` -> 403 |
| `handleBusinessRule_returns422` | `BusinessRuleException` -> 422 |
| `handleValidation_returns400_withFieldErrors` | `MethodArgumentNotValidException` -> 400 with field error map |
| `handleAuth_returns401` | `AuthenticationException` -> 401 |
| `handleAccessDenied_returns403` | `AccessDeniedException` -> 403 |
| `handleGeneral_returns500` | Generic `Exception` -> 500, message is generic (does not leak internals) |

##### 3.1.12 Additional tests for existing services (coverage gaps)

**File:** `src/test/java/com/demo/app/iam/service/AuthServiceTest.java` (add to existing file)

| Test method | Scenario |
|------------|----------|
| `refresh_succeeds_withValidToken` | Rotates refresh token, issues new access token, sets cookies |
| `refresh_throws_whenNoRefreshCookie` | Throws `ForbiddenException` |
| `refresh_throws_whenTokenExpired` | Revokes token and throws `ForbiddenException` |
| `refresh_throws_whenTokenNotFound` | Throws `ForbiddenException` |
| `getMe_returnsUserInfo` | Returns `UserInfo` with permissions and role name |
| `getMe_throws_whenNotFound` | Throws `ResourceNotFoundException` |
| `changePassword_succeeds` | Updates hash, evicts cache |
| `changePassword_throws_whenCurrentPasswordWrong` | Throws `WrongPasswordException` |
| `handleFailedLogin_locksAfterFiveAttempts` | Verifies `lockedUntil` is set when `failedAttempts >= 5` |

**File:** `src/test/java/com/demo/app/iam/service/UserServiceTest.java` (add to existing file)

| Test method | Scenario |
|------------|----------|
| `update_succeeds_whenRoleChanged` | Deletes old role assignment, creates new, evicts cache |
| `update_succeeds_whenRoleSame` | Does not delete/recreate role assignment |
| `update_throws_whenNotFound` | Throws `ResourceNotFoundException` |
| `update_throws_whenRoleNotFound` | Throws `ResourceNotFoundException` |

##### 3.1.13 Security filter tests

**File:** `src/test/java/com/demo/app/iam/security/JwtCookieAuthFilterTest.java`
**Setup:** Mock `JwtService`, use `MockHttpServletRequest`/`MockHttpServletResponse`/`MockFilterChain`.

| Test method | Scenario |
|------------|----------|
| `doFilter_setsAuthentication_whenValidCookiePresent` | SecurityContext gets populated |
| `doFilter_skips_whenNoCookie` | Chain continues, no authentication set |
| `doFilter_skips_whenJwtInvalid` | Chain continues, no authentication set |
| `doFilter_skips_whenAuthenticationAlreadySet` | Existing auth is not overwritten |

**File:** `src/test/java/com/demo/app/iam/security/InternalApiKeyFilterTest.java`
**Setup:** Use `ReflectionTestUtils` to set the `internalApiKey` field.

| Test method | Scenario |
|------------|----------|
| `doFilter_setsServiceAuth_whenKeyMatches` | SecurityContext gets `ROLE_SERVICE` authority |
| `doFilter_skips_whenKeyMissing` | No authentication set |
| `doFilter_skips_whenKeyWrong` | No authentication set |
| `doFilter_skips_whenKeyBlank` | Blank configured key -- all requests pass through without service auth |

### 3.2 Integration Tests -- Testcontainers

**Base class:** `BaseIntegrationTest` at `src/test/java/com/demo/app/BaseIntegrationTest.java`

The base class already provides:
- `@SpringBootTest(webEnvironment = RANDOM_PORT)`
- `@AutoConfigureMockMvc`
- `@Testcontainers` with PostgreSQL 16
- `@DynamicPropertySource` for datasource and Redis configuration

**Important limitation:** The base class configures `spring.data.redis.host=localhost` / `port=6379` but does not start a Redis Testcontainer. For integration tests that exercise Redis-dependent code paths (idempotency, permission caching), either:
- (a) Add a Redis Testcontainer to `BaseIntegrationTest`, or
- (b) Use `@MockBean` for `StringRedisTemplate` and `RedisIdempotencyService` in specific integration test classes, or
- (c) Add a `@TestConfiguration` with a no-op `CacheManager`.

**Recommended approach:** Option (a) -- add a Redis Testcontainer to `BaseIntegrationTest`.

```java
// Add to BaseIntegrationTest alongside the existing POSTGRES container:

@Container
static final GenericContainer<?> REDIS =
        new GenericContainer<>("redis:7-alpine")
                .withExposedPorts(6379);

// Add to configureProperties():
registry.add("spring.data.redis.host", REDIS::getHost);
registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
```

#### Integration tests to create

**Priority 1 -- Auth flow (most critical path):**

**File:** `src/test/java/com/demo/app/iam/controller/AuthControllerIntegrationTest.java`

| Test | Description |
|------|-------------|
| `login_returns200_withCookies` | POST `/api/v1/auth/login` with valid credentials; assert 200, access-token and refresh-token cookies set |
| `login_returns403_whenBadCredentials` | Invalid password -> 403 |
| `me_returns200_whenAuthenticated` | GET `/api/v1/auth/me` with valid access-token cookie -> 200 |
| `me_returns401_whenNoCookie` | No cookie -> 401 |
| `refresh_returns200_withNewCookies` | POST `/api/v1/auth/refresh` with valid refresh-token -> 200, new cookies |
| `logout_returns204_clearsCookies` | POST `/api/v1/auth/logout` -> 204 |

**Priority 2 -- User CRUD:**

**File:** `src/test/java/com/demo/app/iam/controller/UserControllerIntegrationTest.java`

| Test | Description |
|------|-------------|
| `create_returns201_withLocationHeader` | POST `/api/v1/users` -> 201, Location header set |
| `create_returns409_whenEmailDuplicate` | Duplicate email -> 409 |
| `list_returns200_paged` | GET `/api/v1/users` -> 200 with paginated response |
| `findById_returns200` | GET `/api/v1/users/{id}` -> 200 |
| `findById_returns404` | Non-existent ID -> 404 |
| `update_returns200` | PUT `/api/v1/users/{id}` -> 200 |
| `delete_returns204` | DELETE `/api/v1/users/{id}` -> 204 |

**Priority 3 -- Role CRUD:**

**File:** `src/test/java/com/demo/app/iam/controller/RoleControllerIntegrationTest.java`

(Similar pattern to User CRUD, plus built-in role protection tests.)

### 3.3 What NOT to test in unit tests

- JPA repository interfaces (no hand-written code)
- DTOs / records (no logic)
- Entity classes (Lombok-generated)
- Config classes (wiring; tested indirectly by integration tests)
- `DemoAppApplication.main()` (entry point)
- MapStruct-generated `UserMapperImpl` (test the service that uses it instead)

---

## 4. CI Integration

### Running coverage

```bash
# Run all tests and generate JaCoCo report + enforce thresholds
mvn clean verify
```

If coverage falls below the thresholds (line < 70%, branch < 60%), the build fails at the `verify` phase with a message like:

```
[ERROR] Rule violated for bundle demo-app-backend: lines covered ratio is 0.65, but expected minimum is 0.70
```

### Surfacing the HTML report as a CI artifact

#### GitHub Actions example

```yaml
# .github/workflows/ci.yml
name: CI
on: [push, pull_request]

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4

      - name: Set up JDK 21
        uses: actions/setup-java@v4
        with:
          java-version: '21'
          distribution: 'temurin'

      - name: Build and verify with coverage
        run: mvn clean verify -f demo-app-backend/pom.xml

      - name: Upload JaCoCo report
        if: always()
        uses: actions/upload-artifact@v4
        with:
          name: jacoco-report
          path: demo-app-backend/target/site/jacoco/
          retention-days: 14
```

#### Key points

- `mvn clean verify` runs `test` phase (executes tests) then `verify` phase (generates report and checks thresholds).
- The `if: always()` on the artifact upload ensures the report is available even when the build fails due to low coverage -- this lets developers see exactly which classes need coverage.
- Docker must be available on the CI runner for Testcontainers (GitHub-hosted runners have Docker pre-installed).

---

## 5. Report Output

### Default report path

```
demo-app-backend/target/site/jacoco/index.html
```

### How to open locally

```bash
# After running mvn clean verify:
open demo-app-backend/target/site/jacoco/index.html
# (macOS -- use xdg-open on Linux, start on Windows)
```

### What the report shows

- **Bundle-level summary:** overall line, branch, instruction, complexity, method coverage
- **Per-package drill-down:** each package with its own coverage percentages
- **Per-class drill-down:** each class with line-by-line coverage highlighting (green = covered, red = not covered, yellow = partial branch coverage)

### Additional report formats

JaCoCo also generates:
- `target/site/jacoco/jacoco.xml` -- machine-readable XML, used by CI tools (SonarQube, Codecov, Coveralls)
- `target/site/jacoco/jacoco.csv` -- CSV format for spreadsheet analysis

---

## 6. Implementation Checklist

This is the ordered list of tasks the java-developer agent must execute. Each task is a concrete, verifiable action.

### Phase 1: Configure JaCoCo (pom.xml changes)

- [ ] **Task 1.1:** Replace the `jacoco-maven-plugin` block in `pom.xml` (lines 174-189) with the full configuration from Section 2 of this document. The replacement adds the `check` execution with thresholds and the `excludes` configuration.

- [ ] **Task 1.2:** Add a Redis Testcontainer to `BaseIntegrationTest` -- add the `REDIS` container field and update `configureProperties` to use `REDIS::getHost` and `REDIS.getMappedPort(6379)` instead of hardcoded `localhost:6379`. See the code snippet in Section 3.2.

- [ ] **Task 1.3:** Run `mvn clean verify` to confirm the build compiles and existing tests pass. The build will likely fail on the `check` goal because current coverage is below thresholds. Record the actual coverage numbers from the output.

### Phase 2: Unit tests for services without any coverage (high priority)

Write tests in this order (each depends on understanding gained from the previous):

- [ ] **Task 2.1:** Create `RoleServiceTest.java` with all 12 test methods listed in Section 3.1.1.

- [ ] **Task 2.2:** Create `JwtServiceTest.java` with all 4 test methods listed in Section 3.1.2. This requires generating a test RSA key pair in `@BeforeAll` and constructing a `JwtConfig` mock that returns the test keys.

- [ ] **Task 2.3:** Create `PermissionCacheServiceTest.java` with all 3 test methods listed in Section 3.1.3.

- [ ] **Task 2.4:** Create `DocumentServiceTest.java` with all 10 test methods listed in Section 3.1.4. Use `mock(MultipartFile.class)` for upload tests.

- [ ] **Task 2.5:** Create `StorageServiceTest.java` with all 5 test methods listed in Section 3.1.5. Use `@TempDir Path tempDir` and construct `new StorageService(tempDir)`.

- [ ] **Task 2.6:** Create `CvCandidateServiceTest.java` with all 10 test methods listed in Section 3.1.6.

- [ ] **Task 2.7:** Create `CvExtractionResultMapperTest.java` with all 9 test methods listed in Section 3.1.7. No mocks needed -- instantiate `CvExtractionResultMapper` directly.

- [ ] **Task 2.8:** Create `AuditServiceTest.java` with both test methods listed in Section 3.1.8.

- [ ] **Task 2.9:** Create `ReportServiceTest.java` with all 6 test methods listed in Section 3.1.9.

- [ ] **Task 2.10:** Create `UserSettingsServiceTest.java` with all 4 test methods listed in Section 3.1.10.

- [ ] **Task 2.11:** Create `GlobalExceptionHandlerTest.java` with all 10 test methods listed in Section 3.1.11.

### Phase 3: Fill coverage gaps in existing test files

- [ ] **Task 3.1:** Add 9 test methods to `AuthServiceTest.java` as listed in Section 3.1.12 (coverage for `refresh`, `getMe`, `changePassword`, `handleFailedLogin`).

- [ ] **Task 3.2:** Add 4 test methods to `UserServiceTest.java` as listed in Section 3.1.12 (coverage for `update`).

### Phase 4: Security filter unit tests

- [ ] **Task 4.1:** Create `JwtCookieAuthFilterTest.java` with 4 test methods as listed in Section 3.1.13.

- [ ] **Task 4.2:** Create `InternalApiKeyFilterTest.java` with 4 test methods as listed in Section 3.1.13.

### Phase 5: Verify thresholds are met

- [ ] **Task 5.1:** Run `mvn clean verify`. The build must pass with line >= 70% and branch >= 60%.

- [ ] **Task 5.2:** Open `target/site/jacoco/index.html` and verify that excluded packages (dto, entity, config, exception, repository) are not listed in the report.

- [ ] **Task 5.3:** Identify any remaining classes below 50% line coverage in the report. If any exist, add targeted tests to bring them above 50%.

### Phase 6 (optional, future): Integration tests

- [ ] **Task 6.1:** Create `AuthControllerIntegrationTest.java` extending `BaseIntegrationTest`. This requires seeding a test user via `@BeforeEach` (insert into users, credentials, roles tables using the repositories).

- [ ] **Task 6.2:** Create `UserControllerIntegrationTest.java` extending `BaseIntegrationTest`.

- [ ] **Task 6.3:** Create `RoleControllerIntegrationTest.java` extending `BaseIntegrationTest`.

---

## Appendix A: Complete File List for New Tests

```
src/test/java/com/demo/app/
  iam/
    service/
      RoleServiceTest.java                  (Task 2.1)
      JwtServiceTest.java                   (Task 2.2)
      PermissionCacheServiceTest.java       (Task 2.3)
    security/
      JwtCookieAuthFilterTest.java          (Task 4.1)
      InternalApiKeyFilterTest.java         (Task 4.2)
    controller/
      AuthControllerIntegrationTest.java    (Task 6.1, optional)
      UserControllerIntegrationTest.java    (Task 6.2, optional)
      RoleControllerIntegrationTest.java    (Task 6.3, optional)
  content/
    service/
      DocumentServiceTest.java             (Task 2.4)
      StorageServiceTest.java              (Task 2.5)
  cv/
    service/
      CvCandidateServiceTest.java          (Task 2.6)
    extraction/
      CvExtractionResultMapperTest.java    (Task 2.7)
  compliance/
    service/
      AuditServiceTest.java               (Task 2.8)
  insights/
    service/
      ReportServiceTest.java              (Task 2.9)
  personal/
    service/
      UserSettingsServiceTest.java        (Task 2.10)
  platform/
    handler/
      GlobalExceptionHandlerTest.java     (Task 2.11)
```

## Appendix B: Test Count Summary

| Category | New test files | New test methods | Existing test methods |
|----------|---------------|-----------------|----------------------|
| Unit -- new services | 11 | ~73 | -- |
| Unit -- existing services (gap fill) | 0 | 13 | 25 |
| Unit -- security filters | 2 | 8 | -- |
| Integration (optional) | 3 | ~18 | -- |
| **Total** | **16** | **~112** | **25** |

Estimated total test methods after completion: **~137** (excluding optional integration tests: ~119).

## Appendix C: Threshold Rationale

| Threshold | Value | Rationale |
|-----------|-------|-----------|
| Line coverage | >= 70% | Achievable starting point for a project with zero enforcement history. High enough to catch regression, low enough to not block development. Raise to 80% once baseline is established. |
| Branch coverage | >= 60% | Branch coverage is harder to achieve due to null checks, switch cases, and short-circuit evaluation in Lombok-generated code. 60% ensures meaningful conditional logic is tested. Raise to 70% once baseline is established. |

The excluded classes (DTOs, entities, config, exceptions, repositories, generated mappers) represent approximately 40-50% of the total class count. Excluding them means the 70% line threshold applies only to classes with real business logic, making it effectively equivalent to ~85% coverage of the code that matters.
