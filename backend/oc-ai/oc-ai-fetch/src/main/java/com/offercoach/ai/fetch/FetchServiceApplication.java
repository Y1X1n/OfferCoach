package com.offercoach.ai.fetch;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * 网页抓取服务启动类；仅内网可达，由 oc-app 调用。
 */
@SpringBootApplication(scanBasePackages = "com.offercoach.ai")
@ConfigurationPropertiesScan("com.offercoach.ai")
public class FetchServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(FetchServiceApplication.class, args);
    }
}
