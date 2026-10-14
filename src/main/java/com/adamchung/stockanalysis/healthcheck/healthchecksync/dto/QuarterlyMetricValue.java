package com.adamchung.stockanalysis.healthcheck.healthchecksync.dto;

import java.math.BigDecimal;

/**
 * 一個細項在某一期（{@code 'YYYYQ#'}，年頻細項固定存該年 Q4）的計算結果，供
 * {@link FinMindHealthCheckSyncService} upsert 進 {@code stock_health_snapshot} 使用。
 *
 * @param note 選填的系統建議筆記文字（DEC-074，例如盈餘總分配率的公式明細）；只有目前資料庫
 *             裡這期的筆記還是空白時才會寫入，不會覆蓋使用者已經存過的筆記。絕大多數細項不需要
 *             這個欄位，維持 {@code null}（見下方兩個參數的建構子）。
 */
public record QuarterlyMetricValue(String period, BigDecimal value, String note) {
    public QuarterlyMetricValue(String period, BigDecimal value) {
        this(period, value, null);
    }
}
