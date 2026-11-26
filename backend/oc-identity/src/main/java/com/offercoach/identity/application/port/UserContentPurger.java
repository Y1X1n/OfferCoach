package com.offercoach.identity.application.port;

/**
 * 注销时清理该用户的业务内容（简历/JD/会话/回答/评分/报告与相关文件）。
 *
 * <p>实现位于 oc-app：它同时依赖 oc-interview 与文件存储，而 identity 不能反向依赖业务模块。
 * 额度流水、支付订单与审计日志不在清理范围，保留用于对账与法务。</p>
 */
public interface UserContentPurger {

    /**
     * 清除该用户全部业务内容；幂等，重复调用不报错。
     */
    void purge(long userId);
}
