package com.adamchung.stockanalysis.finmind.dto;

import com.adamchung.stockanalysis.finmind.deserializer.BlankAsNullBigDecimalDeserializer;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import tools.jackson.databind.annotation.JsonDeserialize;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * TaiwanStockCashFlowsStatement 資料列。跟 {@link FinMindFinancialStatementDto} 結構相同
 * （EAV），刻意獨立成不同 record，理由同 {@link FinMindBalanceSheetDto}。已對真實 API 驗證：
 * 銀行模板（2884）沒有 {@code PropertyAndPlantAndEquipment}（資本支出）這個 type，代表銀行股
 * 的自由金流（營業金流－資本支出）算不出來，這是資料本身的限制，非程式邏輯缺陷。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record FinMindCashFlowsStatementDto(
        @JsonProperty("date") LocalDate date, // 資料日期
        @JsonProperty("stock_id") String stockId, // 股票代號
        @JsonProperty("type") String type, // 現金流量表項目類型（EAV鍵）
        @JsonProperty("value") @JsonDeserialize(using = BlankAsNullBigDecimalDeserializer.class) BigDecimal value, // 該項目數值
        @JsonProperty("origin_name") String originName // 原始中文欄位名稱
) {
}
