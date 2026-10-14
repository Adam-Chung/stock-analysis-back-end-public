package com.adamchung.stockanalysis.finmind.dto;

import com.adamchung.stockanalysis.finmind.deserializer.BlankAsNullBigDecimalDeserializer;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import tools.jackson.databind.annotation.JsonDeserialize;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * TaiwanStockFinancialStatements 資料列。EPS 資料列（{@code type == "EPS"}）已確認是單季數字，
 * 不是累計值（SA-01 DEC-15）；年度 EPS = 該年度四季加總。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record FinMindFinancialStatementDto(
        @JsonProperty("date") LocalDate date, // 資料日期
        @JsonProperty("stock_id") String stockId, // 股票代號
        @JsonProperty("type") String type, // 財報項目類型（EAV鍵，含EPS）
        @JsonProperty("value") @JsonDeserialize(using = BlankAsNullBigDecimalDeserializer.class) BigDecimal value, // 該項目數值
        @JsonProperty("origin_name") String originName // 原始中文欄位名稱
) {
}
