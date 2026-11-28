package com.offercoach.app.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.offercoach.interview.feedback.infrastructure.persistence.AnswerFeedbackMapper;
import com.offercoach.interview.jd.infrastructure.persistence.JdMapper;
import com.offercoach.interview.qbank.QuestionBankDO;
import com.offercoach.interview.qbank.QuestionBankMapper;
import com.offercoach.interview.resume.infrastructure.persistence.ResumeMapper;
import com.offercoach.interview.session.infrastructure.persistence.InterviewSessionMapper;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * 手写 SQL 的集成用例：批量查询的归属过滤、反馈只取最新一版、题库排序走生成列。
 *
 * <p>这些查询用 {@code <script>}/{@code <foreach>} 和 {@code DISTINCT ON} 手写，单测只能证明
 * "仓储被调用了几次"，证明不了 SQL 本身能跑、过滤条件对。这里在真实 PostgreSQL 上跑一遍：
 * Flyway 建库，JdbcTemplate 播种，直接调 Mapper。</p>
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(properties = {"oc.auth.dev-verify-code.enabled=true", "oc.auth.dev-verify-code.code=123456"})
@ActiveProfiles("local")
class MapperSqlIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("offercoach")
            .withUsername("oc")
            .withPassword("oc_test_password");

    @Container
    static final GenericContainer<?> REDIS =
            new GenericContainer<>(DockerImageName.parse("redis:7.2-alpine")).withExposedPorts(6379);

    @DynamicPropertySource
    static void containerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    }

    private static final long OWNER = 900_001L;
    private static final long OTHER = 900_002L;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private InterviewSessionMapper sessionMapper;

    @Autowired
    private JdMapper jdMapper;

    @Autowired
    private ResumeMapper resumeMapper;

    @Autowired
    private AnswerFeedbackMapper feedbackMapper;

    @Autowired
    private QuestionBankMapper questionBankMapper;

    @Autowired
    private StringRedisTemplate redisTemplate;

    private long ownerResume;
    private long ownerJd;
    private long ownerSession;
    private long otherSession;
    private long ownerAnswer;

    @BeforeEach
    void seed() {
        try (RedisConnection connection = redisTemplate.getConnectionFactory().getConnection()) {
            connection.serverCommands().flushDb();
        }
        jdbc.update("DELETE FROM question_bank");
        jdbc.update("DELETE FROM answer_feedback");
        jdbc.update("DELETE FROM interview_answer");
        jdbc.update("DELETE FROM interview_question");
        jdbc.update("DELETE FROM interview_session");
        jdbc.update("DELETE FROM job_description");
        jdbc.update("DELETE FROM resume");
        jdbc.update("DELETE FROM app_user WHERE id >= 900000");

        insertUser(OWNER, "mapper-owner@example.com");
        insertUser(OTHER, "mapper-other@example.com");
        ownerResume = insertResume(OWNER, "我的简历");
        long otherResume = insertResume(OTHER, "别人的简历");
        ownerJd = insertJd(OWNER, "JAVA-OWNER");
        long otherJd = insertJd(OTHER, "JAVA-OTHER");
        ownerSession = insertSession(OWNER, ownerResume, ownerJd, "mapper-owner-session");
        otherSession = insertSession(OTHER, otherResume, otherJd, "mapper-other-session");

        long question = insertQuestion(ownerSession, "介绍一下你的项目");
        ownerAnswer = insertAnswer(ownerSession, question, OWNER, "做了个订单系统");
        // 同一回答的两版反馈：批量查询只能返回 version 最大的那条
        insertFeedback(ownerAnswer, ownerSession, OWNER, 1, "5.0");
        insertFeedback(ownerAnswer, ownerSession, OWNER, 2, "9.0");
    }

    // ---- #71 的三个批量查询 ----

    @Test
    void findByIdsOwnedReturnsOnlyOwnRows() {
        var sessions = sessionMapper.findByIdsOwned(OWNER, Set.of(ownerSession, otherSession));

        assertThat(sessions).extracting(row -> row.getId()).containsExactly(ownerSession);
    }

    @Test
    void findOwnedByIdsFiltersOwnerAndSoftDelete() {
        long ownerDeletedJd = insertJd(OWNER, "JAVA-DELETED");
        jdbc.update("UPDATE job_description SET deleted_at = now() WHERE id = ?", ownerDeletedJd);

        var jds = jdMapper.findOwnedByIds(OWNER, Set.of(ownerJd, ownerDeletedJd));

        assertThat(jds).extracting(row -> row.getId()).containsExactly(ownerJd);

        long ownerDeletedResume = insertResume(OWNER, "已删除简历");
        jdbc.update("UPDATE resume SET deleted_at = now() WHERE id = ?", ownerDeletedResume);

        var resumes = resumeMapper.findOwnedByIds(OWNER, Set.of(ownerResume, ownerDeletedResume));

        assertThat(resumes).extracting(row -> row.getId()).containsExactly(ownerResume);
    }

    // ---- 报告详情的反馈批量查询 ----

    @Test
    void listLatestBySessionKeepsOnlyNewestVersionPerAnswer() {
        var rows = feedbackMapper.listLatestBySession(OWNER, ownerSession);

        assertThat(rows).hasSize(1);
        assertThat(rows.getFirst().getAnswerId()).isEqualTo(ownerAnswer);
        assertThat(rows.getFirst().getVersion()).isEqualTo(2);
        assertThat(rows.getFirst().getOverallScore()).isEqualByComparingTo("9.0");
    }

    // ---- 题库检索的排序（生成列 + 部分索引）----

    @Test
    void selectPublishedByFamilyPrefersRowsWithReferenceAnswer() {
        long noAnswer1 = insertQuestionBank("Test-Family", "题目 A", null);
        long noAnswer2 = insertQuestionBank("Test-Family", "题目 B", "   ");
        long withAnswer = insertQuestionBank("Test-Family", "题目 C", "参考答案");

        List<QuestionBankDO> rows = questionBankMapper.selectPublishedByFamily("test-family", 10);

        // 有参考答案的排最前；空白答案按"没有"处理；其余按 id 升序
        assertThat(rows).extracting(row -> row.getId()).containsExactly(withAnswer, noAnswer1, noAnswer2);

        List<QuestionBankDO> all = questionBankMapper.selectPublished(10);
        assertThat(all).extracting(row -> row.getId()).containsExactly(withAnswer, noAnswer1, noAnswer2);
    }

    @Test
    void unpublishedQuestionsAreExcluded() {
        long published = insertQuestionBank("Test-Family", "已发布", "参考答案");
        long draft = insertQuestionBank("Test-Family", "草稿", "参考答案");
        jdbc.update("UPDATE question_bank SET status = 'DRAFT' WHERE id = ?", draft);

        assertThat(questionBankMapper.selectPublishedByFamily("test-family", 10))
                .extracting(row -> row.getId())
                .containsExactly(published);
    }

    private void insertUser(long id, String email) {
        jdbc.update(
                "INSERT INTO app_user (id, email, password_hash, nickname) VALUES (?, ?, ?, ?)",
                id,
                email,
                "{noop}unused",
                "mapper");
    }

    private long insertResume(long userId, String title) {
        return jdbc.queryForObject(
                "INSERT INTO resume (user_id, title, source_type) VALUES (?, ?, 'MANUAL') RETURNING id",
                Long.class,
                userId,
                title);
    }

    private long insertJd(long userId, String title) {
        return jdbc.queryForObject(
                "INSERT INTO job_description (user_id, title, raw_text, content_hash) VALUES (?, ?, ?, ?) RETURNING id",
                Long.class,
                userId,
                title,
                "jd raw",
                "hash-" + title);
    }

    private long insertSession(long userId, long resumeId, long jdId, String key) {
        return jdbc.queryForObject(
                """
                INSERT INTO interview_session (user_id, resume_id, jd_id, config, question_count, idempotency_key)
                VALUES (?, ?, ?, '{}'::jsonb, 1, ?) RETURNING id
                """,
                Long.class,
                userId,
                resumeId,
                jdId,
                key);
    }

    private long insertQuestion(long sessionId, String text) {
        return jdbc.queryForObject(
                """
                INSERT INTO interview_question (session_id, seq, dimension, difficulty, question_text, source)
                VALUES (?, 1, 'DEPTH', 'MEDIUM', ?, 'BANK') RETURNING id
                """,
                Long.class,
                sessionId,
                text);
    }

    private long insertAnswer(long sessionId, long questionId, long userId, String text) {
        return jdbc.queryForObject(
                """
                INSERT INTO interview_answer (session_id, question_id, user_id, input_mode, answer_text, status)
                VALUES (?, ?, ?, 'TEXT', ?, 'SCORED') RETURNING id
                """,
                Long.class,
                sessionId,
                questionId,
                userId,
                text);
    }

    private void insertFeedback(long answerId, long sessionId, long userId, int version, String score) {
        jdbc.update(
                """
                INSERT INTO answer_feedback
                    (answer_id, version, request_id, session_id, user_id, overall_score, dimensions, model, provider)
                VALUES (?, ?, ?, ?, ?, ?::numeric, '{}'::jsonb, 'test-model', 'test-provider')
                """,
                answerId,
                version,
                "req-" + answerId + "-" + version,
                sessionId,
                userId,
                score);
    }

    private long insertQuestionBank(String jobFamily, String title, String referenceAnswer) {
        return jdbc.queryForObject(
                """
                INSERT INTO question_bank (title, job_family, dimension, difficulty, stem, reference_answer, status)
                VALUES (?, ?, 'DEPTH', 'MEDIUM', ?, ?, 'PUBLISHED') RETURNING id
                """,
                Long.class,
                title,
                jobFamily,
                title + " 的题干",
                referenceAnswer);
    }
}
