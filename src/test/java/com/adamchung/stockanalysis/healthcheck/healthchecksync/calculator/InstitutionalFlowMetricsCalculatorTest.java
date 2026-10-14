package com.adamchung.stockanalysis.healthcheck.healthchecksync.calculator;

import com.adamchung.stockanalysis.externaldata.dto.QuarterRange;
import com.adamchung.stockanalysis.finmind.dto.FinMindInstitutionalInvestorsBuySellDto;
import com.adamchung.stockanalysis.finmind.dto.FinMindShareholdingDto;
import com.adamchung.stockanalysis.healthcheck.healthchecksync.dto.QuarterlyMetricValue;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class InstitutionalFlowMetricsCalculatorTest {

    private static final QuarterRange QUARTER =
            new QuarterRange(LocalDate.of(2024, 1, 1), LocalDate.of(2024, 3, 31), "2024Q1");

    @Test
    void foreignNetBuySellLots_sums_only_foreign_categories_within_quarter() {
        List<FinMindInstitutionalInvestorsBuySellDto> rows = List.of(
                row("2024-01-02", "Foreign_Investor", 7893000L, 2000000L),
                row("2024-01-03", "Foreign_Dealer_Self", 100000L, 50000L),
                row("2024-01-03", "Investment_Trust", 500000L, 0L), // must be excluded
                row("2023-12-31", "Foreign_Investor", 999999L, 0L)); // outside quarter, must be excluded

        List<QuarterlyMetricValue> result =
                InstitutionalFlowMetricsCalculator.foreignNetBuySellLots(rows, List.of(QUARTER));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).period()).isEqualTo("2024Q1");
        // (7893000-2000000) + (100000-50000) = 5943000 shares = 5943 lots
        assertThat(result.get(0).value()).isEqualByComparingTo("5943.0000");
    }

    @Test
    void institutionalNetBuySellLots_sums_all_categories() {
        List<FinMindInstitutionalInvestorsBuySellDto> rows = List.of(
                row("2024-01-02", "Foreign_Investor", 1000000L, 0L),
                row("2024-01-02", "Dealer_self", 4000L, 0L),
                row("2024-01-02", "Dealer_Hedging", 90000L, 0L),
                row("2024-01-02", "Investment_Trust", 71000L, 13000L));

        List<QuarterlyMetricValue> result =
                InstitutionalFlowMetricsCalculator.institutionalNetBuySellLots(rows, List.of(QUARTER));

        assertThat(result).hasSize(1);
        // (1000000+4000+90000+71000-13000) = 1152000 shares = 1152 lots
        assertThat(result.get(0).value()).isEqualByComparingTo("1152.0000");
    }

    @Test
    void quarterWithNoTradingDataStillProducesZeroValuePoint() {
        List<QuarterlyMetricValue> result =
                InstitutionalFlowMetricsCalculator.foreignNetBuySellLots(List.of(), List.of(QUARTER));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).value()).isEqualByComparingTo("0");
    }

    @Test
    void foreignShareholdingRatio_picks_latest_date_on_or_before_quarter_end() {
        List<FinMindShareholdingDto> rows = List.of(
                new FinMindShareholdingDto(LocalDate.of(2024, 3, 28), "2884", new BigDecimal("31.6")),
                new FinMindShareholdingDto(LocalDate.of(2024, 4, 1), "2884", new BigDecimal("32.0"))); // after quarter end

        List<QuarterlyMetricValue> result =
                InstitutionalFlowMetricsCalculator.foreignShareholdingRatio(rows, List.of(QUARTER));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).value()).isEqualByComparingTo("31.6");
    }

    private FinMindInstitutionalInvestorsBuySellDto row(String date, String name, Long buy, Long sell) {
        return new FinMindInstitutionalInvestorsBuySellDto(LocalDate.parse(date), "2884", name, buy, sell);
    }
}
