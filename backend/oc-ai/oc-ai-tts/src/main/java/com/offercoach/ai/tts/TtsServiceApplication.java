package com.offercoach.ai.tts;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * 语音合成服务启动类；仅内网可达，由 oc-app 代理调用。
 */
@SpringBootApplication(scanBasePackages = "com.offercoach.ai")
@ConfigurationPropertiesScan("com.offercoach.ai")
public class TtsServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(TtsServiceApplication.class, args);
    }
}
