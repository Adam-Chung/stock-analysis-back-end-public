package com.adamchung.stockanalysis.auth.dto;

import jakarta.validation.constraints.NotBlank;

/** Google Identity Services 登入請求。 */
public record GoogleLoginRequest(
        @NotBlank String credential // Google 簽發的 ID token，不是 access token
) {
}
