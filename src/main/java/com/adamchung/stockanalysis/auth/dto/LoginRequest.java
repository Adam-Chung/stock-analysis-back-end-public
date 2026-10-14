package com.adamchung.stockanalysis.auth.dto;

import jakarta.validation.constraints.NotBlank;

/** 簡易登入資料。 */
public record LoginRequest(
        @NotBlank String username, // 帳號
        @NotBlank String password // 明碼密碼；僅供目前臨時防護
) {
}

