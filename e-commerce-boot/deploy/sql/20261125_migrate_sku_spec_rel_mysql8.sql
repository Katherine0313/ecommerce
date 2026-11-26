-- ==================== SKU-规格多对多迁移（MySQL 8.0 版）====================
-- 适用：本地开发库（MySQL 8.0.46）。生产库是 MySQL 5.7，请用 _archive 下的 Python 版。
--
-- 关键点：
--   1. JSON path 中中文 key 必须写成 $."key"，否则报 Invalid JSON path expression
--   2. MySQL 8.0 默认 sql_mode=only_full_group_by，GROUP BY 必须列出全部非聚合列
--   3. JSON_TABLE 输出列默认 utf8mb4_0900_ai_ci，与字典表 utf8mb4_general_ci 冲突，需显式指定
--
-- 幂等：可重复执行（TRUNCATE 后重建 rel；末尾的 ALTER 段需先确认列是否存在）

USE e_commerce_db;

-- ===== 1. 建中间表 =====
CREATE TABLE IF NOT EXISTS product_sku_spec_rel (
  sku_id          BIGINT       NOT NULL COMMENT 'product_sku.id',
  spec_group_id   BIGINT       NOT NULL COMMENT 'product_spec_group.id',
  spec_value_id   BIGINT                DEFAULT NULL COMMENT 'product_spec_value.id',
  spec_value      VARCHAR(64)  NOT NULL COMMENT '冗余规格值字符串，如 曜石黑 / 256G',
  sort            INT          NOT NULL DEFAULT 0 COMMENT '展示顺序（同 sku 内按 group.sort）',
  create_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (sku_id, spec_group_id),
  KEY idx_rel_value (spec_value_id),
  KEY idx_rel_group (spec_group_id),
  KEY idx_rel_sku_value (sku_id, spec_value_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='SKU 与规格值多对多关联';

-- ===== 2. 把每个 SKU 的 spec_json 展开成 (sku_id, 规格名, 规格值) 临时表 =====
DROP TEMPORARY TABLE IF EXISTS tmp_sku_spec;
CREATE TEMPORARY TABLE tmp_sku_spec (
  sku_id     BIGINT NOT NULL,
  gname      VARCHAR(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL,
  gval       VARCHAR(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL,
  ctime      DATETIME NOT NULL
) ENGINE=InnoDB;

INSERT INTO tmp_sku_spec (sku_id, gname, gval, ctime)
SELECT s.id,
       k.k,
       JSON_UNQUOTE(JSON_EXTRACT(s.spec_json, CONCAT('$.\"', k.k, '\"'))),
       s.create_time
FROM product_sku s
JOIN JSON_TABLE(JSON_KEYS(s.spec_json), '$[*]'
                COLUMNS(k VARCHAR(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci PATH '$')) k ON TRUE
WHERE s.spec_json IS NOT NULL AND s.spec_json <> '{}' AND s.spec_key <> '__DEFAULT__';

-- ===== 3. 规格名 → group_id（同名启用组取 MIN(id)，与生产保持一致）=====
DROP TEMPORARY TABLE IF EXISTS tmp_gmap;
CREATE TEMPORARY TABLE tmp_gmap (
  name  VARCHAR(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL,
  gid   BIGINT NOT NULL,
  gsort INT NOT NULL DEFAULT 0,
  PRIMARY KEY (name)
) ENGINE=InnoDB;

INSERT INTO tmp_gmap (name, gid, gsort)
SELECT g.name, MIN(g.id), MIN(g.sort)
FROM product_spec_group g
WHERE g.status = 1
GROUP BY g.name;

-- ===== 4. 自动补建缺失的 spec_value =====
INSERT INTO product_spec_value (group_id, value, sort, create_time)
SELECT gm.gid, t.gval, 999, NOW()
FROM tmp_sku_spec t
JOIN tmp_gmap gm ON gm.name = t.gname
LEFT JOIN product_spec_value sv ON sv.group_id = gm.gid AND sv.value = t.gval
WHERE sv.id IS NULL
GROUP BY gm.gid, t.gval;

-- ===== 5. 规格值 → value_id =====
DROP TEMPORARY TABLE IF EXISTS tmp_vmap;
CREATE TEMPORARY TABLE tmp_vmap (
  gid BIGINT NOT NULL,
  val VARCHAR(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL,
  vid BIGINT NOT NULL,
  PRIMARY KEY (gid, val)
) ENGINE=InnoDB;

INSERT INTO tmp_vmap (gid, val, vid)
SELECT sv.group_id, sv.value, MIN(sv.id)
FROM product_spec_value sv
JOIN tmp_gmap gm ON gm.gid = sv.group_id
GROUP BY sv.group_id, sv.value;

-- ===== 6. 写入中间表 =====
TRUNCATE TABLE product_sku_spec_rel;

INSERT INTO product_sku_spec_rel (sku_id, spec_group_id, spec_value_id, spec_value, sort, create_time)
SELECT t.sku_id,
       gm.gid,
       vm.vid,
       t.gval,
       gm.gsort,
       t.ctime
FROM tmp_sku_spec t
JOIN tmp_gmap gm ON gm.name = t.gname
LEFT JOIN tmp_vmap vm ON vm.gid = gm.gid AND vm.val = t.gval
ON DUPLICATE KEY UPDATE spec_value_id = VALUES(spec_value_id), spec_value = VALUES(spec_value);

SELECT CONCAT('rel 总行数=', COUNT(*),
              '，覆盖 SKU=', COUNT(DISTINCT sku_id),
              '，总 SKU=', (SELECT COUNT(*) FROM product_sku),
              '，spec_value_id 缺失=', SUM(spec_value_id IS NULL)) AS backfill_result
FROM product_sku_spec_rel;

-- ===== 7. 下线冗余字段（执行前请确认代码已不再读写 spec_json / spec_key）=====
-- ALTER TABLE product_sku DROP INDEX uk_sku_spec;
-- ALTER TABLE product_sku DROP COLUMN spec_json, DROP COLUMN spec_key;
