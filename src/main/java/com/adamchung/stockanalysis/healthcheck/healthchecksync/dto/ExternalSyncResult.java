package com.adamchung.stockanalysis.healthcheck.healthchecksync.dto;

import java.util.List;

/**
 * 一次外部資料同步實際寫入了哪些 metric_key（供呼叫端確認這次同步涵蓋範圍）。
 *
 * @param warnings 個別來源失敗時的說明（DEC-053：TWSE/銀行局來源與 FinMind 來源各自獨立呼叫，
 *                 任一來源成功就回 200，失敗的來源記錄在這裡，不影響另一來源已同步的結果）。
 *                 兩個來源都成功時是空清單，不是 {@code null}。
 */
public record ExternalSyncResult(String stockId, List<String> syncedMetricKeys, List<String> warnings) {
}
