package com.adamchung.stockanalysis.valuation.dto;

import java.math.BigDecimal;

/** SA-01 六檔目標價其中一檔（1便宜價..6爆炸貴）。 */
public record TierPrice(
        int level, // 目標價等級（1~6）
        String label, // 等級標籤（例如「便宜價」「合理價」「昂貴價」）
        BigDecimal price // 該等級對應價格
) {
}
