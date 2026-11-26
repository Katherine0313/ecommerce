-- ==================== product_sku.is_default 字段迁移 ====================
-- 用途：把"单规格商品默认 SKU"的标记职责从 sku_code 字符串（__DEFAULT__ 前缀）
--       迁移到独立字段 is_default，让 sku_code 回归"商家编码"单一语义。
-- 幂等：重复执行会因列已存在而报错，请先检查后再跑。

USE e_commerce_db;

-- 1. 新增字段
ALTER TABLE product_sku
  ADD COLUMN is_default TINYINT NOT NULL DEFAULT 0 COMMENT '1=单规格商品的默认SKU；0=普通SKU';

-- 2. 回填：sku_type=0（单规格）的商品，其启用且排序最靠前的那条 SKU 标记为默认
UPDATE product_sku s
JOIN (
  SELECT s2.product_id, MIN(s2.id) AS sku_id
  FROM product_sku s2
  JOIN products p2 ON p2.id = s2.product_id
  WHERE p2.sku_type = 0 AND s2.status = 1
  GROUP BY s2.product_id
) d ON d.sku_id = s.id
SET s.is_default = 1;

-- 3. 验证
SELECT CONCAT('is_default=1 的 SKU 数=', COUNT(*)) AS stat
FROM product_sku WHERE is_default = 1;

SELECT id, product_id, sku_code, is_default, status
FROM product_sku WHERE is_default = 1 LIMIT 10;

DESC product_sku;
