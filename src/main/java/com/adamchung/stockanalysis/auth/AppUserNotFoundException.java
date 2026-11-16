package com.adamchung.stockanalysis.auth;

/** 管理操作指定的本站使用者不存在。 */
public class AppUserNotFoundException extends RuntimeException {

    /**
     * 建立不包含個人資料的安全錯誤訊息。
     *
     * @param userId 找不到的本站使用者 id
     */
    public AppUserNotFoundException(long userId) {
        super("找不到使用者：" + userId);
    }
}
