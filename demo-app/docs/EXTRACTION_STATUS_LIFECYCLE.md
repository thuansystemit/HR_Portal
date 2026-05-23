# Extraction Status Lifecycle

## 1. Status State Machine

```
                          +-----------+
        (user uploads) -->| PENDING   |
                          +-----+-----+
                                |
                  extractor picks up file
                   (PATCH /extraction-status)
                                |
                          +-----v-----+
                          | PROCESSING|
                          +-----+-----+
                               / \
                              /   \
              ingest succeeds/     \ingest fails OR
              (POST /cv-candidates  pipeline error
               or /invoice-records) (PATCH /extraction-status)
                            /       \
                     +-----v-+     +-v-------+
                     |SUCCESS|     | FAILED  |
                     +-------+     +----+----+
                                        |
                          user clicks "Retry"
                           (POST /retry)
                                        |
                                  +-----v-----+
                                  | PENDING   |  (re-enters cycle)
                                  +-----------+
```

### Valid Transitions

| From       | To         | Triggered By          | Mechanism                                     |
|------------|------------|-----------------------|-----------------------------------------------|
| (none)     | PENDING    | Backend upload handler| `DocumentService.upload()` sets status inline  |
| PENDING    | PROCESSING | cv-batch-extractor    | `PATCH /api/v1/documents/{id}/extraction-status` |
| PROCESSING | SUCCESS    | Backend ingest service| `CvCandidateService.ingest()` / `InvoiceRecordService.ingest()` sets after DB commit |
| PROCESSING | FAILED     | Backend ingest service OR cv-batch-extractor | Ingest exception handler, or extractor calls PATCH on pipeline error |
| FAILED     | PENDING    | User via frontend     | `POST /api/v1/documents/{id}/retry-extraction` |

### Invalid Transitions (rejected by backend)

- PENDING -> SUCCESS (cannot skip PROCESSING)
- PENDING -> FAILED (cannot skip PROCESSING)
- SUCCESS -> anything (terminal state; delete and re-upload to re-extract)
- PROCESSING -> PENDING (no backward movement except via FAILED -> retry)

---

## 2. Component Interaction Diagram

```
  Browser (Angular)                   Spring Boot Backend              cv-batch-extractor (Python)
  ==================                  ===================              ===========================

  1. User uploads file
     POST /categories/{catId}/documents
     ---------------------------------->
                                        save doc with
                                        extraction_status = PENDING
                                        write file to upload_dir
     <----------------------------------
     201 Created (extractionStatus=PENDING)

                                                                       2. watchdog detects new file
                                                                          extract doc_id from path
                                                                          PATCH /documents/{id}/extraction-status
                                                                          { "status": "PROCESSING" }
                                        <---------------------------------------------------
                                        validate transition PENDING->PROCESSING
                                        update DB
                                        --------------------------------------------------->
                                        200 OK

                                                                       3. run guardrails + LLM pipeline
                                                                          ...
                                                                          (on success) write JSON output

                                                                       4a. POST /cv-candidates (or /invoice-records)
                                                                           { documentId, categoryId, jsonFile }
                                        <---------------------------------------------------
                                        read JSON, save entities
                                        set extraction_status = SUCCESS
                                        --------------------------------------------------->
                                        201 Created

                                                                       4b. (on pipeline error)
                                                                           PATCH /documents/{id}/extraction-status
                                                                           { "status": "FAILED", "error": "..." }
                                        <---------------------------------------------------
                                        update DB
                                        --------------------------------------------------->
                                        200 OK

  5. Frontend polls document list
     GET /categories/{catId}/documents
     ---------------------------------->
                                        return docs with extractionStatus
     <----------------------------------
     200 OK (extractionStatus visible)

  6. User clicks Retry on FAILED doc
     POST /documents/{id}/retry-extraction
     ---------------------------------->
                                        validate status == FAILED
                                        set status = PENDING
                                        copy file to upload_dir (re-trigger watchdog)
     <----------------------------------
     200 OK (extractionStatus=PENDING)
```

---

## 3. Design Decisions

### 3.1 Status Update Triggers -- Dedicated Endpoint vs Piggyback

**Decision: Introduce a dedicated `PATCH /api/v1/documents/{id}/extraction-status` endpoint for the extractor to call.**

Rationale:

- The extractor needs to signal PROCESSING at the *start* of work, before any ingest payload exists. There is no existing endpoint that serves this purpose. Reusing the ingest POST is impossible because there is no extracted data yet.
- The SUCCESS transition is still set *inside* the existing `CvCandidateService.ingest()` and `InvoiceRecordService.ingest()` methods, as it is today (with the status value changing from `COMPLETED` to `SUCCESS`). This keeps the status update atomic with the data persistence -- if the ingest transaction rolls back, the status stays at PROCESSING.
- The FAILED transition can come from two places: (a) the backend ingest `catch` block (already present), or (b) the extractor calling the PATCH endpoint when the pipeline itself fails (currently, the extractor only logs and dead-letters; it does not notify the backend of failures).
- A single-purpose status endpoint is simple to secure (require `X-Internal-Api-Key`), validate (enforce the state machine), and test.

Why NOT direct DB access from the extractor:
- The extractor runs in a separate Docker container. Granting it direct PostgreSQL access would break service boundary isolation, create a shared-schema coupling risk, and require managing a second DB credential. The backend already has an internal API key mechanism that the extractor uses.

### 3.2 Frontend Update Strategy -- Polling vs SSE/WebSocket

**Decision: Polling with adaptive interval.**

Rationale:

- Document uploads are low-frequency operations (a human uploads a CV/invoice, then waits). There are not hundreds of concurrent extractions.
- LLM extraction through Ollama takes 30-120 seconds typically. The status will change at most 2-3 times per document per upload session.
- Polling is zero new infrastructure. SSE or WebSocket would require: (a) a new connection management layer in Spring Boot, (b) handling reconnects and auth token refresh on the frontend, (c) scaling considerations for persistent connections if the app grows. None of this complexity is justified for the current use case.
- Adaptive polling: poll every 5 seconds while any document in the current list has `extractionStatus` of `PENDING` or `PROCESSING`. Stop polling when all documents are terminal (`SUCCESS`, `FAILED`, or `null`). This avoids wasted requests on pages with no in-progress extractions.

Implementation: the `DocumentStore` starts an `interval(5000)` subscription when `loadByCategory()` is called and any document has a non-terminal status. The interval re-fetches the document list. When all statuses are terminal or the component is destroyed, the interval unsubscribes.

Why NOT SSE:
- SSE is a better technical fit for push notifications, but the team's stack has no existing SSE infrastructure. Adding it for a single feature creates maintenance burden disproportionate to the benefit. If more real-time features are needed in the future (e.g., live collaboration, notifications), SSE should be revisited as a platform capability, not bolted on per-feature.

### 3.3 Retry Strategy for FAILED Extractions

**Decision: User-initiated retry via a button in the UI that calls `POST /api/v1/documents/{id}/retry-extraction`.**

Rationale:

- Automatic retry is dangerous for LLM extraction. If the failure was caused by a malformed document or an unsupported format, retrying will fail again and waste Ollama compute. A human should inspect the error message before deciding to retry.
- The retry endpoint resets `extraction_status` to `PENDING` and `extraction_error` to `null`. It then copies the original uploaded file back into the watched directory (or touches the existing file to trigger the watchdog's `on_created` event). This re-enters the normal pipeline without any special retry logic in the extractor.
- The retry count is NOT tracked in this design. If a user retries 10 times, they get 10 attempts. This is acceptable because: (a) extraction is a local Ollama call, not a paid API; (b) the worker pool semaphore in the extractor already bounds concurrency; (c) dead-lettering in the extractor catches repeated failures. If abuse becomes a concern, a `retry_count` column can be added in a future migration with a configurable cap.

### 3.4 Naming: SUCCESS vs COMPLETED

**Decision: Rename `COMPLETED` to `SUCCESS` throughout the codebase.**

The existing code uses `"COMPLETED"` as the success status. The new lifecycle introduces `FAILED` as a peer state. `SUCCESS`/`FAILED` is a more natural and symmetric pair than `COMPLETED`/`FAILED`. The Flyway migration will update existing rows: `UPDATE documents SET extraction_status = 'SUCCESS' WHERE extraction_status = 'COMPLETED'`.

---

## 4. API Contracts

### 4.1 Update Extraction Status (new endpoint)

Used by `cv-batch-extractor` to signal PROCESSING and FAILED.

```
PATCH /api/v1/documents/{id}/extraction-status

Headers:
  X-Internal-Api-Key: <shared secret>    (required -- ROLE_SERVICE auth)

Request body:
{
  "status": "PROCESSING",           // required -- one of: PROCESSING, FAILED
  "errorPhase": "LLM_EXTRACTION",   // optional -- only when status=FAILED
  "errorMessage": "Ollama timeout"   // optional -- only when status=FAILED
}

Response 200 OK:
{
  "id": "550e8400-...",
  "extractionStatus": "PROCESSING",
  "extractionError": null
}

Response 400 Bad Request:
{
  "status": 400,
  "error": "Bad Request",
  "message": "Invalid transition: PENDING -> FAILED is not allowed without PROCESSING",
  "path": "/api/v1/documents/{id}/extraction-status",
  "traceId": "..."
}

Response 404 Not Found:
  Document with given ID does not exist.
```

**State machine validation rules in the endpoint:**

| Current Status | Requested Status | Result |
|---------------|-----------------|--------|
| PENDING       | PROCESSING      | allowed |
| PROCESSING    | FAILED          | allowed |
| PROCESSING    | SUCCESS         | rejected (SUCCESS is set internally by ingest, not via this endpoint) |
| FAILED        | *               | rejected (use retry endpoint instead) |
| SUCCESS       | *               | rejected (terminal) |
| null          | *               | rejected (non-extractable document) |

### 4.2 Retry Extraction (new endpoint)

Used by the frontend to re-queue a FAILED document.

```
POST /api/v1/documents/{id}/retry-extraction

Headers:
  Cookie: access_token=<jwt>    (required -- normal user auth)

Request body: (none)

Response 200 OK:
{
  "id": "550e8400-...",
  "extractionStatus": "PENDING",
  "extractionError": null
}

Response 400 Bad Request:
  If extractionStatus is not FAILED.

Response 404 Not Found:
  Document with given ID does not exist.
```

Backend logic:
1. Load document, verify `extraction_status == 'FAILED'`
2. Set `extraction_status = 'PENDING'`, `extraction_error = null`
3. Re-trigger the watchdog by copying the original file to a temporary name in the upload directory, then renaming it (atomic file creation triggers watchdog `on_created`)

### 4.3 DocumentResponse DTO (modified)

Current:
```java
public record DocumentResponse(
    UUID id, UUID categoryId, String name, String mimeType,
    long sizeBytes, UUID uploadedBy, Instant uploadedAt
) {}
```

New:
```java
public record DocumentResponse(
    UUID id, UUID categoryId, String name, String mimeType,
    long sizeBytes, UUID uploadedBy, Instant uploadedAt,
    String extractionStatus,    // null for non-extractable, else PENDING|PROCESSING|SUCCESS|FAILED
    String extractionError      // null unless FAILED
) {}
```

The `toResponse()` mapping in `DocumentService` changes to:
```java
private DocumentResponse toResponse(Document d) {
    return new DocumentResponse(
        d.getId(), d.getCategoryId(), d.getName(),
        d.getMimeType(), d.getSizeBytes(), d.getUploadedBy(), d.getUploadedAt(),
        d.getExtractionStatus(), d.getExtractionError()
    );
}
```

### 4.4 GET Document List (unchanged URL, enriched response)

```
GET /api/v1/categories/{categoryId}/documents?page=0&size=10

Response 200 OK -- each item now includes:
{
  "content": [
    {
      "id": "...",
      "categoryId": "...",
      "name": "john_doe_cv.pdf",
      "mimeType": "application/pdf",
      "sizeBytes": 204800,
      "uploadedBy": "...",
      "uploadedAt": "2026-05-21T14:30:00Z",
      "extractionStatus": "PROCESSING",
      "extractionError": null
    },
    {
      "id": "...",
      ...
      "extractionStatus": "FAILED",
      "extractionError": "[LLM_EXTRACTION] Ollama timeout after 300s"
    }
  ],
  "page": 0,
  "size": 10,
  "totalElements": 42,
  "totalPages": 5
}
```

---

## 5. Frontend Behavior Spec

### 5.1 ExtractionStatus Type

Add to `document.model.ts`:

```typescript
export type ExtractionStatus = 'PENDING' | 'PROCESSING' | 'SUCCESS' | 'FAILED';

export interface AppDocument {
  id:               string;
  categoryId:       string;
  name:             string;
  mimeType:         string;
  fileSize:         number;
  uploadedBy:       string;
  createdAt:        string;
  extractionStatus: ExtractionStatus | null;  // null for non-extractable types
  extractionError:  string | null;
}
```

### 5.2 Document List Table -- New Column

Add an "Extraction" column between "Status" and "Uploaded" in the data table. This column is only visible when the category's `documentType` is `CV` or `INVOICE`. For non-extractable categories, omit the column entirely.

### 5.3 Badge Rendering per Status

| `extractionStatus` | Badge                       | Animated | Tooltip                                   |
|---------------------|-----------------------------|----------|--------------------------------------------|
| `null`              | (no badge, column hidden)   | --       | --                                         |
| `PENDING`           | `badge bg-secondary` "Queued" | no       | "Waiting for extraction to start"          |
| `PROCESSING`        | `badge bg-warning text-dark` "Extracting..." | yes (spinner icon `bi-arrow-repeat spin`) | "LLM extraction in progress" |
| `SUCCESS`           | `badge bg-success` "Extracted" | no      | "Extraction completed successfully"        |
| `FAILED`            | `badge bg-danger` "Failed"  | no       | Shows `extractionError` text from backend  |

Angular template fragment for the extraction status cell:

```html
<ng-template #extractionTpl let-row="row">
  @switch (row.extractionStatus) {
    @case ('PENDING') {
      <span class="badge bg-secondary" title="Waiting for extraction to start">
        <i class="bi bi-hourglass-split me-1"></i>Queued
      </span>
    }
    @case ('PROCESSING') {
      <span class="badge bg-warning text-dark" title="LLM extraction in progress">
        <i class="bi bi-arrow-repeat spin me-1"></i>Extracting...
      </span>
    }
    @case ('SUCCESS') {
      <span class="badge bg-success" title="Extraction completed successfully">
        <i class="bi bi-check-circle me-1"></i>Extracted
      </span>
    }
    @case ('FAILED') {
      <span class="badge bg-danger" [title]="row.extractionError ?? 'Extraction failed'">
        <i class="bi bi-x-circle me-1"></i>Failed
      </span>
    }
  }
</ng-template>
```

CSS for the spinning icon:

```scss
.spin {
  animation: spin 1s linear infinite;
}
@keyframes spin {
  from { transform: rotate(0deg); }
  to   { transform: rotate(360deg); }
}
```

### 5.4 Action Button Behavior per Status

The "View Candidate" / "View Invoice" button behavior changes based on extraction status:

| `extractionStatus` | "View Candidate/Invoice" Button | "Retry" Button |
|---------------------|--------------------------------|----------------|
| `null`              | hidden (not an extractable type) | hidden        |
| `PENDING`           | disabled, grayed out           | hidden         |
| `PROCESSING`        | disabled, grayed out           | hidden         |
| `SUCCESS`           | enabled, normal                | hidden         |
| `FAILED`            | disabled, grayed out           | visible, enabled |

The Retry button calls `POST /api/v1/documents/{id}/retry-extraction` and on success, the local document's `extractionStatus` is immediately set to `PENDING` (optimistic update) and the polling interval re-activates.

Updated actions template:

```html
<ng-template #actionsTpl let-row="row">
  <div class="d-flex justify-content-center gap-2">
    @if (catStore.selected()?.documentType === 'CV') {
      <app-btn size="sm" variant="outline-info" icon="bi-person-badge" [iconOnly]="true"
               title="View Candidate" (btnClick)="viewCandidate(row)"
               [disabled]="row.extractionStatus !== 'SUCCESS'" />
    }
    @if (catStore.selected()?.documentType === 'INVOICE') {
      <app-btn size="sm" variant="outline-success" icon="bi-receipt" [iconOnly]="true"
               title="View Invoice" (btnClick)="viewInvoice(row)"
               [disabled]="row.extractionStatus !== 'SUCCESS'" />
    }
    @if (row.extractionStatus === 'FAILED') {
      <app-btn size="sm" variant="outline-warning" icon="bi-arrow-clockwise" [iconOnly]="true"
               title="Retry Extraction" (btnClick)="retryExtraction(row)" />
    }
    @if (canView()) {
      <app-btn size="sm" variant="outline-primary" icon="bi-eye" [iconOnly]="true"
               title="View" (btnClick)="openPreview(row)" />
    }
    @if (canDeleteDoc()) {
      <app-btn size="sm" variant="outline-danger" icon="bi-trash" [iconOnly]="true"
               title="Delete" (btnClick)="confirmDelete(row)" />
    }
  </div>
</ng-template>
```

### 5.5 Adaptive Polling

The `DocumentStore` manages polling internally:

```typescript
// In DocumentStore

private pollSub: Subscription | null = null;
private readonly POLL_INTERVAL = 5_000; // 5 seconds

loadByCategory(categoryId: string): void {
  this._loading.set(true);
  this._error.set(null);
  this.api.getByCategory(categoryId).subscribe({
    next: docs => {
      this._documents.set(docs);
      this._loading.set(false);
      this.startPollingIfNeeded(categoryId);
    },
    error: err => {
      this._error.set(err.message);
      this._loading.set(false);
    },
  });
}

private startPollingIfNeeded(categoryId: string): void {
  this.stopPolling();
  if (this.hasActiveExtractions()) {
    this.pollSub = interval(this.POLL_INTERVAL).pipe(
      switchMap(() => this.api.getByCategory(categoryId)),
      tap(docs => {
        this._documents.set(docs);
        if (!this.hasActiveExtractions()) {
          this.stopPolling();
        }
      }),
    ).subscribe();
  }
}

private hasActiveExtractions(): boolean {
  return this._documents().some(d =>
    d.extractionStatus === 'PENDING' || d.extractionStatus === 'PROCESSING'
  );
}

stopPolling(): void {
  this.pollSub?.unsubscribe();
  this.pollSub = null;
}
```

The `DocumentListPage` component calls `this.docStore.stopPolling()` in `ngOnDestroy()` to clean up when navigating away.

---

## 6. Error Handling and Recovery

### 6.1 Failure Scenarios and Responses

| Failure Point | Current Behavior | New Behavior |
|---------------|-----------------|-------------|
| Pipeline REJECTED (guardrail block) | Dead-lettered, backend not notified | Extractor calls `PATCH /extraction-status` with `status=FAILED`, `errorPhase=GUARDRAIL`, `errorMessage=<guard name>: <reason>`. Then dead-letters. |
| Pipeline ERROR (unhandled exception) | Dead-lettered, backend not notified | Extractor calls `PATCH /extraction-status` with `status=FAILED`, `errorPhase=PIPELINE`, `errorMessage=<exception message>`. Then dead-letters. |
| LLM output parse failure | Dead-lettered, backend not notified | Same as ERROR above -- the `OutputParserException` is caught, extractor calls PATCH. |
| Backend ingest POST fails (network) | Logged, backend not notified | Extractor retries the POST 3 times with exponential backoff (1s, 2s, 4s). If all fail, calls PATCH with `status=FAILED`, `errorPhase=BACKEND_NOTIFY`, `errorMessage=<HTTP status or timeout>`. Then dead-letters. |
| Backend ingest POST fails (409 Conflict) | RuntimeException | Extractor treats 409 as success (idempotent -- the candidate/invoice already exists). Calls `PATCH` with `status=SUCCESS` through the normal ingest flow. No dead-lettering. Actually, the ingest endpoint already sets SUCCESS on success, so the extractor does not need to do anything extra on 409 except NOT dead-letter. |
| Backend ingest succeeds but JSON read fails | Status set to FAILED by ingest catch block | No change needed -- already handled. `errorPhase=JSON_READ` already written. |
| Worker queue full (semaphore blocked) | Dead-lettered immediately | Additionally call PATCH with `status=FAILED`, `errorPhase=WORKER_POOL`, `errorMessage=queue full`. |
| Backend PATCH /extraction-status fails | N/A (endpoint does not exist yet) | Extractor logs the failure and proceeds. The document will be stuck in its current state. The dead-letter log provides an offline audit trail. A future improvement could add a reconciliation job that checks dead-letter entries against DB status. |

### 6.2 Stuck Document Detection

A document can get stuck in PROCESSING if the extractor crashes mid-pipeline without calling the FAILED status update. To handle this:

- Add a `extraction_started_at TIMESTAMPTZ` column to `documents` (set when transitioning to PROCESSING).
- A scheduled task (`@Scheduled`) in the backend runs every 10 minutes and queries: `SELECT * FROM documents WHERE extraction_status = 'PROCESSING' AND extraction_started_at < now() - interval '15 minutes'`.
- Matching documents are moved to FAILED with `errorPhase=TIMEOUT`, `errorMessage=Extraction did not complete within 15 minutes`.
- This is a safety net, not the primary failure path. The 15-minute timeout is generous because Ollama extraction can legitimately take 2-5 minutes for large documents.

### 6.3 Retry Flow Detail

```
User clicks "Retry"
  |
  v
POST /api/v1/documents/{id}/retry-extraction
  |
  v
Backend:
  1. Load document
  2. Assert extraction_status == FAILED (else 400)
  3. Set extraction_status = PENDING, extraction_error = null
  4. Delete any existing cv_candidate or invoice_record for this document_id
     (so the ingest endpoint does not hit a 409 Conflict on re-extraction)
  5. Resolve the original file path from document.storageKey
  6. Touch/copy the file to re-trigger the watchdog
     - Create a temp file in the same directory, then rename
       (atomic rename triggers watchdog on_created)
  7. Return updated DocumentResponse
```

---

## 7. Database Changes

### 7.1 Flyway Migration V17

```sql
-- V17__extraction_status_lifecycle.sql

-- 1. Widen extraction_status to support new enum values (already VARCHAR(20), sufficient)

-- 2. Rename COMPLETED -> SUCCESS for consistency
UPDATE documents
SET extraction_status = 'SUCCESS'
WHERE extraction_status = 'COMPLETED';

-- 3. Add extraction_started_at for stuck detection
ALTER TABLE documents
    ADD COLUMN extraction_started_at TIMESTAMPTZ;

-- 4. Add CHECK constraint enforcing valid status values
ALTER TABLE documents
    ADD CONSTRAINT chk_documents_extraction_status
    CHECK (extraction_status IS NULL OR extraction_status IN ('PENDING', 'PROCESSING', 'SUCCESS', 'FAILED'));

-- 5. Index for the stuck-document query
CREATE INDEX idx_documents_extraction_processing
    ON documents (extraction_started_at)
    WHERE extraction_status = 'PROCESSING';

-- 6. Index for the document list query to include extraction_status efficiently
CREATE INDEX idx_documents_category_status_extraction
    ON documents (category_id, upload_status, extraction_status)
    WHERE deleted_at IS NULL;
```

### 7.2 Entity Change

```java
// Document.java -- add field
@Column(name = "extraction_started_at")
private Instant extractionStartedAt;
```

---

## 8. ExtractionStatus Enum

Replace string literals with a Java enum for type safety.

```java
package com.demo.app.content.entity;

public enum ExtractionStatus {
    PENDING,
    PROCESSING,
    SUCCESS,
    FAILED;

    /**
     * Validates that a transition from this status to the target is allowed.
     * @throws IllegalStateException if the transition is invalid
     */
    public void validateTransitionTo(ExtractionStatus target) {
        boolean valid = switch (this) {
            case PENDING    -> target == PROCESSING;
            case PROCESSING -> target == SUCCESS || target == FAILED;
            case FAILED     -> target == PENDING;  // retry
            case SUCCESS    -> false;               // terminal
        };
        if (!valid) {
            throw new IllegalStateException(
                "Invalid extraction status transition: " + this + " -> " + target);
        }
    }
}
```

The `Document` entity changes `extractionStatus` from `String` to `@Enumerated(EnumType.STRING) ExtractionStatus`.

---

## 9. Backend Implementation Changes Summary

### 9.1 New Classes

| Class | Package | Purpose |
|-------|---------|---------|
| `ExtractionStatus` | `content.entity` | Enum with transition validation |
| `ExtractionStatusController` | `content.controller` | PATCH endpoint for extractor, POST retry for frontend |
| `UpdateExtractionStatusRequest` | `content.dto` | Request DTO for PATCH |
| `ExtractionStatusResponse` | `content.dto` | Lightweight response for status-only operations |
| `StuckExtractionJob` | `content.service` | `@Scheduled` job for timeout detection |

### 9.2 Modified Classes

| Class | Change |
|-------|--------|
| `Document` | Add `extractionStartedAt` field; change `extractionStatus` from `String` to `ExtractionStatus` enum |
| `DocumentService` | Update `toResponse()` to include `extractionStatus` and `extractionError`; update `updateExtractionStatus()` to validate transitions and set `extractionStartedAt` when transitioning to PROCESSING |
| `DocumentResponse` | Add `extractionStatus` and `extractionError` fields |
| `CvCandidateService.ingest()` | Change `"COMPLETED"` to `ExtractionStatus.SUCCESS` |
| `InvoiceRecordService.ingest()` | Change `"COMPLETED"` to `ExtractionStatus.SUCCESS` |
| `SecurityConfig` | No change needed -- the PATCH and retry endpoints are behind `.anyRequest().authenticated()`, and the extractor authenticates via `X-Internal-Api-Key` (ROLE_SERVICE). The retry endpoint requires a JWT (normal user). |

### 9.3 New Controller

```java
@RestController
@RequestMapping("/api/v1/documents")
@RequiredArgsConstructor
public class ExtractionStatusController {

    private final DocumentService documentService;

    /**
     * Called by cv-batch-extractor to update extraction status.
     * Requires ROLE_SERVICE (internal API key).
     */
    @PatchMapping("/{id}/extraction-status")
    @PreAuthorize("hasRole('SERVICE')")
    public ResponseEntity<ExtractionStatusResponse> updateStatus(
            @PathVariable UUID id,
            @RequestBody @Valid UpdateExtractionStatusRequest request) {
        var response = documentService.updateExtractionStatusWithValidation(
                id, request.status(), request.errorPhase(), request.errorMessage());
        return ResponseEntity.ok(response);
    }

    /**
     * Called by the frontend to retry a FAILED extraction.
     * Requires normal user authentication.
     */
    @PostMapping("/{id}/retry-extraction")
    public ResponseEntity<ExtractionStatusResponse> retryExtraction(
            @PathVariable UUID id) {
        var response = documentService.retryExtraction(id);
        return ResponseEntity.ok(response);
    }
}
```

---

## 10. cv-batch-extractor Changes Summary

### 10.1 New Function in `backend_client.py`

```python
def update_extraction_status(
    document_id: str,
    status: str,
    error_phase: str | None = None,
    error_message: str | None = None,
) -> None:
    url = f"{settings.backend_url}/api/v1/documents/{document_id}/extraction-status"
    payload = {"status": status}
    if error_phase:
        payload["errorPhase"] = error_phase
    if error_message:
        payload["errorMessage"] = error_message[:2000]  # truncate to avoid oversized payloads
    logger.info("PATCH %s  status=%s", url, status)
    resp = requests.patch(url, json=payload, headers=_HEADERS, timeout=settings.backend_timeout)
    resp.raise_for_status()
```

### 10.2 Changes to `worker.py`

```python
def _process_task(document_id: str, category_id: str, file_path: str, document_type: str) -> None:
    # Signal PROCESSING at the start
    try:
        update_extraction_status(document_id, "PROCESSING")
    except Exception:
        logger.exception("Failed to set PROCESSING for document %s — proceeding anyway", document_id)

    result: ProcessingResult = _pipeline.run(document_id, category_id, file_path, document_type)

    if result.output_file is not None:
        try:
            if result.document_type == "INVOICE":
                notify_invoice_ready(...)
            else:
                notify_candidate_ready(...)
        except Exception:
            logger.exception("Failed to notify backend for document %s", document_id)
            # Notify FAILED since backend ingest did not complete
            _safe_update_status(document_id, "FAILED", "BACKEND_NOTIFY", "POST to ingest endpoint failed")
    else:
        # Pipeline produced no output -- status is REJECTED or ERROR
        reason = result.error or _first_block_reason(result.reports) or "unknown"
        phase = "GUARDRAIL" if result.status == "REJECTED" else "PIPELINE"
        _safe_update_status(document_id, "FAILED", phase, reason)

    if result.status in ("REJECTED", "ERROR"):
        dead_letter_append(...)


def _safe_update_status(document_id: str, status: str, phase: str, message: str) -> None:
    """Best-effort status update -- log and continue on failure."""
    try:
        update_extraction_status(document_id, status, phase, message)
    except Exception:
        logger.exception("Failed to update extraction status for document %s", document_id)
```

### 10.3 Changes to `WorkerPool.submit()` (queue-full case)

When the queue is full and a document is dead-lettered immediately, also call:

```python
_safe_update_status(document_id, "FAILED", "WORKER_POOL", "queue full, document could not be scheduled")
```

---

## 11. Implementation Checklist

### Phase 1: Backend (do first -- all other layers depend on this)

- [ ] Create `ExtractionStatus` enum in `content.entity` with `validateTransitionTo()` method
- [ ] Write Flyway migration `V17__extraction_status_lifecycle.sql`
  - Rename `COMPLETED` -> `SUCCESS`
  - Add `extraction_started_at` column
  - Add CHECK constraint
  - Add partial indexes
- [ ] Update `Document` entity: change `extractionStatus` field to `ExtractionStatus` enum, add `extractionStartedAt`
- [ ] Update `DocumentResponse` record: add `extractionStatus` (String, serialized from enum) and `extractionError` fields
- [ ] Update `DocumentService.toResponse()` to map the two new fields
- [ ] Create `UpdateExtractionStatusRequest` DTO with Jakarta validation (`@NotNull status`)
- [ ] Create `ExtractionStatusResponse` DTO (id, extractionStatus, extractionError)
- [ ] Update `DocumentService.updateExtractionStatus()` to validate state machine transitions and set `extractionStartedAt` on PROCESSING
- [ ] Add `DocumentService.retryExtraction(UUID id)` method: validate FAILED, reset to PENDING, clear error, delete existing cv_candidate/invoice_record, re-trigger file watcher
- [ ] Create `ExtractionStatusController` with `PATCH /{id}/extraction-status` and `POST /{id}/retry-extraction`
- [ ] Add `@PreAuthorize("hasRole('SERVICE')")` on the PATCH endpoint
- [ ] Update `CvCandidateService.ingest()`: change `"COMPLETED"` to `ExtractionStatus.SUCCESS`
- [ ] Update `InvoiceRecordService.ingest()`: change `"COMPLETED"` to `ExtractionStatus.SUCCESS`
- [ ] Create `StuckExtractionJob` with `@Scheduled(fixedDelay = 600_000)` to timeout PROCESSING documents older than 15 minutes
- [ ] Write unit tests for `ExtractionStatus.validateTransitionTo()` (all valid + invalid transitions)
- [ ] Write unit tests for `DocumentService.updateExtractionStatusWithValidation()`
- [ ] Write unit tests for `DocumentService.retryExtraction()`
- [ ] Write integration test for `PATCH /documents/{id}/extraction-status` (auth, valid transition, invalid transition)
- [ ] Write integration test for `POST /documents/{id}/retry-extraction` (auth, valid retry, invalid retry)
- [ ] Write integration test verifying `GET /categories/{catId}/documents` returns `extractionStatus` and `extractionError`

### Phase 2: cv-batch-extractor (depends on Phase 1 backend endpoints being deployed)

- [ ] Add `update_extraction_status()` function to `backend_client.py`
- [ ] Add `_safe_update_status()` helper to `worker.py`
- [ ] Modify `_process_task()` in `worker.py`: call `update_extraction_status(doc_id, "PROCESSING")` at start
- [ ] Modify `_process_task()` in `worker.py`: call FAILED status on pipeline error/rejection (no output file)
- [ ] Modify `_process_task()` in `worker.py`: call FAILED status on backend notify failure
- [ ] Modify `WorkerPool.submit()`: call FAILED status on queue-full dead-letter
- [ ] Add unit tests for the new `update_extraction_status()` function (mock requests)
- [ ] Add integration test: end-to-end pipeline run verifying PROCESSING is called at start and SUCCESS/FAILED at end

### Phase 3: Frontend (depends on Phase 1 backend DTO changes being deployed)

- [ ] Update `AppDocument` interface in `document.model.ts`: add `extractionStatus` and `extractionError`
- [ ] Add `ExtractionStatus` type
- [ ] Update `BackendDoc` interface and `mapDoc()` in `document.api.ts` to include new fields
- [ ] Add `retryExtraction(documentId: string)` method to `DocumentApi`
- [ ] Implement adaptive polling in `DocumentStore` (start/stop based on active extraction statuses)
- [ ] Add `retryExtraction()` method to `DocumentStore`
- [ ] Add `stopPolling()` and call from `DocumentListPage.ngOnDestroy()`
- [ ] Add `#extractionTpl` column template in `document-list.page.html` with badge rendering per status
- [ ] Conditionally add the "Extraction" column only when `catStore.selected()?.documentType` is CV or INVOICE
- [ ] Update `#actionsTpl`: disable "View Candidate"/"View Invoice" buttons unless `extractionStatus === 'SUCCESS'`
- [ ] Add "Retry" button visible only when `extractionStatus === 'FAILED'`
- [ ] Add `.spin` CSS animation in `document-list.page.scss`
- [ ] Add `retryExtraction(row)` method to `DocumentListPage` that calls the store and shows success/error toast
- [ ] Manual test: upload CV, verify badge progression PENDING -> PROCESSING -> SUCCESS
- [ ] Manual test: upload malformed file, verify badge progression PENDING -> PROCESSING -> FAILED, then retry

---

## 12. Sequence Diagram -- Full Happy Path

```
 User          Angular               Spring Boot           File System       cv-batch-extractor
  |               |                       |                     |                    |
  |--upload CV--->|                       |                     |                    |
  |               |--POST /documents----->|                     |                    |
  |               |                       |--save(PENDING)----->|                    |
  |               |                       |--write file-------->|                    |
  |               |<--201 {PENDING}-------|                     |                    |
  |               |                       |                     |                    |
  |               |  (poll every 5s)      |                     |--on_created------->|
  |               |                       |                     |                    |
  |               |                       |<--PATCH(PROCESSING)-|                    |
  |               |                       |--save(PROCESSING)   |                    |
  |               |                       |--200 OK------------>|                    |
  |               |                       |                     |  run pipeline...   |
  |               |--GET /documents------>|                     |                    |
  |               |<--[{PROCESSING}]------|                     |                    |
  |               |                       |                     |  write JSON output |
  |               |                       |<--POST /cv-candidates------------------- |
  |               |                       |--save candidate     |                    |
  |               |                       |--save(SUCCESS)      |                    |
  |               |                       |--201 Created------->|                    |
  |               |                       |                     |                    |
  |               |--GET /documents------>|                     |                    |
  |               |<--[{SUCCESS}]---------|                     |                    |
  |               |  (stop polling)       |                     |                    |
  |<-badge green--|                       |                     |                    |
  |               |                       |                     |                    |
  |--click View-->|                       |                     |                    |
  |               |--navigate to          |                     |                    |
  |               |  /cv-candidates/{cat} |                     |                    |
```

---

## 13. Sequence Diagram -- Failure and Retry Path

```
 User          Angular               Spring Boot           cv-batch-extractor
  |               |                       |                        |
  |               |  (badge: PROCESSING)  |                        |
  |               |                       |<--pipeline fails-------|
  |               |                       |<--PATCH(FAILED, err)---|
  |               |                       |--save(FAILED, err)     |
  |               |                       |--200 OK--------------->|
  |               |                       |                        |
  |               |--GET /documents------>|                        |
  |               |<--[{FAILED, err}]-----|                        |
  |               |  (stop polling)       |                        |
  |<-badge red----|                       |                        |
  |  (tooltip: err)                       |                        |
  |               |                       |                        |
  |--click Retry->|                       |                        |
  |               |--POST /retry--------->|                        |
  |               |                       |--validate FAILED       |
  |               |                       |--set PENDING, clear err|
  |               |                       |--re-trigger file------>| (watchdog picks up)
  |               |<--200 {PENDING}-------|                        |
  |               |  (start polling)      |                        |
  |<-badge queued-|                       |                        |
  |               |                       |                        |  ... cycle repeats
```
