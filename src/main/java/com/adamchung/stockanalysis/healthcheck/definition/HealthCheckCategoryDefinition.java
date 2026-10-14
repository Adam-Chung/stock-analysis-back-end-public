package com.adamchung.stockanalysis.healthcheck.definition;

import java.util.List;

/**
 * 一個檢視大項的定義，底下包含一個或多個細項（SA-04）。
 *
 * @param key                  全域唯一識別碼（例如 {@code "debt"}）
 * @param name                 大項名稱（例如「欠錢壓力」）
 * @param thresholdDescription 大項層級的門檻摘要文字，顯示在 category 標題旁
 * @param metrics              底下的細項定義，依畫面顯示順序排列
 * @param historyLimit         畫面顯示近幾期歷史（多數大項是「近五季」＝5；
 *                             {@code bankRevenue} 是月頻「近三年」＝36）
 */
public record HealthCheckCategoryDefinition(
        String key,
        String name,
        String thresholdDescription,
        List<HealthCheckMetricDefinition> metrics,
        int historyLimit) {
}
