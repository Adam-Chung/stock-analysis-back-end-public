package com.adamchung.stockanalysis.valuation.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * {@link TargetPriceValuationService} 計算單一股票 SA-01 3.3 欄位所需的全部輸入。清單欄位
 * 預期都已經是「只有完整曆年」（DB-01 DEC-24／SA-01 DEC-9）——即使如此，服務內部仍會防禦性地
 * 重新排序並限制在最近10年。
 *
 * {@code currentYearHighPrice}/{@code currentYearLowPrice} 是唯一的例外：當年度（尚未結束）
 * 至今的即時最高/最低，只會併入10年高/10年低（不影響其他計算——PER/PBR/EPS/BPS 平均依然只用
 * 完整年度），這樣今年創的新高/新低才不會要等到年度結束、落地進 stock_price_yearly 後才看得到。
 * 當年度尚未同步過股價資料時為 null。
 */
public record ValuationInput(
        BigDecimal currentPrice, // 最新收盤價
        int epsAvgYears, // EPS平均年數
        int bpsAvgYears, // BPS平均年數
        int targetPriceLevel, // 目標價等級
        List<YearlyPriceStats> yearlyPrices, // 年度價格統計
        List<YearlyValuationStats> yearlyValuations, // 年度估值統計
        List<YearlyEps> yearlyEps, // 年度EPS統計
        BigDecimal currentYearHighPrice, // 當年度最高價
        BigDecimal currentYearLowPrice // 當年度最低價
) {
}
