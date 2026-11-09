package com.offercoach.admin.config;

import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 管理端配置。
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "oc.admin")
public class AdminProperties {

    private boolean enabled;

    private String email = "";

    private String password = "";

    private String nickname = "管理员";

    /** 可选管理端 IP 白名单；为空表示不限制。 */
    private List<String> allowedIps = List.of();
}
