package com.adamchung.stockanalysis.stock.dto;

/** 手動觸發股票目錄同步的結果摘要。 */
public record StockDirectorySyncResponse(
        int syncedCount // 同步的股票數量
) {
}
