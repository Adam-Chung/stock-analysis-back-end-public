package com.adamchung.stockanalysis.auth.dto;

/** 登入成功後回傳的永久 Token。 */
public record LoginResponse(
        String token // 前端後續呼叫 API 使用的 Bearer Token
) {
}

