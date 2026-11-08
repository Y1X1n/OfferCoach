package com.offercoach.interview.resume.domain;

/**
 * 简历来源常量；MANUAL 无文件，FILE 必须有 file_key。
 */
public final class ResumeSource {

    public static final String FILE = "FILE";
    public static final String MANUAL = "MANUAL";

    private ResumeSource() {}
}
