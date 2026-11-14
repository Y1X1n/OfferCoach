package com.offercoach.ai.ocr;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * 文字识别服务启动类；仅内网可达，由 oc-app 在简历解析降级时调用。
 */
@SpringBootApplication(scanBasePackages = "com.offercoach.ai")
@ConfigurationPropertiesScan("com.offercoach.ai")
public class OcrServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(OcrServiceApplication.class, args);
    }
}
