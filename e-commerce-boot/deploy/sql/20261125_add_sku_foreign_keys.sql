-- ==================== 商品域外键约束补齐 ====================
-- 背景：删除商品时只 DELETE FROM products，product_sku / product_sku_spec_rel
--       没有外键约束，导致残留孤儿 SKU（商品已删、SKU 还在）。
-- 修复：清理现存孤儿 + 补齐 ON DELETE CASCADE 外键，从数据库层根治。
--
-- 兼容性：MySQL 5.7 / 8.0 通用。
-- 注意：ALTER TABLE 会锁表，生产库建议在低峰期执行。

USE e_commerce_db;

-- ===== 1. 清理现存孤儿数据 =====
-- 1.1 孤儿 SKU 的规格关联
DELETE r FROM product_sku_spec_rel r
  JOIN product_sku s ON s.id = r.sku_id
  LEFT JOIN products p ON p.id = s.product_id
  WHERE p.id IS NULL;

-- 1.2 孤儿 SKU 本身
DELETE s FROM product_sku s
  LEFT JOIN products p ON p.id = s.product_id
  WHERE p.id IS NULL;

-- 1.3 指向不存在 SKU 的 rel
DELETE r FROM product_sku_spec_rel r
  LEFT JOIN product_sku s ON s.id = r.sku_id
  WHERE s.id IS NULL;

-- 1.4 指向不存在商品的 cart 行
DELETE c FROM cart c
  LEFT JOIN products p ON p.id = c.product_id
  WHERE p.id IS NULL;

SELECT '===== 清理后孤儿统计 =====' AS info;
SELECT 'product_sku 孤儿' AS tbl, COUNT(*) AS cnt
  FROM product_sku s LEFT JOIN products p ON p.id=s.product_id WHERE p.id IS NULL
UNION ALL
SELECT 'product_sku_spec_rel 无主', COUNT(*)
  FROM product_sku_spec_rel r LEFT JOIN product_sku s ON s.id=r.sku_id WHERE s.id IS NULL
UNION ALL
SELECT 'cart 孤儿', COUNT(*)
  FROM cart c LEFT JOIN products p ON p.id=c.product_id WHERE p.id IS NULL;

-- ===== 2. 补齐外键约束 =====
-- product_sku → products：删商品自动删 SKU
ALTER TABLE product_sku
  ADD CONSTRAINT fk_sku_product FOREIGN KEY (product_id) REFERENCES products(id) ON DELETE CASCADE;

-- product_sku_spec_rel → product_sku：删 SKU 自动删规格关联
ALTER TABLE product_sku_spec_rel
  ADD CONSTRAINT fk_rel_sku FOREIGN KEY (sku_id) REFERENCES product_sku(id) ON DELETE CASCADE;

-- ===== 3. 验证 =====
SELECT '===== 外键确认 =====' AS info;
SELECT rc.TABLE_NAME, rc.CONSTRAINT_NAME, rc.REFERENCED_TABLE_NAME, rc.DELETE_RULE
FROM INFORMATION_SCHEMA.REFERENTIAL_CONSTRAINTS rc
WHERE rc.CONSTRAINT_SCHEMA='e_commerce_db' AND rc.REFERENCED_TABLE_NAME IN ('products','product_sku')
ORDER BY rc.TABLE_NAME;

-- ===== 4. 说明 =====
-- order_items / after_sale_items / product_reviews 故意不加外键：
--   这些是历史快照/凭证，商品删除后必须保留，否则订单与售后记录会丢失。
