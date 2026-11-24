-- 历史已支付商品订单缺收款单：只读 dry-run，不写任何表
-- 包含用户已软删除的订单；已有收款单（含已删除）不重复补录。

SET NAMES utf8mb4;

SELECT COUNT(*) AS missing_count, COALESCE(SUM(o.pay_amount), 0) AS missing_amount
FROM orders o
WHERE o.pay_at IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM finance_receipt r WHERE r.order_id = o.id);

-- 按渠道预览补录金额及默认账户归集结果
SELECT missing.channel,
       COUNT(*) AS missing_count,
       COALESCE(SUM(missing.amount), 0) AS missing_amount,
       SUM(CASE WHEN missing.account_id IS NULL THEN 1 ELSE 0 END) AS unassigned_count
FROM (
    SELECT o.id, COALESCE(NULLIF(o.pay_type, ''), 'Unknown') AS channel,
           o.pay_amount AS amount,
           (SELECT fa.id
            FROM finance_account fa
            WHERE fa.channel = COALESCE(NULLIF(o.pay_type, ''), 'Unknown')
              AND fa.status = 1 AND fa.is_default = 1 AND fa.is_deleted = 0
            ORDER BY fa.id ASC LIMIT 1) AS account_id
    FROM orders o
    WHERE o.pay_at IS NOT NULL
      AND NOT EXISTS (SELECT 1 FROM finance_receipt r WHERE r.order_id = o.id)
) missing
GROUP BY missing.channel
ORDER BY missing.channel;

-- 抽样核对订单快照，正式补录必须保留这些订单字段
SELECT o.id AS order_id, o.order_no, o.user_id,
       COALESCE(NULLIF(o.pay_type, ''), 'Unknown') AS channel,
       o.pay_amount, o.pay_at, o.is_deleted
FROM orders o
WHERE o.pay_at IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM finance_receipt r WHERE r.order_id = o.id)
ORDER BY o.pay_at ASC, o.id ASC
LIMIT 20;
