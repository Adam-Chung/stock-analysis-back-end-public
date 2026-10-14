package com.adamchung.stockanalysis.healthcheck.calculator;

import java.util.List;
import com.adamchung.stockanalysis.healthcheck.enums.HealthCheckTemplate;

/**
 * 依 {@code stock.industry_category} 字串比對決定套用一般或銀行模板（DEC-028）。
 * 不提供使用者手動覆寫；若之後遇到分類字串不標準、判斷有誤的個案，再評估要不要加手動覆寫欄位。
 */
public final class HealthCheckTemplateResolver {

    private static final List<String> BANK_KEYWORDS = List.of("銀行", "金控", "保險", "證券");

    private HealthCheckTemplateResolver() {
    }

    public static HealthCheckTemplate resolve(String industryCategory) {
        if (industryCategory != null) {
            for (String keyword : BANK_KEYWORDS) {
                if (industryCategory.contains(keyword)) {
                    return HealthCheckTemplate.BANK;
                }
            }
        }
        return HealthCheckTemplate.GENERAL;
    }
}
