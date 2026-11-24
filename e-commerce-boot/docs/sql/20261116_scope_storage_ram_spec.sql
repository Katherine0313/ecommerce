-- ============================================================
-- 规格库数据修正：存储容量 / 运行内存 不再对全部分类生效
-- ------------------------------------------------------------
-- 问题：这两条 product_spec_group 的 category_id 为 NULL（全局通用），
--       导致耳机、手表、相机等分类的商品编辑页也出现这两个规格。
-- 方案：改绑到 手机(1) / 电脑(2) / 平板(3)，并为新分类复制一份规格值。
-- 说明：product_spec_group 一条记录只能挂一个分类，所以要按分类各建一条。
-- 兼容 MySQL 5.7 / 8.0，可重复执行（重复执行不会产生重复数据）。
-- ============================================================

-- ---------- 执行前预览：确认这两条当前是全局 ----------
SELECT id, category_id, name, status
FROM product_spec_group
WHERE name IN ('存储容量', '运行内存')
ORDER BY name, category_id;

-- ---------- 1. 手机(1)：把原全局记录直接改绑 ----------
UPDATE product_spec_group
SET category_id = 1, update_time = NOW()
WHERE name = '存储容量' AND category_id IS NULL;

UPDATE product_spec_group
SET category_id = 1, update_time = NOW()
WHERE name = '运行内存' AND category_id IS NULL;

-- ---------- 2. 电脑(2) / 平板(3)：各建一条同名规格 ----------
INSERT INTO product_spec_group (category_id, name, sort, status, create_time, update_time)
SELECT c.cid, s.name, s.sort, 1, NOW(), NOW()
FROM (SELECT 2 AS cid UNION ALL SELECT 3) c
CROSS JOIN (SELECT '存储容量' AS name, 2 AS sort
      UNION ALL
      SELECT '运行内存', 3) s
WHERE NOT EXISTS (
    SELECT 1 FROM (
        SELECT category_id, name FROM product_spec_group
    ) g
    WHERE g.category_id = c.cid AND g.name = s.name
);

-- ---------- 3. 把手机那份的规格值复制给 电脑 / 平板 ----------
INSERT INTO product_spec_value (group_id, value, sort, create_time)
SELECT g.id, v.value, v.sort, NOW()
FROM product_spec_group g
JOIN product_spec_group src
  ON src.category_id = 1 AND src.name = g.name
JOIN product_spec_value v
  ON v.group_id = src.id
WHERE g.category_id IN (2, 3)
  AND g.name IN ('存储容量', '运行内存')
  AND NOT EXISTS (
      SELECT 1 FROM (
          SELECT group_id, value FROM product_spec_value
      ) x
      WHERE x.group_id = g.id AND x.value = v.value
  );

-- ---------- 4. 结果校验 ----------
-- 期望：存储容量 / 运行内存 只出现在 category_id = 1/2/3，且没有 NULL
SELECT id, category_id, name, status
FROM product_spec_group
WHERE name IN ('存储容量', '运行内存')
ORDER BY name, category_id;

-- 期望：三条记录的规格值数量一致（存储容量 5、运行内存 2）
SELECT g.category_id, g.name, COUNT(v.id) AS value_count
FROM product_spec_group g
LEFT JOIN product_spec_value v ON v.group_id = g.id
WHERE g.name IN ('存储容量', '运行内存')
GROUP BY g.category_id, g.name
ORDER BY g.name, g.category_id;

-- ============================================================
-- 附：数码配件(7) 下另外 4 条规格的归属建议（未执行，确认后再放开）
--   轴体      (红轴/青轴/茶轴)          → 电脑配件(702)
--   清晰度    (2K/4K)                   → 待定（若指显示器/摄像头，需对应分类）
--   版本      (标准版/水箱版)           → 待定（水箱版更像智能家居）
--   电池容量  (10000mAh/20000mAh)       → 充电设备(704)
-- 确认后按下面的形式改绑（需同时复制规格值，不执行则保持现状）：
-- UPDATE product_spec_group SET category_id = 702 WHERE name = '轴体'   AND category_id = 7;
-- UPDATE product_spec_group SET category_id = 704 WHERE name = '电池容量' AND category_id = 7;
-- ============================================================
