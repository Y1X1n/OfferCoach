package com.offercoach.interview.api.dto.resume;

/**
 * 上传 / 手录响应；charged 为本次实际扣减的额度数。
 */
public record ResumeUploadResponse(
        String id, String title, String parseStatus, long estimatedReadyInMs, ResumeQuota quota) {

    public record ResumeQuota(int charged, int resumeCredits) {}
}
