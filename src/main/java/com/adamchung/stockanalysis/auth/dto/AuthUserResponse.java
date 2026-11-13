package com.adamchung.stockanalysis.auth.dto;

import com.adamchung.stockanalysis.auth.AppUserPrincipal;

/** 前端需要的最小登入者資料，不回傳 Google subject。 */
public record AuthUserResponse(
        long id, // 本站使用者識別碼
        String email, // Google 已驗證的電子郵件
        String displayName, // Google 顯示名稱
        String pictureUrl, // Google 個人圖片網址
        boolean admin // 是否具有管理者權限
) {

    /**
     * 由安全 principal 建立 API 回應。
     *
     * @param principal 已驗證的本站使用者
     * @return 安全的使用者摘要
     */
    public static AuthUserResponse from(AppUserPrincipal principal) {
        return new AuthUserResponse(
                principal.userId(), principal.email(), principal.displayName(), principal.pictureUrl(), principal.admin());
    }
}
