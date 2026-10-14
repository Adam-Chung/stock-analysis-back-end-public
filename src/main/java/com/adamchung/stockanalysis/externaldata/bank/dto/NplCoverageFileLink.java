package com.adamchung.stockanalysis.externaldata.bank.dto;

/** 銀行局揭露頁面上一份「本國銀行資產品質評估分析統計表」xlsx 檔案連結。 */
public record NplCoverageFileLink(
        String url, // 檔案下載連結
        int rocYear, // 民國年
        int rocMonth // 月份
) {
}
