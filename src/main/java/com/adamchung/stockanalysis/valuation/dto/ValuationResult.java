package com.adamchung.stockanalysis.valuation.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * SA-01 3.3 的輸出結果。{@code yearsToRecover == null} 代表「0／已達成」（DEC-1）；
 * {@code yearsOfProfitIfSold == null} 代表「尚未獲利」（DEC-7）；
 * {@code frontYear == null} 代表10年窗口內從未有任何一年收在目標價之下。
 */
public record ValuationResult(
        BigDecimal low10, // 10年低
        BigDecimal high10, // 10年高
        BigDecimal tenYearAvgYield, // 10年平均殖利率
        List<TierPrice> tiers, // 分級價格
        BigDecimal targetPrice, // 目標價
        BigDecimal gapRatio, // 差距比率
        BigDecimal plusOneYearDividendPrice, // 加一年殖利率
        BigDecimal yearsToRecover, // 需要多少年才能回收
        BigDecimal yearsOfProfitIfSold, // 需要多少年才能獲利
        Integer frontYear, // 首次低於目標價的年份
        ValuationDataCoverage dataCoverage // 資料覆蓋率
) {
}
