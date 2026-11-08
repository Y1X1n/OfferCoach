package com.offercoach.common.cache;

public final class RedisKeys {

    private RedisKeys() {}

    public static String refreshToken(String tokenHash) {
        return "oc:auth:refresh:" + tokenHash;
    }

    public static String accessBlacklist(String jwtId) {
        return "oc:auth:blacklist:" + jwtId;
    }

    /** Refresh Token 家族：Set 成员为该家族所有 Refresh Token 的 SHA-256 摘要。 */
    public static String refreshFamily(String familyId) {
        return "oc:auth:refresh-family:" + familyId;
    }

    /** 已被轮换消费的 Refresh Token 标记，值为其 familyId，用于重放检测。 */
    public static String refreshUsed(String tokenHash) {
        return "oc:auth:refresh-used:" + tokenHash;
    }

    /** 用户全部 Refresh 家族索引，供停用/改密/降权时整户吊销。 */
    public static String refreshUserFamilies(long userId) {
        return "oc:auth:refresh-user:" + userId;
    }

    /** 已吊销家族墓碑：阻止并发刷新在吊销后重建家族键。 */
    public static String refreshFamilyRevoked(String familyId) {
        return "oc:auth:refresh-revoked:" + familyId;
    }

    public static String registerRateLimit(String ip) {
        return "oc:ratelimit:register:" + ip;
    }

    public static String loginRateLimit(String ip) {
        return "oc:ratelimit:login:" + ip;
    }

    public static String loginFailAccount(String account) {
        return "oc:login:fail:acct:" + account;
    }

    public static String loginFailIp(String ip) {
        return "oc:login:fail:ip:" + ip;
    }

    public static String quotaCache(long userId) {
        return "oc:quota:cache:" + userId;
    }

    public static String dailyQuota(long userId, String date) {
        return "oc:quota:daily:" + userId + ":" + date;
    }

    /** 每日简历解析次数。 */
    public static String dailyResumeParse(long userId, String date) {
        return "oc:quota:daily-resume:" + userId + ":" + date;
    }

    /** 图形/算术验证码挑战。 */
    public static String captcha(String id) {
        return "oc:auth:captcha:" + id;
    }

    /** 注册风控：单 IP / 单设备每日成功注册计数。 */
    public static String registerIpDaily(String ip, String date) {
        return "oc:ratelimit:register:ipday:" + ip + ":" + date;
    }

    public static String registerDeviceDaily(String deviceHash, String date) {
        return "oc:ratelimit:register:devday:" + deviceHash + ":" + date;
    }

    /** 评分 SSE 短期令牌，值为 userId:answerId，TTL 5 分钟。 */
    public static String aiStreamToken(String token) {
        return "oc:ai:stream:" + token;
    }

    /** 简历重解析小时窗口计数。 */
    public static String resumeReparse(long userId, long resumeId) {
        return "oc:resume:reparse:" + userId + ":" + resumeId;
    }
}
