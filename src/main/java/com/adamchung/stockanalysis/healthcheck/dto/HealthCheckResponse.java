package com.adamchung.stockanalysis.healthcheck.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import com.adamchung.stockanalysis.healthcheck.enums.HealthCheckEvalType;
import com.adamchung.stockanalysis.healthcheck.enums.HealthCheckStatus;
import com.adamchung.stockanalysis.healthcheck.enums.HealthCheckTemplate;

/** SA-03 企業體質檢視畫面的完整回應。 */
public record HealthCheckResponse(
        String stockId, // 股票代號
        String stockName, // 股票名稱
        String industryCategory, // 產業別
        HealthCheckTemplate template, // 檢視模板（一般/銀行）
        BigDecimal currentPrice, // 最新收盤價
        ProfileView profile, // 公司資訊區塊
        List<CategoryView> categories, // 檢視大項清單
        /** 銀行模板「股價變化（近10年年均價/PBR/PER）」；一般模板固定為空清單（DEC-031）。 */
        List<YearlyPriceTrendView> priceTrend, // 銀行模板股價趨勢清單
        AiSuggestionView aiSuggestion // AI投資建議內容快取（DEC-068 後續實作）
) {

    /** 頁面載入時顯示的快取 AI 建議；產生邏輯屬於 {@code HealthCheckAiSuggestionService}。 */
    public record AiSuggestionView(
            String text, // AI建議文字內容
            Instant updatedAt // 產生/更新時間
    ) {
    }

    public record ProfileView(
            String profile, // 公司概況
            String customNote, // 自訂備註
            String lastReviewedQuarter, // 最後複核季度
            Instant updatedAt // 更新時間
    ) {
    }

    public record CategoryView(
            String key, // 識別碼
            String name, // 大項名稱
            String thresholdDescription, // 門檻摘要
            List<MetricView> metrics // 細項清單
    ) {
    }

    public record MetricView(
            String key, // 細項識別碼
            String name, // 畫面顯示名稱
            String unit, // 單位（%、億、倍、元、張）
            HealthCheckEvalType evalType, // 達標判定方式
            BigDecimal threshold, // 門檻值
            String thresholdLabel, // 門檻說明文字覆寫
            /** 這個細項每一期代表一整年、前端只顯示年份不顯示「Q4」（DEC-066）。 */
            boolean isAnnual, // 是否為年頻細項
            /** 筆記欄位尚未存過任何內容時，預設帶入已算好的數值當起始文字，見 {@link HealthCheckMetricDefinition#autofillNoteWithValue}。 */
            boolean autofillNoteWithValue, // 是否用計算值預填筆記
            List<PeriodValueView> periods // 各期數值清單
    ) {
    }

    /** 一期（季或月）的數值＋即時計算的達標狀態＋筆記。 */
    public record PeriodValueView(
            String period, // 期別
            BigDecimal value, // 數值
            HealthCheckStatus status, // 達標狀態
            String note // 筆記
    ) {
    }

    public record YearlyPriceTrendView(
            Integer year, // 年度
            BigDecimal avgClosePrice, // 年均收盤價
            BigDecimal avgPer, // 年均本益比
            BigDecimal avgPbr // 年均股價淨值比
    ) {
    }
}
