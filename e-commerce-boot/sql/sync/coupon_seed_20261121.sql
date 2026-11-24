-- ============================================================================
-- coupon 券模板同步脚本（本地 → 生产）
-- 生成时间：2026-11-21
-- 来源：本地库 e_commerce_db 的 coupon(6 条) + coupon_scope(2 条)
-- 范围：仅【券模板】数据，不含任何用户券(coupon_user)、秒杀订单、退款单
-- 特性：可重复执行（先按 id 删除再插入），支持事务回滚
-- ============================================================================

-- ----------------------------------------
-- 执行前必查（在【生产库】上跑，确认后再执行本脚本）
-- ----------------------------------------
-- 1) 生产 coupon 表必须已有秒杀相关列，否则 INSERT 会报 Unknown column
--    SHOW COLUMNS FROM coupon;
--    必须包含：issue_type / seckill_price / seckill_begin_time / seckill_end_time / version
--
-- 2) coupon_scope 引用的目标必须在生产存在，且 ID 对应同一条数据，
--    否则券会"适用范围指向不存在/错误的分类或商品"。
--    本脚本来自本地：category id=1 → 手机；product id=136 → 雷达晶萃 R30939103 自动机械男表
--    SELECT id,name FROM categories WHERE id=1;
--    SELECT id,name FROM products   WHERE id=136;
--    ★ 若生产 ID 不同，请把下面 coupon_scope 的 INSERT 里 1 / 136 换成生产实际 ID
--
-- 3) 若生产已给用户发过这些 id 的券，coupon_user 外键(RESTRICT)会挡住 DELETE，
--    整个事务回滚。这是保护机制，不是脚本错误；此时应改为逐条 UPDATE 而不是删除重插。
--
-- 4) 秒杀活动时间按本地原样带过去，如生产要重新开活动，请先改
--    seckill_begin_time / seckill_end_time / receive_start / receive_end / valid_start / valid_end
-- ----------------------------------------

SET NAMES utf8mb4;
START TRANSACTION;

-- 1) 清理同 ID 旧模板（coupon_scope 通过 ON DELETE CASCADE 一并清除）
DELETE FROM coupon WHERE id IN (9002, 9003, 9004, 9005, 9006, 9007);

-- 2) 插入券模板
INSERT INTO `coupon` (`id`, `name`, `type`, `issue_type`, `seckill_price`, `seckill_begin_time`, `seckill_end_time`, `face_value`, `discount_rate`, `max_discount`, `threshold_amount`, `scope_type`, `target_group`, `min_member_level`, `total_quantity`, `remain_quantity`, `version`, `per_user_limit`, `receive_start`, `receive_end`, `valid_type`, `valid_start`, `valid_end`, `valid_days`, `manual_status`, `is_deleted`, `description`, `create_time`, `update_time`) VALUES
(9002,'国家补贴10%','DISCOUNT','RECEIVE',NULL,NULL,NULL,NULL,0.90,200.00,0.00,'PRODUCT','ALL',0,100,100,0,1,'2026-11-01 00:00:00','2026-12-31 23:59:59','FIXED','2026-11-01 00:00:00','2026-12-31 23:59:59',NULL,0,0,'结算页选择该券立减10%','2026-11-07 21:35:15',NULL),
(9003,'100元代金券','FIXED','SECKILL',80.00,'2026-11-21 20:23:43','2026-11-22 20:23:59',100.00,NULL,NULL,0.00,'CATEGORY','ALL',0,100,100,0,1,'2026-11-21 20:23:43','2026-11-22 20:23:59','FIXED','2026-11-21 20:25:06','2026-11-30 20:24:17',NULL,0,0,'不可与其他券叠加使用','2026-11-21 20:24:44',NULL),
(9004,'50元代金券','FIXED','SECKILL',30.00,'2026-11-21 20:34:03','2026-11-23 20:34:08',50.00,NULL,NULL,0.00,'ALL','ALL',0,100,100,0,1,'2026-11-21 20:34:03','2026-11-23 20:34:08','FIXED','2026-11-21 20:34:21','2026-11-30 20:34:26',NULL,0,0,'请在有效期使用，过期失效。','2026-11-21 20:34:55',NULL),
(9005,'200元代金券','FIXED','SECKILL',150.00,'2026-11-21 20:51:59','2026-11-23 20:52:03',200.00,NULL,NULL,0.00,'ALL','ALL',0,100,100,0,1,'2026-11-21 20:51:59','2026-11-23 20:52:03','FIXED','2026-11-21 20:51:34','2026-11-30 20:52:13',NULL,0,0,'请在有效时间内使用','2026-11-21 20:52:39',NULL),
(9006,'500元代金券','FIXED','SECKILL',400.00,'2026-11-21 20:55:33','2026-11-24 20:55:37',500.00,NULL,NULL,0.00,'ALL','ALL',0,100,100,0,1,'2026-11-21 20:55:33','2026-11-24 20:55:37','FIXED','2026-11-21 20:55:14','2026-11-30 20:55:45',NULL,0,0,'请在有效期内使用','2026-11-21 20:55:58',NULL),
(9007,'1000元代金券','FIXED','SECKILL',800.00,'2026-11-21 20:56:25','2026-11-25 20:56:27',1000.00,NULL,NULL,0.00,'ALL','ALL',0,100,100,0,1,'2026-11-21 20:56:25','2026-11-25 20:56:27','FIXED','2026-11-21 20:56:08','2026-11-30 20:56:38',NULL,0,0,'请在有效期内使用','2026-11-21 20:56:50',NULL);

-- 3) 插入适用范围（不指定 id，交生产自增，避免与生产已有 scope id 冲突）
INSERT INTO `coupon_scope` (`coupon_id`, `target_type`, `target_id`) VALUES
(9002,'PRODUCT',136),
(9003,'CATEGORY',1);

COMMIT;

-- 4) 校正自增：避免后续新建券再落到 9xxx 区间。
--    若生产当前自增值已更大，MySQL 会忽略本语句、不会回退。
ALTER TABLE coupon AUTO_INCREMENT = 9008;

-- 5) 执行后校验
SELECT id, name, type, issue_type, seckill_price, face_value, threshold_amount,
       total_quantity, remain_quantity, receive_start, receive_end, valid_start, valid_end
FROM coupon WHERE id IN (9002, 9003, 9004, 9005, 9006, 9007) ORDER BY id;

SELECT * FROM coupon_scope WHERE coupon_id IN (9002, 9003, 9004, 9005, 9006, 9007);
