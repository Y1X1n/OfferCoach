package com.offercoach.admin.infrastructure.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.offercoach.admin.application.model.AdminModels.ImportBatch;
import com.offercoach.admin.application.model.AdminModels.ImportedQuestion;
import com.offercoach.admin.application.model.AdminModels.Question;
import com.offercoach.admin.application.model.AdminModels.QuestionWrite;
import com.offercoach.admin.application.port.AdminQuestionRepository;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

/**
 * 题库管理仓储：所有筛选与分页参数均通过占位符传入。
 */
@Repository
public class AdminQuestionRepositoryImpl implements AdminQuestionRepository {

    private static final char TAG_SEPARATOR = (char) 31;
    private static final String SELECT_COLUMNS =
            """
            id, title, stem, job_family, dimension, difficulty, status, source, source_repo, company,
            array_to_string(tags, chr(31)) AS tags_joined, created_at
            """;
    private static final String BATCH_COLUMNS =
            """
            id, source_repo, total_count, success_count, failed_count, skipped_count, status,
            error_report::text AS error_report, created_by, created_at, finished_at
            """;

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final RowMapper<Question> questionMapper = (rs, rowNum) -> new Question(
            rs.getLong("id"),
            rs.getString("title"),
            rs.getString("stem"),
            rs.getString("job_family"),
            rs.getString("dimension"),
            rs.getString("difficulty"),
            rs.getString("status"),
            rs.getString("source"),
            rs.getString("source_repo"),
            rs.getString("company"),
            splitTags(rs.getString("tags_joined")),
            rs.getObject("created_at", OffsetDateTime.class));
    private final RowMapper<ImportBatch> batchMapper = (rs, rowNum) -> new ImportBatch(
            rs.getLong("id"),
            rs.getString("source_repo"),
            rs.getInt("total_count"),
            rs.getInt("success_count"),
            rs.getInt("failed_count"),
            rs.getInt("skipped_count"),
            rs.getString("status"),
            parseJson(rs.getString("error_report")),
            rs.getObject("created_by", Long.class),
            rs.getObject("created_at", OffsetDateTime.class),
            rs.getObject("finished_at", OffsetDateTime.class));

    public AdminQuestionRepositoryImpl(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public List<Question> search(
            String status,
            String jobFamily,
            String dimension,
            String difficulty,
            String keyword,
            int offset,
            int limit) {
        WhereClause where = filter(status, jobFamily, dimension, difficulty, keyword);
        List<Object> args = new ArrayList<>(where.args());
        args.add(limit);
        args.add(offset);
        return jdbcTemplate.query(
                "SELECT " + SELECT_COLUMNS + " FROM question_bank" + where.sql()
                        + " ORDER BY created_at DESC, id DESC LIMIT ? OFFSET ?",
                questionMapper,
                args.toArray());
    }

    @Override
    public long count(String status, String jobFamily, String dimension, String difficulty, String keyword) {
        WhereClause where = filter(status, jobFamily, dimension, difficulty, keyword);
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM question_bank" + where.sql(),
                Long.class,
                where.args().toArray());
        return count == null ? 0 : count;
    }

    @Override
    public Optional<Question> findById(long id) {
        List<Question> rows =
                jdbcTemplate.query("SELECT " + SELECT_COLUMNS + " FROM question_bank WHERE id = ?", questionMapper, id);
        return rows.stream().findFirst();
    }

    @Override
    public long insert(QuestionWrite write, long createdBy) {
        String joinedTags = String.join(String.valueOf(TAG_SEPARATOR), write.tags());
        Long id = jdbcTemplate.queryForObject(
                """
                INSERT INTO question_bank
                    (title, job_family, dimension, difficulty, tags, stem, reference_answer, scoring_points,
                     source, source_repo, source_path, source_url, source_license, company, language, status,
                     embedding_status, created_by, created_at, updated_at)
                VALUES
                    (?, ?, ?, ?,
                     CASE WHEN ? = '' THEN '{}'::text[] ELSE string_to_array(?, chr(31)) END,
                     ?, ?, CAST(? AS jsonb), 'MANUAL', NULL, NULL, ?, ?, ?, ?, 'DRAFT', 'READY', ?,
                     now(), now())
                RETURNING id
                """,
                Long.class,
                write.title(),
                write.jobFamily(),
                write.dimension(),
                write.difficulty(),
                joinedTags,
                joinedTags,
                write.stem(),
                write.referenceAnswer(),
                toJson(write.scoringPoints() == null ? List.of() : write.scoringPoints()),
                write.sourceUrl(),
                write.sourceLicense(),
                write.company(),
                write.language(),
                createdBy);
        return id == null ? 0 : id;
    }

    @Override
    public int update(long id, QuestionWrite patch) {
        StringBuilder sql = new StringBuilder("UPDATE question_bank SET updated_at = now()");
        List<Object> args = new ArrayList<>();
        if (patch.title() != null) {
            sql.append(", title = ?");
            args.add(patch.title());
        }
        if (patch.jobFamily() != null) {
            sql.append(", job_family = ?");
            args.add(patch.jobFamily());
        }
        if (patch.dimension() != null) {
            sql.append(", dimension = ?");
            args.add(patch.dimension());
        }
        if (patch.difficulty() != null) {
            sql.append(", difficulty = ?");
            args.add(patch.difficulty());
        }
        if (patch.tags() != null) {
            String joinedTags = String.join(String.valueOf(TAG_SEPARATOR), patch.tags());
            sql.append(", tags = CASE WHEN ? = '' THEN '{}'::text[] ELSE string_to_array(?, chr(31)) END");
            args.add(joinedTags);
            args.add(joinedTags);
        }
        if (patch.stem() != null) {
            sql.append(", stem = ?");
            args.add(patch.stem());
        }
        if (patch.referenceAnswer() != null) {
            sql.append(", reference_answer = ?");
            args.add(patch.referenceAnswer());
        }
        if (patch.scoringPoints() != null) {
            sql.append(", scoring_points = CAST(? AS jsonb)");
            args.add(toJson(patch.scoringPoints()));
        }
        if (patch.company() != null) {
            sql.append(", company = ?");
            args.add(patch.company());
        }
        if (patch.sourceUrl() != null) {
            sql.append(", source_url = ?");
            args.add(patch.sourceUrl());
        }
        if (patch.sourceLicense() != null) {
            sql.append(", source_license = ?");
            args.add(patch.sourceLicense());
        }
        if (patch.language() != null) {
            sql.append(", language = ?");
            args.add(patch.language());
        }
        sql.append(" WHERE id = ?");
        args.add(id);
        return jdbcTemplate.update(sql.toString(), args.toArray());
    }

    @Override
    public void updateStatus(long id, String status) {
        jdbcTemplate.update("UPDATE question_bank SET status = ?, updated_at = now() WHERE id = ?", status, id);
    }

    @Override
    public long insertImportBatch(String sourceRepo, int totalCount, long createdBy) {
        Long id = jdbcTemplate.queryForObject(
                """
                INSERT INTO question_import_batch
                    (source_repo, total_count, success_count, failed_count, skipped_count, status,
                     error_report, created_by, created_at)
                VALUES (?, ?, 0, 0, 0, 'PENDING', '[]'::jsonb, ?, now())
                RETURNING id
                """,
                Long.class,
                sourceRepo,
                totalCount,
                createdBy);
        return id == null ? 0 : id;
    }

    @Override
    public void finishImportBatch(
            long id, String status, int success, int failed, int skipped, String errorReportJson) {
        jdbcTemplate.update(
                """
                UPDATE question_import_batch
                SET success_count = ?, failed_count = ?, skipped_count = ?, status = ?,
                    error_report = CAST(? AS jsonb), finished_at = now()
                WHERE id = ?
                """,
                success,
                failed,
                skipped,
                status,
                errorReportJson,
                id);
    }

    @Override
    public int insertImported(ImportedQuestion question, String sourceRepo, String sourcePath, long createdBy) {
        String joinedTags = String.join(String.valueOf(TAG_SEPARATOR), question.tags());
        return jdbcTemplate.update(
                """
                INSERT INTO question_bank
                    (title, job_family, dimension, difficulty, tags, stem, reference_answer, scoring_points,
                     source, source_repo, source_path, source_url, source_license, company, language, status,
                     embedding_status, created_by, created_at, updated_at)
                VALUES
                    (?, ?, ?, ?,
                     CASE WHEN ? = '' THEN '{}'::text[] ELSE string_to_array(?, chr(31)) END,
                     ?, ?, CAST(? AS jsonb), 'IMPORT', ?, ?, ?, ?, ?, ?, 'PUBLISHED', 'READY', ?,
                     now(), now())
                ON CONFLICT (source_repo, source_path, stem_hash) DO NOTHING
                """,
                question.title(),
                question.jobFamily(),
                question.dimension(),
                question.difficulty(),
                joinedTags,
                joinedTags,
                question.stem(),
                question.referenceAnswer(),
                toJson(question.scoringPoints()),
                sourceRepo,
                sourcePath,
                question.sourceUrl(),
                question.sourceLicense(),
                question.company(),
                question.language(),
                createdBy);
    }

    @Override
    public List<ImportBatch> listBatches(int offset, int limit) {
        return jdbcTemplate.query(
                "SELECT " + BATCH_COLUMNS
                        + " FROM question_import_batch ORDER BY created_at DESC, id DESC LIMIT ? OFFSET ?",
                batchMapper,
                limit,
                offset);
    }

    @Override
    public long countBatches() {
        Long count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM question_import_batch", Long.class);
        return count == null ? 0 : count;
    }

    @Override
    public Optional<ImportBatch> findBatch(long id) {
        return jdbcTemplate
                .query("SELECT " + BATCH_COLUMNS + " FROM question_import_batch WHERE id = ?", batchMapper, id)
                .stream()
                .findFirst();
    }

    private static WhereClause filter(
            String status, String jobFamily, String dimension, String difficulty, String keyword) {
        StringBuilder sql = new StringBuilder(" WHERE 1 = 1");
        List<Object> args = new ArrayList<>();
        if (status != null) {
            sql.append(" AND status = ?");
            args.add(status);
        }
        if (jobFamily != null) {
            sql.append(" AND lower(job_family) = lower(?)");
            args.add(jobFamily);
        }
        if (dimension != null) {
            sql.append(" AND dimension = ?");
            args.add(dimension);
        }
        if (difficulty != null) {
            sql.append(" AND difficulty = ?");
            args.add(difficulty);
        }
        if (keyword != null) {
            sql.append(" AND (title ILIKE '%' || ? || '%' OR stem ILIKE '%' || ? || '%'"
                    + " OR company ILIKE '%' || ? || '%')");
            args.add(keyword);
            args.add(keyword);
            args.add(keyword);
        }
        return new WhereClause(sql.toString(), args);
    }

    private static List<String> splitTags(String joined) {
        if (joined == null || joined.isBlank()) {
            return List.of();
        }
        return Arrays.stream(joined.split(String.valueOf(TAG_SEPARATOR)))
                .map(String::strip)
                .filter(tag -> !tag.isEmpty())
                .distinct()
                .toList();
    }

    private JsonNode parseJson(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readTree(json);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("JSONB 解析失败", exception);
        }
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value == null ? List.of() : value);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("JSONB 序列化失败", exception);
        }
    }

    private record WhereClause(String sql, List<Object> args) {}
}
