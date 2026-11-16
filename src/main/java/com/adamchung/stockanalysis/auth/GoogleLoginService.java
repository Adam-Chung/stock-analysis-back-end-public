package com.adamchung.stockanalysis.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.stereotype.Service;

/** 驗證 Google ID token，並將 Google 身分轉成本地使用者。 */
@Service
public class GoogleLoginService {

    private final String clientId;
    private final AppUserService appUserService;
    private final JwtDecoder jwtDecoder;

    /**
     * 建立 Google 登入服務。
     *
     * @param clientId Google OAuth Web Client ID
     * @param appUserService 本站使用者建立與舊資料認領服務
     * @param jwtDecoder 應用程式共用的 Google token decoder
     */
    public GoogleLoginService(
            @Value("${app.auth.google-client-id:}") String clientId,
            AppUserService appUserService,
            JwtDecoder jwtDecoder) {
        this.clientId = clientId;
        this.appUserService = appUserService;
        this.jwtDecoder = jwtDecoder;
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
        Jwt jwt = jwtDecoder.decode(credential);
        if (!Boolean.TRUE.equals(jwt.getClaimAsBoolean("email_verified"))) {
            throw new IllegalArgumentException("Google 帳號的電子郵件尚未驗證");
        }
        return appUserService.provision(
                jwt.getSubject(),
                jwt.getClaimAsString("email"),
                jwt.getClaimAsString("name"),
                jwt.getClaimAsString("picture"));
    }

}
