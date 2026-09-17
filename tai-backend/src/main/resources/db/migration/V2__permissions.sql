CREATE TABLE permissions (
    id          BIGSERIAL PRIMARY KEY,
    code        VARCHAR(80) NOT NULL UNIQUE,
    description VARCHAR(255)
);

CREATE TABLE role_permissions (
    role_id       BIGINT NOT NULL REFERENCES roles(id) ON DELETE CASCADE,
    permission_id BIGINT NOT NULL REFERENCES permissions(id) ON DELETE CASCADE,
    PRIMARY KEY (role_id, permission_id)
);

INSERT INTO permissions (code, description) VALUES
    ('USER_VIEW', 'View users'),
    ('USER_MANAGE', 'Create, update, activate/deactivate users'),
    ('USER_INVITE', 'Invite new users'),
    ('ROLE_MANAGE', 'Create, update and assign roles and permissions'),
    ('PRODUCT_VIEW', 'View products'),
    ('PRODUCT_CREATE', 'Create products'),
    ('PRODUCT_UPDATE', 'Update products'),
    ('PRODUCT_DELETE', 'Delete products'),
    ('REPORT_VIEW', 'View reports');

-- ADMIN gets every permission
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r CROSS JOIN permissions p WHERE r.name = 'ADMIN';

-- MANAGER: manage inventory/users, view reports, no role management
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r CROSS JOIN permissions p
WHERE r.name = 'MANAGER'
  AND p.code IN ('USER_VIEW', 'USER_INVITE', 'PRODUCT_VIEW', 'PRODUCT_CREATE', 'PRODUCT_UPDATE', 'REPORT_VIEW');

-- STAFF: read-only + product create/update for day-to-day work
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r CROSS JOIN permissions p
WHERE r.name = 'STAFF'
  AND p.code IN ('PRODUCT_VIEW', 'PRODUCT_UPDATE');
