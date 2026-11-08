package com.offercoach.identity.api.dto;

/**
 * 算术验证码挑战：题面用于展示，答案只存服务端。
 */
public record CaptchaResponse(String captchaId, String question, long expiresIn) {}
