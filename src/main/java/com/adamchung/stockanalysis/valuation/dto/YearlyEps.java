package com.adamchung.stockanalysis.valuation.dto;

import java.math.BigDecimal;

/** 年度 EPS ＝ 該年度四季數字加總（SA-01 DEC-15）。 */
public record YearlyEps(
        int year, // 年度
        BigDecimal eps // 該年度EPS（四季加總）
) {
}
