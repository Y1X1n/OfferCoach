package com.offercoach.pay.service.channel;

import com.offercoach.pay.service.config.PayChannelProperties;
import com.offercoach.pay.service.config.PayOpsProperties;
import com.offercoach.pay.service.domain.ChannelCode;
import com.offercoach.pay.service.domain.OrderStatus;
import com.offercoach.pay.service.domain.PaymentOrder;
import com.offercoach.pay.service.domain.PaymentRefund;
import com.offercoach.pay.service.error.PayErrorKind;
import com.offercoach.pay.service.error.PayException;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * 本地模拟渠道：仅用于开发联调，默认关闭，且禁止在生产启用。
 *
 * <p>下单返回一个指向本服务确认端点的链接，在浏览器打开即视为支付成功；不产生任何真实资金流。</p>
 */
@Component
public class MockPayChannel implements PaymentChannel {

    private final PayChannelProperties.Mock mock;
    private final PayOpsProperties ops;

    public MockPayChannel(PayChannelProperties properties, PayOpsProperties ops) {
        this.mock = properties.getMock();
        this.ops = ops;
    }

    @Override
    public ChannelCode code() {
        return ChannelCode.MOCK;
    }

    @Override
    public boolean available() {
        return mock.isEnabled();
    }

    @Override
    public String notifyPath() {
        return mock.getConfirmPath();
    }

    @Override
    public Prepay prepay(PaymentOrder order, String clientIp) {
        requireEnabled();
        String base = ops.getPublicBaseUrl() == null || ops.getPublicBaseUrl().isBlank()
                ? "http://127.0.0.1:8099"
                : ops.getPublicBaseUrl().replaceAll("/+$", "");
        return new Prepay(base + mock.getConfirmPath() + "/" + order.orderNo() + "/pay", order.orderNo());
    }

    @Override
    public ChannelOrderState query(PaymentOrder order) {
        boolean paid = order.status() == OrderStatus.PAID;
        return new ChannelOrderState(paid, order.providerTxnId(), order.paidAt(), paid ? "SUCCESS" : "NOTPAY", null);
    }

    @Override
    public boolean close(PaymentOrder order) {
        // 模拟渠道无需与外部交互，本地关单即可生效
        return true;
    }

    @Override
    public RefundOutcome refund(PaymentOrder order, PaymentRefund refund) {
        requireEnabled();
        return new RefundOutcome(true, "MOCK-" + refund.refundNo(), "SUCCESS", "模拟退款成功");
    }

    @Override
    public ChannelNotify parseNotify(Map<String, String> headers, String body) {
        throw new PayException(PayErrorKind.VALIDATION_FAILED, "模拟渠道不支持回调");
    }

    @Override
    public String successResponseBody() {
        return "success";
    }

    private void requireEnabled() {
        if (!mock.isEnabled()) {
            throw new PayException(PayErrorKind.NOT_CONFIGURED, "模拟支付渠道未启用");
        }
    }
}
