package com.offercoach.pay.service.config;

import com.offercoach.pay.config.PayProperties;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * 启动期安全校验：默认校验、显式豁免（只有 local / test 跳过）。
 *
 * <p>此前这里用 {@code activeProfiles.contains("prod")}：只有 profile 恰好是 prod 才校验。
 * 漏设 {@code SPRING_PROFILES_ACTIVE}（或缺省成 local）时整套静默跳过——模拟支付渠道可能
 * 在生产被启用、内部令牌可能根本没配，而服务照常启动。</p>
 */
@Component
public class PaySafetyValidator implements InitializingBean {

    private static final Logger log = LoggerFactory.getLogger(PaySafetyValidator.class);

    private static final Set<String> EXEMPT_PROFILES = Set.of("local", "test");

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
        Set<String> profiles = effectiveProfiles(environment);
        boolean exempt = profiles.stream().anyMatch(EXEMPT_PROFILES::contains);
        boolean mockEnabled = channels.getMock().isEnabled();
        boolean realChannel =
                channels.getWechat().isEnabled() || channels.getAlipay().isEnabled();
        boolean internalTokenMissing = payProperties.getInternalToken() == null
                || payProperties.getInternalToken().isBlank();
        boolean publicBaseMissing =
                ops.getPublicBaseUrl() == null || ops.getPublicBaseUrl().isBlank();
        boolean appBaseMissing =
                ops.getAppBaseUrl() == null || ops.getAppBaseUrl().isBlank();

        if (exempt) {
            log.info("支付安全配置校验已跳过（开发/测试 profile={}）", profiles);
            return;
        }

        if (mockEnabled) {
            throw new IllegalStateException("非本机环境禁止启用模拟支付渠道（oc.pay.channels.mock.enabled）");
        }
        if (internalTokenMissing) {
            throw new IllegalStateException("必须配置 oc.pay.internal-token：缺失时所有内部接口拒绝调用，已付款订单会直接进入死信");
        }
        if (appBaseMissing) {
            throw new IllegalStateException("必须配置 oc-app 地址（oc.pay.ops.app-base-url）以发放额度");
        }
        if (realChannel && publicBaseMissing) {
            throw new IllegalStateException("已启用真实支付渠道但未配置对外回调基地址（oc.pay.ops.public-base-url），渠道下单必然失败");
        }
        // 渠道地址必须 https：对账会拿渠道响应去 markPaid 并发额度，http 等于把 TLS 屏障去掉。
        requireHttps("oc.pay.channels.wechat.api-base-url", channels.getWechat().getApiBaseUrl());
        requireHttps("oc.pay.channels.alipay.gateway-url", channels.getAlipay().getGatewayUrl());
        log.info("支付安全配置校验通过（profile={}）", profiles);
    }

    private static void requireHttps(String key, String url) {
        if (url == null || url.isBlank()) {
            return;
        }
        if (!url.trim().toLowerCase(Locale.ROOT).startsWith("https://")) {
            throw new IllegalStateException("渠道地址必须使用 https：" + key + "=" + url);
        }
    }

    private static Set<String> effectiveProfiles(Environment environment) {
        Set<String> profiles = new LinkedHashSet<>(Arrays.asList(environment.getActiveProfiles()));
        if (profiles.isEmpty()) {
            profiles.addAll(Arrays.asList(environment.getDefaultProfiles()));
        }
        return profiles;
    }
}
