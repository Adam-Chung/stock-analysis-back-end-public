package com.adamchung.stockanalysis.auth;

/** 管理者已停用 Google 帳號對應的本站使用者。 */
public class DisabledAccountException extends RuntimeException {

    /** 建立不洩漏帳號細節的停用錯誤。 */
    public DisabledAccountException() {
        super("此帳號已停用");
    }
}
