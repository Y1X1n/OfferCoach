package com.offercoach.pay.service.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.offercoach.pay.config.PayProperties;
import com.offercoach.pay.service.application.port.PaymentOrderRepository;
import com.offercoach.pay.service.config.PayOpsProperties;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * 组件的构造函数必须能被 Spring 唯一确定。
 *
 * <p>{@code EntitlementNotifier} 同时有 5 参（生产注入）与 6 参（测试注入桩）两个构造函数。
 * Spring 在候选不唯一且没有 {@code @Autowired} 时会退回无参构造，于是整个
 * oc-pay-service 启动失败——而模块内没有任何测试加载 Spring 上下文，这个错误从
 * PR #45 起潜伏了 9 天，本地 verify 与 CI 全程绿灯。这条测试专门守住这一点。</p>
 */
class PayServiceComponentWiringTest {

    @Configuration
    @Import(EntitlementNotifier.class)
    static class Config {

        @Bean
        PayOpsProperties payOpsProperties() {
            return new PayOpsProperties();
        }

        @Bean
        PayProperties payProperties() {
            return new PayProperties();
        }

        @Bean
        PaymentOrderRepository paymentOrderRepository() {
            return mock(PaymentOrderRepository.class);
        }

        @Bean
        PaymentExceptionRecorder paymentExceptionRecorder() {
            return mock(PaymentExceptionRecorder.class);
        }

        @Bean
        ObjectMapper objectMapper() {
            return JsonMapper.builder().findAndAddModules().build();
        }
    }

    @Test
    void entitlementNotifierIsInstantiableBySpring() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(Config.class)) {
            assertThat(context.getBean(EntitlementNotifier.class)).isNotNull();
        }
    }
}
