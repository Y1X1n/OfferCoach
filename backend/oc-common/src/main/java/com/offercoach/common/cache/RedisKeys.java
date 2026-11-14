package com.offercoach.common.cache;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public final class RedisKeys {

    private RedisKeys() {}

    public static String refreshToken(String tokenHash) {
        return "oc:auth:refresh:" + tokenHash;
    }

    public static String accessBlacklist(String jwtId) {
        return "oc:auth:blacklist:" + jwtId;
    }

    /** 用户 Access Token 版本号：角色/状态/密码变化时递增，使旧 Access 立即失效。 */
    public static String accessTokenEpoch(long userId) {
        return "oc:auth:token-epoch:" + userId;
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
        return "oc:ratelimit:register:" + digest(ip);
    }

    public static String loginRateLimit(String ip) {
        return "oc:ratelimit:login:" + digest(ip);
    }

    public static String loginFailAccount(String account) {
        return "oc:login:fail:acct:" + digest(account);
    }

    public static String loginFailIp(String ip) {
        return "oc:login:fail:ip:" + digest(ip);
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

    /** 每日语音作答次数。 */
    public static String dailyVoiceAnswer(long userId, String date) {
        return "oc:quota:daily-voice:" + userId + ":" + date;
    }

    /** 每日题目朗读次数。 */
    public static String dailyTts(long userId, String date) {
        return "oc:quota:daily-tts:" + userId + ":" + date;
    }

    /** 每日 OCR 识别次数。 */
    public static String dailyOcr(long userId, String date) {
        return "oc:quota:daily-ocr:" + userId + ":" + date;
    }

    /** 每日联网搜索次数。 */
    public static String dailySearch(long userId, String date) {
        return "oc:quota:daily-search:" + userId + ":" + date;
    }

    /** 每日网页抓取次数。 */
    public static String dailyFetch(long userId, String date) {
        return "oc:quota:daily-fetch:" + userId + ":" + date;
    }

    /** 岗位搜索候选缓存，值为候选列表 JSON（含正文）。 */
    public static String jdSearchCache(String token) {
        return "oc:jd:search:" + token;
    }

    /** 相同查询词的检索结果缓存，按 query+count 摘要键。 */
    public static String jdSearchQuery(String queryDigest) {
        return "oc:jd:search-q:" + queryDigest;
    }

    /** 网页抓取正文缓存，按 URL 摘要键。 */
    public static String jdFetchCache(String urlDigest) {
        return "oc:jd:fetch:" + urlDigest;
    }

    /** JD 预览缓存，值为预览对象 JSON，供创建会话时落库。 */
    public static String jdPreview(String token) {
        return "oc:jd:preview:" + token;
    }

    /** 图形/算术验证码挑战。 */
    public static String captcha(String id) {
        return "oc:auth:captcha:" + id;
    }

    /** 注册风控：单 IP / 单设备每日成功注册计数。 */
    public static String registerIpDaily(String ip, String date) {
        return "oc:ratelimit:register:ipday:" + digest(ip) + ":" + date;
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

    /** 邮箱、IP 等 PII 不落 Redis 键名，仅保留不可逆摘要。 */
    private static String digest(String value) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                hex.append(Character.forDigit((b >> 4) & 0xF, 16));
                hex.append(Character.forDigit(b & 0xF, 16));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 不可用", exception);
        }
    }
}
