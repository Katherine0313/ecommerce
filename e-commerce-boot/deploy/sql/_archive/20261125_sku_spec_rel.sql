-- ==================== SKU-规格多对多迁移 ====================
-- MySQL 8.0.39，避免在存储过程里用 JSON_TABLE（不可靠）

-- DDL: 中间表
CREATE TABLE IF NOT EXISTS product_sku_spec_rel (
  sku_id          BIGINT       NOT NULL COMMENT 'product_sku.id',
  spec_group_id   BIGINT       NOT NULL COMMENT 'product_spec_group.id',
  spec_value_id   BIGINT                DEFAULT NULL COMMENT 'product_spec_value.id；字典被删时可为 NULL',
  spec_value      VARCHAR(64)  NOT NULL COMMENT '冗余规格值字符串，如 曜石黑 / 256G',
  sort            INT          NOT NULL DEFAULT 0 COMMENT '展示顺序（同 sku 内按 group.sort）',
  create_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (sku_id, spec_group_id),
  KEY idx_rel_value (spec_value_id),
  KEY idx_rel_group (spec_group_id),
  KEY idx_rel_sku_value (sku_id, spec_value_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='SKU 与规格值多对多关联';

-- 步骤 1：清空旧数据（幂等重跑）
TRUNCATE TABLE product_sku_spec_rel;

-- 步骤 2：把每条多规格 SKU 的 spec_json 展开成 key-value 行
-- 思路：先用 JSON_KEYS 拿 key 数组长度，然后用动态 SQL 循环 i=0..n-1
--       对每个 key：用 JSON_UNQUOTE(JSON_EXTRACT(spec_json, CONCAT('$.', key))) 拿 value
-- 这里用一种更可维护的方式：Python/外部脚本处理。
-- 先写出所有需要回填的 SKU 的 id 和 spec_json，脚本处理后写回。

-- 输出清单（外部脚本会读这个）
SELECT id, spec_json, COALESCE(create_time, NOW()) AS create_time
FROM product_sku
WHERE spec_json IS NOT NULL
  AND spec_json <> ''
  AND spec_json <> '{}'
  AND spec_key <> '__DEFAULT__'
ORDER BY id;
