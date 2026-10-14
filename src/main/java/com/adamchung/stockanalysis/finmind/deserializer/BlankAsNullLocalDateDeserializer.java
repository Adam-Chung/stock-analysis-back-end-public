package com.adamchung.stockanalysis.finmind.deserializer;

import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;

import java.time.LocalDate;

/**
 * FinMind 對於某一列不適用的日期欄位會回傳 ""（不是 null，也不是省略欄位）——例如
 * TaiwanStockDividend.StockExDividendTradingDate 在該期間沒有股票股利時就是這樣。
 * 已於 2026-07-29 對真實 API 驗證過（見 docs/agent/DECISIONS.md DEC-006）。
 *
 * 使用 Jackson 3.x 的 tools.jackson.* API（Spring Boot 4.1 透過 spring-boot-starter-jackson
 * 引入 Jackson 3）——反序列化器的基底類別是 ValueDeserializer，不是 Jackson 2.x 的
 * JsonDeserializer。
 */
public class BlankAsNullLocalDateDeserializer extends ValueDeserializer<LocalDate> {

    /**
     * 將 JSON 值反序列化為 {@link LocalDate}，空白字串視為 {@code null}。
     *
     * @param p JSON 解析器
     * @param ctxt 反序列化上下文
     * @return 解析後的 {@link LocalDate}，原始值為空白時回傳 {@code null}
     */
    @Override
    public LocalDate deserialize(JsonParser p, DeserializationContext ctxt) {
        String text = p.getValueAsString();
        if (text == null || text.isBlank()) {
            return null;
        }
        return LocalDate.parse(text);
    }
}
