# HR Platform — Product Backlog & Business Workflow

> **Audience:** HR team, product owner, development team
> **Delivery strategy:** Incremental — one epic at a time, ship as each is production-ready
> **Last updated:** 2026-05-25
> **Status:** Living document — update as features are delivered and gaps are closed

---

## Status Key

| Symbol | Meaning |
|--------|---------|
| ✅ | Done — shipped to production |
| 🔲 | Not started |
| 🚧 | In progress |
| ⚠️ | Partial / blocked |

---

## 1. End-to-End Hiring Workflow

The complete hiring lifecycle as driven by three actors: **Dev Team (Requester)**, **HR**, and **Interviewer (Dev Team member)**. Each step is annotated with the system feature supporting it.

```
    +---------------------------+
    |  1. HIRING REQUEST        |  <-- Actor: Dev Team (Requester)
    |  Submit request via       |
    |  platform (role type,     |
    |  description, urgency)    |
    +------------+--------------+
                 |
                 v
    +---------------------------+
    |  2. JOB POSTING           |  <-- Actor: HR
    |  Create job posting from  |
    |  the hiring request       |
    +------------+--------------+
                 |
                 v
    +---------------------------+
    |  3. SOURCING              |  <-- Actor: HR
    |  Upload candidate CVs     |
    |  to CV category           |
    +------------+--------------+
                 |
                 v
    +---------------------------+
    |  4. EXTRACTION            |  <-- Automated (CV Batch Extractor)
    |  Parse & structure        |
    |  candidate profiles       |
    +------------+--------------+
                 |
                 v
    +---------------------------+
    |  5. CANDIDATE SEARCH      |  <-- Actor: HR
    |  Search, filter &         |
    |  shortlist candidates     |
    +------------+--------------+
                 |
                 v
    +---------------------------+
    | 5.5 CV SHARING            |  <-- Actor: HR → Dev Team
    |  HR shares shortlisted    |
    |  candidate CV with the    |
    |  requesting Dev Team      |
    +------------+--------------+
                 |
                 v
    +---------------------------+
    | 5.6 CV REVIEW             |  <-- Actor: Dev Team (Requester)
    |  Dev Team reviews shared  |
    |  CV and gives preliminary |
    |  impression (Interested / |
    |  Not Interested / Later)  |
    +------------+--------------+
                 |
           +-----+-----+
           |           |
    [Interested]  [Not Interested]---> HR sees impression, may
           |                           loop back to Step 5
           v
    +---------------------------+
    |  6. APPLICATION           |  <-- Actor: HR
    |  Apply shortlisted        |
    |  candidates to posting    |
    +------------+--------------+
                 |
                 v
    +---------------------------+
    |  7. INTERVIEW SCHEDULING  |  <-- Actor: HR
    |  Schedule interview &     |
    |  assign Interviewer       |
    +------------+--------------+
                 |
                 v
    +---------------------------+
    |  8. INTERVIEW & FEEDBACK  |  <-- Actor: Interviewer (Dev Team)
    |  Conduct interview &      |
    |  submit feedback          |
    +------------+--------------+
                 |
           +-----+-----+
           |           |
      [Positive]   [Negative]---> Reject or loop back to Step 5
           |
           v
    +---------------------------+
    |  9. HIRING DECISION       |  <-- Actor: HR
    |  Review feedback &        |
    |  decide (hire/reject)     |
    +------------+--------------+
                 |
                 v
    +---------------------------+
    | 10. ONBOARDING PREP       |  <-- Actor: HR
    |  Mark as Hired &          |
    |  prepare onboarding       |
    +---------------------------+
```

### Detailed Step Descriptions

**Step 1 — Hiring Request.** A Dev Team member (the Requester) identifies a staffing need and submits a hiring request **via the platform** using a dedicated form. The form captures role type (Frontend / Backend / Fullstack), description of the need, urgency (Normal / Urgent), and department. The Dev Team member can then track the status of their submitted requests (Pending / In Progress / Candidate Found / Hired / Closed) from a "My Requests" dashboard. **NOTE:** There is currently no system support for this step — requests arrive via email, Slack, or verbal communication (see Gap G14). The platform should provide this formal request submission form and Dev Team dashboard.

**Step 2 — Job Posting.** HR receives the hiring request from the Dev Team and creates a job posting with title, department, location, description, requirements, and deadline. Postings go through a lifecycle: Draft (not visible) → Open (accepting applications) → Closed. The job posting is the system's formalization of the Dev Team's request.

**Step 3 — Sourcing.** HR sources candidates for the open role. CVs are collected from job boards, referrals, career fairs, or direct applications and uploaded to Document Management under a CV-type document category.

**Step 4 — Extraction.** The CV Batch Extractor (automated Python microservice) detects new uploads, runs OCR if needed, extracts structured data via LLM (name, contact, skills, work history, education, certifications), validates through guardrail rules, and writes the structured candidate profile to the database. No manual data entry required.

**Step 5 — Candidate Search.** HR searches for candidates using multi-criteria filters: skills (comma-separated, exact word-boundary matching), job title, location, minimum experience years, and free-text keywords. Results are ranked by a relevance score (0-100) with configurable weights (skills 40%, title 25%, keyword 20%, location 10%, experience 5%). HR reviews candidate profiles and shortlists those who match the Dev Team's requirements.

**Step 5.5 — CV Sharing (HR → Dev Team).** After shortlisting candidates in Step 5, HR shares one or more candidate CV profiles with the Dev Team member who submitted the original hiring request. HR uses the "Share a CV with Dev Team" panel on the hiring request detail page — selecting the candidate from a searchable dropdown and the Dev Team recipient — and the share is recorded with an optional comment. The Dev Team member can then view the shared candidate from their "Shared CVs" inbox. ✅ **Implemented (V18 — cv_shares table, WF-17).**

**Step 5.6 — CV Review (Dev Team Impression).** The Dev Team member reviews shared CV profiles from their "Shared CVs" inbox. Clicking a card opens the full candidate profile (read-only), where they can submit a preliminary impression: **Interested** (proceed with this candidate), **Not Interested** (skip this candidate), or **Review Later** (need more time), with an optional comment. HR sees these impressions on the "Already Shared" panel of the hiring request detail. ✅ **Implemented (WF-18).**

**Step 5.7 — Candidate Hiring Status.** Every candidate in the system carries a denormalised `hiring_status` field that reflects their highest-priority recruitment stage across all active job applications. The system recalculates this automatically whenever a stage changes: `AVAILABLE` (no applications) → `IN_PROCESS` (Applied/Screening/Interview) → `OFFERED` → `HIRED` → `REJECTED`. HR can also manually set a candidate to `WITHDRAWN` from the candidate detail page and return them to the pool. The status badge appears on the candidate list, candidate detail header, and the Dev Team's Shared CVs inbox cards. ✅ **Implemented (V20 — hiring_status column, CandidateHiringStatusService).**

**Step 6 — Application.** HR applies shortlisted candidates to the job posting. Two paths: (a) from Candidate Search results via the "Apply to Job" button, which opens a modal to select an open posting; or (b) from the Kanban board via the "Apply Candidate" action. Each job posting has a Kanban board with pipeline stages (Applied → Screening → Interview → Offer → Hired / Rejected). Applying or moving a stage automatically recalculates the candidate's `hiring_status` (Step 5.7).

**Step 7 — Interview Scheduling.** HR schedules interviews for shortlisted candidates, assigning an Interviewer from the Dev Team. The Interviewer is typically the Dev Team member who submitted the original hiring request (Step 1), or a peer they nominate — their identity is already known from the hiring request and CV review steps. The system captures date/time, meeting link, interviewer assignment, and notes. **NOTE:** The system does not yet send notifications to the assigned Interviewer (see Gap G6 — now CRITICAL given the Interviewer is a Dev Team member who must be informed).

**Step 8 — Interview and Feedback.** The assigned Interviewer (a Dev Team member) conducts the interview and submits structured feedback via the platform: a 1-5 rating, written notes, and a recommendation (Pass / Hold / Reject). A consolidated view aggregates all assessments per interview. **NOTE:** HR is not yet notified when feedback is submitted (see Gap G13 — now CRITICAL).

**Step 9 — Hiring Decision.** HR reviews the Interviewer's feedback. If feedback is positive (Pass recommendation), HR moves the candidate toward hiring. If feedback is negative (Reject), HR either rejects the candidate or loops back to Step 5 to consider other candidates. Candidates with a Hold recommendation may be re-interviewed or placed in a talent pool.

**Step 10 — Onboarding Preparation.** HR marks the candidate as Hired on the Kanban board and initiates onboarding preparation. **NOTE:** The onboarding workflow is not yet built in the system (see Gap G8). Currently, the "Hired" stage is the terminal state in the platform.

---

## 2. Actor Roles

The hiring workflow involves three primary actors and two supporting roles. Each actor maps to a platform **role** with a specific set of **permissions** (see Permission Model below).

| Actor | Platform Role | Description | Workflow Steps | Key System Actions |
|-------|--------------|-------------|----------------|-------------------|
| **Dev Team (Requester)** | `Dev Team` | Initiates the hiring process by identifying a staffing need, submits a hiring request via the platform, tracks request status, reviews candidate CVs shared by HR, provides preliminary impressions, and submits interview feedback | Steps 1, 5.5, 5.6, 8 — Hiring Request, CV Review, Interview Feedback | Submit hiring request via platform form (role type, description, urgency). Track status of submitted requests (Pending / In Progress / Candidate Found / Hired / Closed). View CVs shared by HR. Leave preliminary impression on shared CVs (Interested / Not Interested / Review Later). View Kanban board (read-only). Submit interview feedback. |
| **HR** | `HR` | Receives hiring requests, manages the entire recruitment pipeline end-to-end, shares candidate CVs with Dev Team for review, makes the final hiring decision, and prepares onboarding | Steps 2-7, 5.5, 9-10 — Job Posting, Sourcing, Candidate Search, CV Sharing, Application, Interview Scheduling, Hiring Decision, Onboarding Prep | View all hiring requests and manage their status. Create job postings, upload CVs, search/shortlist candidates, share candidate CVs with Dev Team, apply candidates to postings, schedule interviews, assign interviewers, review feedback, move Kanban stages, mark as hired, prepare onboarding. View full analytics. |
| **Interviewer (Dev Team member)** | `Dev Team` (same role) | A developer from the requesting team who conducts the technical interview and provides structured assessment back to HR | Step 8 — Interview and Feedback | Receive interview notification (G6), view candidate profile and CV, conduct interview, submit feedback (rating 1-5, notes, recommendation: Pass / Hold / Reject) |
| **Administrator** | `Administrator` | Manages platform configuration, users, roles, permissions (supporting role) — has ALL permissions | All (support) | Manage user accounts, configure roles and permissions, manage document categories, view reports and audit logs, plus all recruitment and hiring actions |
| **Manager** | `Manager` | Organizational oversight — read-only visibility into recruitment pipeline and analytics, plus IAM management | Visibility across all | View all hiring requests, view Kanban boards (read-only), search candidates, view analytics. Manage users (create/edit). Does NOT manage recruitment workflow directly. |

### Workflow Actor Interaction

```
Dev Team (Requester)          HR                          Interviewer (Dev Team)
       |                       |                                |
       |--- Hiring Request --->|                                |
       |   (via platform form: |                                |
       |    role, desc, urgency)|                               |
       |                       |--- Create Job Posting          |
       |                       |--- Upload CVs (Sourcing)       |
       |                       |--- Search & Shortlist          |
       |                       |                                |
       |<-- Share CV ----------|                                |
       |--- Review CV -------->|                                |
       |   (Interested / Not   |                                |
       |    Interested / Later) |                               |
       |                       |                                |
       |                       |--- [If interested] Apply       |
       |                       |--- Schedule Interview -------->|
       |                       |                                |--- Conduct Interview
       |                       |<--- Submit Feedback -----------|
       |                       |                                |
       |                       |--- Review Feedback             |
       |                       |--- [If positive] Hire & Onboard|
       |                       |--- [If negative] Reject/Loop   |
       |                       |                                |
```

### Permission Model

The platform uses a `PERM_*` authority model. **Never use `@PreAuthorize("hasAnyRole(...)")` — it evaluates `ROLE_*` prefixes and always returns 403.** Use `hasAuthority('PERM_...')` or rely on `anyRequest().authenticated()` for internal-only endpoints.

#### IAM Permissions (existing — V8)

| Permission | Category | Administrator | Manager | HR | Dev Team | Viewer |
|------------|----------|:---:|:---:|:---:|:---:|:---:|
| usersView | Users | X | X | -- | -- | -- |
| usersCreate | Users | X | X | -- | -- | -- |
| usersEdit | Users | X | X | -- | -- | -- |
| usersDelete | Users | X | -- | -- | -- | -- |
| rolesView | Roles | X | X | -- | -- | -- |
| rolesEdit | Roles | X | -- | -- | -- | -- |

#### Recruitment & Hiring Permissions (new — V19)

| Permission | Category | Administrator | Manager | HR | Dev Team | Viewer |
|------------|----------|:---:|:---:|:---:|:---:|:---:|
| hiringRequestsSubmit | Hiring Requests | X | -- | -- | X | -- |
| hiringRequestsViewOwn | Hiring Requests | X | -- | -- | X | -- |
| hiringRequestsViewAll | Hiring Requests | X | X | X | -- | -- |
| hiringRequestsManage | Hiring Requests | X | -- | X | -- | -- |
| cvSharesSend | CV Sharing | X | -- | X | -- | -- |
| cvSharesReceive | CV Sharing | X | -- | -- | X | -- |
| cvSharesSubmitImpression | CV Sharing | X | -- | -- | X | -- |
| recruitmentBoardView | Recruitment | X | X | X | X | -- |
| recruitmentManage | Recruitment | X | -- | X | -- | -- |
| interviewFeedbackSubmit | Recruitment | X | -- | -- | X | -- |
| candidateSearch | Candidates | X | X | X | -- | -- |
| analyticsView | Analytics | X | X | X | -- | -- |

#### Role Summary

| Role | UUID | Total Permissions | Description |
|------|------|:-:|-------------|
| Administrator | `...0001` | 18 (all) | Full system access — IAM + all recruitment |
| Manager | `...0002` | 8 | IAM read/write + recruitment visibility (read-only) + analytics |
| Viewer | `...0003` | 0 | Read-only access (authenticated endpoints only) |
| HR | `...0004` | 7 | Full recruitment pipeline management (no IAM) |
| Dev Team | `...0005` | 6 | Hiring requests, CV review, board view, interview feedback |

#### Demo Users (V19)

| User | Email | Password | Role |
|------|-------|----------|------|
| Admin User | admin@demo.com | admin123 | Administrator |
| Manager User | manager@demo.com | manager123 | Manager |
| Viewer User | viewer@demo.com | viewer123 | Viewer |
| HR User | hr@demo.com | hr123 | HR |
| Dev Team User | devteam@demo.com | devteam123 | Dev Team |

#### Key Design Decisions

- **Dev Team does NOT get `candidateSearch`** — searching all candidates is HR's job. Dev Team only sees candidates that HR explicitly shares with them via the CV Sharing mechanism.
- **Dev Team gets `recruitmentBoardView` (read-only)** — they can view the Kanban board for postings linked to their hiring requests to track pipeline progress, but cannot create postings, apply candidates, or move stages.
- **HR has zero IAM permissions** — HR manages recruitment, not user accounts. If an HR user also needs IAM access, assign them the Manager role as a secondary role.
- **Manager has read-only recruitment visibility** — Managers can view hiring request queues, Kanban boards, candidate profiles, and analytics for organizational oversight, but do not directly manage the recruitment workflow.

Document Management uses per-category role visibility (canView, canUpload, canDelete). HR can view and upload; Dev Team can view only.

---

## 3. Workflow Phases → Feature Coverage

### Step 1 — Hiring Request (Dev Team → HR)

| Activity | System Feature | Status |
|----------|---------------|--------|
| Dev Team submits hiring request via platform (role type, description, urgency, department) | **Not built** — planned: Dev Team request form with fields for role type (Frontend/Backend/Fullstack), description, urgency (Normal/Urgent), department | 🔲 GAP G14 (WF-16) |
| Dev Team tracks status of submitted requests (Pending / In Progress / Candidate Found / Hired / Closed) | **Not built** — planned: "My Requests" dashboard for Dev Team showing all submitted requests with live status | 🔲 GAP G14 (WF-19) |
| HR receives and reviews request queue | **Not built** — planned: HR-facing request queue with filters by status, urgency, department | 🔲 GAP G14 |
| HR approves request and converts to job posting | **Not built** — planned: "Convert to Job Posting" action that pre-fills posting fields from request data | 🔲 GAP G14 |

**Path:** Not yet implemented — currently handled via email/Slack/verbal communication. Planned: Sidebar > Dev Team > My Requests > Submit New Request

**Actor:** Dev Team (Requester)

---

### Step 2 — Job Posting (HR creates from request)

| Activity | System Feature | Status |
|----------|---------------|--------|
| Create job posting (title, dept, location, requirements, deadline) | Job Form page | ✅ |
| Manage posting lifecycle (Draft / Open / Closed) | Job List with status filter | ✅ |
| Edit existing posting | Job Form (edit mode) | ✅ |
| Add structured required skills to a posting | Not built — requirements is free text only | 🔲 GAP G3 (WF-9) |
| Link posting back to hiring request | Not built — no hiring request entity exists | 🔲 GAP G14 |

**Path:** Sidebar › Hiring › Job Postings › New Job Posting

**Actor:** HR

---

### Step 3 — Sourcing (HR uploads CVs)

| Activity | System Feature | Status |
|----------|---------------|--------|
| Upload CVs from various sources | Document Management — upload to CV category | ✅ |
| Bulk upload multiple CVs | Multi-file upload | ✅ |
| Track upload status (pending, committed, failed) | Document status tracking | ✅ |

**Path:** Sidebar › Documents › CV category › Upload Document

**Actor:** HR

---

### Step 4 — Automated Extraction

| Activity | System Feature | Status |
|----------|---------------|--------|
| Parse CV text (PDF, images) | CV Batch Extractor — OCR + LLM pipeline | ✅ |
| Extract structured fields (name, skills, experience, education) | Guardrail-validated extraction to `cv_candidates` tables | ✅ |
| Handle extraction failures | Status tracking (PASS/DEGRADED → COMPLETED; REJECTED/ERROR → FAILED) | ✅ |

**Path:** Automated — no user action required after upload

**Actor:** System (automated)

---

### Step 5 — Candidate Search and Shortlisting (HR)

| Activity | System Feature | Status |
|----------|---------------|--------|
| Search by skills (word-boundary matching) | Multi-criteria search with PostgreSQL POSIX regex | ✅ |
| Search by title, location, experience | CvSearchService with configurable relevance weights | ✅ |
| Free-text keyword search | Keyword parameter in search API | ✅ |
| View candidate profile (skills, work history, education) | Candidate Detail page | ✅ |
| View candidate's application history across postings | Application History section on profile | ✅ Sprint 1 |
| Search candidates in context of a specific job posting | Not yet built | 🔲 GAP G1 (WF-4) |
| See which candidates already applied to a posting | Not yet built | 🔲 GAP G2 (WF-5) |
| Auto-suggest candidates based on skill match to posting | Not built | 🔲 GAP (WF-10) |

**Path:** Sidebar › Hiring › Candidates › Search form

**Actor:** HR

---

### Step 5.5 — CV Sharing (HR → Dev Team)

| Activity | System Feature | Status |
|----------|---------------|--------|
| HR shares shortlisted candidate CV with a Dev Team member | "Share a CV with Dev Team" panel on Hiring Request detail — searchable candidate dropdown + Dev Team member dropdown + optional comment → `POST /api/v1/hiring-requests/{id}/cv-shares` | ✅ WF-17 |
| HR sees all CVs already shared for this hiring request | "Already Shared" list on Hiring Request detail with impression badges and share dates | ✅ WF-17 |
| Dev Team member views inbox of CVs shared with them | "Shared CVs" inbox page — card grid with candidate name, hiring request title, impression badge, hiring status badge → `GET /api/v1/cv-shares/inbox` | ✅ WF-17 |
| Dev Team member opens full candidate CV profile from inbox | Click card → navigates to read-only Candidate Detail page (`/cv-shares/:shareId`) | ✅ WF-17 |

**Path (HR):** Sidebar › HR Request Queue › [request] › Share a CV with Dev Team panel

**Path (Dev Team):** Sidebar › Shared CVs › [card]

**Actor:** HR (shares), Dev Team (receives and views)

---

### Step 5.6 — CV Review and Preliminary Impression (Dev Team)

| Activity | System Feature | Status |
|----------|---------------|--------|
| Dev Team sees list of candidates HR has shared | "Shared CVs" inbox — card grid shows candidate name, hiring request, sharer, date, impression badge, hiring status badge | ✅ WF-18 |
| Dev Team opens full candidate CV profile | Click card → `CvShareDetailPage` — loads full read-only candidate profile (`/cv-shares/:shareId`) | ✅ WF-18 |
| Dev Team marks preliminary impression (Interested / Not Interested / Review Later) | Impression form on CV Share Detail page → `PATCH /api/v1/cv-shares/{shareId}/impression` | ✅ WF-18 |
| Dev Team leaves optional comment on candidate | Comment textarea on impression form | ✅ WF-18 |
| HR sees Dev Team impression and decides whether to proceed | "Already Shared" panel on Hiring Request Detail shows impression badge per share (Pending / INTERESTED / NOT_INTERESTED / REVIEW_LATER) | ✅ WF-18 |

**Path:** Sidebar › Shared CVs › [card] › impression form at bottom of CV Detail

**Actor:** Dev Team (Requester)

---

### Step 5.7 — Candidate Hiring Status (Cross-cutting)

| Activity | System Feature | Status |
|----------|---------------|--------|
| System derives and persists candidate's highest-priority recruitment status | `CandidateHiringStatusService.recalculate()` called after every `apply()` and `moveStage()`. Priority: HIRED > OFFERED > IN_PROCESS > REJECTED > AVAILABLE | ✅ V20 |
| Status badge on candidate list table | "Hiring Status" column with colour-coded badge (Available / In Process / Offered / Hired / Rejected / Withdrawn) | ✅ V20 |
| Status badge on candidate detail header | Hiring status badge shown alongside confidence level badge in profile header | ✅ V20 |
| Status badge on Dev Team Shared CVs inbox | Non-AVAILABLE hiring status shown as secondary badge on inbox cards | ✅ V20 |
| HR manually withdraws candidate from pool | "Withdraw" button on candidate detail (HR only) → `PATCH /api/v1/cv-candidates/{id}/hiring-status` with `{ hiringStatus: "WITHDRAWN" }` | ✅ V20 |
| HR returns withdrawn candidate to pool | "Return to Pool" button replaces Withdraw when status is WITHDRAWN → resets to AVAILABLE | ✅ V20 |
| Backfill from existing applications on migration | V20 Flyway migration backfills `hiring_status` from `job_applications` for all existing candidates | ✅ V20 |

**Actor:** System (automatic recalculation); HR (manual Withdraw/Return)

---

### Step 6 — Application (HR applies shortlisted candidates)

| Activity | System Feature | Status |
|----------|---------------|--------|
| Apply candidate to job from search results | "Apply to Job" button per search result row | ✅ Sprint 1 |
| Apply candidate from Kanban board | "Apply Candidate" action on Job Board | ✅ |
| Batch-apply multiple candidates | Not built | 🔲 GAP G4 (WF-11) |
| Prevent duplicate applications — "already applied" indicator | Not built | 🔲 GAP G2 (WF-5) |
| View candidates per stage on Kanban board | Job Board columns: Applied → Screening → Interview → Offer → Hired / Rejected | ✅ |
| Move candidate between stages with notes | Angular signal-controlled dropdown (position:fixed to escape overflow clipping); stage transition with audit log | ✅ |
| hiring_status auto-updated on stage change | `CandidateHiringStatusService.recalculate()` runs in same transaction as every stage move | ✅ V20 |
| View stage transition history (who moved, when, notes) | Stage history log | ✅ |
| View candidate profile from Kanban card | Clickable candidate name → Candidate Detail | ✅ Sprint 1 |
| View pipeline health stats on board header | Pipeline stats (total, in-pipeline, hired, hire rate) | ✅ Sprint 1 |
| Per-posting analytics (funnel, conversion, time metrics) on board | Not built — analytics are aggregate-only | 🔲 GAP G5 (WF-7) |

**Path:** Candidates › Search › "Apply to Job", OR Job Board › "Apply Candidate"

**Actor:** HR

---

### Step 7 — Interview Scheduling (HR assigns Interviewer)

**NOTE:** The Interviewer is typically already known at this point — it is the Dev Team member who submitted the original hiring request (Step 1) and reviewed the CV (Step 5.6), or a peer they nominate. HR does not need to search for an interviewer; they confirm or reassign from the known requester.

| Activity | System Feature | Status |
|----------|---------------|--------|
| Schedule interview (date/time, meeting link, notes) | Interview scheduling on Kanban board | ✅ |
| Assign Interviewer (Dev Team member) to interview | Interviewer assignment on interview form | ✅ |
| Pre-populate interviewer from hiring request's requester | **Not built** — planned: auto-suggest the requester as default interviewer | 🔲 GAP G14 (dependency) |
| View scheduled interviews | Interview list per application | ✅ |
| Cancel or reschedule interview | Interview management | ✅ |
| **Send notification to assigned Interviewer** | **Not built — Interviewer has no way to know they are scheduled** | 🔲 **GAP G6 (CRITICAL)** |
| Send interview invitation email to candidate | Not built | 🔲 GAP G6 |
| Calendar integration (Google, Outlook) | Not built | 🔲 GAP (deferred) |

**Path:** Job Board › candidate card › Schedule Interview

**Actor:** HR

---

### Step 8 — Interview and Feedback (Interviewer submits)

| Activity | System Feature | Status |
|----------|---------------|--------|
| View candidate profile and CV before interview | Candidate Detail page (accessible to Interviewer) | ✅ |
| Submit feedback (1-5 rating, notes, recommendation: Pass / Hold / Reject) | Interview feedback form | ✅ |
| View consolidated feedback per interview | Aggregated feedback view | ✅ |
| Compare feedback across multiple interviewers | Consolidated view shows all assessments | ✅ |
| **Notify HR when feedback is submitted** | **Not built — HR must manually check for new feedback** | 🔲 **GAP G13 (CRITICAL)** |
| Automated scoring/recommendation aggregation | Not built — manual review only | 🔲 GAP |

**Path:** Job Board › candidate card › Interview › Submit Feedback

**Actor:** Interviewer (Dev Team member)

---

### Step 9 — Hiring Decision (HR reviews feedback)

| Activity | System Feature | Status |
|----------|---------------|--------|
| Review Interviewer feedback (rating, notes, recommendation) | Consolidated feedback view | ✅ |
| Move candidate to Offer stage (positive feedback) | Kanban stage transition | ✅ |
| Move candidate to Rejected stage (negative feedback) | Kanban stage transition | ✅ |
| Loop back to search for more candidates (if needed) | Manual navigation to Candidate Search | ✅ |
| Generate offer letter | Not built | 🔲 GAP G7 (deferred) |
| Track offer terms (salary, start date, expiration) | Not built | 🔲 GAP G7 |
| Track offer status (pending / accepted / declined / expired) | Not built — stage movement only | 🔲 GAP G7 |

**Path:** Job Board › candidate card › view feedback → move stage

**Actor:** HR

---

### Step 10 — Onboarding Preparation (HR finalizes hire)

| Activity | System Feature | Status |
|----------|---------------|--------|
| Mark candidate as hired | Kanban stage transition to "Hired" | ✅ |
| **Initiate onboarding preparation checklist** | **Not built** | 🔲 **GAP G8 (MEDIUM)** |
| Create employee record | Not built | 🔲 GAP G8 (deferred) |
| Auto-close posting when position is filled | Not built — manual close only | 🔲 GAP G9 |
| Notify Dev Team (Requester) that position is filled | Not built | 🔲 GAP G13 |

**Path:** Job Board › move candidate card to "Hired" column

**Actor:** HR

---

### Supporting — Analytics & Continuous Improvement

| Activity | System Feature | Status |
|----------|---------------|--------|
| Recruitment funnel (candidates per stage) | Funnel chart on HR Analytics page | ✅ |
| Time-to-hire per posting (top 10) | Time-to-hire bar chart | ✅ |
| Top 15 skills in candidate pool | Top skills bar chart | ✅ |
| Monthly application volume trend (12-month rolling) | Area chart | ✅ |
| Stage conversion rates | Conversion rate visualization | ✅ |
| Filter analytics by specific job posting | Not built — aggregate only | 🔲 GAP G11 (WF-7) |
| Drill down from chart to underlying data | Not built — charts are static | 🔲 GAP G10 (WF-6, WF-12, WF-13) |
| Export reports (PDF, Excel) | Not built | 🔲 GAP G12 |

**Path:** Sidebar › Hiring › Pipeline Analytics

**Actor:** HR

---

## 4. Gap Analysis

### 4.1 Summary Matrix

| Gap ID | Phase | Description | Severity | Updated |
|--------|-------|-------------|----------|---------|
| **G14** | **Hiring Request** | **No system support for Dev Team to submit hiring requests or track their status — currently done via email/Slack/verbal. Also needs Dev Team dashboard showing request status and shared CVs.** | **CRITICAL** | **2026-05-24** |
| G1 | Discovery / Requisition | No job-context-aware candidate search — cannot search "for" a specific posting | HIGH | |
| G2 | Discovery / Application | Search results do not indicate already-applied candidates | MEDIUM | |
| G3 | Requisition | Job requirements are free text, not structured skills — no auto-matching | HIGH | |
| G4 | Application | No batch-apply for multiple candidates at once | LOW | |
| G5 | Screening | No per-posting analytics on the Kanban board | MEDIUM | |
| **G6** | **Interview** | **No notification to Interviewer when interview is scheduled — Interviewer is a Dev Team member who must be informed by the system** | **CRITICAL** | **2026-05-24** |
| G7 | Offer | No offer letter generation or offer term tracking | MEDIUM | |
| G8 | Hired / Onboarding | No onboarding workflow or employee record creation — final step of the hiring process has no system support | MEDIUM | 2026-05-24 |
| G9 | Hired | Job posting not auto-closed when position is filled | LOW | |
| G10 | Analytics | Charts are static — no drill-down to underlying candidate records | MEDIUM | |
| G11 | Analytics | No per-posting filter on HR Analytics — aggregate metrics only | MEDIUM | |
| G12 | Analytics | No report export (PDF, Excel) | LOW | |
| **G13** | **Cross-cutting** | **No notifications for stage changes or feedback submissions — HR has no way to know when an Interviewer submits feedback without manually checking** | **CRITICAL** | **2026-05-24** |
| ~~G15~~ | ~~CV Sharing~~ | ~~No CV sharing mechanism — HR cannot share a candidate's CV profile with the Dev Team.~~ | ~~CRITICAL~~ | **✅ RESOLVED 2026-05-25** — `cv_shares` table (V18), `CvShareService`, HR share panel on request detail, Dev Team Shared CVs inbox (`cv-shares` route) |
| ~~G16~~ | ~~CV Review~~ | ~~No preliminary impression / CV review step for Dev Team.~~ | ~~CRITICAL~~ | **✅ RESOLVED 2026-05-25** — `CvShareDetailPage` with impression form; HR sees impression badges on hiring request detail |

---

### 4.2 Detailed Gap Descriptions

#### G1 + G3 — Job-to-Candidate Matching (HIGH, compound)

**What users try to do:** An HR officer creates a job posting for "Senior Java Developer in Berlin" and wants to immediately find matching candidates.

**What happens today:** After creating a posting, the user lands on the Job List with no link to Candidate Search. They must manually navigate to search, re-type the skills from the posting's requirements field, enter the location, and run the search. The `JobPosting.requirements` column is free text (`TEXT`), not structured data, so there is no automated bridge between job requirements and search criteria.

**Ideal experience:** Job postings should have a structured `required_skills` field (tag input, same pattern as Candidate Search). A "Find Candidates" button on the Kanban board should pre-populate the search form with the posting's skills and location. This requires two stories: WF-9 (structured skills on job form) and WF-4 (Find Candidates button).

---

#### G2 — No Already-Applied Indicator (MEDIUM)

**What users try to do:** Search for candidates for a specific job posting without re-applying someone who is already in the pipeline.

**What happens today:** `CvSearchResultResponse` returns candidate data only. There is no field indicating whether a candidate has already been applied to any job posting. The user has no visual cue and risks submitting duplicate applications (which are blocked at the backend with a `ConflictException`, but the UX offers no warning beforehand).

**Ideal experience:** When navigating to Candidate Search from a job posting context, search results should badge already-applied candidates. Requires an optional `forJobPostingId` query param on the search endpoint and a left join to `job_applications`.

---

#### G3 — Unstructured Job Requirements (HIGH) — see G1+G3 above

---

#### G4 — No Batch Apply (LOW)

**What users try to do:** Shortlist 10 candidates from search results and apply all of them to a posting at once.

**What happens today:** Each "Apply to Job" action is individual. The user must open the modal, select the posting, submit, close — and repeat for each candidate.

**Ideal experience:** Row checkboxes on search results + "Apply Selected" button that submits all selected candidates to one posting.

---

#### G5 — No Per-Posting Stats on Kanban (MEDIUM)

**What users try to do:** Understand this specific job posting's pipeline health without leaving the Kanban board.

**What happens today:** The board header now shows Total / In Pipeline / Hired / Hire Rate (delivered Sprint 1). However, there are no conversion rates, no average time-in-stage, and no comparison to other postings.

**Ideal experience:** A compact analytics panel on the board showing per-stage conversion and time-in-stage metrics for this posting specifically.

---

#### G6 — No Interview Notifications (CRITICAL — updated 2026-05-24)

**Why CRITICAL:** In the revised workflow, the Interviewer is a Dev Team member who is assigned by HR to conduct the interview. They are NOT actively monitoring the recruitment pipeline. Without system notification, the Interviewer has no way to know they have been scheduled unless HR manually contacts them via Slack/email outside the platform. This breaks the workflow at Step 7 → Step 8.

**What users try to do:** HR schedules an interview and assigns a Dev Team member as Interviewer. The Interviewer should be automatically notified with meeting details.

**What happens today:** The system captures date, time, meeting link, and notes for each interview, but nothing is sent. Interviewers learn about interviews through external channels (email, Slack, calendar invites created manually by HR).

**Ideal experience:** When HR schedules an interview and assigns an Interviewer, the system sends an in-app notification AND email to the Interviewer with: candidate name, role, date/time, meeting link, and a direct link to the candidate's profile. Requires SMTP relay integration (already referenced in the platform architecture) and wiring to the existing notification infrastructure (`notifications` table).

---

#### G7 — No Offer Tracking (MEDIUM)

**What happens today:** The "Offer" stage on the Kanban marks that an offer has been extended, but no offer terms, status, or letter are tracked. The entire offer process happens outside the system (email, spreadsheets).

**Ideal experience:** Offer stage should capture salary, start date, offer expiration date, and offer status (pending / accepted / declined / expired). Offer letter generation is deferred.

---

#### G8 — No Onboarding Preparation (MEDIUM — updated 2026-05-24)

**Why upgraded from LOW to MEDIUM:** In the revised workflow, onboarding preparation is the explicit final step (Step 10). When HR receives positive feedback and decides to hire, they need to prepare onboarding within the platform. This is no longer a "nice to have" — it is the defined conclusion of the business process.

**What happens today:** When a candidate moves to "Hired," there is no workflow handoff to onboarding. The "Hired" column on the Kanban board is a terminal dead-end. All onboarding activities happen outside the system.

**Ideal experience:** After marking a candidate as Hired, the system should present an onboarding preparation checklist (document collection, equipment request, team assignment, start date confirmation). A basic version could be a simple checklist attached to the "Hired" stage without requiring a full employee management module.

---

#### G9 — Manual Posting Close (LOW)

When a candidate is hired, the job posting remains "Open" unless manually closed. Stale open postings accumulate and may receive additional applications. Fix: prompt to close the posting when a candidate reaches "Hired."

---

#### G10 — Static Analytics Charts (MEDIUM)

**What happens today:** All five charts on the HR Analytics page are read-only Highcharts visualizations with no click handlers. `HrAnalyticsPage` has no `Router` injection. Clicking a funnel bar, a time-to-hire column, or a skill bar does nothing.

**Ideal experience:** Funnel bars → navigate to filtered list of candidates in that stage. Time-to-hire columns → open that posting's Kanban board. Top skills bars → open Candidate Search pre-filled with that skill. Requires Highcharts `point.events.click` handlers and router navigation.

---

#### G11 — No Per-Posting Analytics Filter (MEDIUM)

**What happens today:** All five `HrAnalyticsService` queries aggregate across all job postings. There is no `WHERE job_posting_id = ?` clause in any of the five analytics SQL statements.

**Ideal experience:** A job posting dropdown on the analytics page filters all charts to show metrics for one posting. Requires an optional `jobPostingId` query param on all five analytics endpoints.

---

#### G12 — No Report Export (LOW)

Charts and tables cannot be exported. Analytics insights cannot be shared in leadership meetings without screen captures.

---

#### G13 — No Stage Change / Feedback Notifications (CRITICAL — updated 2026-05-24)

**Why CRITICAL:** In the revised workflow, the handoff between Interviewer (Step 8) and HR (Step 9) depends entirely on HR being notified when feedback is submitted. Without this notification, HR must manually poll the system to check if the Interviewer has completed their feedback. This creates a bottleneck at the most critical decision point in the workflow.

**What happens today:** When a candidate moves stages, or an interview is scheduled, or feedback is submitted — only the person taking the action knows. The notification infrastructure exists in the database schema (`notifications`, `push_subscriptions` tables) and is referenced in the architecture docs, but it has not been wired to recruitment domain events.

**Ideal experience:** Domain events (stage moved, interview scheduled, feedback submitted) should be published and consumed by a notification service that sends in-app and email notifications to relevant stakeholders. Priority notifications:
1. **Feedback submitted** → notify HR (enables Step 9 — Hiring Decision)
2. **Interview scheduled** → notify Interviewer (enables Step 8 — covered by G6)
3. **Stage changed** → notify HR Manager for visibility

---

#### G14 — No Hiring Request Submission (CRITICAL — new 2026-05-24)

**Why CRITICAL:** The hiring request is Step 1 of the entire workflow — it is the trigger that starts everything. Currently, there is zero system support for this step. Dev Team members communicate hiring needs to HR via email, Slack, or verbal requests. This means:
- No audit trail of who requested what and when
- No standardized format for requests (role type, team, urgency, required skills)
- No way for HR to track request backlog or prioritize across teams
- No visibility for the Dev Team on request status

**What happens today:** A Dev Team lead sends an email or Slack message saying "we need a frontend developer." HR manually translates this into a job posting. There is no connection between the original request and the resulting posting.

**Ideal experience:** A "Hiring Request" form accessible to Dev Team members (role: Requester) that captures:
- Role type (Frontend / Backend / Fullstack)
- Team / department
- Urgency (Normal / Urgent)
- Key skills required (free text or structured)
- Justification / context
- Preferred start date

The request should appear in an HR queue, and HR should be able to convert an approved request into a job posting with pre-filled fields. The requesting Dev Team member should be able to track the status of their request.

**Data model required:**
```sql
CREATE TABLE hiring_requests (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    requester_id    UUID NOT NULL REFERENCES users(id),
    role_type       VARCHAR(20) NOT NULL,  -- FRONTEND, BACKEND, FULLSTACK
    team            VARCHAR(100) NOT NULL,
    urgency         VARCHAR(20) DEFAULT 'NORMAL',  -- NORMAL, URGENT
    required_skills TEXT,
    justification   TEXT,
    preferred_start DATE,
    status          VARCHAR(20) DEFAULT 'PENDING',  -- PENDING, APPROVED, IN_PROGRESS, FILLED, REJECTED
    job_posting_id  UUID REFERENCES job_postings(id),  -- linked when HR creates the posting
    created_at      TIMESTAMP DEFAULT now(),
    updated_at      TIMESTAMP DEFAULT now()
);
CREATE INDEX idx_hiring_requests_requester ON hiring_requests(requester_id);
CREATE INDEX idx_hiring_requests_status ON hiring_requests(status);
```

---

#### ~~G15~~ — CV Sharing Mechanism — ✅ RESOLVED 2026-05-25

**What was missing:** No mechanism for HR to share a candidate's CV with the Dev Team.

**How it was resolved:**
- **V18 Flyway migration** — `cv_shares` table (`id`, `hiring_request_id`, `cv_candidate_id`, `shared_by`, `shared_with`, `impression`, `comment`, `shared_at`, `reviewed_at`)
- **`CvShareService` / `CvShareController`** — `POST /api/v1/hiring-requests/{id}/cv-shares` (share), `GET /cv-shares/inbox` (Dev Team inbox), `GET /cv-shares/{shareId}` (detail)
- **HR UI** — "Share a CV with Dev Team" panel + "Already Shared" list on Hiring Request detail page (`request-detail.page.html`)
- **Dev Team UI** — "Shared CVs" inbox page (`cv-share-inbox.page`), guarded by `cvSharesReceive` permission

---

#### ~~G16~~ — Preliminary Impression / CV Review Step — ✅ RESOLVED 2026-05-25

**What was missing:** No mechanism for the Dev Team to give preliminary impressions on shared CVs.

**How it was resolved:**
- **`CvShareDetailPage`** — reads the share by ID, loads the full candidate profile (read-only), shows impression form at the bottom
- **Impression API** — `PATCH /api/v1/cv-shares/{shareId}/impression` → writes `impression` + `comment` + `reviewedAt`
- **HR visibility** — impression badges (Pending / INTERESTED / NOT\_INTERESTED / REVIEW\_LATER) shown in "Already Shared" panel on the hiring request detail

---

### 4.3 Data Model Gaps

#### Missing: Structured skills on `job_postings`

`JobPosting.requirements` is free text (`TEXT`). No `job_posting_skills` table exists. Required for G1, G3, WF-4, WF-9, WF-10, and skill-demand analytics.

```sql
CREATE TABLE job_posting_skills (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    job_posting_id  UUID NOT NULL REFERENCES job_postings(id) ON DELETE CASCADE,
    skill_name      VARCHAR(100) NOT NULL,
    is_required     BOOLEAN DEFAULT true,
    UNIQUE (job_posting_id, skill_name)
);
CREATE INDEX idx_job_posting_skills_posting ON job_posting_skills(job_posting_id);
CREATE INDEX idx_job_posting_skills_name    ON job_posting_skills(skill_name);
```

#### ~~Missing: `cv_shares` table for CV sharing and Dev Team impressions~~ — ✅ RESOLVED 2026-05-25

`cv_shares` table is live (V18). `CvShareService`, `CvShareController`, `CvShareInboxPage`, `CvShareDetailPage`, and the HR share panel on `RequestDetailPage` are all implemented. G15 and G16 are closed.

#### Missing: `hiring_status` on candidate pool — ✅ RESOLVED 2026-05-25 (V20)

`cv_candidates.hiring_status VARCHAR(20)` added by V20 migration. Values: `AVAILABLE | IN_PROCESS | OFFERED | HIRED | REJECTED | WITHDRAWN`. Recalculated automatically by `CandidateHiringStatusService.recalculate()` on every application stage change. HR can manually set `WITHDRAWN` / `AVAILABLE` via `PATCH /api/v1/cv-candidates/{id}/hiring-status`.

#### Missing: `appliedJobPostingIds` on candidate search results

`CvSearchResultResponse` has no field indicating whether a candidate has been applied to a given posting. Required for G2, WF-5. Fix: add optional `forJobPostingId` query param + left join to `job_applications`.

#### Missing: `jobPostingId` on analytics DTOs

`TimeToHireEntry` returns `jobTitle` and `avgDays` only; `FunnelStageEntry` has no job-level breakdown. Required for G10, G11, WF-7, WF-13. Fix: add `jobPostingId: UUID` to `TimeToHireEntry`; add optional `jobPostingId` filter to all five analytics endpoints.

---

## 5. Product Backlog

### Epic 1 — Candidate Search & Profile Management

> Goal: Allow HR team to search and browse candidate profiles extracted from uploaded CVs.

| Story ID | Story | Backend | Frontend | Status |
|----------|-------|---------|----------|--------|
| CS-1 | Multi-criteria candidate search (`GET /api/v1/cv-candidates/search`) with relevance scoring (skills=40%, title=25%, keyword=20%, location=10%, experience=5%), word-boundary regex skill matching, pagination, and sort | ✅ | ✅ | ✅ Done |

**CS-1 Acceptance Criteria:**
- Search by skills (comma-separated), title, location, min experience, keyword
- Relevance score 0–100 per candidate; `Java` does NOT match `JavaScript` (POSIX `\m...\M`)
- Paginated response (`page`, `size`, `totalElements`); sort by `relevanceScore` / `fullName` / `experienceYears`
- Angular skill tag input with auto-flush on search click
- 276 unit tests; JaCoCo ≥ 95% branch coverage

---

### Epic 2 — Recruitment Pipeline (ATS)

> Goal: Track candidates through a structured hiring process from application to offer.

#### 2A — Job Postings

| Story ID | Story | Backend | Frontend | Status |
|----------|-------|---------|----------|--------|
| RP-1 | Create / edit / archive job postings (title, department, location, description, requirements, deadline) | ✅ | ✅ | ✅ Done |
| RP-2 | List job postings with status filter (Open / Closed / Draft) and pagination | ✅ | ✅ | ✅ Done |
| RP-3 | Link CV candidates to job postings as applicants | ✅ | ✅ | ✅ Done |

#### 2B — ATS Kanban Board

| Story ID | Story | Backend | Frontend | Status |
|----------|-------|---------|----------|--------|
| RP-4 | Pipeline stages: Applied → Screening → Interview → Offer → Hired / Rejected | ✅ | ✅ | ✅ Done |
| RP-5 | Move candidate between stages with notes (Kanban board per job posting) | ✅ | ✅ | ✅ Done |
| RP-6 | Stage history log — who moved the candidate and when | ✅ | ✅ | ✅ Done |

#### 2C — Interview & Feedback

| Story ID | Story | Backend | Frontend | Status |
|----------|-------|---------|----------|--------|
| RP-7 | Schedule interviews — date/time, meeting link, notes | ✅ | ✅ | ✅ Done |
| RP-8 | Submit interview feedback (rating 1–5, notes, recommendation: Pass / Hold / Reject) | ✅ | ✅ | ✅ Done |
| RP-9 | Consolidated feedback view per interview | ✅ | ✅ | ✅ Done |

---

### Epic 3 — HR Analytics Dashboard

> Goal: Give HR managers visibility into recruitment funnel health and workforce metrics.

| Story ID | Story | Backend | Frontend | Status |
|----------|-------|---------|----------|--------|
| AN-1 | Recruitment funnel: candidate counts by pipeline stage | ✅ | ✅ | ✅ Done |
| AN-2 | Time-to-hire: average days from Applied to Hired per posting (top 10) | ✅ | ✅ | ✅ Done |
| AN-3 | Top 15 skills by unique candidate count (bar chart) | ✅ | ✅ | ✅ Done |
| AN-4 | Monthly application volume trend (12-month rolling area chart) | ✅ | ✅ | ✅ Done |
| AN-5 | Stage conversion rates: % of candidates advancing to next stage | ✅ | ✅ | ✅ Done |

---

### Sprint 1 — Workflow Integration

> Goal: Bridge the three epics so HR users can navigate naturally between Candidate Search, Recruitment Pipeline, and Analytics without dead ends.

| Story ID | Story | Backend | Frontend | Status |
|----------|-------|---------|----------|--------|
| WF-1 | "Apply to Job" button on Candidate Search results → modal with open job postings selector | ✅ | ✅ | ✅ Done |
| WF-2 | Clickable candidate name on Kanban board → navigates to candidate detail profile | ✅ | ✅ | ✅ Done |
| WF-3 | Application history section on candidate detail page — shows all job applications with stage, job title, and date | ✅ | ✅ | ✅ Done |
| WF-8 | Pipeline stats header (Total / In Pipeline / Hired / Hire Rate) on Kanban board | — | ✅ | ✅ Done |
| Sidebar | Sidebar grouped into Hiring / Content / Administration sections | — | ✅ | ✅ Done |

---

### Delivery Timeline

```
Phase 1 (DONE) ─── Epic 1: Candidate Search ──────────────── ✅ Shipped
                    CS-1: multi-criteria search + relevance scoring

Phase 2 (DONE) ─── Epic 2A: Job Postings ─────────────────── ✅ RP-1, RP-2, RP-3
                    Epic 2B: ATS Kanban Board ──────────────── ✅ RP-4, RP-5, RP-6
                    Epic 2C: Interview & Feedback ──────────── ✅ RP-7, RP-8, RP-9

Phase 3 (DONE) ─── Epic 3: HR Analytics Dashboard ───────────── ✅ AN-1 → AN-5

Sprint 1 (DONE) ── Integration stories (workflow bridges) ─────── ✅ WF-1, WF-2, WF-3, WF-8, Sidebar

Sprint 2 (IN PROGRESS) ── Dev Team UI + critical workflow gaps
                    ✅ WF-17: CV Sharing (HR shares candidate with Dev Team)
                    ✅ WF-18: CV Review & Preliminary Impression (Dev Team)
                    ✅ HS-1:  Candidate Hiring Status (V20) — auto-recalculation,
                              status badges everywhere, Withdraw/Return actions
                    ✅ Access control: Shared CVs inbox (cvSharesReceive guard),
                              Recruitment route (recruitmentManage guard),
                              Documents/Knowledge Base hidden from Dev Team,
                              candidate search restricted to HR/Admin/Manager
                    🔲 WF-16: Hiring Request Form (Dev Team → HR)
                    🔲 WF-19: Request Status Tracking (Dev Team dashboard)
                    🔲 G6:  Interview notification to Interviewer
                    🔲 G13: Feedback-submitted notification to HR
                    🔲 WF-9: Structured skills on job postings
                    🔲 WF-4 + WF-5: Job-candidate matching UX

Sprint 3         ── Onboarding prep + analytics enhancements
                    - G8: Onboarding preparation checklist
                    - WF-7: Per-posting analytics filter
                    - WF-6 + WF-12 + WF-13: Chart drill-down
```

---

## 6. Integration Stories — Full Catalog

### Must Have

| Story ID | User Story | Affected Components | Effort | Status |
|----------|-----------|---------------------|--------|--------|
| WF-1 | As an HR officer, I want to apply a candidate to a job posting directly from the search results, so that I do not have to navigate to the Kanban board and re-search. | `candidate-search.page.ts` (Apply to Job action), `candidate-search.page.html` (modal), `recruitment.api.ts` | M | ✅ Done |
| WF-2 | As an HR officer, I want to click a candidate's name on the Kanban board to view their full CV profile, so that I can make informed stage-progression decisions. | `job-board.page.html` (routerLink), `ApplicationResponse.java` (documentCategoryId), `JobApplicationService.java` | S | ✅ Done |
| WF-3 | As an HR officer, I want to see a candidate's application history on their profile page, so that I know which jobs they have applied to and their current stage. | `candidate-detail.page.ts`, `GET /api/v1/cv-candidates/{id}/applications`, `CvCandidateController.java`, `JobApplicationRepository.java` | M | ✅ Done |
| WF-17 | As an HR user, I want to share a shortlisted candidate's CV with a Dev Team member from the hiring request detail page. | `CvShareService`, `CvShareController`, share panel on `request-detail.page.html`, `CvShareInboxPage` (`/cv-shares/inbox`), `cv_shares` table (V18) | M | ✅ Done |
| WF-18 | As a Dev Team member, I want to open shared CVs and mark my preliminary impression (Interested / Not Interested / Review Later) so HR knows whether to proceed. | `CvShareDetailPage`, `PATCH /cv-shares/{shareId}/impression`, impression badges on request detail | M | ✅ Done |
| HS-1 | As an HR officer, I want to see each candidate's current hiring status at a glance (Available / In Process / Offered / Hired / Rejected / Withdrawn), so that I can quickly assess the talent pool and avoid engaging candidates who are already hired or withdrawn. | V20 migration (`cv_candidates.hiring_status`), `CandidateHiringStatusService.recalculate()` hooked into `JobApplicationService.apply()` + `moveStage()`, status badge on candidate list/detail/cv-share-inbox, `PATCH /cv-candidates/{id}/hiring-status` for manual Withdraw/Return | M | ✅ Done |
| WF-4 | As an HR officer, I want to click "Find Candidates" on a job posting's Kanban board, so that the system takes me to Candidate Search with the posting's skills and location pre-filled. | `job-board.page.html` (button), `job-board.page.ts` (navigate with query params), `candidate-search.page.ts` (read query params), WF-9 (prerequisite) | M | 🔲 Next |
| WF-16 | As a Dev Team member, I want to submit a hiring request (role type, description, urgency) via the platform, so that HR receives it formally and can track it. | `HiringRequestController.java`, `HiringRequestService.java`, `hiring_requests` table (Flyway), `my-requests.page.ts`, `request-form.page.ts` | L | 🔲 Sprint 2 |
| WF-17 | As an HR user, I want to share a shortlisted candidate's CV with the Dev Team member, so that they can review the profile before the interview is scheduled. | `CvShareController.java`, `CvShareService.java`, `cv_shares` table (V18), HR share panel on `request-detail.page.html`, `cv-share-inbox.page.ts` | M | ✅ Done |
| WF-18 | As a Dev Team member, I want to see a list of CVs that HR has shared with me and mark each as Interested / Not Interested / Review Later, so that HR knows my preliminary impression. | `CvShareInboxPage`, `CvShareDetailPage`, `PATCH /cv-shares/{shareId}/impression`, impression badges on request detail | M | ✅ Done |
| WF-19 | As a Dev Team member, I want to see the status of my submitted hiring requests (Pending / In Progress / Candidate Found / Hired / Closed), so that I have visibility without asking HR directly. | `my-requests.page.ts` (status column + filters), `HiringRequestController.java` (GET by requester) | S | 🔲 Sprint 2 |

### Should Have

| Story ID | User Story | Affected Components | Effort | Status |
|----------|-----------|---------------------|--------|--------|
| WF-5 | As an HR officer, I want search results to indicate which candidates have already applied to the current job posting, so that I do not create duplicates. | `CvSearchResultResponse.java` (appliedJobPostingIds), `CvSearchService.java` (left join to job_applications), `candidate-search.page.html` (badge) | M | 🔲 |
| WF-6 | As an HR manager, I want to click a bar in the recruitment funnel chart to see the candidates in that stage, so that I can act on bottlenecks. | `hr-analytics.page.ts` (Highcharts point.click), new filtered candidate list endpoint or route | M | 🔲 |
| WF-7 | As an HR manager, I want to filter HR Analytics by a specific job posting, so that I can assess individual role pipeline health. | `hr-analytics.page.ts` (dropdown), `HrAnalyticsService.java` (WHERE job_posting_id), all 5 analytics endpoints | L | 🔲 |
| WF-8 | As an HR manager, I want a compact pipeline summary on the Kanban board header (total, in-pipeline, hired, hire rate). | `job-board.page.ts` (computed signals), `job-board.page.html` (stats row) | S | ✅ Done |

### Could Have

| Story ID | User Story | Affected Components | Effort | Status |
|----------|-----------|---------------------|--------|--------|
| WF-9 | As an HR officer, I want to add structured required skills to a job posting (tag input), so that the system can match candidates automatically. | `JobPosting.java` + `job_posting_skills` table (Flyway migration), `job-form.page.ts/html`, DTOs | L | 🔲 |
| WF-10 | As an HR officer, I want the system to show a job fit score when viewing candidates for a posting. | `JobMatchService.java`, `GET /api/v1/recruitment/job-postings/{id}/candidate-matches`, Angular modal/page; depends on WF-9 | L | 🔲 |
| WF-11 | As an HR officer, I want to batch-apply multiple candidates from search results to a posting at once. | `candidate-search.page.ts` (row checkboxes), `POST /api/v1/recruitment/job-postings/{id}/applications/batch` | M | 🔲 |
| WF-12 | As an HR manager, I want to click a skill on the Top Skills chart to open Candidate Search pre-filled with that skill. | `hr-analytics.page.ts` (click handler, router navigate) | S | 🔲 |
| WF-13 | As an HR manager, I want to click a job posting on the Time-to-Hire chart to open its Kanban board. | `hr-analytics.page.ts` (click handler), `TimeToHireEntry` (add jobPostingId) | S | 🔲 |

### Won't Have (This Cycle)

| Story ID | User Story | Rationale |
|----------|-----------|-----------|
| WF-14 | Auto-extract skills from free-text job requirements using LLM | Defer until WF-9 proves structured skills are valuable |
| ~~WF-15~~ | ~~Real-time notifications for stage changes~~ | **MOVED TO SPRINT 2** — G6 and G13 are now CRITICAL per workflow analysis (2026-05-24). Interviewer notification and feedback-submitted notification are required for the Dev Team ↔ HR handoff to function. |
| WF-20 | Full onboarding workflow (employee record, IT provisioning, access management) | Deferred — basic onboarding checklist (G8) is in Sprint 3; full module is a separate epic |

---

## 7. Recommended Next Stories

> **Updated 2026-05-25** — Sprint 2 partially delivered. WF-17 (CV Sharing), WF-18 (CV Review), and HS-1 (Candidate Hiring Status) are shipped. Remaining Sprint 2 items focus on notification infrastructure and hiring request submission.

### Priority 1 — CRITICAL Workflow Gaps (Sprint 2 remaining)

| # | Story | Gaps Closed | Business Justification | Effort | Status |
|---|-------|-------------|----------------------|--------|--------|
| 1 | **WF-17 — CV Sharing (HR → Dev Team)** | ~~G15~~ | Shipped — HR share panel on request detail; Dev Team Shared CVs inbox | M | ✅ Done |
| 2 | **WF-18 — CV Review and Preliminary Impression** | ~~G16~~ | Shipped — impression form on CV Share Detail page; badges visible to HR | M | ✅ Done |
| 3 | **HS-1 — Candidate Hiring Status (V20)** | — | Shipped — auto-recalculation on every stage change; badges on list/detail/inbox; Withdraw/Return actions for HR | M | ✅ Done |
| 4 | **WF-16 — Hiring Request Form** — Dev Team submits hiring request (role type, description, urgency, department) via the platform → HR queue → convert to job posting | G14 | Step 1 of the workflow has ZERO system support. The entire process starts with this request, yet it happens outside the platform. | L | 🔲 |
| 5 | **WF-19 — Request Status Tracking** — Dev Team sees status of submitted requests (Pending / In Progress / Candidate Found / Hired / Closed) | G14 | Dev Team members have zero visibility into what happens after they submit a request. | S | 🔲 |
| 6 | **G6** — Interview notification to Interviewer (in-app + email with candidate details, meeting link, profile link) | G6 | Step 7 → Step 8 handoff is broken. The Interviewer has no way to know they have been assigned an interview. | M | 🔲 |
| 7 | **G13** — Feedback-submitted notification to HR (in-app + email) | G13 | Step 8 → Step 9 handoff is broken. HR cannot make a hiring decision if they do not know feedback has been submitted. | M | 🔲 |

### Priority 2 — High Value (Sprint 2-3)

| # | Story | Gaps Closed | Business Justification | Effort |
|---|-------|-------------|----------------------|--------|
| 7 | **WF-9** — Structured required skills on job postings (tag input → `job_posting_skills` table) | G3 | Foundation for all job-candidate matching. Without structured skills, no automated matching, no fit scoring, no skill-demand analytics. Every downstream feature depends on this. | L |
| 8 | **WF-4** — "Find Candidates" button on job posting/Kanban → Candidate Search pre-filled | G1 | Eliminates the highest-friction daily workflow: re-typing job requirements into search. Depends on WF-9. | M |
| 9 | **WF-5** — Already-applied indicator on search results when in posting context | G2 | Prevents duplicate applications and wasted recruiter time on candidates already in the pipeline. | M |

### Priority 3 — Should Have (Sprint 3-4)

| # | Story | Gaps Closed | Business Justification | Effort |
|---|-------|-------------|----------------------|--------|
| 10 | **G8** — Basic onboarding preparation checklist on "Hired" stage | G8 | Step 10 of the workflow (onboarding prep) has no system support. A simple checklist attached to the Hired stage would bridge this gap without requiring a full employee module. | M |
| 11 | **WF-7** — Per-posting analytics filter on HR Analytics page | G5, G11 | HR managers need individual role health, not just aggregate metrics. Critical for spotting bottlenecks in specific roles. | L |
| 12 | **WF-6 + WF-12 + WF-13** — Interactive chart drill-down | G10 | Analytics are currently "look but don't act." Drill-down turns dashboards into operational tools. | M |

### Priority 4 — Could Have (Future Sprints)

| # | Story | Gaps Closed | Business Justification | Effort |
|---|-------|-------------|----------------------|--------|
| 13 | **WF-10** — Job fit scoring (candidate skills vs. posting required skills) | G1 | Helps recruiters prioritize which candidates to review. Depends on WF-9. | L |
| 14 | **WF-11** — Batch-apply candidates from search results | G4 | Efficiency gain for high-volume roles. | M |
| 15 | **G12** — Report export (PDF / Excel) from HR Analytics | G12 | Enables offline sharing of metrics in leadership meetings. | M |
| 16 | **G7** — Offer term tracking (salary, start date, status) | G7 | Brings the offer process into the system instead of spreadsheets. | M |
| 17 | **G9** — Auto-close posting prompt when candidate is hired | G9 | Prevents stale open postings. | S |

### Priority 5 — Deferred

| # | Story | Gap | Rationale |
|---|-------|-----|-----------|
| 18 | **WF-20** — Full onboarding workflow (employee record, IT provisioning) | G8 | Basic checklist (Priority 3) should be delivered first; full module is a separate epic |
| 19 | Calendar integration (Google / Outlook) | G6 | OAuth2 integration — complex. In-app + email notification (Priority 1) is sufficient for MVP |
| 20 | Auto-extract skills from requirements text via LLM | G3 | Wait until WF-9 proves structured skills are valuable |
| 21 | Offer letter generation | G7 | Requires document template engine — low priority until G7 basics are in place |

---

## 8. Navigation Structure

Sidebar items are shown or hidden based on the logged-in user's permissions. The required permission for each item is listed in brackets.

```
Dashboard                                                    [all authenticated]

─── HIRING ────────────────────────────────────────────────────
  Candidates              Search and browse candidate profiles       [candidateSearch: HR, Manager, Admin]
                          (hidden from Dev Team — no candidateSearch perm)
                          Badges show hiring_status on every row.
  Job Postings            Manage postings and Kanban pipeline        [recruitmentManage: HR, Admin ONLY]
                          (hidden from Dev Team and Manager)
  Hiring Requests         HR request queue                           [hiringRequestsViewAll: HR, Manager, Admin]
  Pipeline Analytics      Recruitment metrics and charts             [analyticsView: HR, Manager, Admin]

─── DEV TEAM ──────────────────────────────────────────────────
  My Requests             Submit new hiring request, view status     [hiringRequestsSubmit + hiringRequestsViewOwn: Dev Team, Admin]
                          of all submitted requests — 🔲 not yet built (WF-16, WF-19)
  Shared CVs              View candidates HR has shared; open        [cvSharesReceive: Dev Team, Admin] ✅ LIVE
                          full CV profile; mark impression
                          (Interested / Not Interested / Review
                          Later); leave comment.
                          Inbox cards show hiring_status badge.

─── CONTENT ───────────────────────────────────────────────────
  Documents               Document categories and file management    [all authenticated EXCEPT Dev Team]
                          (hidden from Dev Team — hiddenFor cvSharesReceive)
  Knowledge Base          Technical knowledge graph                  [all authenticated EXCEPT Dev Team]
                          (hidden from Dev Team — hiddenFor cvSharesReceive)

─── ADMINISTRATION ────────────────────────────────────────────
  Users                   User account management                    [usersView: Manager, Admin]
  Roles                   Role and permission management             [rolesEdit: Admin]
  Reports                 System reports                             [usersView: Manager, Admin]
```

The "Hiring" group presents the three HR-facing recruitment features in workflow order:
1. **Candidates** — find the people (sourcing, search)
2. **Job Postings** — manage the process (pipeline, interviews, feedback)
3. **Pipeline Analytics** — measure the outcomes (funnel, time-to-hire, trends)

The "Dev Team" group presents the Dev Team's own UI experience:
1. **My Requests** — submit new hiring requests and track their lifecycle status
2. **Shared CVs** — review candidate profiles HR has shared, give preliminary impressions

### Page-Level CTA Map

| Page | Current CTAs | Planned Additions |
|------|-------------|-------------------|
| Candidate Search | Search, Clear, View Profile per row, Apply to Job per row | "Search for Job: [title]" context banner when arriving from a posting (WF-4) |
| Candidate Detail | Back to Candidates, Application History section, hiring_status badge, Withdraw/Return button (HR only) | Apply to Job button in header (WF-1 variant) |
| Job List | New Job Posting, View Board / Edit / Close per row | "Find Candidates" per row (WF-4) |
| Job Board (Kanban) | Back to Jobs, Apply Candidate, candidate name link, pipeline stats header, Move Stage dropdown (Angular signal-controlled) | "Find Candidates" button (WF-4), per-stage analytics (WF-7) |
| HR Analytics | Read-only charts | Job posting filter dropdown (WF-7), clickable chart elements (WF-6, WF-12, WF-13) |
| My Requests (Dev Team) | **New page** — not yet built | Submit New Request (form), View Status per row, Filter by status (WF-16, WF-19) |
| Shared CVs (Dev Team) | Card grid inbox, impression badge, hiring_status badge on card, click → full CV profile + impression form | ✅ Live (WF-17, WF-18) |

---

## 9. Story ↔ Gap Cross-Reference

> Updated 2026-05-25 — Sprint 2 partially delivered; CV sharing + hiring status shipped.

| Story | Gap Addressed | Sprint | Status | Priority |
|-------|--------------|--------|--------|----------|
| **WF-16 — Hiring Request Form** | G14 | Sprint 2 | 🔲 | P1 CRITICAL |
| **WF-17 — CV Sharing (HR → Dev Team)** | G15 | Sprint 2 | ✅ Done | ~~P1 CRITICAL~~ |
| **WF-18 — CV Review and Preliminary Impression** | G16 | Sprint 2 | ✅ Done | ~~P1 CRITICAL~~ |
| **HS-1 — Candidate Hiring Status** | Cross-cutting | Sprint 2 | ✅ Done | — |
| **WF-19 — Request Status Tracking** | G14 | Sprint 2 | 🔲 | P1 CRITICAL |
| **G6 — Interview notification to Interviewer** | G6 | Sprint 2 | 🔲 | P1 CRITICAL |
| **G13 — Feedback-submitted notification to HR** | G13 | Sprint 2 | 🔲 | P1 CRITICAL |
| WF-9 — Structured skills on postings | G3 | Sprint 2 | 🔲 | P2 |
| WF-4 — Find Candidates from posting | G1 | Sprint 2-3 | 🔲 | P2 |
| WF-5 — Already-applied indicator | G2 | Sprint 2-3 | 🔲 | P2 |
| G8 — Onboarding preparation checklist | G8 | Sprint 3 | 🔲 | P3 |
| WF-7 — Per-posting analytics filter | G11 | Sprint 3 | 🔲 | P3 |
| WF-6 — Clickable funnel chart | G10 | Sprint 3 | 🔲 | P3 |
| WF-12 — Clickable top skills chart | G10 | Sprint 3 | 🔲 | P3 |
| WF-13 — Clickable time-to-hire chart | G10 | Sprint 3 | 🔲 | P3 |
| WF-10 — Job fit scoring | G1 | Future | 🔲 | P4 |
| WF-11 — Batch-apply candidates | G4 | Future | 🔲 | P4 |
| G7 — Offer term tracking | G7 | Future | 🔲 | P4 |
| G9 — Auto-close posting | G9 | Future | 🔲 | P4 |
| G12 — Report export | G12 | Future | 🔲 | P4 |
| WF-1 — Apply to Job from search | G4 (partial) | Sprint 1 | ✅ Done | — |
| WF-2 — Clickable candidate on Kanban | Navigation (Screening phase) | Sprint 1 | ✅ Done | — |
| WF-3 — Application history on profile | Discovery context | Sprint 1 | ✅ Done | — |
| WF-8 — Pipeline stats on Kanban | G5 | Sprint 1 | ✅ Done | — |
| Sidebar grouping | Navigation clarity | Sprint 1 | ✅ Done | — |

---

## 10. Technical Notes

- **Auth model:** JWT cookie name is `access-token`. Permissions use `PERM_*` authority style — **never** `@PreAuthorize("hasAnyRole(...)")` which evaluates `ROLE_*` and always returns 403. Use `hasAuthority('PERM_...')` or rely on `anyRequest().authenticated()` for internal endpoints.
- **Search engine:** `CvSearchService` uses `NamedParameterJdbcTemplate` native SQL (CTEs + dynamic WHERE) — not JPA Specification.
- **Skill matching:** PostgreSQL POSIX regex `~*` with `\m...\M` word-boundary anchors — `Java` does NOT match `JavaScript`.
- **Coverage gate:** JaCoCo 95% branch coverage enforced in `pom.xml` — all new services must meet this threshold.
- **Schema validation:** Hibernate validates on startup — DB `SMALLINT` must map to Java `short`, not `int`.
- **Analytics:** `HrAnalyticsService` uses `EntityManager` native queries (same pattern as `ReportService`).
- **Docker rebuild:** `docker compose build app && docker compose up -d --force-recreate app` — restart alone does not pick up code changes.
- **Maven path:** `JAVA_HOME=/opt/tools/jdk-21.0.11/Contents/Home`, `MAVEN_HOME=/opt/tools/apache-maven-3.9.16`; always run from `demo-app-backend/`.
- **Angular conventions:** Signals-based stores, standalone components, `SHARED_IMPORTS`, `app-data-table`, `app-page-layout`, `app-hc-chart` — no NgModules, no Angular Material.
- **Sidebar hidden items:** `hiddenFor: keyof RolePermissions` on `NavItem` hides an item when the user HAS that permission. Used to hide Documents and Knowledge Base from Dev Team (`hiddenFor: 'cvSharesReceive'`). Distinct from `permission` which shows an item only when the user has that permission.
- **Angular custom dropdowns:** Use `openId = signal<string | null>(null)` + `@HostListener('document:click') close()` + `position: fixed` + `getBoundingClientRect()` for dropdowns inside `overflow: auto` containers (e.g. Kanban board). Never use Bootstrap JS `data-bs-toggle="dropdown"` — Bootstrap JS is not loaded.
- **Hiring status recalculation:** `CandidateHiringStatusService.recalculate()` (in `recruitment.service` package) is called synchronously in the same transaction as `JobApplicationService.apply()` and `moveStage()`. Priority: `HIRED > OFFERED > IN_PROCESS > REJECTED > AVAILABLE`. WITHDRAWN is manual-only.
- **Login:** `admin@demo.com` / `admin123`
