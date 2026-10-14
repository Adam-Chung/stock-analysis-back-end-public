package com.adamchung.stockanalysis.healthcheck.definition;

import java.math.BigDecimal;
import com.adamchung.stockanalysis.healthcheck.enums.HealthCheckEvalType;

/**
 * 一個檢視細項的定義（SA-04）。定義本身寫死在程式碼，資料庫只存 {@code key} 對應的實際數值
 * （DEC-039）——{@code key} 必須全域唯一，對應 {@code stock_health_snapshot.metric_key}。
 *
 * @param key             全域唯一識別碼，格式 {@code 大項.細項}（例如 {@code "debt.currentRatio"}）
 * @param name             畫面顯示名稱（逐字取自 SA-04／Excel）
 * @param unit             單位（例如 "%"、"億"、"倍"、"元"、"張"），JUDGMENT 類允許為空字串
 * @param evalType         達標判定方式
 * @param threshold        門檻值；{@code evalType} 為 {@code JUDGMENT} 或 {@code BOOLEAN} 時為 {@code null}
 * @param thresholdLabel   畫面顯示用的門檻說明文字覆寫；{@code null} 時由前端依 threshold 自行組字
 * @param isAnnual         這個細項的每一期資料代表「一整年」而非「一季」（DEC-066）——資料庫仍統一
 *                         存成 {@code 'YYYYQ4'} 格式（沿用既有排序慣例），但前端顯示期間標籤時
 *                         只顯示年份（例如「2025」），不顯示「2025Q4」，避免使用者誤以為這是
 *                         季度資料
 * @param autofillNoteWithValue 這個細項的筆記欄位，在使用者還沒存過任何筆記時，預設帶入該期
 *                         已算好的數值（格式化後）當起始文字，方便使用者直接在數字後面補充判斷
 *                         依據，不用自己抄一次數字；一旦使用者存過筆記（即使存成空字串），
 *                         之後一律顯示實際存的筆記，不再覆蓋。目前只有「盈餘總分配率」設為
 *                         true——後端明確宣告，不是前端猜測（比照 {@link #isAnnual} 的做法）
 */
public record HealthCheckMetricDefinition(
        String key,
        String name,
        String unit,
        HealthCheckEvalType evalType,
        BigDecimal threshold,
        String thresholdLabel,
        boolean isAnnual,
        boolean autofillNoteWithValue) {
}
