package com.offercoach.identity.api.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 注销账号：必须再次输入当前密码，防止浏览器遗留会话被他人操作。
 */
public record DeleteAccountRequest(@NotBlank(message = "请输入密码") String password) {}
