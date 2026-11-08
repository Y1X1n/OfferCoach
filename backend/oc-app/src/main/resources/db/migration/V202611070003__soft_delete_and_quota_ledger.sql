-- DB-1：账号软删后，唯一性只约束未删除行，释放被软删行占用的邮箱 / 手机号哈希。
-- DB-2：为存量 user_quota 回填初始发放流水，使 quota_ledger 成为可核对的事实源。

ALTER TABLE app_user DROP CONSTRAINT IF EXISTS app_user_email_key;
ALTER TABLE app_user DROP CONSTRAINT IF EXISTS app_user_phone_hash_key;

CREATE UNIQUE INDEX IF NOT EXISTS uq_app_user_email_active
    ON app_user (email) WHERE deleted_at IS NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uq_app_user_phone_hash_active
    ON app_user (phone_hash) WHERE deleted_at IS NULL;

COMMENT ON INDEX uq_app_user_email_active IS '未删除账号邮箱唯一；软删行不占用邮箱';
COMMENT ON INDEX uq_app_user_phone_hash_active IS '未删除账号手机号哈希唯一；软删行不占用手机号';

-- 注销账号会清空联系方式，因此 status='DELETED' 时允许 email 与 phone_hash 同时为空。
ALTER TABLE app_user DROP CONSTRAINT IF EXISTS app_user_check;

-- 先删后加，保证迁移可重复执行且约束定义始终与预期一致。
ALTER TABLE app_user DROP CONSTRAINT IF EXISTS app_user_contact_required_check;

ALTER TABLE app_user
    ADD CONSTRAINT app_user_contact_required_check
    CHECK (email IS NOT NULL OR phone_hash IS NOT NULL OR status = 'DELETED');

COMMENT ON CONSTRAINT app_user_contact_required_check ON app_user
    IS '邮箱或手机号至少一项；已注销(DELETED)账号允许两者皆空';

COMMENT ON COLUMN quota_ledger.reason IS
    'PLAN_GRANT / SESSION_CHARGE / SESSION_REFUND / PARSE_CHARGE / PARSE_REFUND / ADMIN_COMPENSATION';

-- 存量额度回填初始发放流水：幂等键 plan-grant:{TYPE}:{user_id}，余额为 0 的用户不产生流水（delta 不得为 0）。
INSERT INTO quota_ledger (user_id, quota_type, delta, balance_after, reason, ref_type, ref_id, idempotency_key)
SELECT q.user_id, 'INTERVIEW', q.interview_credits, q.interview_credits, 'PLAN_GRANT', 'PLAN', NULL,
       'plan-grant:INTERVIEW:' || q.user_id
  FROM user_quota q
 WHERE q.interview_credits > 0
   AND NOT EXISTS (
       SELECT 1
         FROM quota_ledger l
        WHERE l.user_id = q.user_id
          AND l.idempotency_key = 'plan-grant:INTERVIEW:' || q.user_id
   )
ON CONFLICT (user_id, idempotency_key) DO NOTHING;

INSERT INTO quota_ledger (user_id, quota_type, delta, balance_after, reason, ref_type, ref_id, idempotency_key)
SELECT q.user_id, 'RESUME', q.resume_credits, q.resume_credits, 'PLAN_GRANT', 'PLAN', NULL,
       'plan-grant:RESUME:' || q.user_id
  FROM user_quota q
 WHERE q.resume_credits > 0
   AND NOT EXISTS (
       SELECT 1
         FROM quota_ledger l
        WHERE l.user_id = q.user_id
          AND l.idempotency_key = 'plan-grant:RESUME:' || q.user_id
   )
ON CONFLICT (user_id, idempotency_key) DO NOTHING;
