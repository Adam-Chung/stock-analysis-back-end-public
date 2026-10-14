package com.adamchung.stockanalysis.valuation.dto;

import java.math.BigDecimal;

/** 一個完整曆年的股價統計，形狀對應 DB-01 的 stock_price_yearly。 */
public record YearlyPriceStats(
        int year, // 年度
        BigDecimal avgClosePrice, // 年均收盤價
        BigDecimal highPrice, // 年最高價
        BigDecimal lowPrice // 年最低價
) {
}
