-- ==================== product_sku.sku_code 改为 NOT NULL ====================
-- 用途：sku_code 是"商家编码"，业务上必须存在（对接 ERP / WMS / 人工拣货）。
--       数据库层加 NOT NULL 约束兜底，配合代码层 resolveSkuCode 自动生成逻辑。
--
-- 兼容性：MySQL 5.7 / 8.0 通用（已避免窗口函数）。
-- 幂等：重复执行时 ALTER 会因已是 NOT NULL 而报错，请先确认。

USE e_commerce_db;

-- ===== 1. 回填空的 sku_code =====
-- 规则与代码 AdminProductServiceImpl.resolveSkuCode 保持一致：
--   有商品编码 → 「商品编码-规格值拼接」
--   无商品（孤儿 SKU）→ 「SKU{product_id}-{id}」

-- 1.1 正常 SKU
UPDATE product_sku s
JOIN products p ON p.id = s.product_id
LEFT JOIN (
  SELECT r.sku_id, GROUP_CONCAT(r.spec_value ORDER BY r.sort SEPARATOR '-') AS spec_suffix
  FROM product_sku_spec_rel r GROUP BY r.sku_id
) agg ON agg.sku_id = s.id
SET s.sku_code = CASE
  WHEN agg.spec_suffix IS NOT NULL AND agg.spec_suffix <> ''
       THEN CONCAT(COALESCE(NULLIF(p.product_code, ''), CONCAT('SKU', p.id)), '-', agg.spec_suffix)
  ELSE CONCAT(COALESCE(NULLIF(p.product_code, ''), CONCAT('SKU', p.id)), '-1')
END
WHERE s.sku_code IS NULL OR s.sku_code = '';

-- 1.2 孤儿 SKU（所属商品已删除）
UPDATE product_sku s
LEFT JOIN products p ON p.id = s.product_id
SET s.sku_code = CONCAT('SKU', s.product_id, '-', s.id)
WHERE p.id IS NULL AND (s.sku_code IS NULL OR s.sku_code = '');

SELECT CONCAT('回填后仍为空=', COUNT(*)) AS c
FROM product_sku WHERE sku_code IS NULL OR sku_code = '';

-- ===== 2. 加 NOT NULL 约束 =====
ALTER TABLE product_sku
  MODIFY COLUMN sku_code VARCHAR(64) NOT NULL COMMENT '商家编码';

-- ===== 3. 验证 =====
SELECT CONCAT('空 sku_code=', SUM(sku_code IS NULL OR sku_code = ''),
              '，最大长度=', MAX(CHAR_LENGTH(sku_code))) AS stat
FROM product_sku;

SELECT CONCAT('同商品内重复的 sku_code 组数=', COUNT(*)) AS dup_groups FROM (
  SELECT product_id, sku_code FROM product_sku
  GROUP BY product_id, sku_code HAVING COUNT(*) > 1
) x;

DESC product_sku;
