package com.adamchung.stockanalysis.stock.dto;

/** 最精簡的股票識別資訊，搜尋結果與我的最愛清單都共用這個型別。 */
public record StockSummary(
        String stockId, // 股票代號
        String stockName, // 股票名稱
        String industryCategory // 產業別
) {
}
