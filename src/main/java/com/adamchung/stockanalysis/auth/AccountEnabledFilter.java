package com.adamchung.stockanalysis.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/** 每次已登入請求確認帳號仍啟用，讓管理者停用立即生效。 */
@Component
public class AccountEnabledFilter extends OncePerRequestFilter {

    private final JdbcClient jdbcClient;

    /**
     * 建立帳號狀態 filter。
     *
     * @param jdbcClient 資料庫操作邊界
     */
    public AccountEnabledFilter(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    /** 已驗證使用者不存在或停用時清除 session 並回傳 401。 */
    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        Object principal = org.springframework.security.core.context.SecurityContextHolder.getContext()
                .getAuthentication() == null ? null
                : org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (principal instanceof AppUserPrincipal user && !isEnabled(user.userId())) {
            if (request.getSession(false) != null) {
                request.getSession(false).invalidate();
            }
            org.springframework.security.core.context.SecurityContextHolder.clearContext();
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "此帳號已停用");
            return;
        }
        filterChain.doFilter(request, response);
    }

    /** 查詢目前帳號是否仍存在且啟用。 */
    private boolean isEnabled(long userId) {
        return jdbcClient.sql("SELECT enabled FROM app_user WHERE id=:userId")
                .param("userId", userId).query(Boolean.class).optional().orElse(false);
    }
}
