package com.adamchung.stockanalysis.healthcheck.healthchecksync.dto;

import java.util.List;

/**
 * {@link FinMindHealthCheckSyncService#sync} 的結果，供上層 controller 合併進整體同步回應。
 *
 * @param latestDataQuarter 這次同步抓到的財報三表聯集中，最新一筆有資料的 {@code 'YYYYQ#'}
 *                          季度（{@code facts.get(0).quarter()}，新到舊排序取第一筆）；三表
 *                          完全沒有任何資料時為 {@code null}（DEC-067，供「資料季度」欄位使用，
 *                          取代原本寫死 {@code LocalDate.now()} 推算的當前曆季）
 */
public record FinMindHealthCheckSyncResult(String stockId, List<String> syncedMetricKeys, String latestDataQuarter) {
}
