package com.offercoach.ai.tools.config;

import java.time.Duration;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * AI 工具服务配置：内部鉴权、调用超时与在途并发上限。
 *
 * <p>DeepSeek 工具接口与对话接口同源同 Key，Base URL 与密钥统一从 {@code ai_runtime_config}
 * 读取（见 oc-ai-client），此处只放本侧行为参数。</p>
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "oc.ai.tools")
public class ToolsProperties {

    /**
     * 服务间内部鉴权令牌；oc-app 与三个工具服务必须一致。
     */
    private String internalToken = "";

    /** 内部请求头名，固定便于 Nginx / 网关白名单。 */
    private String internalHeader = "X-OC-Internal-Token";

    private String userIdHeader = "X-OC-User-Id";

    private String requestIdHeader = "X-OC-Request-Id";

    /** ASR 单次调用超时：上传 + 识别；官方最长 120s 音频，留出上传余量。 */
    private Duration asrTimeout = Duration.ofSeconds(180);

    /** TTS 单次调用超时；长文本流式返回，按 30s 缓冲。 */
    private Duration ttsTimeout = Duration.ofSeconds(120);

    /** OCR 单次调用超时；官方总时长上限 180s。 */
    private Duration ocrTimeout = Duration.ofSeconds(200);

    /** Search 单次调用超时；返回最多 50 条结构化结果。 */
    private Duration searchTimeout = Duration.ofSeconds(60);

    /** Fetch 单次调用超时；含整页下载。 */
    private Duration fetchTimeout = Duration.ofSeconds(90);

    /** ASR 在途并发上限（官方账号级 50）。 */
    private int asrMaxConcurrency = 20;

    /** TTS 在途并发上限（官方账号级 50）。 */
    private int ttsMaxConcurrency = 20;

    /** OCR 在途并发上限（官方账号级 15）。 */
    private int ocrMaxConcurrency = 8;

    /** Search 在途并发上限（官方账号级 QPS 100）。 */
    private int searchMaxConcurrency = 20;

    /** Fetch 在途并发上限（官方账号级 QPS 25）。 */
    private int fetchMaxConcurrency = 8;

    /** 工具服务间调用地址，由 oc-app 侧使用。 */
    private String asrBaseUrl = "http://127.0.0.1:8094";

    private String ttsBaseUrl = "http://127.0.0.1:8095";

    private String ocrBaseUrl = "http://127.0.0.1:8096";

    private String searchBaseUrl = "http://127.0.0.1:8097";

    private String fetchBaseUrl = "http://127.0.0.1:8098";

    /** 每用户每日搜索次数上限（0 表示不限）。 */
    private int searchDailyLimit = 20;

    /** 每用户每日抓取次数上限（0 表示不限）。 */
    private int fetchDailyLimit = 20;

    /** 单次搜索返回条数上限（官方 1–50，本侧收紧以控制响应体与成本）。 */
    private int searchMaxCount = 10;

    /** TTS 本地缓存目录；空表示不缓存。 */
    private String ttsCacheDir = ".local/storage/tts";

    /** TTS 缓存保留天数。 */
    private int ttsCacheDays = 7;

    /** 默认音色。 */
    private String ttsDefaultVoice = "stella";
}
