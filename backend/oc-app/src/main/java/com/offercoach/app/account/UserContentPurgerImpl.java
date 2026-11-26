package com.offercoach.app.account;

import com.offercoach.identity.application.port.UserContentPurger;
import com.offercoach.interview.storage.FileStorage;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 注销时清除用户业务内容。
 *
 * <p>放在 oc-app：这里同时可见 oc-interview（文件存储）与数据库，而 oc-identity 不能反向依赖业务模块。
 * 顺序按外键依赖自底向上：评分 → 回答 → 报告 → 题目 → 会话 → JD/简历。
 * 额度流水、支付订单、审计日志与 ai_call_log 不在清理范围（对账与法务留存）。</p>
 */
@Component
public class UserContentPurgerImpl implements UserContentPurger {

    private static final Logger log = LoggerFactory.getLogger(UserContentPurgerImpl.class);

    private final JdbcTemplate jdbcTemplate;
    private final FileStorage fileStorage;

    public UserContentPurgerImpl(JdbcTemplate jdbcTemplate, FileStorage fileStorage) {
        this.jdbcTemplate = jdbcTemplate;
        this.fileStorage = fileStorage;
    }

    @Override
    @Transactional
    public void purge(long userId) {
        // 先取文件 key，删库后再删文件；文件删除失败只告警，不阻断注销。
        List<String> resumeKeys = jdbcTemplate.queryForList(
                "SELECT file_key FROM resume WHERE user_id = ? AND file_key IS NOT NULL", String.class, userId);
        List<String> audioKeys = jdbcTemplate.queryForList(
                "SELECT audio_key FROM interview_answer WHERE user_id = ? AND audio_key IS NOT NULL",
                String.class,
                userId);

        jdbcTemplate.update("DELETE FROM answer_feedback WHERE user_id = ?", userId);
        jdbcTemplate.update("DELETE FROM interview_answer WHERE user_id = ?", userId);
        jdbcTemplate.update("DELETE FROM interview_report WHERE user_id = ?", userId);
        jdbcTemplate.update(
                """
                DELETE FROM interview_question
                WHERE session_id IN (SELECT id FROM interview_session WHERE user_id = ?)
                """,
                userId);
        jdbcTemplate.update("DELETE FROM interview_session WHERE user_id = ?", userId);
        jdbcTemplate.update("DELETE FROM job_description WHERE user_id = ?", userId);
        jdbcTemplate.update("DELETE FROM resume WHERE user_id = ?", userId);

        deleteQuietly(resumeKeys, "简历文件");
        deleteQuietly(audioKeys, "作答音频");
        log.info("注销用户内容已清除：userId={} resumes={} audio={}", userId, resumeKeys.size(), audioKeys.size());
    }

    private void deleteQuietly(List<String> keys, String label) {
        for (String key : keys) {
            try {
                fileStorage.delete(key);
            } catch (RuntimeException exception) {
                log.warn("注销清理{}失败：key={} reason={}", label, key, exception.getClass().getSimpleName());
            }
        }
    }
}
