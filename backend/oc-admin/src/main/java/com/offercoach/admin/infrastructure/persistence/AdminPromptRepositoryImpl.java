package com.offercoach.admin.infrastructure.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.offercoach.admin.application.model.AdminModels.PromptTemplate;
import com.offercoach.admin.application.model.AdminModels.PromptVersion;
import com.offercoach.admin.application.port.AdminPromptRepository;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

/**
 * Prompt 模板与版本仓储：版本内容不可变，发布状态由唯一部分索引兜底。
 */
@Repository
public class AdminPromptRepositoryImpl implements AdminPromptRepository {

    private static final String TEMPLATE_COLUMNS =
            """
            t.id, t.code, t.name, t.scene, t.description,
            (SELECT pv.version FROM prompt_version pv
             WHERE pv.template_id = t.id AND pv.status = 'PUBLISHED') AS active_version,
            t.created_at, t.updated_at
            """;
    private static final String VERSION_COLUMNS =
            """
            v.id, t.code AS template_code, v.version, v.status, v.content,
            v.variables_schema::text AS variables_schema, v.model_config::text AS model_config,
            v.content_hash, v.created_by, v.published_at, v.created_at
            """;

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final RowMapper<PromptTemplate> templateMapper = (rs, rowNum) -> new PromptTemplate(
            rs.getLong("id"),
            rs.getString("code"),
            rs.getString("name"),
            rs.getString("scene"),
            rs.getString("description"),
            rs.getString("active_version"),
            rs.getObject("created_at", OffsetDateTime.class),
            rs.getObject("updated_at", OffsetDateTime.class));
    private final RowMapper<PromptVersion> versionMapper = (rs, rowNum) -> new PromptVersion(
            rs.getLong("id"),
            rs.getString("template_code"),
            rs.getString("version"),
            rs.getString("status"),
            rs.getString("content"),
            parseJson(rs.getString("variables_schema")),
            parseJson(rs.getString("model_config")),
            rs.getString("content_hash"),
            rs.getObject("created_by", Long.class),
            rs.getObject("published_at", OffsetDateTime.class),
            rs.getObject("created_at", OffsetDateTime.class));

    public AdminPromptRepositoryImpl(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public List<PromptTemplate> listTemplates() {
        return jdbcTemplate.query(
                "SELECT " + TEMPLATE_COLUMNS + " FROM prompt_template t ORDER BY t.id", templateMapper);
    }

    @Override
    public Optional<PromptTemplate> findTemplateByCode(String code) {
        return jdbcTemplate
                .query("SELECT " + TEMPLATE_COLUMNS + " FROM prompt_template t WHERE t.code = ?", templateMapper, code)
                .stream()
                .findFirst();
    }

    @Override
    public long insertTemplate(String code, String name, String scene, String description) {
        Long id = jdbcTemplate.queryForObject(
                """
                INSERT INTO prompt_template (code, name, scene, description, created_at, updated_at)
                VALUES (?, ?, ?, ?, now(), now())
                RETURNING id
                """,
                Long.class,
                code,
                name,
                scene,
                description);
        return id == null ? 0 : id;
    }

    @Override
    public List<PromptVersion> listVersions(String code) {
        return jdbcTemplate.query(
                "SELECT " + VERSION_COLUMNS + " FROM prompt_version v"
                        + " JOIN prompt_template t ON t.id = v.template_id"
                        + " WHERE t.code = ? ORDER BY v.created_at DESC, v.id DESC",
                versionMapper,
                code);
    }

    @Override
    public Optional<PromptVersion> findVersion(String code, String version) {
        return jdbcTemplate
                .query(
                        "SELECT " + VERSION_COLUMNS + " FROM prompt_version v"
                                + " JOIN prompt_template t ON t.id = v.template_id"
                                + " WHERE t.code = ? AND v.version = ?",
                        versionMapper,
                        code,
                        version)
                .stream()
                .findFirst();
    }

    @Override
    public Optional<PromptVersion> findPublishedVersion(String code) {
        return jdbcTemplate
                .query(
                        "SELECT " + VERSION_COLUMNS + " FROM prompt_version v"
                                + " JOIN prompt_template t ON t.id = v.template_id"
                                + " WHERE t.code = ? AND v.status = 'PUBLISHED'",
                        versionMapper,
                        code)
                .stream()
                .findFirst();
    }

    @Override
    public long insertVersion(
            long templateId,
            String version,
            String content,
            String variablesSchemaJson,
            String modelConfigJson,
            String contentHash,
            Long createdBy) {
        Long id = jdbcTemplate.queryForObject(
                """
                INSERT INTO prompt_version
                    (template_id, version, content, variables_schema, model_config, status,
                     content_hash, created_by, created_at)
                VALUES (?, ?, ?, CAST(? AS jsonb), CAST(? AS jsonb), 'DRAFT', ?, ?, now())
                RETURNING id
                """,
                Long.class,
                templateId,
                version,
                content,
                variablesSchemaJson,
                modelConfigJson,
                contentHash,
                createdBy);
        return id == null ? 0 : id;
    }

    @Override
    public int archivePublished(long templateId) {
        return jdbcTemplate.update(
                "UPDATE prompt_version SET status = 'ARCHIVED' WHERE template_id = ? AND status = 'PUBLISHED'",
                templateId);
    }

    @Override
    public void publish(long versionId) {
        jdbcTemplate.update(
                "UPDATE prompt_version SET status = 'PUBLISHED', published_at = now() WHERE id = ?", versionId);
    }

    @Override
    public void touchTemplate(long templateId) {
        jdbcTemplate.update("UPDATE prompt_template SET updated_at = now() WHERE id = ?", templateId);
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
