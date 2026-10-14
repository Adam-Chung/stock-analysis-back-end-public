package com.adamchung.stockanalysis.valuation.calculator;

import com.adamchung.stockanalysis.valuation.dto.QuarterlyEps;
import com.adamchung.stockanalysis.valuation.dto.YearlyEps;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class EpsAggregatorTest {

    @Test
    void sums_four_quarters_into_one_annual_figure() {
        List<QuarterlyEps> quarters = List.of(
                new QuarterlyEps(LocalDate.of(2025, 3, 31), BigDecimal.valueOf(13.95)),
                new QuarterlyEps(LocalDate.of(2025, 6, 30), BigDecimal.valueOf(15.36)),
                new QuarterlyEps(LocalDate.of(2025, 9, 30), BigDecimal.valueOf(17.44)),
                new QuarterlyEps(LocalDate.of(2025, 12, 31), BigDecimal.valueOf(19.51)));

        List<YearlyEps> annual = EpsAggregator.toAnnual(quarters);

        assertThat(annual).hasSize(1);
        assertThat(annual.get(0).year()).isEqualTo(2025);
        // 13.95 + 15.36 + 17.44 + 19.51
        assertThat(annual.get(0).eps()).isEqualByComparingTo(BigDecimal.valueOf(66.26));
    }

    @Test
    void excludes_years_with_fewer_than_four_quarters_TTM_DEC9() {
        List<QuarterlyEps> quarters = List.of(
                // 2025: only 1 quarter reported so far (e.g. current year, not yet complete)
                new QuarterlyEps(LocalDate.of(2025, 3, 31), BigDecimal.valueOf(22.08)),
                // 2024: all 4 quarters present
                new QuarterlyEps(LocalDate.of(2024, 3, 31), BigDecimal.valueOf(10)),
                new QuarterlyEps(LocalDate.of(2024, 6, 30), BigDecimal.valueOf(11)),
                new QuarterlyEps(LocalDate.of(2024, 9, 30), BigDecimal.valueOf(12)),
                new QuarterlyEps(LocalDate.of(2024, 12, 31), BigDecimal.valueOf(13)));

        List<YearlyEps> annual = EpsAggregator.toAnnual(quarters);

        assertThat(annual).hasSize(1);
        assertThat(annual.get(0).year()).isEqualTo(2024);
        assertThat(annual.get(0).eps()).isEqualByComparingTo(BigDecimal.valueOf(46));
    }

    @Test
    void returns_empty_list_for_no_input() {
        assertThat(EpsAggregator.toAnnual(List.of())).isEmpty();
    }
}
