-- ============================================================================
-- V19: Dev Team & HR Roles with Recruitment Permissions
--
-- Introduces two new roles and 12 new permissions covering the recruitment
-- and hiring workflow features built in Sprint 2 (hiring requests, CV sharing,
-- recruitment pipeline, interview feedback, candidate search, analytics).
--
-- The existing 6 permissions (IAM: Users + Roles) remain unchanged.
-- Existing role-permission assignments (Administrator, Manager, Viewer) are
-- extended with the new permissions where appropriate.
--
-- Dependencies: V2 (IAM tables), V8 (seed data)
-- ============================================================================

-- ─────────────────────────────────────────────────────────────────────────────
-- 1. NEW PERMISSIONS (12 permissions for recruitment workflow)
--    Continuing from 10000000-0000-0000-0000-000000000006 (last existing)
-- ─────────────────────────────────────────────────────────────────────────────

INSERT INTO permissions (id, code, description, category) VALUES
    -- Hiring Requests
    ('10000000-0000-0000-0000-000000000007', 'hiringRequestsSubmit',
     'Submit new hiring requests',                          'Hiring Requests'),
    ('10000000-0000-0000-0000-000000000008', 'hiringRequestsViewOwn',
     'View own submitted hiring requests',                  'Hiring Requests'),
    ('10000000-0000-0000-0000-000000000009', 'hiringRequestsViewAll',
     'View all hiring requests (HR queue)',                  'Hiring Requests'),
    ('10000000-0000-0000-0000-000000000010', 'hiringRequestsManage',
     'Update hiring request status (approve/reject/close)',  'Hiring Requests'),

    -- CV Sharing
    ('10000000-0000-0000-0000-000000000011', 'cvSharesSend',
     'Share candidate CVs with Dev Team members',           'CV Sharing'),
    ('10000000-0000-0000-0000-000000000012', 'cvSharesReceive',
     'View CV sharing inbox and shared candidate profiles', 'CV Sharing'),
    ('10000000-0000-0000-0000-000000000013', 'cvSharesSubmitImpression',
     'Submit preliminary impression on shared CVs',         'CV Sharing'),

    -- Recruitment
    ('10000000-0000-0000-0000-000000000014', 'recruitmentBoardView',
     'View Kanban board and job posting details (read-only)', 'Recruitment'),
    ('10000000-0000-0000-0000-000000000015', 'recruitmentManage',
     'Full recruitment pipeline management (postings, applications, stages, interviews)', 'Recruitment'),
    ('10000000-0000-0000-0000-000000000016', 'interviewFeedbackSubmit',
     'Submit interview feedback (rating, notes, recommendation)', 'Recruitment'),

    -- Candidates
    ('10000000-0000-0000-0000-000000000017', 'candidateSearch',
     'Search and browse all candidate profiles',            'Candidates'),

    -- Analytics
    ('10000000-0000-0000-0000-000000000018', 'analyticsView',
     'View full HR analytics dashboard',                    'Analytics')
ON CONFLICT (id) DO NOTHING;


-- ─────────────────────────────────────────────────────────────────────────────
-- 2. NEW ROLES
--    Continuing from 00000000-0000-0000-0000-000000000003 (Viewer)
-- ─────────────────────────────────────────────────────────────────────────────

INSERT INTO roles (id, name, description, is_builtin) VALUES
    ('00000000-0000-0000-0000-000000000004', 'HR',
     'Recruitment pipeline management — job postings, candidate sourcing, CV sharing, interview scheduling, and analytics',
     true),
    ('00000000-0000-0000-0000-000000000005', 'Dev Team',
     'Hiring requests, CV review, and interview feedback — the development team participant in the hiring workflow',
     true)
ON CONFLICT (id) DO NOTHING;


-- ─────────────────────────────────────────────────────────────────────────────
-- 3. ROLE-PERMISSION ASSIGNMENTS
-- ─────────────────────────────────────────────────────────────────────────────

-- 3a. Administrator: gets ALL new permissions (already has all 6 IAM perms)
INSERT INTO role_permissions (role_id, permission_id)
SELECT '00000000-0000-0000-0000-000000000001', id
FROM   permissions
WHERE  code IN (
    'hiringRequestsSubmit', 'hiringRequestsViewOwn', 'hiringRequestsViewAll',
    'hiringRequestsManage', 'cvSharesSend', 'cvSharesReceive',
    'cvSharesSubmitImpression', 'recruitmentBoardView', 'recruitmentManage',
    'interviewFeedbackSubmit', 'candidateSearch', 'analyticsView'
)
ON CONFLICT DO NOTHING;

-- 3b. Manager: visibility into recruitment (read-only) + analytics
--     Gets: hiringRequestsViewAll, recruitmentBoardView, candidateSearch, analyticsView
INSERT INTO role_permissions (role_id, permission_id)
SELECT '00000000-0000-0000-0000-000000000002', id
FROM   permissions
WHERE  code IN (
    'hiringRequestsViewAll', 'recruitmentBoardView',
    'candidateSearch', 'analyticsView'
)
ON CONFLICT DO NOTHING;

-- 3c. HR: full recruitment management (no IAM permissions)
--     Gets: hiringRequestsViewAll, hiringRequestsManage,
--            cvSharesSend, recruitmentBoardView, recruitmentManage,
--            candidateSearch, analyticsView
INSERT INTO role_permissions (role_id, permission_id)
SELECT '00000000-0000-0000-0000-000000000004', id
FROM   permissions
WHERE  code IN (
    'hiringRequestsViewAll', 'hiringRequestsManage',
    'cvSharesSend', 'recruitmentBoardView', 'recruitmentManage',
    'candidateSearch', 'analyticsView'
)
ON CONFLICT DO NOTHING;

-- 3d. Dev Team: hiring workflow participant permissions
--     Gets: hiringRequestsSubmit, hiringRequestsViewOwn,
--            cvSharesReceive, cvSharesSubmitImpression,
--            recruitmentBoardView, interviewFeedbackSubmit
INSERT INTO role_permissions (role_id, permission_id)
SELECT '00000000-0000-0000-0000-000000000005', id
FROM   permissions
WHERE  code IN (
    'hiringRequestsSubmit', 'hiringRequestsViewOwn',
    'cvSharesReceive', 'cvSharesSubmitImpression',
    'recruitmentBoardView', 'interviewFeedbackSubmit'
)
ON CONFLICT DO NOTHING;

-- 3e. Viewer: no new permissions (remains read-only, no recruitment access)


-- ─────────────────────────────────────────────────────────────────────────────
-- 4. DEMO USERS
-- ─────────────────────────────────────────────────────────────────────────────

-- 4a. HR demo user: hr@demo.com / hr123
INSERT INTO users (id, full_name, email, status) VALUES
    ('20000000-0000-0000-0000-000000000004', 'HR User', 'hr@demo.com', 'active')
ON CONFLICT (id) DO NOTHING;

INSERT INTO credentials (user_id, password_hash) VALUES
    ('20000000-0000-0000-0000-000000000004',
     '$2b$12$VyexH2Zjj0p8vn2SU9aoCu6Iik8R8Euv3Y/c3RdkS3.IMo9IFq5Ma')
ON CONFLICT (user_id) DO NOTHING;

INSERT INTO user_roles (user_id, role_id) VALUES
    ('20000000-0000-0000-0000-000000000004', '00000000-0000-0000-0000-000000000004')
ON CONFLICT DO NOTHING;

INSERT INTO user_settings (user_id) VALUES
    ('20000000-0000-0000-0000-000000000004')
ON CONFLICT (user_id) DO NOTHING;

-- 4b. Dev Team demo user: devteam@demo.com / devteam123
INSERT INTO users (id, full_name, email, status) VALUES
    ('20000000-0000-0000-0000-000000000005', 'Dev Team User', 'devteam@demo.com', 'active')
ON CONFLICT (id) DO NOTHING;

INSERT INTO credentials (user_id, password_hash) VALUES
    ('20000000-0000-0000-0000-000000000005',
     '$2b$12$CJIYPA.mQSZWgRbuHqYUiOC76MUAzabBu08N8SO.B8QJUGvdtE3qK')
ON CONFLICT (user_id) DO NOTHING;

INSERT INTO user_roles (user_id, role_id) VALUES
    ('20000000-0000-0000-0000-000000000005', '00000000-0000-0000-0000-000000000005')
ON CONFLICT DO NOTHING;

INSERT INTO user_settings (user_id) VALUES
    ('20000000-0000-0000-0000-000000000005')
ON CONFLICT (user_id) DO NOTHING;


-- ─────────────────────────────────────────────────────────────────────────────
-- 5. DOCUMENT CATEGORY VISIBILITY FOR NEW ROLES
--    HR and Dev Team can view document categories; HR can upload; neither can delete
-- ─────────────────────────────────────────────────────────────────────────────

INSERT INTO category_role_visibility (category_id, role_id, can_view, can_upload, can_delete)
SELECT c.id, r.id,
       true,
       r.name = 'HR',
       false
FROM document_categories c
CROSS JOIN roles r
WHERE r.id IN (
    '00000000-0000-0000-0000-000000000004',
    '00000000-0000-0000-0000-000000000005'
)
AND c.id IN (
    '30000000-0000-0000-0000-000000000001',
    '30000000-0000-0000-0000-000000000002',
    '30000000-0000-0000-0000-000000000003'
)
ON CONFLICT DO NOTHING;
