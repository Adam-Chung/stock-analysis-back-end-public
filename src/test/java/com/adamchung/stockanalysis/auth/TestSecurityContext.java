package com.adamchung.stockanalysis.auth;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

/** 單元測試建立已驗證使用者的共用工具。 */
public final class TestSecurityContext {

    private TestSecurityContext() {
    }

    /** 將指定 user id 放入目前測試執行緒的 SecurityContext。 */
    public static void login(long userId) {
        AppUserPrincipal principal = new AppUserPrincipal(userId, "user@example.com", "User", null, false);
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.authorities()));
    }
}
