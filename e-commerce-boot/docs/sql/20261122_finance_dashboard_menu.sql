-- 财务看板菜单与角色授权：admin / saler / operator
-- 不使用固定菜单 ID，避免与目标库已有菜单冲突；按 perms 幂等执行。

SET NAMES utf8mb4;

INSERT INTO admin_menu (parent_id, menu_name, menu_type, path, component, perms, icon,
                        sort_order, visible, status, create_time, update_time)
SELECT 912, '财务看板', 'C', '/finance/dashboard', NULL, 'finance:dashboard:view', NULL,
       1, 1, 1, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM admin_menu WHERE perms = 'finance:dashboard:view');

UPDATE admin_menu
SET parent_id = 912, menu_name = '财务看板', menu_type = 'C', path = '/finance/dashboard',
    component = NULL, icon = NULL, sort_order = 1, visible = 1, status = 1, update_time = NOW()
WHERE perms = 'finance:dashboard:view';

-- 看板排在收款单、收款账户之前
UPDATE admin_menu SET sort_order = 2, update_time = NOW() WHERE id = 913;
UPDATE admin_menu SET sort_order = 3, update_time = NOW() WHERE id = 914;

-- 三个角色都需要父菜单；operator 不因此获得 913 / 914 及其按钮权限
INSERT IGNORE INTO admin_role_menu (role_id, menu_id)
SELECT r.id, 912
FROM admin_role r
WHERE r.role_key IN ('admin', 'saler', 'operator');

INSERT IGNORE INTO admin_role_menu (role_id, menu_id)
SELECT r.id, m.id
FROM admin_role r
JOIN admin_menu m ON m.perms = 'finance:dashboard:view'
WHERE r.role_key IN ('admin', 'saler', 'operator');

-- 执行后核对
SELECT id, parent_id, menu_name, menu_type, path, perms, sort_order, visible, status
FROM admin_menu
WHERE id IN (912, 913, 914) OR perms = 'finance:dashboard:view'
ORDER BY id;

SELECT r.role_key, m.id AS menu_id, m.menu_name, m.perms
FROM admin_role r
JOIN admin_role_menu rm ON rm.role_id = r.id
JOIN admin_menu m ON m.id = rm.menu_id
WHERE m.perms = 'finance:dashboard:view'
ORDER BY r.role_key;
