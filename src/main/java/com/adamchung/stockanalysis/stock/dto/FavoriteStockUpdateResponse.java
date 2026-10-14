package com.adamchung.stockanalysis.stock.dto;

import java.math.BigDecimal;

/** 我的最愛區結果。 */
public record FavoriteStockUpdateResponse(
        String stockId, // 股票代號
        String stockName, // 股票名稱
        BigDecimal latestPrice, // 最新股價
        BigDecimal targetPrice, // 目標股價
        String targetPriceLabel, // 目標價等級名稱
        BigDecimal gapRatio, // 最新股價相對目標股價的差距比例
        String latestDataQuarter, // FinMind最新財報季度
        String investmentStrategy, // 股票目標價頁面的投資策略
        String errorMessage // 單檔更新失敗訊息；成功時為null
) {
}
