package com.offercoach.ai.search;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * 联网检索服务启动类；仅内网可达，由 oc-app 调用。
 */
@SpringBootApplication(scanBasePackages = "com.offercoach.ai")
@ConfigurationPropertiesScan("com.offercoach.ai")
public class SearchServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(SearchServiceApplication.class, args);
    }
}
