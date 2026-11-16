package com.adamchung.stockanalysis.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

/** 建立可重用 Google 公開金鑰快取的 JWT decoder。 */
@Configuration
public class GoogleJwtConfig {

    private static final String GOOGLE_ISSUER = "https://accounts.google.com";
    private static final String GOOGLE_JWK_SET_URI = "https://www.googleapis.com/oauth2/v3/certs";

    /**
     * 建立應用程式共用的 Google JWT decoder，重用其遠端 JWK cache。
     *
     * @param clientId Google OAuth Web Client ID
     * @return 驗證 Google 簽章、時間、issuer 與 audience 的 decoder
     */
    @Bean
    JwtDecoder googleJwtDecoder(@Value("${app.auth.google-client-id:}") String clientId) {
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
