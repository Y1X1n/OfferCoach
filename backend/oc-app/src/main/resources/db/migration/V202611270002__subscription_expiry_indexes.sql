-- 订阅与额度周期到期扫描索引。
-- 到期结算任务每天按「status='ACTIVE' AND expires_at <= now()」和
-- 「period_end <= today」取批，此前两张表都没有可用索引，只能全表扫。
CREATE INDEX IF NOT EXISTS idx_user_subscription_expiry
    ON user_subscription (expires_at) WHERE status = 'ACTIVE';

CREATE INDEX IF NOT EXISTS idx_user_quota_period_end
    ON user_quota (period_end) WHERE period_end IS NOT NULL;
