package com.adamchung.stockanalysis.stock.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/** 由日成交資料聚合而成的一根週 K 棒。 */
public record WeeklyPriceCandleResponse(
        LocalDate weekStart, // 該週週一日期
        BigDecimal open, // 該週第一個交易日開盤價
        BigDecimal high, // 該週最高價
        BigDecimal low, // 該週最低價
        BigDecimal close // 該週最後一個交易日收盤價
) {
}
