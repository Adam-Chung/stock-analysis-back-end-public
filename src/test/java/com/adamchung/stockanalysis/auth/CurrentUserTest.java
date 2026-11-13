package com.adamchung.stockanalysis.auth;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CurrentUserTest {

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void require_returns_the_google_session_principal() {
        TestSecurityContext.login(42L);
        assertThat(CurrentUser.id()).isEqualTo(42L);
    }

    @Test
    void require_rejects_anonymous_requests() {
        assertThatThrownBy(CurrentUser::require).hasMessage("請先登入");
    }
}
