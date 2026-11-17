package com.offercoach.pay.service.channel;

import com.offercoach.pay.api.PayDtos;
import com.offercoach.pay.service.domain.ChannelCode;
import com.offercoach.pay.service.error.PayErrorKind;
import com.offercoach.pay.service.error.PayException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * 渠道注册表：按 {@link ChannelCode} 索引各渠道适配器。
 */
@Component
public class ChannelRegistry {

    private final Map<ChannelCode, PaymentChannel> channels = new EnumMap<>(ChannelCode.class);

    public ChannelRegistry(List<PaymentChannel> beans) {
        for (PaymentChannel channel : beans) {
            channels.put(channel.code(), channel);
        }
    }

    /**
     * 取渠道适配器；不存在视为未配置。
     */
    public PaymentChannel require(ChannelCode code) {
        PaymentChannel channel = channels.get(code);
        if (channel == null) {
            throw new PayException(PayErrorKind.NOT_CONFIGURED, "未知支付渠道：" + code.code());
        }
        return channel;
    }

    public PaymentChannel find(ChannelCode code) {
        return channels.get(code);
    }

    /**
     * 渠道可用性总览，按 微信 / 支付宝 / 模拟 顺序返回。
     */
    public PayDtos.ChannelOverview overview() {
        List<PayDtos.ChannelInfo> infos = new ArrayList<>();
        for (ChannelCode code : List.of(ChannelCode.WECHAT, ChannelCode.ALIPAY, ChannelCode.MOCK)) {
            PaymentChannel channel = channels.get(code);
            boolean enabled = channel != null && channel.available();
            infos.add(new PayDtos.ChannelInfo(code.code(), enabled, code.method()));
        }
        return new PayDtos.ChannelOverview(infos);
    }
}
