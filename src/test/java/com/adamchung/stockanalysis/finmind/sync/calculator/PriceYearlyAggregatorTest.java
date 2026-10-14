package com.adamchung.stockanalysis.finmind.sync.calculator;

import com.adamchung.stockanalysis.finmind.dto.FinMindStockPriceDto;
import com.adamchung.stockanalysis.valuation.dto.YearlyPriceStats;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PriceYearlyAggregatorTest {

    private static FinMindStockPriceDto row(LocalDate date, double open, double max, double min, double close) {
        return new FinMindStockPriceDto(
                date, "2330", 1_000_000L, 1_000_000L,
                BigDecimal.valueOf(open), BigDecimal.valueOf(max), BigDecimal.valueOf(min), BigDecimal.valueOf(close),
                BigDecimal.ZERO, 100L);
    }

    @Test
    void aggregates_one_year_of_daily_rows_into_avg_high_low() {
        List<FinMindStockPriceDto> daily = List.of(
                row(LocalDate.of(2025, 1, 2), 100, 105, 99, 100),
                row(LocalDate.of(2025, 6, 15), 110, 120, 108, 115),
                row(LocalDate.of(2025, 12, 30), 90, 95, 85, 90));

        List<YearlyPriceStats> result = PriceYearlyAggregator.aggregate(daily);

        assertThat(result).hasSize(1);
        YearlyPriceStats year2025 = result.get(0);
        assertThat(year2025.year()).isEqualTo(2025);
        // (100 + 115 + 90) / 3
        assertThat(year2025.avgClosePrice()).isEqualByComparingTo(BigDecimal.valueOf(101.6667));
        assertThat(year2025.highPrice()).isEqualByComparingTo(BigDecimal.valueOf(120));
        assertThat(year2025.lowPrice()).isEqualByComparingTo(BigDecimal.valueOf(85));
    }

    @Test
    void groups_multiple_years_separately_and_sorts_ascending() {
        List<FinMindStockPriceDto> daily = List.of(
                row(LocalDate.of(2024, 3, 1), 50, 55, 48, 50),
                row(LocalDate.of(2023, 3, 1), 40, 45, 38, 40));

        List<YearlyPriceStats> result = PriceYearlyAggregator.aggregate(daily);

        assertThat(result).extracting(YearlyPriceStats::year).containsExactly(2023, 2024);
    }

    @Test
    void excludes_incomplete_rows_from_all_yearly_statistics() {
        FinMindStockPriceDto validRow = row(LocalDate.of(2025, 1, 2), 100, 105, 99, 100);
        FinMindStockPriceDto nullCloseRow = new FinMindStockPriceDto(
                LocalDate.of(2025, 1, 3), "2330", 0L, 0L,
                BigDecimal.valueOf(100), BigDecimal.valueOf(200), BigDecimal.valueOf(50), null,
                BigDecimal.ZERO, 0L);

        List<YearlyPriceStats> result = PriceYearlyAggregator.aggregate(List.of(validRow, nullCloseRow));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).avgClosePrice()).isEqualByComparingTo(BigDecimal.valueOf(100));
        assertThat(result.get(0).highPrice()).isEqualByComparingTo(BigDecimal.valueOf(105));
        assertThat(result.get(0).lowPrice()).isEqualByComparingTo(BigDecimal.valueOf(99));
    }

    @Test
    void excludes_a_zero_price_day_from_average_denominator_and_high_low() {
        List<FinMindStockPriceDto> daily = List.of(
                row(LocalDate.of(2025, 1, 2), 100, 110, 90, 100),
                row(LocalDate.of(2025, 1, 3), 100, 120, 0, 200),
                row(LocalDate.of(2025, 1, 4), 120, 130, 110, 120));

        List<YearlyPriceStats> result = PriceYearlyAggregator.aggregate(daily);

        assertThat(result).hasSize(1);
        // 低價為 0 的日期整筆排除，平均分母是 2 而不是 3：(100 + 120) / 2。
        assertThat(result.get(0).avgClosePrice()).isEqualByComparingTo(BigDecimal.valueOf(110));
        assertThat(result.get(0).highPrice()).isEqualByComparingTo(BigDecimal.valueOf(130));
        assertThat(result.get(0).lowPrice()).isEqualByComparingTo(BigDecimal.valueOf(90));
    }

    @Test
    void omits_a_year_entirely_when_it_has_no_usable_rows() {
        FinMindStockPriceDto allNulls = new FinMindStockPriceDto(
                LocalDate.of(2025, 1, 3), "2330", 0L, 0L, null, null, null, null, null, 0L);

        List<YearlyPriceStats> result = PriceYearlyAggregator.aggregate(List.of(allNulls));

        assertThat(result).isEmpty();
    }

    @Test
    void returns_empty_list_for_no_input() {
        assertThat(PriceYearlyAggregator.aggregate(List.of())).isEmpty();
    }
}
