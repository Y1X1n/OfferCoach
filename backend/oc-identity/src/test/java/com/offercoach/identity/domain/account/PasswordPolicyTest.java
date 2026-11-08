package com.offercoach.identity.domain.account;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PasswordPolicyTest {

    @Test
    void acceptsLettersDigitsAndEightChars() {
        assertThat(PasswordPolicy.isValid("Passw0rd")).isTrue();
        assertThat(PasswordPolicy.isValid("a1b2c3d4")).isTrue();
    }

    @Test
    void rejectsShortOrUnbalancedPasswords() {
        assertThat(PasswordPolicy.isValid(null)).isFalse();
        assertThat(PasswordPolicy.isValid("short1")).isFalse();
        assertThat(PasswordPolicy.isValid("12345678")).isFalse();
        assertThat(PasswordPolicy.isValid("abcdefgh")).isFalse();
        assertThat(PasswordPolicy.isValid("a1".repeat(33))).isFalse();
    }
}
