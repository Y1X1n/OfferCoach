package com.offercoach.interview.ai;

import com.offercoach.ai.AiScene;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 读取管理端已发布的 Prompt 版本；按场景缓存 60 秒，未发布时由调用方回退到内置提示词。
 */
@Component
public class PromptProvider {

    private static final Logger log = LoggerFactory.getLogger(PromptProvider.class);
    private static final long CACHE_TTL_SECONDS = 60L;

    private final JdbcTemplate jdbcTemplate;
    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();

    public PromptProvider(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<String> active(AiScene scene) {
        if (scene == null) {
            return Optional.empty();
        }
        String key = scene.name();
        CacheEntry cached = cache.get(key);
        Instant now = Instant.now();
        if (cached != null && cached.expiresAt().isAfter(now)) {
            return Optional.ofNullable(cached.content());
        }
        String content = query(scene);
        cache.put(key, new CacheEntry(content, now.plusSeconds(CACHE_TTL_SECONDS)));
        return Optional.ofNullable(content);
    }

    private String query(AiScene scene) {
        try {
            var contents = jdbcTemplate.queryForList(
                    """
                    SELECT pv.content
                    FROM prompt_version pv
                    JOIN prompt_template pt ON pt.id = pv.template_id
                    WHERE pt.scene = ? AND pv.status = 'PUBLISHED'
                    ORDER BY pv.published_at DESC NULLS LAST, pv.id DESC
                    LIMIT 1
                    """,
                    String.class,
                    scene.name());
            return contents.isEmpty() ? null : contents.getFirst();
        } catch (RuntimeException exception) {
            log.warn(
                    "Prompt 读取失败，回退内置提示词：scene={} reason={}",
                    scene,
                    exception.getClass().getSimpleName());
            return null;
        }
    }

    private record CacheEntry(String content, Instant expiresAt) {}
}
