package com.adamchung.stockanalysis.valuation.dto;

import java.math.BigDecimal;

/**
 * 一個完整曆年的 PER/PBR/殖利率統計。{@code avgPer}/{@code avgPbr} 等欄位形狀對應 DB-01 的
 * stock_valuation_yearly；{@code avgDividendYield} 不是該表欄位，是請求當下用
 * {@link DividendYieldCalculator} 即時算出來、專供 {@link TargetPriceValuationService} 使用的
 * 計算輸入。
 */
public record YearlyValuationStats(
        int year, // 年度
        BigDecimal avgPer, // 年平均本益比
        BigDecimal lowPer, // 年最低本益比
        BigDecimal highPer, // 年最高本益比
        BigDecimal avgPbr, // 年平均股價淨值比
        BigDecimal lowPbr, // 年最低股價淨值比
        BigDecimal highPbr, // 年最高股價淨值比
        BigDecimal avgDividendYield // 年平均殖利率（即時計算，非DB欄位）
) {
}
