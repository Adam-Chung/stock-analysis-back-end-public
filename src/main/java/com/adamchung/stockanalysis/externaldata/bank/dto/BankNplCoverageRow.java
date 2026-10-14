package com.adamchung.stockanalysis.externaldata.bank.dto;

/** 本國銀行逾期放款比率／備抵呆帳覆蓋率（銀行局資產品質評估分析統計表）。 */
public record BankNplCoverageRow(
        String bankName, // 銀行名稱
        /** 例如 {@code "115年3月"}（民國年）。 */
        String period,
        double overdueLoanRatioPercent, // 逾期放款比率(%)
        double provisionCoverageRatioPercent, // 備抵呆帳覆蓋率(%)
        String sourceUrl // 資料來源網址
) {
}
