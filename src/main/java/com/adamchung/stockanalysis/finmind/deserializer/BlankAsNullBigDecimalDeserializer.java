package com.adamchung.stockanalysis.finmind.deserializer;

import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;

import java.math.BigDecimal;

/**
 * 跟 {@link BlankAsNullLocalDateDeserializer} 一樣的防禦性處理，套用在數值欄位上，因應
 * FinMind 對於某一列不適用的數值回傳 ""（見 docs/agent/DECISIONS.md DEC-006）。
 * 對數值型與字串型的 JSON token 都能正確處理。
 */
public class BlankAsNullBigDecimalDeserializer extends ValueDeserializer<BigDecimal> {

    /**
     * 將 JSON 值反序列化為 {@link BigDecimal}，空白字串視為 {@code null}。
     *
     * @param p JSON 解析器
     * @param ctxt 反序列化上下文
     * @return 解析後的 {@link BigDecimal}，原始值為空白時回傳 {@code null}
     */
    @Override
    public BigDecimal deserialize(JsonParser p, DeserializationContext ctxt) {
        String text = p.getValueAsString();
        if (text == null || text.isBlank()) {
            return null;
        }
        return new BigDecimal(text);
    }
}
