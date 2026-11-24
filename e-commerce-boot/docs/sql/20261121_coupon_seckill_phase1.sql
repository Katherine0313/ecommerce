-- =============================================================================
-- 优惠券秒杀 第一期：2 张新表 + coupon 加 5 列 + 退款权限 + 超时关单任务
-- 2026-11-21
--
-- 前置：本地库与生产库已完成 coupon_template -> coupon、user_coupon -> coupon_user
--       重命名，列已统一为 coupon_id；本脚本不再包含 RENAME。
-- 幂等：建表用 IF NOT EXISTS；加列/权限/任务均先判断或 ON DUPLICATE KEY UPDATE。
-- 兼容：生产 MySQL 5.7，不使用 8.0 语法。
--
-- 执行：
--   本地：mysql -D e_commerce_db < docs/sql/20261121_coupon_seckill_phase1.sql
--   生产：先备份，再执行本脚本
-- =============================================================================

SELECT IF(DATABASE() IS NULL,
       '错误：必须带库名执行，例如 mysql -D e_commerce_db < 本脚本.sql',
       CONCAT('当前库：', DATABASE(), '，开始执行')) AS _guard;

-- 1) 秒杀券购买订单
CREATE TABLE IF NOT EXISTS `coupon_seckill_order` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键，内部使用',
  `order_no` varchar(64) NOT NULL COMMENT '业务订单号，对外使用',
  `coupon_id` bigint NOT NULL COMMENT '秒杀券 ID，关联 coupon.id',
  `user_id` bigint NOT NULL COMMENT '购买用户 ID',
  `coupon_user_id` bigint DEFAULT NULL COMMENT '支付后发放的用户券 ID',
  `coupon_name` varchar(128) NOT NULL COMMENT '券名称快照',
  `pay_amount` decimal(10,2) NOT NULL COMMENT '成交金额快照，元',
  `pay_type` varchar(32) DEFAULT NULL COMMENT '支付方式',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '1 待支付 2 已支付 3 已取消 4 已退款',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `expire_time` datetime NOT NULL COMMENT '支付截止时间',
  `pay_time` datetime DEFAULT NULL,
  `cancel_time` datetime DEFAULT NULL,
  `refund_time` datetime DEFAULT NULL,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_coupon_seckill_order_no` (`order_no`),
  UNIQUE KEY `uk_coupon_seckill_order_user` (`coupon_user_id`),
  UNIQUE KEY `uk_coupon_seckill_order_coupon_user` (`coupon_id`, `user_id`),
  KEY `idx_coupon_seckill_order_user` (`user_id`, `create_time`),
  KEY `idx_coupon_seckill_order_timeout` (`status`, `expire_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='秒杀优惠券购买订单';

-- 2) 秒杀券退款单
CREATE TABLE IF NOT EXISTS `coupon_seckill_refund` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `refund_no` varchar(64) NOT NULL COMMENT '退款单号',
  `seckill_order_id` bigint NOT NULL COMMENT '关联 coupon_seckill_order.id',
  `user_id` bigint NOT NULL,
  `coupon_user_id` bigint DEFAULT NULL COMMENT '退款的用户券',
  `refund_amount` decimal(10,2) NOT NULL COMMENT '退款金额，取 pay_amount 快照',
  `status` varchar(16) NOT NULL DEFAULT 'REVIEWING' COMMENT 'REVIEWING / APPROVING / REFUNDED / REJECTED',
  `reason` varchar(255) DEFAULT NULL,
  `images` varchar(1024) DEFAULT NULL,
  `merchant_remark` varchar(255) DEFAULT NULL,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `refund_time` datetime DEFAULT NULL,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_seckill_refund_no` (`refund_no`),
  UNIQUE KEY `uk_seckill_refund_order` (`seckill_order_id`),
  KEY `idx_seckill_refund_status` (`status`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='秒杀券退款单';

-- 3) coupon 增加秒杀字段（逐列判断，可重复执行）
SET @exists := (SELECT COUNT(*) FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'coupon' AND COLUMN_NAME = 'issue_type');
SET @sql := IF(@exists = 0,
  'ALTER TABLE `coupon` ADD COLUMN `issue_type` varchar(16) NOT NULL DEFAULT ''RECEIVE'' COMMENT ''RECEIVE 普通券 / SECKILL 秒杀券'' AFTER `type`',
  'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @exists := (SELECT COUNT(*) FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'coupon' AND COLUMN_NAME = 'seckill_price');
SET @sql := IF(@exists = 0,
  'ALTER TABLE `coupon` ADD COLUMN `seckill_price` decimal(10,2) DEFAULT NULL COMMENT ''秒杀购买价，元'' AFTER `issue_type`',
  'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @exists := (SELECT COUNT(*) FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'coupon' AND COLUMN_NAME = 'seckill_begin_time');
SET @sql := IF(@exists = 0,
  'ALTER TABLE `coupon` ADD COLUMN `seckill_begin_time` datetime DEFAULT NULL COMMENT ''秒杀开始时间'' AFTER `seckill_price`',
  'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @exists := (SELECT COUNT(*) FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'coupon' AND COLUMN_NAME = 'seckill_end_time');
SET @sql := IF(@exists = 0,
  'ALTER TABLE `coupon` ADD COLUMN `seckill_end_time` datetime DEFAULT NULL COMMENT ''秒杀结束时间'' AFTER `seckill_begin_time`',
  'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @exists := (SELECT COUNT(*) FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'coupon' AND COLUMN_NAME = 'version');
SET @sql := IF(@exists = 0,
  'ALTER TABLE `coupon` ADD COLUMN `version` int NOT NULL DEFAULT 0 COMMENT ''库存乐观锁版本号'' AFTER `remain_quantity`',
  'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 4) 退款审核按钮权限
INSERT INTO `admin_menu`
  (`id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `perms`, `icon`, `sort_order`, `visible`, `status`, `create_time`, `update_time`)
VALUES
  (524, 501, '优惠券退款审核', 'F', NULL, NULL, 'business:coupon:refund', NULL, 5, 1, 1, NOW(), NOW())
ON DUPLICATE KEY UPDATE
  `parent_id` = VALUES(`parent_id`), `menu_name` = VALUES(`menu_name`), `menu_type` = VALUES(`menu_type`),
  `path` = VALUES(`path`), `component` = VALUES(`component`), `perms` = VALUES(`perms`), `icon` = VALUES(`icon`),
  `sort_order` = VALUES(`sort_order`), `visible` = VALUES(`visible`), `status` = VALUES(`status`),
  `update_time` = NOW();

-- 已拥有「优惠券」菜单的角色自动获得退款审核权限
INSERT IGNORE INTO `admin_role_menu` (`role_id`, `menu_id`)
SELECT `role_id`, 524 FROM `admin_role_menu` WHERE `menu_id` = 501;

-- 5) 注册超时关单动态任务（每分钟扫描一次）
INSERT INTO `admin_monitor_task`
  (`task_name`, `task_group`, `bean_name`, `method_name`, `task_type`, `schedule_value`, `status`, `concurrent`, `remark`)
VALUES
  ('closeExpiredCouponSeckillOrders', 'order-service', 'closeExpiredCouponSeckillOrdersTaskRunner',
   'closeExpired', 2, '60000', 1, 1, '关闭超时未支付的秒杀券订单并回补库存')
ON DUPLICATE KEY UPDATE
  `task_group` = VALUES(`task_group`), `bean_name` = VALUES(`bean_name`), `method_name` = VALUES(`method_name`),
  `task_type` = VALUES(`task_type`), `schedule_value` = VALUES(`schedule_value`), `status` = VALUES(`status`),
  `concurrent` = VALUES(`concurrent`), `remark` = VALUES(`remark`), `update_time` = NOW();

-- 6) 结果自检
SELECT 'coupon_seckill_order' AS table_name, COUNT(*) AS exists_flag
FROM information_schema.TABLES
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'coupon_seckill_order'
UNION ALL
SELECT 'coupon_seckill_refund', COUNT(*)
FROM information_schema.TABLES
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'coupon_seckill_refund';

SELECT COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE, COLUMN_DEFAULT
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'coupon'
  AND COLUMN_NAME IN ('issue_type', 'seckill_price', 'seckill_begin_time', 'seckill_end_time', 'version')
ORDER BY ORDINAL_POSITION;
