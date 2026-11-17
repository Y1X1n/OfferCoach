package com.offercoach.identity.internal;

import com.offercoach.common.error.BizException;
import com.offercoach.common.error.ErrorCode;
import com.offercoach.identity.application.payment.PaymentEntitlementService;
import com.offercoach.pay.api.PayDtos;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 支付额度发放内部接口；仅供 oc-pay 调用，令牌由 {@code InternalTokenFilter} 校验。
 */
@RestController
@RequestMapping("/internal/v1/payments")
@RequiredArgsConstructor
public class PaymentNotifyController {

    private final PaymentEntitlementService entitlementService;

    @PostMapping("/notify")
    public PayDtos.EntitlementAck notify(@RequestBody(required = false) PayDtos.EntitlementNotice notice) {
        if (notice == null) {
            throw new BizException(ErrorCode.VALIDATION_FAILED, Map.of("body", "required"));
        }
        return entitlementService.apply(notice);
    }
}
