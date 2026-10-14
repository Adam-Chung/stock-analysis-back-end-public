package com.adamchung.stockanalysis.externaldata;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RocPeriodTest {

    @Test
    void converts_capital_adequacy_style_period_with_trailing_suffix() {
        assertThat(RocPeriod.toQuarterLabel("115年3月底")).isEqualTo("2026Q1");
    }

    @Test
    void converts_npl_coverage_style_period_without_trailing_suffix() {
        assertThat(RocPeriod.toQuarterLabel("115年6月")).isEqualTo("2026Q2");
    }

    @Test
    void maps_each_calendar_month_to_the_correct_quarter() {
        assertThat(RocPeriod.toQuarterLabel("113年9月")).isEqualTo("2024Q3");
        assertThat(RocPeriod.toQuarterLabel("113年12月")).isEqualTo("2024Q4");
    }

    @Test
    void throws_for_a_string_that_does_not_match_the_roc_year_month_shape() {
        assertThatThrownBy(() -> RocPeriod.toQuarterLabel("not a period"))
                .isInstanceOf(ExternalDataException.class);
    }

    @Test
    void converts_iso_year_month_to_quarter_label() {
        assertThat(RocPeriod.isoMonthToQuarterLabel("2026-06")).isEqualTo("2026Q2");
        assertThat(RocPeriod.isoMonthToQuarterLabel("2025-01")).isEqualTo("2025Q1");
    }
}
