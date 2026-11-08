package com.offercoach.interview.document;

/**
 * 文本抽取失败；由解析 worker 捕获后置为 FAILED 并退款。
 */
public class DocumentExtractionException extends RuntimeException {

    public DocumentExtractionException(String message) {
        super(message);
    }

    public DocumentExtractionException(String message, Throwable cause) {
        super(message, cause);
    }
}
