package com.adamchung.stockanalysis.finmind.sync.calculator;

import com.adamchung.stockanalysis.finmind.dto.FinMindStockValuationDto;
import com.adamchung.stockanalysis.valuation.dto.YearlyValuationStats;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ValuationYearlyAggregatorTest {

    private static FinMindStockValuationDto row(LocalDate date, Double yield, Double per, Double pbr) {
        return new FinMindStockValuationDto(
                date, "2330",
                yield == null ? null : BigDecimal.valueOf(yield),
                per == null ? null : BigDecimal.valueOf(per),
                pbr == null ? null : BigDecimal.valueOf(pbr));
    }

    @Test
    void aggregates_avg_low_high_per_and_pbr_and_leaves_dividend_yield_null() {
        List<FinMindStockValuationDto> daily = List.of(
                row(LocalDate.of(2025, 1, 2), 2.0, 15.0, 5.0),
                row(LocalDate.of(2025, 6, 15), 3.0, 20.0, 6.0),
                row(LocalDate.of(2025, 12, 30), 4.0, 10.0, 4.0));

        List<YearlyValuationStats> result = ValuationYearlyAggregator.aggregate(daily);

        assertThat(result).hasSize(1);
        YearlyValuationStats stats = result.get(0);
        assertThat(stats.year()).isEqualTo(2025);
        assertThat(stats.avgPer()).isEqualByComparingTo(BigDecimal.valueOf(15)); // (15+20+10)/3
        assertThat(stats.lowPer()).isEqualByComparingTo(BigDecimal.valueOf(10));
        assertThat(stats.highPer()).isEqualByComparingTo(BigDecimal.valueOf(20));
        assertThat(stats.avgPbr()).isEqualByComparingTo(BigDecimal.valueOf(5)); // (5+6+4)/3
        assertThat(stats.lowPbr()).isEqualByComparingTo(BigDecimal.valueOf(4));
        assertThat(stats.highPbr()).isEqualByComparingTo(BigDecimal.valueOf(6));
        // FinMind's dividend_yield is no longer trusted/aggregated - yield is computed at request
        // time from stock_price_yearly + stock_dividend instead (DividendYieldCalculator).
        assertThat(stats.avgDividendYield()).isNull();
    }

    @Test
    void leaves_a_metric_null_for_the_year_when_every_row_is_null_for_it_loss_making_stock() {
        List<FinMindStockValuationDto> daily = List.of(
                row(LocalDate.of(2025, 1, 2), 0.0, null, 2.0),
                row(LocalDate.of(2025, 6, 15), 0.0, null, 3.0));

        List<YearlyValuationStats> result = ValuationYearlyAggregator.aggregate(daily);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).avgPer()).isNull();
        assertThat(result.get(0).lowPer()).isNull();
        assertThat(result.get(0).highPer()).isNull();
        assertThat(result.get(0).avgPbr()).isEqualByComparingTo(BigDecimal.valueOf(2.5));
    }

    @Test
    void groups_multiple_years_separately_and_sorts_ascending() {
        List<FinMindStockValuationDto> daily = List.of(
                row(LocalDate.of(2024, 3, 1), 2.0, 12.0, 3.0),
                row(LocalDate.of(2023, 3, 1), 2.0, 10.0, 2.0));

        List<YearlyValuationStats> result = ValuationYearlyAggregator.aggregate(daily);

        assertThat(result).extracting(YearlyValuationStats::year).containsExactly(2023, 2024);
    }

    @Test
    void returns_empty_list_for_no_input() {
        assertThat(ValuationYearlyAggregator.aggregate(List.of())).isEmpty();
    }
}
