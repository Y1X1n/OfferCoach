-- 发放通知重试扫描的索引：每 3 分钟跑一次
--   WHERE status='PAID' AND notify_status IN ('PENDING','FAILED') AND notify_attempts < N
--   ORDER BY updated_at ASC LIMIT 100
-- 除此之外 payment_order 上只有 (user_id, created_at DESC) 与 (status, expires_at)，
-- 两个都用不上：status='PAID' 几乎覆盖全表，notify_status 与 updated_at 都不在任何索引里，
-- 只能 Seq Scan + top-N sort（5 万订单实测 2062 buffers / 4.8ms，随历史订单线性变差）。
--
-- 部分索引只保留"还没通知成功"的行，历史订单不进索引，规模与当前待重试量同级
-- （实测同一查询 4 buffers / 0.04ms，且没有排序节点）。
CREATE INDEX IF NOT EXISTS idx_payment_order_notify_pending
    ON payment_order (updated_at)
    WHERE status = 'PAID' AND notify_status IN ('PENDING', 'FAILED');

COMMENT ON INDEX idx_payment_order_notify_pending IS '额度发放重试扫描：未通知成功的已支付订单按更新时间排序取批';

-- 对账扫描（findReconcileCandidates）没有新增索引：它的选择条件是 expires_at > now()，
-- 已有的 idx_payment_order_status_expire (status, expires_at) 既能区间扫描又天然按到期时间
-- 有序，把排序键从 created_at 改成 expires_at 之后就完全够用。反过来给 created_at 建部分索引
-- 反而用不上——created_at < ? 这个条件过滤不掉任何在单，规划器仍会选带 seek 的
-- (status, expires_at) 并排序（实测 15 buffers + top-N sort 对 7 buffers、无排序）。
