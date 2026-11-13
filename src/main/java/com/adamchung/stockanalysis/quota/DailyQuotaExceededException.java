package com.adamchung.stockanalysis.quota;

/** 使用者或全站的免費外部服務每日額度已滿。 */
public class DailyQuotaExceededException extends RuntimeException {

    /**
     * 建立可安全顯示給前端的超限錯誤。
     *
     * @param message 額度說明
     */
    public DailyQuotaExceededException(String message) {
        super(message);
    }
}
