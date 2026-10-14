package com.adamchung.stockanalysis.auth;

import com.adamchung.stockanalysis.auth.dto.LoginRequest;
import com.adamchung.stockanalysis.auth.dto.LoginResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** 提供簡易登入與登出 API。 */
@RestController
public class AuthController {

    private final SimpleAuthService authService;

    public AuthController(SimpleAuthService authService) {
        this.authService = authService;
    }

    /** 驗證帳密並建立登入 Token。 */
    @PostMapping("/api/auth/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request.username(), request.password())
                .map(LoginResponse::new)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "帳號或密碼錯誤"));
    }

    /** 刪除目前使用的登入 Token。 */
    @PostMapping("/api/auth/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@RequestHeader("Authorization") String authorization) {
        authService.logout(authorization.substring("Bearer ".length()));
    }
}

