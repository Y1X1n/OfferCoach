package com.offercoach.interview.api.dto.resume;

/**
 * 原文件下载地址；expiresIn 单位秒。
 */
public record ResumeDownloadUrlResponse(String url, long expiresIn) {}
