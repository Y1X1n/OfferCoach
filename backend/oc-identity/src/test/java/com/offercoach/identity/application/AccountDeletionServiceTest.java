package com.offercoach.identity.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.offercoach.common.error.BizException;
import com.offercoach.identity.application.port.UserContentPurger;
import com.offercoach.identity.application.port.UserRepository;
import com.offercoach.identity.domain.user.UserAccount;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AccountDeletionServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private RefreshTokenService refreshTokenService;

    @Mock
    private TokenEpochService tokenEpochService;

    @Mock
    private UserContentPurger userContentPurger;

    @InjectMocks
    private AccountDeletionService service;

    @Test
    void softDeletesAndRevokesAllSessionsAfterPasswordMatches() {
        UserAccount user = new UserAccount(
                7L,
                "user@example.com",
                "hash",
                "user",
                null,
                "USER",
                "ACTIVE",
                "Asia/Shanghai",
                OffsetDateTime.now(),
                null,
                null);
        when(userRepository.findById(7L)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("secret", "hash")).thenReturn(true);

        service.delete(7L, "secret");

        verify(userRepository).softDelete(anyLong(), any(OffsetDateTime.class));
        verify(userContentPurger).purge(7L);
        verify(refreshTokenService).revokeAllForUser(7L);
        verify(tokenEpochService).bump(7L);
    }

    @Test
    void rejectsWrongPasswordWithoutTouchingData() {
        UserAccount user = new UserAccount(
                7L,
                "user@example.com",
                "hash",
                "user",
                null,
                "USER",
                "ACTIVE",
                "Asia/Shanghai",
                OffsetDateTime.now(),
                null,
                null);
        when(userRepository.findById(7L)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong", "hash")).thenReturn(false);

        assertThatThrownBy(() -> service.delete(7L, "wrong")).isInstanceOf(BizException.class);
        verify(userRepository, never()).softDelete(anyLong(), any(OffsetDateTime.class));
        verify(userContentPurger, never()).purge(anyLong());
        verify(refreshTokenService, never()).revokeAllForUser(anyLong());
    }
}
