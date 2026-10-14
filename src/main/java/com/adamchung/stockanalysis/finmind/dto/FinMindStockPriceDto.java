package com.adamchung.stockanalysis.finmind.dto;

import com.adamchung.stockanalysis.finmind.deserializer.BlankAsNullBigDecimalDeserializer;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import tools.jackson.databind.annotation.JsonDeserialize;

import java.math.BigDecimal;
import java.time.LocalDate;

/** TaiwanStockPrice 資料列（已於 2026-07-29 對真實 API 驗證過）。 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record FinMindStockPriceDto(
        @JsonProperty("date") LocalDate date, // 交易日期
        @JsonProperty("stock_id") String stockId, // 股票代號
        @JsonProperty("Trading_Volume") Long tradingVolume, // 成交股數
        @JsonProperty("Trading_money") Long tradingMoney, // 成交金額
        @JsonProperty("open") @JsonDeserialize(using = BlankAsNullBigDecimalDeserializer.class) BigDecimal open, // 開盤價
        @JsonProperty("max") @JsonDeserialize(using = BlankAsNullBigDecimalDeserializer.class) BigDecimal max, // 最高價
        @JsonProperty("min") @JsonDeserialize(using = BlankAsNullBigDecimalDeserializer.class) BigDecimal min, // 最低價
        @JsonProperty("close") @JsonDeserialize(using = BlankAsNullBigDecimalDeserializer.class) BigDecimal close, // 收盤價
        @JsonProperty("spread") @JsonDeserialize(using = BlankAsNullBigDecimalDeserializer.class) BigDecimal spread, // 漲跌價差
        @JsonProperty("Trading_turnover") Long tradingTurnover // 成交筆數
) {
}
