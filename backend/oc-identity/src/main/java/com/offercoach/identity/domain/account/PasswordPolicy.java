package com.offercoach.identity.domain.account;

import java.util.regex.Pattern;

/**
 * 密码规则：8–64 位，至少包含一个字母与一个数字。
 */
public final class PasswordPolicy {

    private static final Pattern PATTERN = Pattern.compile("^(?=.*[A-Za-z])(?=.*\\d).{8,64}$");

    private PasswordPolicy() {}

    public static boolean isValid(String rawPassword) {
        return rawPassword != null && PATTERN.matcher(rawPassword).matches();
    }
}
