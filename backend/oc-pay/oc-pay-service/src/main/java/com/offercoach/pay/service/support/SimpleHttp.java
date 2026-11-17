package com.offercoach.pay.service.support;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

/**
 * 渠道网关 HTTP 调用封装；统一超时、重定向与异常语义。
 */
public final class SimpleHttp {

    private final HttpClient httpClient;

    public SimpleHttp(Duration connectTimeout) {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(connectTimeout)
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    /**
     * 发送请求并返回原始响应；网络异常由调用方按渠道上游失败处理。
     */
    public HttpResponse<String> send(
            String method, String url, Map<String, String> headers, String body, Duration timeout)
            throws IOException, InterruptedException {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(timeout)
                .method(
                        method,
                        body == null
                                ? HttpRequest.BodyPublishers.noBody()
                                : HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
        headers.forEach(builder::header);
        return httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }
}
