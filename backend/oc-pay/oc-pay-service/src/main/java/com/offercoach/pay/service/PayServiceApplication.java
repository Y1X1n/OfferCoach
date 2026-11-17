package com.offercoach.pay.service;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 支付服务启动类。
 *
 * <p>内部接口仅回环可达；微信 / 支付宝回调路径由 Nginx 反向代理暴露到公网（验签即鉴权）。
 * 额度与套餐发放不由本服务完成，改为回调 oc-app 的内部接口（见 {@code EntitlementNotifier}）。</p>
 */
@SpringBootApplication(scanBasePackages = "com.offercoach.pay")
@ConfigurationPropertiesScan("com.offercoach.pay")
@MapperScan(basePackages = "com.offercoach.pay.service.infrastructure.persistence")
@EnableScheduling
public class PayServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(PayServiceApplication.class, args);
    }
}
