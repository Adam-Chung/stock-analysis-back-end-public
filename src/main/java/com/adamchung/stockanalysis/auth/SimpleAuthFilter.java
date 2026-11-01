package com.adamchung.stockanalysis.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/** 驗證所有受保護 API 的 Bearer Token。 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class SimpleAuthFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";
    private static final String LIVENESS_PATH = "/api/health/liveness";
    private final SimpleAuthService authService;
    private final boolean authEnabled;

    public SimpleAuthFilter(SimpleAuthService authService, @Value("${app.auth.enabled:false}") boolean authEnabled) {
        this.authService = authService;
        this.authEnabled = authEnabled;
    }

    /** 登入功能停用時略過全部驗證；啟用時放行登入、存活檢查、非 API 路徑及 CORS 預檢。 */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !authEnabled
                || !request.getRequestURI().startsWith("/api/")
                || request.getRequestURI().equals("/api/auth/login")
                || request.getRequestURI().equals(LIVENESS_PATH)
                || request.getMethod().equals("OPTIONS");
    }

    /** Token 無效時直接回傳 401，不讓請求進入 Controller。 */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String authorization = request.getHeader("Authorization");
        if (authorization == null || !authorization.startsWith(BEARER_PREFIX)
                || !authService.isValid(authorization.substring(BEARER_PREFIX.length()))) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "請先登入");
            return;
        }
        chain.doFilter(request, response);
    }
}
