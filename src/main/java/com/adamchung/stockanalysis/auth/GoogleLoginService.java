package com.adamchung.stockanalysis.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.stereotype.Service;

/** 驗證 Google ID token，並將 Google 身分轉成本地使用者。 */
@Service
public class GoogleLoginService {

    private static final String GOOGLE_ISSUER = "https://accounts.google.com";
    private static final String GOOGLE_JWK_SET_URI = "https://www.googleapis.com/oauth2/v3/certs";

    private final String clientId;
    private final AppUserService appUserService;

    /**
     * 建立 Google 登入服務。
     *
     * @param clientId Google OAuth Web Client ID
     * @param appUserService 本站使用者建立與舊資料認領服務
     */
    public GoogleLoginService(
            @Value("${app.auth.google-client-id:}") String clientId,
            AppUserService appUserService) {
        this.clientId = clientId;
        this.appUserService = appUserService;
    }

    /**
     * 驗證簽章、issuer、到期時間、audience 與 email_verified，再建立本地 session principal。
     *
     * @param credential Google Identity Services 回傳的 ID token
     * @return 本站使用者 principal
     * @throws IllegalStateException 未設定 Google Client ID 時拋出
     */
    public AppUserPrincipal login(String credential) {
        if (clientId == null || clientId.isBlank()) {
            throw new IllegalStateException("GOOGLE_CLIENT_ID 尚未設定");
        }
        Jwt jwt = decoder().decode(credential);
        if (!Boolean.TRUE.equals(jwt.getClaimAsBoolean("email_verified"))) {
            throw new IllegalArgumentException("Google 帳號的電子郵件尚未驗證");
        }
        return appUserService.provision(
                jwt.getSubject(),
                jwt.getClaimAsString("email"),
                jwt.getClaimAsString("name"),
                jwt.getClaimAsString("picture"));
    }

    /**
     * 建立僅信任 Google 公開金鑰與本網站 audience 的 decoder。
     *
     * @return 每次登入使用的 JWT decoder
     */
    private JwtDecoder decoder() {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(GOOGLE_JWK_SET_URI).build();
        OAuth2TokenValidator<Jwt> audienceValidator = jwt -> jwt.getAudience().contains(clientId)
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "Google audience 不符", null));
        OAuth2TokenValidator<Jwt> issuerValidator = jwt ->
                GOOGLE_ISSUER.equals(jwt.getIssuer() == null ? null : jwt.getIssuer().toString())
                        || "accounts.google.com".equals(jwt.getIssuer() == null ? null : jwt.getIssuer().toString())
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "Google issuer 不符", null));
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                new JwtTimestampValidator(), issuerValidator, audienceValidator));
        return decoder;
    }
}
