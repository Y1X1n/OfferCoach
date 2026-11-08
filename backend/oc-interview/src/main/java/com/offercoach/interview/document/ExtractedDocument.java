package com.offercoach.interview.document;

/**
 * 文本抽取结果；pageCount 对 DOCX 为按篇幅估算值。
 */
public record ExtractedDocument(String text, int pageCount) {}
