-- Roles (fixed UUIDs for idempotent re-seeding)
INSERT INTO roles (id, name, description, is_builtin) VALUES
    ('00000000-0000-0000-0000-000000000001', 'Administrator', 'Full system access',          true),
    ('00000000-0000-0000-0000-000000000002', 'Manager',       'User and content management', true),
    ('00000000-0000-0000-0000-000000000003', 'Viewer',        'Read-only access',            true)
ON CONFLICT (id) DO NOTHING;

-- Permissions
INSERT INTO permissions (id, code, description, category) VALUES
    ('10000000-0000-0000-0000-000000000001', 'usersView',   'View user list',      'Users'),
    ('10000000-0000-0000-0000-000000000002', 'usersCreate', 'Create users',        'Users'),
    ('10000000-0000-0000-0000-000000000003', 'usersEdit',   'Edit users',          'Users'),
    ('10000000-0000-0000-0000-000000000004', 'usersDelete', 'Delete users',        'Users'),
    ('10000000-0000-0000-0000-000000000005', 'rolesView',   'View role list',      'Roles'),
    ('10000000-0000-0000-0000-000000000006', 'rolesEdit',   'Create / edit roles', 'Roles')
ON CONFLICT (id) DO NOTHING;

-- Administrator: all 6 permissions
INSERT INTO role_permissions (role_id, permission_id)
SELECT '00000000-0000-0000-0000-000000000001', id FROM permissions
ON CONFLICT DO NOTHING;

-- Manager: usersView, usersCreate, usersEdit, rolesView
INSERT INTO role_permissions (role_id, permission_id)
SELECT '00000000-0000-0000-0000-000000000002', id
FROM   permissions
WHERE  code IN ('usersView', 'usersCreate', 'usersEdit', 'rolesView')
ON CONFLICT DO NOTHING;

-- Viewer: no permissions

-- Demo users
INSERT INTO users (id, full_name, email, status) VALUES
    ('20000000-0000-0000-0000-000000000001', 'Admin User',   'admin@demo.com',   'active'),
    ('20000000-0000-0000-0000-000000000002', 'Manager User', 'manager@demo.com', 'active'),
    ('20000000-0000-0000-0000-000000000003', 'Viewer User',  'viewer@demo.com',  'active')
ON CONFLICT (id) DO NOTHING;

-- Passwords: admin123 / manager123 / viewer123 (bcrypt $2b$12 cost)
INSERT INTO credentials (user_id, password_hash) VALUES
    ('20000000-0000-0000-0000-000000000001',
     '$2b$12$/pCSjj6veC0Se2UegPP/JuaAgIE526YA2RXpePw/IFzEp7EJTfmMG'),
    ('20000000-0000-0000-0000-000000000002',
     '$2b$12$TEJSZg.M6Fnqigs0z4tvbuXfE5eBBTWUiHBnnaRqXp7ARiVlqpo5m'),
    ('20000000-0000-0000-0000-000000000003',
     '$2b$12$HqynRC2tdTfWHF/5w5Sq8.Gh5dRfmsZzcC2vxa53IWqdJDjLvRRwS')
ON CONFLICT (user_id) DO NOTHING;

INSERT INTO user_roles (user_id, role_id) VALUES
    ('20000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000001'),
    ('20000000-0000-0000-0000-000000000002', '00000000-0000-0000-0000-000000000002'),
    ('20000000-0000-0000-0000-000000000003', '00000000-0000-0000-0000-000000000003')
ON CONFLICT DO NOTHING;

INSERT INTO user_settings (user_id) VALUES
    ('20000000-0000-0000-0000-000000000001'),
    ('20000000-0000-0000-0000-000000000002'),
    ('20000000-0000-0000-0000-000000000003')
ON CONFLICT (user_id) DO NOTHING;

-- Sample document categories
INSERT INTO document_categories (id, name, description) VALUES
    ('30000000-0000-0000-0000-000000000001', 'Contracts',   'Legal contracts and agreements'),
    ('30000000-0000-0000-0000-000000000002', 'Reports',     'Monthly and quarterly reports'),
    ('30000000-0000-0000-0000-000000000003', 'HR Policies', 'Human resources policy documents')
ON CONFLICT (id) DO NOTHING;

-- All roles can view; Admin+Manager can upload; only Admin can delete
INSERT INTO category_role_visibility (category_id, role_id, can_view, can_upload, can_delete)
SELECT c.id, r.id,
       true,
       r.name IN ('Administrator', 'Manager'),
       r.name = 'Administrator'
FROM document_categories c
CROSS JOIN roles r
WHERE c.id IN (
    '30000000-0000-0000-0000-000000000001',
    '30000000-0000-0000-0000-000000000002',
    '30000000-0000-0000-0000-000000000003'
)
ON CONFLICT DO NOTHING;
