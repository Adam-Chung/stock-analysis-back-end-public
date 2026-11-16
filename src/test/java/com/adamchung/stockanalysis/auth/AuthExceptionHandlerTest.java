package com.adamchung.stockanalysis.auth;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

import static org.assertj.core.api.Assertions.assertThat;

/** 驗證登入與帳號管理的穩定錯誤契約。 */
class AuthExceptionHandlerTest {

    private final AuthExceptionHandler handler = new AuthExceptionHandler();

    /** 不存在的管理帳號必須回傳 404 與穩定錯誤代碼。 */
    @Test
    void handleUserNotFound_returnsStableNotFoundProblem() {
        ProblemDetail detail = handler.handleUserNotFound(new AppUserNotFoundException(999L));

        assertThat(detail.getStatus()).isEqualTo(HttpStatus.NOT_FOUND.value());
        assertThat(detail.getDetail()).isEqualTo("找不到使用者：999");
        assertThat(detail.getProperties()).containsEntry("code", "USER_NOT_FOUND");
    }
}
