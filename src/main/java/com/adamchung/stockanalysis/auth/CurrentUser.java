package com.adamchung.stockanalysis.auth;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

/** 取得目前 HTTP 請求已驗證的使用者。 */
public final class CurrentUser {

    private CurrentUser() {
    }

    /**
     * 取得目前使用者；匿名或錯誤 principal 一律拒絕，避免資料查詢漏掉 user 維度。
     *
     * @return 已驗證使用者
     */
    public static AppUserPrincipal require() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken
                || !(authentication.getPrincipal() instanceof AppUserPrincipal principal)) {
            throw new UsernameNotFoundException("請先登入");
        }
        return principal;
    }

    /**
     * 取得目前使用者 id。
     *
     * @return 本站 user id
     */
    public static long id() {
        return require().userId();
    }
}
