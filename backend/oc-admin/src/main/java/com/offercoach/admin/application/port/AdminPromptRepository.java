package com.offercoach.admin.application.port;

import com.offercoach.admin.application.model.AdminModels.PromptTemplate;
import com.offercoach.admin.application.model.AdminModels.PromptVersion;
import java.util.List;
import java.util.Optional;

/**
 * Prompt 模板与版本仓储；code 与 version 组合唯一定位版本。
 */
public interface AdminPromptRepository {

    List<PromptTemplate> listTemplates();

    Optional<PromptTemplate> findTemplateByCode(String code);

    long insertTemplate(String code, String name, String scene, String description);

    List<PromptVersion> listVersions(String code);

    Optional<PromptVersion> findVersion(String code, String version);

    Optional<PromptVersion> findPublishedVersion(String code);

    /**
     * 新建 DRAFT 版本，返回主键；版本号冲突由唯一约束抛出 DuplicateKeyException。
     */
    long insertVersion(
            long templateId,
            String version,
            String content,
            String variablesSchemaJson,
            String modelConfigJson,
            String contentHash,
            Long createdBy);

    /**
     * 将同模板已发布版本置为 ARCHIVED，返回影响行数。
     */
    int archivePublished(long templateId);

    /**
     * 发布指定版本并写入 published_at。
     */
    void publish(long versionId);

    void touchTemplate(long templateId);
}
