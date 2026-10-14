package com.adamchung.stockanalysis.finmind.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * TaiwanStockInfo 資料列。FinMind 可能對同一個 stock_id 回傳多筆、industry_category
 * 不同的資料——呼叫端應該取第一筆（見 DECISIONS.md DEC-007）。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record FinMindStockInfoDto(
        @JsonProperty("industry_category") String industryCategory, // 產業別
        @JsonProperty("stock_id") String stockId, // 股票代號
        @JsonProperty("stock_name") String stockName, // 股票名稱
        @JsonProperty("type") String type // 市場別（twse/tpex）
) {
}
