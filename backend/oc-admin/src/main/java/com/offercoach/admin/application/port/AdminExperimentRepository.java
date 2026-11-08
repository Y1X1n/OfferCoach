package com.offercoach.admin.application.port;

import com.offercoach.admin.application.model.AdminModels.Experiment;
import java.util.List;
import java.util.Optional;

/**
 * A/B 实验仓储；仅管理端最小 CRUD，不参与运行时分流。
 */
public interface AdminExperimentRepository {

    List<Experiment> list(int offset, int limit);

    long count();

    Optional<Experiment> findById(long id);

    long insert(
            long templateId,
            String name,
            String status,
            int trafficPercent,
            String variantsJson,
            String guardrailsJson);

    /**
     * 更新状态；setStart / setEnd 控制是否补齐开始与结束时间。
     */
    void updateStatus(long id, String status, boolean setStart, boolean setEnd);
}
