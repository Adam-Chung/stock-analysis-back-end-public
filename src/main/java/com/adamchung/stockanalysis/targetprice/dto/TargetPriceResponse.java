package com.adamchung.stockanalysis.targetprice.dto;

import com.adamchung.stockanalysis.valuation.dto.TierPrice;
import com.adamchung.stockanalysis.valuation.dto.ValuationDataCoverage;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** SA-01 目標價畫面所需的完整回應內容。 */
public record TargetPriceResponse(
        String stockId, // 股票代號
        String stockName, // 股票名稱
        String industryCategory, // 產業別
        BigDecimal currentPrice, // 最新收盤價
        LocalDate currentPriceDate, // 最新收盤價日期
        BigDecimal low10, // 10年低價
        BigDecimal high10, // 10年高價
        BigDecimal tenYearAvgYield, // 10年平均殖利率
        LocalDate dividendDate, // 股利日期
        Instant lastUpdated, // 最後更新時間
        List<TierPrice> tiers, // 六檔目標價分級清單
        BigDecimal targetPrice, // 使用者選定等級的目標價
        BigDecimal gapRatio, // 現價與目標價的差距比率
        BigDecimal plusOneYearDividendPrice, // 加計次一年股利後的價格
        BigDecimal yearsToRecover, // 回本所需年數
        BigDecimal yearsOfProfitIfSold, // 賣出可獲利的年數
        Integer frontYear, // 最近一次低於目標價的年份
        String investmentStrategy, // 投資策略說明
        int epsAvgYears, // EPS平均採計年數
        int bpsAvgYears, // BPS平均採計年數
        String settingNote, // 設定備註
        int targetPriceLevel, // 使用者選擇的目標價等級
        ValuationDataCoverage dataCoverage, // 估值資料涵蓋年度數
        AiSuggestionView aiSuggestion, // AI建議內容快取
        List<YearlyLowPrice> yearlyLowPrices // 各年度最低價清單
) {

    /** 頁面載入時顯示的快取 AI 建議；產生邏輯屬於 TASK-008。 */
    public record AiSuggestionView(
            String text, // AI建議文字內容
            Instant updatedAt // 產生/更新時間
    ) {
    }

    /**
     * 單一年度的最低價，依年份新到舊排序，供前端在使用者改變「目標價選擇」下拉選單時，
     * 不必重新呼叫後端即可即時重算「前目標價出現」。若當年度（尚未結束）有即時最低價，
     * 會以當前曆年當作清單第一筆——跟後端算 frontYear 的邏輯一致（見
     * TargetPriceValuationService#findFrontYear）。
     */
    public record YearlyLowPrice(
            int year, // 年度
            BigDecimal lowPrice // 該年度最低價
    ) {
    }
}
