package com.adamchung.stockanalysis.finmind.dto;

import com.adamchung.stockanalysis.finmind.deserializer.BlankAsNullBigDecimalDeserializer;
import com.adamchung.stockanalysis.finmind.deserializer.BlankAsNullLocalDateDeserializer;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import tools.jackson.databind.annotation.JsonDeserialize;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * TaiwanStockDividend 資料列。FinMind 原始的 "year" 欄位是民國年+季度的文字標籤（例如
 * 「112年第3季」），這裡只保留成原始字串供除錯用——**不會**用來填入
 * stock_dividend.year 欄位（見 docs/agent/DECISIONS.md DEC-005）。有幾個日期欄位在不適用時
 * 會回傳 "" 而不是 null（DEC-006）。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record FinMindDividendDto(
        @JsonProperty("date") LocalDate date, // 資料日期
        @JsonProperty("stock_id") String stockId, // 股票代號
        @JsonProperty("year") String rawYearLabel, // 原始年度季別文字標籤（僅供除錯）
        @JsonProperty("CashEarningsDistribution") @JsonDeserialize(using = BlankAsNullBigDecimalDeserializer.class) BigDecimal cashEarningsDistribution, // 現金股利（盈餘分配）
        @JsonProperty("CashStatutorySurplus") @JsonDeserialize(using = BlankAsNullBigDecimalDeserializer.class) BigDecimal cashStatutorySurplus, // 現金股利（公積轉增資）
        @JsonProperty("CashExDividendTradingDate") @JsonDeserialize(using = BlankAsNullLocalDateDeserializer.class) LocalDate cashExDividendTradingDate, // 現金股利除息交易日
        @JsonProperty("CashDividendPaymentDate") @JsonDeserialize(using = BlankAsNullLocalDateDeserializer.class) LocalDate cashDividendPaymentDate, // 現金股利發放日
        @JsonProperty("StockEarningsDistribution") @JsonDeserialize(using = BlankAsNullBigDecimalDeserializer.class) BigDecimal stockEarningsDistribution, // 股票股利（盈餘分配）
        @JsonProperty("StockStatutorySurplus") @JsonDeserialize(using = BlankAsNullBigDecimalDeserializer.class) BigDecimal stockStatutorySurplus, // 股票股利（公積轉增資）
        @JsonProperty("StockExDividendTradingDate") @JsonDeserialize(using = BlankAsNullLocalDateDeserializer.class) LocalDate stockExDividendTradingDate, // 股票股利除權交易日
        @JsonProperty("AnnouncementDate") @JsonDeserialize(using = BlankAsNullLocalDateDeserializer.class) LocalDate announcementDate // 股利公告日
) {
}
