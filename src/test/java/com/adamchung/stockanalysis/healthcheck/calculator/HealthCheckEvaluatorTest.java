package com.adamchung.stockanalysis.healthcheck.calculator;

import com.adamchung.stockanalysis.healthcheck.definition.HealthCheckMetricDefinition;
import com.adamchung.stockanalysis.healthcheck.enums.HealthCheckEvalType;
import com.adamchung.stockanalysis.healthcheck.enums.HealthCheckStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class HealthCheckEvaluatorTest {

    private static final HealthCheckMetricDefinition GT = new HealthCheckMetricDefinition(
            "test.gt", "gt", "%", HealthCheckEvalType.GREATER_THAN, BigDecimal.valueOf(80), null, false, false);
    private static final HealthCheckMetricDefinition LT = new HealthCheckMetricDefinition(
            "test.lt", "lt", "%", HealthCheckEvalType.LESS_THAN, BigDecimal.valueOf(50), null, false, false);
    private static final HealthCheckMetricDefinition BOOL = new HealthCheckMetricDefinition(
            "test.bool", "bool", "", HealthCheckEvalType.BOOLEAN, null, null, false, false);
    private static final HealthCheckMetricDefinition JUDGMENT = new HealthCheckMetricDefinition(
            "test.judgment", "judgment", "%", HealthCheckEvalType.JUDGMENT, null, null, false, false);

    @Test
    void returns_na_when_value_is_null_regardless_of_eval_type() {
        assertThat(HealthCheckEvaluator.evaluate(GT, null)).isEqualTo(HealthCheckStatus.NA);
        assertThat(HealthCheckEvaluator.evaluate(JUDGMENT, null)).isEqualTo(HealthCheckStatus.NA);
    }

    @Test
    void greater_than_passes_strictly_above_threshold_boundary_is_fail() {
        assertThat(HealthCheckEvaluator.evaluate(GT, BigDecimal.valueOf(80.01))).isEqualTo(HealthCheckStatus.PASS);
        assertThat(HealthCheckEvaluator.evaluate(GT, BigDecimal.valueOf(80))).isEqualTo(HealthCheckStatus.FAIL);
        assertThat(HealthCheckEvaluator.evaluate(GT, BigDecimal.valueOf(79.99))).isEqualTo(HealthCheckStatus.FAIL);
    }

    @Test
    void less_than_passes_strictly_below_threshold_boundary_is_fail() {
        assertThat(HealthCheckEvaluator.evaluate(LT, BigDecimal.valueOf(49.99))).isEqualTo(HealthCheckStatus.PASS);
        assertThat(HealthCheckEvaluator.evaluate(LT, BigDecimal.valueOf(50))).isEqualTo(HealthCheckStatus.FAIL);
        assertThat(HealthCheckEvaluator.evaluate(LT, BigDecimal.valueOf(50.01))).isEqualTo(HealthCheckStatus.FAIL);
    }

    @Test
    void boolean_type_treats_nonzero_as_pass_and_zero_as_fail() {
        assertThat(HealthCheckEvaluator.evaluate(BOOL, BigDecimal.ONE)).isEqualTo(HealthCheckStatus.PASS);
        assertThat(HealthCheckEvaluator.evaluate(BOOL, BigDecimal.ZERO)).isEqualTo(HealthCheckStatus.FAIL);
    }

    @Test
    void judgment_type_always_returns_judgment_for_any_non_null_value() {
        assertThat(HealthCheckEvaluator.evaluate(JUDGMENT, BigDecimal.valueOf(-999)))
                .isEqualTo(HealthCheckStatus.JUDGMENT);
    }
}
