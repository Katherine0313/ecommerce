-- 出库单幂等：同一订单只允许存在一张 SHIPMENT 单据
--
-- 背景：发货重试 / 并发调用 recordShipment 时「先查再插」存在竞态，会生成重复出库单。
-- 做法：只对 SHIPMENT 类型加唯一约束。入库(RECEIPT)、调整(ADJUST) 的 related_no 允许复用，
--       所以用生成列把非 SHIPMENT 归为 NULL（NULL 不参与唯一约束），而不是直接给 related_no 加索引。
--
-- 执行方式：本地执行完必须在生产执行同一份。

-- 1) 先检查是否已有重复。有结果时需先人工合并/清理，否则第 2 步的 ALTER 会失败。
-- SELECT type, related_no, COUNT(*) AS cnt
--   FROM stock_order
--  WHERE type = 'SHIPMENT' AND related_no IS NOT NULL AND related_no <> ''
--  GROUP BY type, related_no
-- HAVING COUNT(*) > 1;

-- 2) 加生成列 + 唯一索引（幂等：索引已存在时不做任何事）
SET @idx_exists := (
  SELECT COUNT(*) FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'stock_order'
    AND INDEX_NAME = 'uk_shipment_related'
);

SET @ddl := IF(@idx_exists = 0,
  'ALTER TABLE stock_order
     ADD COLUMN shipment_related_no varchar(64)
       GENERATED ALWAYS AS (IF(type = ''SHIPMENT'', related_no, NULL)) VIRTUAL,
     ADD UNIQUE KEY uk_shipment_related (shipment_related_no)',
  'DO 0');

PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
