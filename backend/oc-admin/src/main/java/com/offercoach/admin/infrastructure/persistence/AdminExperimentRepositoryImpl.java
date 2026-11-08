package com.offercoach.admin.infrastructure.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.offercoach.admin.application.model.AdminModels.Experiment;
import com.offercoach.admin.application.port.AdminExperimentRepository;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

/**
 * A/B 实验仓储：variants/guardrails 以 JSONB 文本出入。
 */
@Repository
public class AdminExperimentRepositoryImpl implements AdminExperimentRepository {

    private static final String SELECT_COLUMNS =
            """
            e.id, t.code AS template_code, e.name, e.status, e.traffic_percent,
            e.variants::text AS variants, e.guardrails::text AS guardrails,
            e.start_at, e.end_at, e.created_at
            """;

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final RowMapper<Experiment> mapper = (rs, rowNum) -> new Experiment(
            rs.getLong("id"),
            rs.getString("template_code"),
            rs.getString("name"),
            rs.getString("status"),
            rs.getInt("traffic_percent"),
            parseJson(rs.getString("variants")),
            parseJson(rs.getString("guardrails")),
            rs.getObject("start_at", OffsetDateTime.class),
            rs.getObject("end_at", OffsetDateTime.class),
            rs.getObject("created_at", OffsetDateTime.class));

    public AdminExperimentRepositoryImpl(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public List<Experiment> list(int offset, int limit) {
        return jdbcTemplate.query(
                "SELECT " + SELECT_COLUMNS + " FROM ab_experiment e"
                        + " JOIN prompt_template t ON t.id = e.template_id"
                        + " ORDER BY e.created_at DESC, e.id DESC LIMIT ? OFFSET ?",
                mapper,
                limit,
                offset);
    }

    @Override
    public long count() {
        Long count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM ab_experiment", Long.class);
        return count == null ? 0 : count;
    }

    @Override
    public Optional<Experiment> findById(long id) {
        return jdbcTemplate
                .query(
                        "SELECT " + SELECT_COLUMNS + " FROM ab_experiment e"
                                + " JOIN prompt_template t ON t.id = e.template_id WHERE e.id = ?",
                        mapper,
                        id)
                .stream()
                .findFirst();
    }

    @Override
    public long insert(
            long templateId,
            String name,
            String status,
            int trafficPercent,
            String variantsJson,
            String guardrailsJson) {
        Long id = jdbcTemplate.queryForObject(
                """
                INSERT INTO ab_experiment
                    (template_id, name, status, traffic_percent, variants, guardrails, created_at)
                VALUES (?, ?, ?, ?, CAST(? AS jsonb), CAST(? AS jsonb), now())
                RETURNING id
                """,
                Long.class,
                templateId,
                name,
                status,
                trafficPercent,
                variantsJson,
                guardrailsJson);
        return id == null ? 0 : id;
    }

    @Override
    public void updateStatus(long id, String status, boolean setStart, boolean setEnd) {
        jdbcTemplate.update(
                """
                UPDATE ab_experiment
                SET status = ?,
                    start_at = CASE WHEN ? THEN COALESCE(start_at, now()) ELSE start_at END,
                    end_at = CASE WHEN ? THEN COALESCE(end_at, now()) ELSE end_at END
                WHERE id = ?
                """,
                status,
                setStart,
                setEnd,
                id);
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
}
