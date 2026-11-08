package com.offercoach.interview.resume.domain;

import java.time.OffsetDateTime;

/**
 * 简历领域视图；structuredJson 为 JSONB 的文本形式，仅在应用层解析。
 */
public record Resume(
        long id,
        long userId,
        String title,
        String sourceType,
        String fileKey,
        String fileName,
        String mimeType,
        Long fileSize,
        String sha256,
        Integer pageCount,
        String parseStatus,
        String parseError,
        String rawText,
        String structuredJson,
        String summary,
        String summaryHash,
        boolean isDefault,
        OffsetDateTime createdAt) {

    public static Resume uploaded(
            long userId,
            String title,
            String fileKey,
            String fileName,
            String mimeType,
            long fileSize,
            String sha256,
            boolean isDefault) {
        return new Resume(
                0,
                userId,
                title,
                ResumeSource.FILE,
                fileKey,
                fileName,
                mimeType,
                fileSize,
                sha256,
                null,
                ResumeStatus.PENDING,
                null,
                null,
                null,
                null,
                null,
                isDefault,
                null);
    }

    public static Resume manualStructured(
            long userId,
            String title,
            String structuredJson,
            String summary,
            String summaryHash,
            String sha256,
            boolean isDefault) {
        return new Resume(
                0,
                userId,
                title,
                ResumeSource.MANUAL,
                null,
                null,
                null,
                null,
                sha256,
                1,
                ResumeStatus.READY,
                null,
                null,
                structuredJson,
                summary,
                summaryHash,
                isDefault,
                null);
    }

    public static Resume manual(
            long userId, String title, String rawText, String sha256, long fileSize, boolean isDefault) {
        return new Resume(
                0,
                userId,
                title,
                ResumeSource.MANUAL,
                null,
                null,
                null,
                fileSize,
                sha256,
                1,
                ResumeStatus.PENDING,
                null,
                rawText,
                null,
                null,
                null,
                isDefault,
                null);
    }
}
