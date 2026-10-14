package com.adamchung.stockanalysis.finmind.dto;

import com.adamchung.stockanalysis.finmind.deserializer.BlankAsNullBigDecimalDeserializer;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import tools.jackson.databind.annotation.JsonDeserialize;

import java.math.BigDecimal;
import java.time.LocalDate;

/** TaiwanStockPER 資料列——PER、PBR 與 dividend_yield 都是 FinMind 官方欄位（SA-01 DEC-11）。 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record FinMindStockValuationDto(
        @JsonProperty("date") LocalDate date, // 資料日期
        @JsonProperty("stock_id") String stockId, // 股票代號
        @JsonProperty("dividend_yield") @JsonDeserialize(using = BlankAsNullBigDecimalDeserializer.class) BigDecimal dividendYield, // 殖利率
        @JsonProperty("PER") @JsonDeserialize(using = BlankAsNullBigDecimalDeserializer.class) BigDecimal per, // 本益比
        @JsonProperty("PBR") @JsonDeserialize(using = BlankAsNullBigDecimalDeserializer.class) BigDecimal pbr // 股價淨值比
) {
}
