package com.adamchung.stockanalysis.healthcheck.healthchecksync.dto;

import java.math.BigDecimal;

/**
 * 單一股票、單一季度，從 TaiwanStockFinancialStatements／TaiwanStockBalanceSheet／
 * TaiwanStockCashFlowsStatement 三個 dataset 合併出的原始欄位（SA-04）。任何欄位都可能是
 * {@code null}——單季缺某個 type 不能讓整批同步失敗，只影響用到該欄位的細項算不出來
 * （見 {@link HealthCheckFinancialMetricsCalculator}）。
 *
 * <p>已對真實 API 驗證：銀行模板（例如 2884）的 BalanceSheet 沒有 {@code CapitalStock}
 * （改用 {@code OrdinaryShare}）、FinancialStatements 沒有 {@code IncomeAfterTaxes}
 * （改用單數的 {@code IncomeAfterTax}）、也完全沒有 {@code OperatingIncome}／
 * {@code GrossProfit}／{@code CurrentAssets} 等一般模板欄位；{@link QuarterlyFactsJoiner}
 * 已處理前兩個欄位名稱別名，其餘欄位對銀行模板來說本來就是「用不到、缺就缺」。
 */
public record QuarterlyFinancialFacts(
        String quarter, // 季度
        BigDecimal revenue, // 營收
        BigDecimal epsQuarter, // 單季EPS
        BigDecimal operatingIncome, // 營業利益
        BigDecimal preTaxIncome, // 稅前淨利
        BigDecimal incomeAfterTaxes, // 稅後淨利
        BigDecimal otherComprehensiveIncome, // 其他綜合損益
        BigDecimal grossProfit, // 毛利
        BigDecimal accountsReceivableNet, // 應收帳款淨額
        BigDecimal inventories, // 存貨
        BigDecimal liabilities, // 負債總額
        BigDecimal totalAssets, // 資產總額
        BigDecimal currentAssets, // 流動資產
        BigDecimal currentLiabilities, // 流動負債
        BigDecimal capitalStock, // 股本
        BigDecimal equity, // 股東權益
        BigDecimal operatingCashFlow, // 營業活動現金流
        BigDecimal capex, // 資本支出
        BigDecimal interestExpense // 利息費用
) {
}
