package com.adamchung.stockanalysis.auth;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 驗證 Google 登入重用注入的 decoder 並保留既有驗證流程。 */
class GoogleLoginServiceTest {

    private final AppUserService appUserService = mock(AppUserService.class);
    private final JwtDecoder jwtDecoder = mock(JwtDecoder.class);

    /** 多次登入皆使用同一個應用程式共用 decoder。 */
    @Test
    void login_reusesInjectedDecoder() {
        Jwt jwt = verifiedJwt();
        AppUserPrincipal principal = new AppUserPrincipal(
                42L, "user@example.com", "User", null, false);
        when(jwtDecoder.decode("credential-1")).thenReturn(jwt);
        when(jwtDecoder.decode("credential-2")).thenReturn(jwt);
        when(appUserService.provision("google-sub", "user@example.com", "User", null))
                .thenReturn(principal);
        GoogleLoginService service = new GoogleLoginService("client-id", appUserService, jwtDecoder);

        assertThat(service.login("credential-1")).isSameAs(principal);
        assertThat(service.login("credential-2")).isSameAs(principal);
        verify(jwtDecoder, times(1)).decode("credential-1");
        verify(jwtDecoder, times(1)).decode("credential-2");
    }

    /** 未設定 Client ID 時應在呼叫 decoder 前立即失敗。 */
    @Test
    void login_rejectsMissingClientIdBeforeDecoding() {
        GoogleLoginService service = new GoogleLoginService(" ", appUserService, jwtDecoder);

        assertThatThrownBy(() -> service.login("credential"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("GOOGLE_CLIENT_ID 尚未設定");
        verify(jwtDecoder, never()).decode("credential");
    }

    /** 建立含已驗證信箱的 Google JWT 測試資料。 */
    private Jwt verifiedJwt() {
        Instant issuedAt = Instant.parse("2026-11-16T00:00:00Z");
        return new Jwt(
                "token",
                issuedAt,
                issuedAt.plusSeconds(300),
                Map.of("alg", "RS256"),
                Map.of(
                        "sub", "google-sub",
                        "email", "user@example.com",
                        "name", "User",
                        "email_verified", true));
    }
}
