package com.adamchung.stockanalysis.auth;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** 登入與帳號管理的穩定 Problem Details 錯誤回應。 */
@RestControllerAdvice(basePackageClasses = AuthController.class)
public class AuthExceptionHandler {

    /** Google token 無效或過期時回傳 401，不回傳底層驗證細節。 */
    @ExceptionHandler(JwtException.class)
    ProblemDetail handleInvalidGoogleToken() {
        return problem(HttpStatus.UNAUTHORIZED, "GOOGLE_TOKEN_INVALID", "Google 登入憑證無效或已過期");
    }

    /** 管理者停用的帳號回傳 403。 */
    @ExceptionHandler(DisabledAccountException.class)
    ProblemDetail handleDisabledAccount(DisabledAccountException exception) {
        return problem(HttpStatus.FORBIDDEN, "ACCOUNT_DISABLED", exception.getMessage());
    }

    /** 管理操作指定不存在的帳號時回傳 404。 */
    @ExceptionHandler(AppUserNotFoundException.class)
    ProblemDetail handleUserNotFound(AppUserNotFoundException exception) {
        return problem(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", exception.getMessage());
    }

    /** Google Client ID 未部署時回傳 503，避免被誤認為使用者登入失敗。 */
    @ExceptionHandler(IllegalStateException.class)
    ProblemDetail handleConfiguration(IllegalStateException exception) {
        return problem(HttpStatus.SERVICE_UNAVAILABLE, "AUTH_NOT_CONFIGURED", exception.getMessage());
    }

    /** 信箱未驗證或管理操作不合法時回傳 400。 */
    @ExceptionHandler(IllegalArgumentException.class)
    ProblemDetail handleBadRequest(IllegalArgumentException exception) {
        return problem(HttpStatus.BAD_REQUEST, "AUTH_REQUEST_INVALID", exception.getMessage());
    }

    /** 建立一致的 RFC 9457 回應。 */
    private ProblemDetail problem(HttpStatus status, String code, String message) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(status, message);
        detail.setTitle(status.getReasonPhrase());
        detail.setProperty("code", code);
        return detail;
    }
}
