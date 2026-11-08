package com.offercoach.identity.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 注册请求：验证码/注册码与邀请码均为可选，由运行时策略决定是否必填。
 */
public record RegisterRequest(
        @NotBlank(message = "邮箱不能为空") @Email(message = "邮箱格式不正确") @Size(max = 320, message = "邮箱过长") String email,
        @NotBlank(message = "密码不能为空") String password,
        @NotBlank(message = "昵称不能为空") String nickname,
        @Size(max = 64, message = "注册码过长") String verifyCode,
        @Size(max = 16, message = "邀请码过长") String inviteCode,
        @Size(max = 64, message = "验证码 ID 过长") String captchaId,
        @Size(max = 16, message = "验证码答案过长") String captchaAnswer) {}
