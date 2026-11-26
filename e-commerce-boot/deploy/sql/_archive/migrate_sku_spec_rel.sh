#!/usr/bin/env bash
# 回填 product_sku_spec_rel 历史数据。
# 用 MySQL 8.0 的 JSON 函数展开 spec_json 的 key-value。

set -euo pipefail
MYSQL="mysql -h 127.0.0.1 -P 3306 -u e_commerce_db -p123456 -D e_commerce_db"

echo "===== 1. 建表 ====="
$MYSQL <<'SQL'
CREATE TABLE IF NOT EXISTS product_sku_spec_rel (
  sku_id          BIGINT       NOT NULL,
  spec_group_id   BIGINT       NOT NULL,
  spec_value_id   BIGINT                DEFAULT NULL,
  spec_value      VARCHAR(64)  NOT NULL,
  sort            INT          NOT NULL DEFAULT 0,
  create_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (sku_id, spec_group_id),
  KEY idx_rel_value (spec_value_id),
  KEY idx_rel_group (spec_group_id),
  KEY idx_rel_sku_value (sku_id, spec_value_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='SKU 与规格值多对多关联';
SQL

$MYSQL -e "TRUNCATE TABLE product_sku_spec_rel"

echo "===== 2. 临时表：展开每条 SKU 的 spec_json ====="
# MySQL 8.0.39 支持 JSON_TABLE
$MYSQL <<'SQL'
DROP TEMPORARY TABLE IF EXISTS tmp_sku_spec_pair;

CREATE TEMPORARY TABLE tmp_sku_spec_pair (
  sku_id          BIGINT       NOT NULL,
  gname           VARCHAR(64)  NOT NULL,
  gvalue          VARCHAR(64)  NOT NULL,
  create_time     DATETIME     NOT NULL,
  INDEX (sku_id)
) ENGINE=InnoDB;

INSERT INTO tmp_sku_spec_pair (sku_id, gname, gvalue, create_time)
SELECT s.id, jt.k, jt.v, COALESCE(s.create_time, NOW())
FROM product_sku s
JOIN JSON_TABLE(
  JSON_KEYS(s.spec_json),
  '$[*]' COLUMNS (k VARCHAR(64) PATH '$')
) jk ON TRUE
JOIN JSON_TABLE(
  s.spec_json, CONCAT('$.', jk.k) COLUMNS (v VARCHAR(64) PATH '$')
) jt ON TRUE
WHERE s.spec_json IS NOT NULL
  AND s.spec_json <> ''
  AND s.spec_json <> '{}'
  AND s.spec_key <> '__DEFAULT__';

SELECT CONCAT('临时表行数: ', COUNT(*)) FROM tmp_sku_spec_pair;
SQL

echo "===== 3. 匹配 group / value ====="
$MYSQL <<'SQL'
INSERT INTO product_sku_spec_rel (sku_id, spec_group_id, spec_value_id, spec_value, sort, create_time)
SELECT p.sku_id,
       g.id,
       v.id,
       p.gvalue,
       COALESCE(g.sort, 999),
       p.create_time
FROM tmp_sku_spec_pair p
JOIN product_spec_group g
  ON g.name = p.gname AND g.status = 1
LEFT JOIN product_spec_value v
  ON v.group_id = g.id AND v.value = p.gvalue
ON DUPLICATE KEY UPDATE
  spec_value_id = VALUES(spec_value_id),
  spec_value    = VALUES(spec_value);

SELECT CONCAT('rel 已写入: ', COUNT(*)) FROM product_sku_spec_rel;
SQL

echo "===== 4. 补建 spec_value 字典（rel 里 spec_value_id 缺失但 value 有值） ====="
$MYSQL <<'SQL'
INSERT INTO product_spec_value (group_id, value, sort, create_time)
SELECT r.spec_group_id, r.spec_value, 999, r.create_time
FROM product_sku_spec_rel r
WHERE r.spec_value_id IS NULL
  AND NOT EXISTS (
    SELECT 1 FROM product_spec_value v
    WHERE v.group_id = r.spec_group_id AND v.value = r.spec_value
  );

UPDATE product_sku_spec_rel r
JOIN product_spec_value v
  ON v.group_id = r.spec_group_id AND v.value = r.spec_value
SET r.spec_value_id = v.id
WHERE r.spec_value_id IS NULL;

SELECT CONCAT('最终 rel 行数: ', COUNT(*),
              ', 覆盖 SKU: ', COUNT(DISTINCT sku_id),
              ', spec_value_id 仍缺失: ', SUM(spec_value_id IS NULL))
FROM product_sku_spec_rel;
SQL

echo "===== 完成 ====="
