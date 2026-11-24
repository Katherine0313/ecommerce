-- 历史已支付商品订单收款单补录
-- 前置：先执行 20261122_finance_receipt_backfill_check.sql 并备份目标库。
-- 幂等：uk_order_id + NOT EXISTS 防重，重复执行只跳过已有单据，不伪造已审核状态。

SET NAMES utf8mb4;
START TRANSACTION;

INSERT IGNORE INTO finance_receipt
    (receipt_no, order_id, order_no, user_id, channel, account_id, amount, paid_at,
     status, audit_by, audit_name, audit_time, remark, is_deleted, create_time, update_time)
SELECT CONCAT('FIN', DATE_FORMAT(o.pay_at, '%Y%m%d%H%i%s'), LPAD(o.id, 10, '0')),
       o.id,
       o.order_no,
       o.user_id,
       COALESCE(NULLIF(o.pay_type, ''), 'Unknown'),
       (SELECT fa.id
        FROM finance_account fa
        WHERE fa.channel = COALESCE(NULLIF(o.pay_type, ''), 'Unknown')
          AND fa.status = 1 AND fa.is_default = 1 AND fa.is_deleted = 0
        ORDER BY fa.id ASC LIMIT 1),
       o.pay_amount,
       o.pay_at,
       0,
       NULL,
       NULL,
       NULL,
       '历史订单补录',
       0,
       NOW(),
       NOW()
FROM orders o
WHERE o.pay_at IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM finance_receipt r WHERE r.order_id = o.id)
ORDER BY o.id ASC;

SELECT ROW_COUNT() AS backfilled_rows;
COMMIT;

-- 执行后核对：missing_count 应为 0；再次执行 backfilled_rows 应为 0
SELECT COUNT(*) AS missing_count, COALESCE(SUM(o.pay_amount), 0) AS missing_amount
FROM orders o
WHERE o.pay_at IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM finance_receipt r WHERE r.order_id = o.id);

SELECT COUNT(*) AS backfill_total,
       SUM(CASE WHEN status = 0 AND audit_by IS NULL AND audit_name IS NULL AND audit_time IS NULL
                THEN 0 ELSE 1 END) AS unexpected_audited_count
FROM finance_receipt
WHERE remark = '历史订单补录';

SELECT order_id, COUNT(*) AS receipt_count
FROM finance_receipt
GROUP BY order_id
HAVING COUNT(*) > 1
LIMIT 10;
