package com.adamchung.stockanalysis.healthcheck.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

/**
 * 「更新」按鈕送出的數值＋筆記（SA-03 §4.4，DEC-030 v1 全部手動輸入）。每筆 entry 各自帶
 * {@code period}，而非整批共用單一 quarter——DEC-043 的 {@code bankRevenue.*} 細項用
 * {@code 'YYYY-MM'}，其餘細項用 {@code 'YYYYQ#'}，同一次「更新」點擊會同時送出兩種格式，
 * 需要各自對應各自的期間，不能共用一個欄位。{@code reviewedQuarter} 單純用來更新
 * {@code stock_health_profile.last_reviewed_quarter}（畫面上「資料季度」的來源），與 entries
 * 各自的 period 是兩件獨立的事。
 */
public record HealthCheckSnapshotRequest(
        @NotBlank @Size(max = 10) String reviewedQuarter, // 資料季度
        @NotEmpty @Valid List<Entry> entries // 這次更新的細項清單
) {

    public record Entry(
            @NotBlank @Size(max = 50) String metricKey, // 細項識別碼
            @NotBlank @Size(max = 10) String period, // 期別
            BigDecimal value, // 數值
            @Size(max = 5000) String note // 筆記
    ) {
    }
}
