package com.adamchung.stockanalysis.valuation.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 單一季度的 EPS，形狀對應 type='EPS' 的 stock_financial_statement 資料列。數值是單季數字，
 * 不是累計值（SA-01 DEC-15）。
 */
public record QuarterlyEps(
        LocalDate statementDate, // 財報所屬季度日期
        BigDecimal eps // 每股盈餘（單季，非累計）
) {
}
