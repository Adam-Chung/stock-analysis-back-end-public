package com.adamchung.stockanalysis.finmind.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * TaiwanStockMonthRevenue 資料列（已對真實 API 驗證，2330 樣本）。{@code revenue} 是新台幣
 * 原始金額（非億元），{@code revenueMonth}／{@code revenueYear} 是這筆營收實際所屬的西元年月
 * （{@code date} 欄位固定回傳當月1號，不能拿來判斷實際月份）。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record FinMindMonthRevenueDto(
        @JsonProperty("stock_id") String stockId, // 股票代號
        @JsonProperty("country") String country, // 國別
        @JsonProperty("revenue") Long revenue, // 月營收（新台幣原始金額）
        @JsonProperty("revenue_month") Integer revenueMonth, // 營收所屬月份
        @JsonProperty("revenue_year") Integer revenueYear // 營收所屬年份
) {
}
