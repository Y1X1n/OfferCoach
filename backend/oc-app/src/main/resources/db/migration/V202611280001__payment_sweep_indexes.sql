-- 支付扫描任务的索引：三个扫描每 3 分钟各跑一次，此前只有 (status, expires_at) 与
-- (user_id, created_at)，两种查询都要在全表上排序。

-- 对账遗漏回调：status='CREATED' + created_at 区间 + 按 created_at 排序。
-- 部分索引只覆盖待支付订单——已支付/已关闭的历史行不进入索引，随订单量增长也不膨胀。
CREATE INDEX IF NOT EXISTS idx_payment_order_reconcile
    ON payment_order (created_at)
    WHERE status = 'CREATED';

-- 发放通知重试：status='PAID' + notify_status IN ('PENDING','FAILED') + notify_attempts < N
-- + 按 updated_at 排序。部分索引同样只保留"还没通知成功"的行。
CREATE INDEX IF NOT EXISTS idx_payment_order_notify_pending
    ON payment_order (updated_at)
    WHERE status = 'PAID' AND notify_status IN ('PENDING', 'FAILED');

COMMENT ON INDEX idx_payment_order_reconcile IS '对账扫描：待支付订单按创建时间排序取批';
COMMENT ON INDEX idx_payment_order_notify_pending IS '额度发放重试扫描：未通知成功的已支付订单按更新时间排序取批';
