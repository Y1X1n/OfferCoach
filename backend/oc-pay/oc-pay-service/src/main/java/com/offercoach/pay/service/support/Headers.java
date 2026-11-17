package com.offercoach.pay.service.support;

import java.util.Map;

/**
 * 请求头读取：渠道回调的请求头大小写不固定，统一按大小写不敏感查找。
 */
public final class Headers {

    private Headers() {}

    /**
     * 大小写不敏感取值；缺失返回 null。
     */
    public static String get(Map<String, String> headers, String name) {
        if (headers == null || name == null) {
            return null;
        }
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            if (entry.getKey() != null && entry.getKey().equalsIgnoreCase(name)) {
                return entry.getValue();
            }
        }
        return null;
    }
}
