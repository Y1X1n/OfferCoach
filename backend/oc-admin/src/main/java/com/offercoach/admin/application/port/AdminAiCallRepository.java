package com.offercoach.admin.application.port;

import com.offercoach.admin.application.model.AdminModels.AiCall;
import java.util.List;

/**
 * AI 调用日志只读仓储。
 */
public interface AdminAiCallRepository {

    List<AiCall> search(String scene, String status, Long userId, int offset, int limit);

    long count(String scene, String status, Long userId);
}
