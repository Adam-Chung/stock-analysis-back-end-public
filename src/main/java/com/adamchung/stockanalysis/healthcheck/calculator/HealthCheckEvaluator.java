package com.adamchung.stockanalysis.healthcheck.calculator;

import com.adamchung.stockanalysis.healthcheck.definition.HealthCheckMetricDefinition;
import com.adamchung.stockanalysis.healthcheck.definition.HealthCheckTemplateDefinitions;
import com.adamchung.stockanalysis.healthcheck.enums.HealthCheckStatus;
import java.math.BigDecimal;

/**
 * 依細項定義＋當季數值，即時計算達標狀態（純函式，不落地存表，DEC-039）。
 * 這樣以後調整門檻只需要改 {@link HealthCheckTemplateDefinitions}，舊資料的達標判定會
 * 自動反映新門檻，不需要資料回填。
 */
public final class HealthCheckEvaluator {

    private HealthCheckEvaluator() {
    }

    public static HealthCheckStatus evaluate(HealthCheckMetricDefinition metric, BigDecimal value) {
        if (value == null) {
            return HealthCheckStatus.NA;
        }
        return switch (metric.evalType()) {
            case BOOLEAN -> value.compareTo(BigDecimal.ZERO) != 0 ? HealthCheckStatus.PASS : HealthCheckStatus.FAIL;
            case GREATER_THAN -> value.compareTo(metric.threshold()) > 0 ? HealthCheckStatus.PASS : HealthCheckStatus.FAIL;
            case LESS_THAN -> value.compareTo(metric.threshold()) < 0 ? HealthCheckStatus.PASS : HealthCheckStatus.FAIL;
            case JUDGMENT -> HealthCheckStatus.JUDGMENT;
        };
    }
}
