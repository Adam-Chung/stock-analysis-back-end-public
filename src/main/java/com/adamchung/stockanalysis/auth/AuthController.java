package com.adamchung.stockanalysis.auth;

import com.adamchung.stockanalysis.auth.dto.AuthUserResponse;
import com.adamchung.stockanalysis.auth.dto.GoogleLoginRequest;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.beans.factory.annotation.Value;

import java.util.Map;

/** 提供 Google 登入、目前使用者與登出 API。 */
@RestController
public class AuthController {

    private final GoogleLoginService googleLoginService;
    private final String googleClientId;
    private final AppUserService appUserService;

    /**
     * 建立登入 HTTP 邊界。
     *
     * @param googleLoginService Google ID token 驗證與使用者建立服務
     * @param appUserService 本站帳號與個人資料生命週期服務
     * @param googleClientId 提供前端初始化 Google 官方登入元件的公開 Client ID
     */
    public AuthController(
            GoogleLoginService googleLoginService,
            AppUserService appUserService,
            @Value("${app.auth.google-client-id:}") String googleClientId) {
        this.googleLoginService = googleLoginService;
        this.appUserService = appUserService;
        this.googleClientId = googleClientId;
    }

    /**
     * 回傳 Google Identity Services 初始化所需的公開 Client ID；它不是秘密憑證。
     *
     * @return 公開登入設定
     */
    @GetMapping("/api/auth/config")
    public Map<String, String> config() {
        return Map.of("googleClientId", googleClientId);
    }

    /**
     * 讓前端先取得 CSRF cookie；回傳 token 也可供非 Angular 用戶端使用。
     *
     * @param csrfToken Spring Security 產生的 CSRF token
     * @return CSRF token
     */
    @GetMapping("/api/auth/csrf")
    public CsrfToken csrf(CsrfToken csrfToken) {
        return csrfToken;
    }

    /**
     * 驗證 Google Credential，建立本站使用者並寫入 30 天閒置逾時的資料庫 session。
     *
     * @param request Google Identity Services 回傳的 ID token
     * @param servletRequest HTTP request，用於建立 session
     * @return 已登入的使用者資料
     */
    @PostMapping("/api/auth/google")
    public AuthUserResponse login(
            @Valid @RequestBody GoogleLoginRequest request,
            HttpServletRequest servletRequest) {
        AppUserPrincipal principal = googleLoginService.login(request.credential());
        UsernamePasswordAuthenticationToken authentication =
                UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.authorities());
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        servletRequest.getSession(true).setAttribute(
                HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);
        return AuthUserResponse.from(principal);
    }

    /**
     * 回傳目前登入者，供前端重新整理後恢復畫面狀態。
     *
     * @return 目前登入者
     */
    @GetMapping("/api/auth/me")
    public AuthUserResponse me() {
        return AuthUserResponse.from(CurrentUser.require());
    }

    /**
     * 清除伺服器 session 與安全上下文。
     *
     * @param request HTTP request
     */
    @PostMapping("/api/auth/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(HttpServletRequest request) {
        SecurityContextHolder.clearContext();
        if (request.getSession(false) != null) {
            request.getSession(false).invalidate();
        }
    }

    /**
     * 永久刪除目前帳號及個人化資料，並立即使目前 session 失效。
     *
     * @param request HTTP request
     */
    @DeleteMapping("/api/account")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteAccount(HttpServletRequest request) {
        appUserService.deleteAccount(CurrentUser.id());
        SecurityContextHolder.clearContext();
        if (request.getSession(false) != null) {
            request.getSession(false).invalidate();
        }
    }
}

