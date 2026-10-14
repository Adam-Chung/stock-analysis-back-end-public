package com.adamchung.stockanalysis.finmind.dto;

import com.adamchung.stockanalysis.finmind.deserializer.BlankAsNullBigDecimalDeserializer;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import tools.jackson.databind.annotation.JsonDeserialize;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * TaiwanStockBalanceSheet 資料列。跟 {@link FinMindFinancialStatementDto} 結構相同（EAV），但
 * 刻意獨立成不同 record——避免呼叫端不小心把這個 dataset 的資料傳進期待
 * {@link FinMindFinancialStatementDto} 的方法，讓這種錯誤在編譯期就能發現。已對真實 API 驗證：
 * 一般模板（2330）約 101 種 type，銀行模板（2884）只有 29 種且欄位命名不同（例如銀行用
 * {@code OrdinaryShare} 取代一般模板的 {@code CapitalStock}，銀行完全沒有
 * {@code CurrentAssets}／{@code Inventories}），見 {@code healthchecksync} 套件的欄位對照。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record FinMindBalanceSheetDto(
        @JsonProperty("date") LocalDate date, // 資料日期
        @JsonProperty("stock_id") String stockId, // 股票代號
        @JsonProperty("type") String type, // 資產負債表項目類型（EAV鍵）
        @JsonProperty("value") @JsonDeserialize(using = BlankAsNullBigDecimalDeserializer.class) BigDecimal value, // 該項目數值
        @JsonProperty("origin_name") String originName // 原始中文欄位名稱
) {
}
