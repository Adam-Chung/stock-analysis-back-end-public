package com.adamchung.stockanalysis.healthcheck.healthchecksync.calculator;

import com.adamchung.stockanalysis.finmind.dto.FinMindMonthRevenueDto;
import com.adamchung.stockanalysis.healthcheck.healthchecksync.dto.QuarterlyMetricValue;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MonthRevenueMetricsCalculatorTest {

    @Test
    void monthlyRevenue_converts_to_hundred_millions_and_labels_by_year_month() {
        List<FinMindMonthRevenueDto> rows = List.of(row(2024, 12, 6420000000L));

        List<QuarterlyMetricValue> result = MonthRevenueMetricsCalculator.monthlyRevenue(rows);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).period()).isEqualTo("2024-12");
        assertThat(result.get(0).value()).isEqualByComparingTo("64.2000");
    }

    @Test
    void momGrowth_compares_against_immediately_preceding_month() {
        List<FinMindMonthRevenueDto> rows = List.of(
                row(2024, 12, 6420000000L),
                row(2024, 11, 6000000000L));

        List<QuarterlyMetricValue> result = MonthRevenueMetricsCalculator.momGrowth(rows);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).period()).isEqualTo("2024-12");
        assertThat(result.get(0).value()).isEqualByComparingTo("7.0000"); // (642-600)/600*100
    }

    @Test
    void momGrowth_handles_year_boundary() {
        List<FinMindMonthRevenueDto> rows = List.of(
                row(2025, 1, 1100000000L),
                row(2024, 12, 1000000000L));

        List<QuarterlyMetricValue> result = MonthRevenueMetricsCalculator.momGrowth(rows);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).period()).isEqualTo("2025-01");
        assertThat(result.get(0).value()).isEqualByComparingTo("10.0000");
    }

    @Test
    void firstMonthWithNoPriorDataYieldsNoValue_notDivideByZeroException() {
        List<FinMindMonthRevenueDto> rows = List.of(row(2024, 1, 1000000000L));

        List<QuarterlyMetricValue> mom = MonthRevenueMetricsCalculator.momGrowth(rows);
        List<QuarterlyMetricValue> yoy = MonthRevenueMetricsCalculator.yoyGrowth(rows);

        assertThat(mom).isEmpty();
        assertThat(yoy).isEmpty();
    }

    @Test
    void yoyGrowth_compares_against_same_month_prior_year() {
        List<FinMindMonthRevenueDto> rows = List.of(
                row(2024, 3, 1100000000L),
                row(2023, 3, 1000000000L),
                row(2024, 2, 999999999L)); // adjacent month must not be picked for YoY

        List<QuarterlyMetricValue> result = MonthRevenueMetricsCalculator.yoyGrowth(rows);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).period()).isEqualTo("2024-03");
        assertThat(result.get(0).value()).isEqualByComparingTo("10.0000");
    }

    private FinMindMonthRevenueDto row(int year, int month, long revenue) {
        return new FinMindMonthRevenueDto("2330", "Taiwan", revenue, month, year);
    }
}
