package com.offercoach.identity.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 登录请求；account 为邮箱。
 */
public record LoginRequest(
        @NotBlank(message = "账号不能为空") @Size(max = 320, message = "账号过长") String account,
        @NotBlank(message = "密码不能为空") String password) {}
