-- ============================================================================
-- coupon 及其关联表数据导出（本地 e_commerce_db → 其他测试库）
-- 导出时间：2026-11-22
-- 数据范围：
--   coupon                 6 条（9002 ~ 9007）
--   coupon_scope           2 条
--   coupon_user            1 条
--   coupon_seckill_order   2 条
--   coupon_seckill_refund  0 条
-- 特性：可重复执行（先按 id 删除再插入），事务包裹
-- 说明：只导数据，不含建表语句；目标库必须已存在这 5 张表
-- ============================================================================

-- ----------------------------------------
-- 执行前必查（在【目标库】上确认）
-- ----------------------------------------
-- 1) 5 张表必须已存在（本脚本不建表）：
--    SHOW TABLES LIKE 'coupon%';
--
-- 2) coupon_scope 指向的业务对象要在目标库存在，否则券的适用范围会指向空数据：
--    product id=136、category id=1（本地对应：雷达晶萃 R30939103 / 手机）
--    SELECT id,name FROM categories WHERE id=1;
--    SELECT id,name FROM products   WHERE id=136;
--    ★ 若目标库 ID 不同，请把下面 coupon_scope 的 136 / 1 换成目标库实际 ID
--
-- 3) coupon_user.user_id=14、coupon_seckill_order.user_id=14/21 在目标库不一定存在。
--    这两张表对 user 表没有外键，导入不会失败；但若要在目标库查看这两个用户的
--    「我的券 / 我的秒杀订单」，需要先导入对应用户数据。
--
-- 4) 状态提醒：
--    - 9003 活动 2026-11-22 20:23:59 结束，导入后基本抢不了
--    - 9006 剩余 99（version=1）、9007 剩余 99（version=1）
--    - 要干净压测建议用 9005（剩余 100、version=0、活动到 2026-11-23 20:52）
--    - 如需重置：UPDATE coupon SET remain_quantity=100, version=0 WHERE id=9005;
--
-- 5) coupon 是自增表，显式插入 9002~9007 后目标库自增值会抬到 9008 以上，属正常
-- ----------------------------------------

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;
START TRANSACTION;

-- 1) 清理同 ID 旧数据
--    coupon_scope / coupon_user 对 coupon 是 ON DELETE CASCADE，删除 coupon 时会一并清掉；
--    这里仍显式先删，保证可重复执行且删除顺序可控。
DELETE FROM coupon_seckill_refund WHERE seckill_order_id IN (1, 2);
DELETE FROM coupon_seckill_order  WHERE id IN (1, 2);
DELETE FROM coupon_user           WHERE id IN (6);
DELETE FROM coupon_scope          WHERE coupon_id IN (9002, 9003, 9004, 9005, 9006, 9007);
DELETE FROM coupon                WHERE id IN (9002, 9003, 9004, 9005, 9006, 9007);

-- 2) 券模板
INSERT INTO `coupon` (`id`, `name`, `type`, `issue_type`, `seckill_price`, `seckill_begin_time`, `seckill_end_time`, `total_quantity`, `remain_quantity`, `face_value`, `discount_rate`, `max_discount`, `threshold_amount`, `scope_type`, `target_group`, `min_member_level`, `version`, `per_user_limit`, `receive_start`, `receive_end`, `valid_type`, `valid_start`, `valid_end`, `valid_days`, `manual_status`, `is_deleted`, `description`, `create_time`, `update_time`) VALUES
(9002,'国家补贴10%','DISCOUNT','RECEIVE',NULL,NULL,NULL,100,100,NULL,0.90,200.00,0.00,'PRODUCT','ALL',0,0,1,'2026-11-01 00:00:00','2026-12-31 23:59:59','FIXED','2026-11-01 00:00:00','2026-12-31 23:59:59',NULL,0,0,'结算页选择该券立减10%','2026-11-07 21:35:15',NULL),
(9003,'100元代金券','FIXED','SECKILL',80.00,'2026-11-21 20:23:43','2026-11-22 20:23:59',100,100,100.00,NULL,NULL,0.00,'CATEGORY','ALL',0,0,1,'2026-11-21 20:23:43','2026-11-22 20:23:59','FIXED','2026-11-21 20:25:06','2026-11-30 20:24:17',NULL,0,0,'不可与其他券叠加使用','2026-11-21 20:24:44',NULL),
(9004,'50元代金券','FIXED','SECKILL',30.00,'2026-11-21 20:34:03','2026-11-23 20:34:08',100,100,50.00,NULL,NULL,0.00,'ALL','ALL',0,0,1,'2026-11-21 20:34:03','2026-11-23 20:34:08','FIXED','2026-11-21 20:34:21','2026-11-30 20:34:26',NULL,0,0,'请在有效期使用，过期失效。','2026-11-21 20:34:55',NULL),
(9005,'200元代金券','FIXED','SECKILL',150.00,'2026-11-21 20:51:59','2026-11-23 20:52:03',100,100,200.00,NULL,NULL,0.00,'ALL','ALL',0,0,1,'2026-11-21 20:51:59','2026-11-23 20:52:03','FIXED','2026-11-21 20:51:34','2026-11-30 20:52:13',NULL,0,0,'请在有效时间内使用','2026-11-21 20:52:39',NULL),
(9006,'500元代金券','FIXED','SECKILL',400.00,'2026-11-21 20:55:33','2026-11-24 20:55:37',100,99,500.00,NULL,NULL,0.00,'ALL','ALL',0,1,1,'2026-11-21 20:55:33','2026-11-24 20:55:37','FIXED','2026-11-21 20:55:14','2026-11-30 20:55:45',NULL,0,0,'请在有效期内使用','2026-11-21 20:55:58',NULL),
(9007,'1000元代金券','FIXED','SECKILL',800.00,'2026-11-21 20:56:25','2026-11-25 20:56:27',100,99,1000.00,NULL,NULL,0.00,'ALL','ALL',0,1,1,'2026-11-21 20:56:25','2026-11-25 20:56:27','FIXED','2026-11-21 20:56:08','2026-11-30 20:56:38',NULL,0,0,'请在有效期内使用','2026-11-21 20:56:50',NULL);

-- 3) 券适用范围
INSERT INTO `coupon_scope` (`id`, `coupon_id`, `target_type`, `target_id`) VALUES
(3,9002,'PRODUCT',136),
(4,9003,'CATEGORY',1);

-- 4) 用户持有券
INSERT INTO `coupon_user` (`id`, `code`, `coupon_id`, `user_id`, `status`, `receive_time`, `start_time`, `end_time`, `order_id`, `lock_time`, `used_time`, `source`) VALUES
(6,'CP202611212116183123493',9007,14,'UNUSED','2026-11-21 21:16:18','2026-11-21 20:56:08','2026-11-30 20:56:38',NULL,NULL,NULL,'SECKILL');

-- 5) 秒杀订单
INSERT INTO `coupon_seckill_order` (`id`, `order_no`, `coupon_id`, `user_id`, `coupon_user_id`, `coupon_name`, `pay_amount`, `pay_type`, `status`, `create_time`, `expire_time`, `pay_time`, `cancel_time`, `refund_time`, `update_time`) VALUES
(1,'CS202611212116168063',9007,14,6,'1000元代金券',800.00,'MOCK',2,'2026-11-21 21:16:16','2026-11-21 21:31:16','2026-11-21 21:16:18',NULL,NULL,'2026-11-21 21:16:18'),
(2,'CS202611221811462672',9006,21,NULL,'500元代金券',400.00,NULL,1,'2026-11-22 18:11:47','2026-11-22 18:26:47',NULL,NULL,NULL,'2026-11-22 18:11:47');

COMMIT;
SET FOREIGN_KEY_CHECKS = 1;

-- ----------------------------------------
-- 导入后核对
-- ----------------------------------------
SELECT 'coupon' AS tbl, COUNT(*) AS cnt FROM coupon WHERE id IN (9002,9003,9004,9005,9006,9007)
UNION ALL SELECT 'coupon_scope', COUNT(*) FROM coupon_scope WHERE coupon_id IN (9002,9003,9004,9005,9006,9007)
UNION ALL SELECT 'coupon_user', COUNT(*) FROM coupon_user WHERE coupon_id IN (9002,9003,9004,9005,9006,9007)
UNION ALL SELECT 'coupon_seckill_order', COUNT(*) FROM coupon_seckill_order WHERE coupon_id IN (9002,9003,9004,9005,9006,9007);
