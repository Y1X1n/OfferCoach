package com.offercoach.pay.service.config;

import com.offercoach.pay.config.PayProperties;
import java.util.Arrays;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * 启动期安全校验：生产环境禁止启用模拟渠道；缺关键配置时告警。
 */
@Component
public class PaySafetyValidator implements InitializingBean {

    private static final Logger log = LoggerFactory.getLogger(PaySafetyValidator.class);

    private final PayChannelProperties channels;
    private final PayOpsProperties ops;
    private final PayProperties payProperties;
    private final Environment environment;

    public PaySafetyValidator(
            PayChannelProperties channels, PayOpsProperties ops, PayProperties payProperties, Environment environment) {
        this.channels = channels;
        this.ops = ops;
        this.payProperties = payProperties;
        this.environment = environment;
    }

    @Override
    public void afterPropertiesSet() {
        boolean prod = Arrays.asList(environment.getActiveProfiles()).contains("prod");
        if (prod && channels.getMock().isEnabled()) {
            throw new IllegalStateException("生产环境禁止启用模拟支付渠道（oc.pay.channels.mock.enabled）");
        }
        if (payProperties.getInternalToken() == null
                || payProperties.getInternalToken().isBlank()) {
            log.warn("未配置内部令牌（oc.pay.internal-token），所有内部接口将拒绝调用");
        }
        boolean realChannel =
                channels.getWechat().isEnabled() || channels.getAlipay().isEnabled();
        if (realChannel
                && (ops.getPublicBaseUrl() == null || ops.getPublicBaseUrl().isBlank())) {
            log.warn("已启用真实支付渠道但未配置对外回调基地址（oc.pay.ops.public-base-url），渠道下单将失败");
        }
        if (prod && (ops.getAppBaseUrl() == null || ops.getAppBaseUrl().isBlank())) {
            throw new IllegalStateException("生产环境必须配置 oc-app 地址（oc.pay.ops.app-base-url）以发放额度");
        }
    }
}
