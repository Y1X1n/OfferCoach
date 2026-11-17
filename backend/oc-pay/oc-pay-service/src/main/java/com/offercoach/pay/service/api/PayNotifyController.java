package com.offercoach.pay.service.api;

import com.offercoach.pay.api.PayDtos;
import com.offercoach.pay.service.application.NotifyService;
import com.offercoach.pay.service.application.OrderService;
import com.offercoach.pay.service.config.PayChannelProperties;
import com.offercoach.pay.service.config.PayOpsProperties;
import com.offercoach.pay.service.domain.ChannelCode;
import com.offercoach.pay.service.error.PayErrorKind;
import com.offercoach.pay.service.error.PayException;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.core.env.Environment;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

/**
 * 渠道回调与本地辅助端点。
 *
 * <p>回调路径由 Nginx 反代暴露到公网，验签即鉴权；应答体按渠道要求区分 JSON（微信）与纯文本（支付宝）。</p>
 */
@RestController
public class PayNotifyController {

    private final NotifyService notifyService;
    private final OrderService orderService;
    private final PayChannelProperties channelProperties;
    private final PayOpsProperties ops;
    private final Environment environment;

    public PayNotifyController(
            NotifyService notifyService,
            OrderService orderService,
            PayChannelProperties channelProperties,
            PayOpsProperties ops,
            Environment environment) {
        this.notifyService = notifyService;
        this.orderService = orderService;
        this.channelProperties = channelProperties;
        this.ops = ops;
        this.environment = environment;
    }

    @PostMapping("/pay/notify/{channel}")
    public ResponseEntity<String> notify(
            @PathVariable String channel, @RequestBody(required = false) String body, HttpServletRequest http) {
        ChannelCode code = ChannelCode.fromCode(channel);
        if (code == null) {
            throw new PayException(PayErrorKind.VALIDATION_FAILED, "未知回调渠道");
        }
        String response = notifyService.handle(code, headerMap(http), body == null ? "" : body);
        MediaType type = code == ChannelCode.WECHAT ? MediaType.APPLICATION_JSON : MediaType.TEXT_PLAIN;
        return ResponseEntity.ok().contentType(type).body(response);
    }

    /**
     * 支付宝同步跳转：不做业务判断，仅提示返回应用查看结果。
     */
    @RequestMapping(
            path = "/pay/return/{channel}",
            method = {RequestMethod.GET, RequestMethod.POST})
    public ResponseEntity<String> syncReturn() {
        String html = "<!doctype html><html lang=\"zh-CN\"><head><meta charset=\"utf-8\">"
                + "<title>支付结果</title></head><body style=\"font-family:sans-serif;padding:32px\">"
                + "<h1>支付已完成</h1><p>请返回应用查看最新额度与套餐状态。</p></body></html>";
        return ResponseEntity.ok().contentType(MediaType.TEXT_HTML).body(html);
    }

    /**
     * 本地模拟支付确认：默认关闭；开启时仅对 mock 订单生效，且生产 profile 一律拒绝。
     */
    @RequestMapping(
            path = "/pay/mock/{orderNo}/pay",
            method = {RequestMethod.GET, RequestMethod.POST})
    public ResponseEntity<String> mockPay(@PathVariable String orderNo) {
        if (!channelProperties.getMock().isEnabled() || productionProfile()) {
            throw new PayException(PayErrorKind.NOT_FOUND, "模拟支付未启用");
        }
        PayDtos.OrderView view = orderService.confirmMockPayment(orderNo);
        String html = "<!doctype html><html lang=\"zh-CN\"><head><meta charset=\"utf-8\">"
                + "<title>模拟支付</title></head><body style=\"font-family:sans-serif;padding:32px\">"
                + "<h1>模拟支付成功</h1><p>订单号：" + view.orderNo() + "，状态：" + view.status()
                + "。该页面仅用于本地联调。</p></body></html>";
        return ResponseEntity.ok().contentType(MediaType.TEXT_HTML).body(html);
    }

    private static Map<String, String> headerMap(HttpServletRequest request) {
        Map<String, String> headers = new LinkedHashMap<>();
        Enumeration<String> names = request.getHeaderNames();
        while (names != null && names.hasMoreElements()) {
            String name = names.nextElement();
            headers.put(name, request.getHeader(name));
        }
        return headers;
    }

    /**
     * 生产 profile 下即使误开 mock 开关也拒绝确认，作为启动期校验之外的第二道防线。
     */
    private boolean productionProfile() {
        for (String profile : environment.getActiveProfiles()) {
            if ("prod".equals(profile)) {
                return true;
            }
        }
        for (String profile : environment.getDefaultProfiles()) {
            if ("prod".equals(profile)) {
                return true;
            }
        }
        return false;
    }
}
