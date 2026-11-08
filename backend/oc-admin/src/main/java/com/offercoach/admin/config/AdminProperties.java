package com.offercoach.admin.config;

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
}
