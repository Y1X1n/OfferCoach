package com.offercoach.ai.tools.http;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

/**
 * multipart/form-data 请求体构造：工具客户端与内部服务网关共用，避免两处重复实现。
 */
public final class MultipartBody {

    private static final String BOUNDARY_PREFIX = "----ocAiMultipart";

    private MultipartBody() {}

    public static Body of(List<Part> parts) {
        String boundary = BOUNDARY_PREFIX + UUID.randomUUID().toString().replace("-", "");
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (Part part : parts) {
            StringBuilder header = new StringBuilder();
            header.append("--").append(boundary).append("\r\n");
            header.append("Content-Disposition: form-data; name=\"")
                    .append(part.name())
                    .append('"');
            if (part.fileName() != null) {
                header.append("; filename=\"").append(part.fileName()).append('"');
            }
            header.append("\r\n");
            if (part.contentType() != null) {
                header.append("Content-Type: ").append(part.contentType()).append("\r\n");
            }
            header.append("\r\n");
            out.writeBytes(header.toString().getBytes(StandardCharsets.UTF_8));
            out.writeBytes(part.content());
            out.writeBytes("\r\n".getBytes(StandardCharsets.UTF_8));
        }
        out.writeBytes(("--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
        return new Body(out.toByteArray(), "multipart/form-data; boundary=" + boundary);
    }

    /**
     * 单个表单字段；fileName 与 contentType 仅用于文件字段。
     */
    public record Part(String name, String fileName, String contentType, byte[] content) {}

    /**
     * 构造结果：请求体字节与 Content-Type。
     */
    public record Body(byte[] bytes, String contentType) {}
}
