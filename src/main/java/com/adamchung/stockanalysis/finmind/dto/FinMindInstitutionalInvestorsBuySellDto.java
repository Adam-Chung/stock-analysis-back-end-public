package com.adamchung.stockanalysis.finmind.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDate;

/**
 * TaiwanStockInstitutionalInvestorsBuySell 資料列，逐日、每個投資人類別一列（已對真實 API
 * 驗證，2884 樣本）。{@code name} 已確認的類別值：{@code Foreign_Investor}／
 * {@code Foreign_Dealer_Self}（合稱外資及陸資）、{@code Investment_Trust}（投信）、
 * {@code Dealer_self}／{@code Dealer_Hedging}（合稱自營商）。{@code buy}／{@code sell}
 * 單位為股數。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record FinMindInstitutionalInvestorsBuySellDto(
        @JsonProperty("date") LocalDate date, // 資料日期
        @JsonProperty("stock_id") String stockId, // 股票代號
        @JsonProperty("name") String name, // 法人類別（外資/投信/自營商）
        @JsonProperty("buy") Long buy, // 買進股數
        @JsonProperty("sell") Long sell // 賣出股數
) {
}
