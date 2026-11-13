package com.adamchung.stockanalysis.auth;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.io.Serializable;
import java.util.List;

/** 儲存在 Spring Session 的最小本站使用者身分。 */
public record AppUserPrincipal(
        long userId, // 本站 user id
        String email, // Google 已驗證電子郵件
        String displayName, // 顯示名稱
        String pictureUrl, // 頭像網址
        boolean admin // 管理者旗標
) implements Serializable {

    /**
     * 轉成 Spring Security 權限清單。
     *
     * @return USER，管理者另含 ADMIN
     */
    public List<GrantedAuthority> authorities() {
        return admin
                ? List.of(new SimpleGrantedAuthority("ROLE_USER"), new SimpleGrantedAuthority("ROLE_ADMIN"))
                : List.of(new SimpleGrantedAuthority("ROLE_USER"));
    }
}
