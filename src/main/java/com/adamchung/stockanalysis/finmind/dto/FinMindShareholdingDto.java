package com.adamchung.stockanalysis.finmind.dto;

import com.adamchung.stockanalysis.finmind.deserializer.BlankAsNullBigDecimalDeserializer;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import tools.jackson.databind.annotation.JsonDeserialize;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * TaiwanStockShareholding 資料列，逐日（已對真實 API 驗證，2884 樣本）。這個 dataset 實際
 * 回傳的欄位遠不只這裡列出的，用 {@code @JsonIgnoreProperties} 只保留體質檢視「外資持股(%)」
 * 用得到的 {@code ForeignInvestmentSharesRatio}。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record FinMindShareholdingDto(
        @JsonProperty("date") LocalDate date, // 資料日期
        @JsonProperty("stock_id") String stockId, // 股票代號
        @JsonProperty("ForeignInvestmentSharesRatio")
        @JsonDeserialize(using = BlankAsNullBigDecimalDeserializer.class) BigDecimal foreignInvestmentSharesRatio // 外資持股比率(%)
) {
}
