package com.adamchung.stockanalysis.healthcheck.healthchecksync.calculator;

import com.adamchung.stockanalysis.finmind.dto.FinMindCashFlowsStatementDto;
import com.adamchung.stockanalysis.finmind.dto.FinMindDividendDto;
import com.adamchung.stockanalysis.finmind.dto.FinMindFinancialStatementDto;
import com.adamchung.stockanalysis.healthcheck.healthchecksync.dto.QuarterlyFinancialFacts;
import com.adamchung.stockanalysis.healthcheck.healthchecksync.dto.QuarterlyMetricValue;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class HealthCheckFinancialMetricsCalculatorTest {

    @Test
    void reinvestmentRatio_takes_absolute_value_of_capex() {
        List<QuarterlyFinancialFacts> facts = List.of(
                facts("2024Q4").capex("-30").incomeAfterTaxes("100").build());

        List<QuarterlyMetricValue> result = HealthCheckFinancialMetricsCalculator.reinvestmentRatio(facts);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).period()).isEqualTo("2024Q4");
        assertThat(result.get(0).value()).isEqualByComparingTo("30.0000");
    }

    @Test
    void reinvestmentRatio_matches_real_1216_data_end_to_end_through_the_joiner() {
        // DEC-063：SA-04 §4 建議的二次驗證，用已知股票（1216 統一）真實數字核對推導公式的
        // 量級是否合理。資本支出（PropertyAndPlantAndEquipment）是年度累計值（DEC-057），
        // 這裡刻意經過 QuarterlyFactsJoiner 而不是直接建構 QuarterlyFinancialFacts，確保
        // 累計轉單季差分＋盈餘再投資比率計算兩段邏輯串接起來也是對的。
        List<FinMindCashFlowsStatementDto> cashFlows = List.of(
                cf("2025-09-30", "PropertyAndPlantAndEquipment", "-21437337000"),
                cf("2025-12-31", "PropertyAndPlantAndEquipment", "-29031117000"));
        List<FinMindFinancialStatementDto> statements = List.of(
                fs("2025-12-31", "IncomeAfterTaxes", "4963353000"));

        List<QuarterlyFinancialFacts> facts = QuarterlyFactsJoiner.join(statements, List.of(), cashFlows);
        List<QuarterlyMetricValue> result = HealthCheckFinancialMetricsCalculator.reinvestmentRatio(facts);

        QuarterlyMetricValue q4 = result.stream().filter(v -> v.period().equals("2025Q4")).findFirst().orElseThrow();
        // |29031117000-21437337000| / 4963353000 * 100 ≈ 153.00%
        assertThat(q4.value()).isEqualByComparingTo("153.0000");
    }

    @Test
    void missing_denominator_skips_the_quarter_instead_of_throwing() {
        List<QuarterlyFinancialFacts> facts = List.of(
                facts("2024Q4").operatingIncome("80").build(), // preTaxIncome missing
                facts("2024Q3").operatingIncome("80").preTaxIncome("100").build());

        List<QuarterlyMetricValue> result = HealthCheckFinancialMetricsCalculator.operatingIncomeRatioQuarter(facts);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).period()).isEqualTo("2024Q3");
        assertThat(result.get(0).value()).isEqualByComparingTo("80.0000");
    }

    @Test
    void cashFlowFree_subtracts_capex_from_operating_cash_flow_in_hundred_millions() {
        List<QuarterlyFinancialFacts> facts = List.of(
                facts("2024Q4").operatingCashFlow("500000000").capex("-200000000").build());

        List<QuarterlyMetricValue> result = HealthCheckFinancialMetricsCalculator.cashFlowFree(facts);

        assertThat(result.get(0).value()).isEqualByComparingTo("3.0000"); // (5億-2億)/1億
    }

    @Test
    void interestCoverageRatio_uses_pretax_plus_interest_as_ebit_proxy() {
        List<QuarterlyFinancialFacts> facts = List.of(
                facts("2024Q4").preTaxIncome("400").interestExpense("100").build());

        List<QuarterlyMetricValue> result = HealthCheckFinancialMetricsCalculator.interestCoverageRatio(facts);

        assertThat(result.get(0).value()).isEqualByComparingTo("5.0000"); // (400+100)/100
    }

    @Test
    void capitalExpansionRatio_compares_same_calendar_quarter_five_years_apart() {
        List<QuarterlyFinancialFacts> facts = List.of(
                facts("2024Q4").capitalStock("1200").build(),
                facts("2019Q4").capitalStock("1000").build(),
                facts("2020Q4").capitalStock("999").build()); // wrong lag year, must not match 2024Q4

        List<QuarterlyMetricValue> result = HealthCheckFinancialMetricsCalculator.capitalExpansionRatio(facts);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).period()).isEqualTo("2024Q4");
        assertThat(result.get(0).value()).isEqualByComparingTo("20.0000"); // (1200-1000)/1000
    }

    @Test
    void capitalExpansionRatio_skips_when_no_matching_lag_quarter_exists() {
        List<QuarterlyFinancialFacts> facts = List.of(facts("2024Q4").capitalStock("1200").build());

        List<QuarterlyMetricValue> result = HealthCheckFinancialMetricsCalculator.capitalExpansionRatio(facts);

        assertThat(result).isEmpty();
    }

    @Test
    void capitalExpansionRatio_shows_latest_quarter_plus_past_four_years_q4() {
        // DEC-077：改成只顯示「最新一季（不論是不是Q4）＋過去4個完整曆年Q4」共5個資料點，
        // 不是每一季都輸出。這裡刻意把 facts 的順序打亂（不照新到舊排序），驗證「最新一季」是
        // 用實際比較 quarter 字串取最大值算出來的，不是假設呼叫端一定會傳排序好的清單。
        List<QuarterlyFinancialFacts> facts = List.of(
                facts("2023Q3").capitalStock("999").build(), // 不是目標期間，應被忽略
                facts("2019Q4").capitalStock("1000").build(), // 2024Q4 的落後對照
                facts("2026Q2").capitalStock("1400").build(), // 最新一季
                facts("2020Q4").capitalStock("1100").build(), // 2025Q4 的落後對照
                facts("2025Q4").capitalStock("1300").build(),
                facts("2021Q2").capitalStock("1000").build(), // 2026Q2 的落後對照
                facts("2024Q4").capitalStock("1250").build());

        List<QuarterlyMetricValue> result = HealthCheckFinancialMetricsCalculator.capitalExpansionRatio(facts);

        // 2023Q4/2022Q4 因為完全沒有資料（本身或落後對照都缺）被跳過，不會補位或報錯
        assertThat(result).extracting(QuarterlyMetricValue::period)
                .containsExactly("2026Q2", "2025Q4", "2024Q4");
        assertThat(result.get(0).value()).isEqualByComparingTo("40.0000"); // (1400-1000)/1000
        assertThat(result.get(1).value()).isEqualByComparingTo("18.1800"); // (1300-1100)/1100
        assertThat(result.get(2).value()).isEqualByComparingTo("25.0000"); // (1250-1000)/1000
    }

    @Test
    void tenYearProfitable_true_when_at_most_one_loss_year_among_recent_ten() {
        List<QuarterlyFinancialFacts> facts = completeYears(2015, 2024, year -> year == 2020 ? "-5" : "10");

        List<QuarterlyMetricValue> result = HealthCheckFinancialMetricsCalculator.tenYearProfitable(facts);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).period()).isEqualTo("2024Q4");
        assertThat(result.get(0).value()).isEqualByComparingTo("1"); // true
    }

    @Test
    void tenYearProfitable_false_when_two_loss_years_among_recent_ten() {
        List<QuarterlyFinancialFacts> facts =
                completeYears(2015, 2024, year -> (year == 2020 || year == 2021) ? "-5" : "10");

        List<QuarterlyMetricValue> result = HealthCheckFinancialMetricsCalculator.tenYearProfitable(facts);

        assertThat(result.get(0).value()).isEqualByComparingTo("0"); // false
    }

    @Test
    void incomplete_current_year_is_excluded_from_annual_metrics() {
        List<QuarterlyFinancialFacts> facts = new ArrayList<>();
        facts.add(facts("2024Q4").epsQuarter("2.0").build());
        facts.add(facts("2024Q3").epsQuarter("1.5").build());
        // 2024Q2/Q1 missing -> 2024 is an incomplete year and must not appear in epsYear

        List<QuarterlyMetricValue> result = HealthCheckFinancialMetricsCalculator.epsYear(facts);

        assertThat(result).isEmpty();
    }

    @Test
    void epsYear_sums_four_complete_quarters() {
        List<QuarterlyFinancialFacts> facts = List.of(
                facts("2024Q4").epsQuarter("1.0").build(),
                facts("2024Q3").epsQuarter("1.5").build(),
                facts("2024Q2").epsQuarter("0.8").build(),
                facts("2024Q1").epsQuarter("0.7").build());

        List<QuarterlyMetricValue> result = HealthCheckFinancialMetricsCalculator.epsYear(facts);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).period()).isEqualTo("2024Q4");
        assertThat(result.get(0).value()).isEqualByComparingTo("4.0");
    }

    @Test
    void roe_uses_annual_net_income_over_year_end_equity_not_quarterly() {
        List<QuarterlyFinancialFacts> facts = List.of(
                facts("2024Q4").incomeAfterTaxes("30").equity("1000").build(),
                facts("2024Q3").incomeAfterTaxes("20").equity("950").build(),
                facts("2024Q2").incomeAfterTaxes("25").equity("900").build(),
                facts("2024Q1").incomeAfterTaxes("25").equity("850").build());

        List<QuarterlyMetricValue> result = HealthCheckFinancialMetricsCalculator.roe(facts);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).period()).isEqualTo("2024Q4");
        // (30+20+25+25) / 1000 (年底股東權益) * 100 = 10%，不是單季 30/1000=3%
        assertThat(result.get(0).value()).isEqualByComparingTo("10.0000");
    }

    @Test
    void revenueInflationRatio_uses_year_end_balance_sheet_snapshot_over_annual_revenue() {
        // DEC-065：改回年度口徑（取代 DEC-035 的當季比對）——應收+存貨用當年Q4（年底）快照，
        // 除以當年四季加總營收，不是單季應收/存貨對單季營收。
        List<QuarterlyFinancialFacts> facts = List.of(
                facts("2024Q4").accountsReceivableNet("100").inventories("50").revenue("300").build(),
                facts("2024Q3").accountsReceivableNet("999").inventories("999").revenue("300").build(),
                facts("2024Q2").accountsReceivableNet("999").inventories("999").revenue("300").build(),
                facts("2024Q1").accountsReceivableNet("999").inventories("999").revenue("300").build());

        List<QuarterlyMetricValue> result = HealthCheckFinancialMetricsCalculator.revenueInflationRatio(facts);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).period()).isEqualTo("2024Q4");
        // (100+50) / (300*4) * 100 = 12.5%，不是用Q1~Q3那些999的季底快照
        assertThat(result.get(0).value()).isEqualByComparingTo("12.5000");
    }

    @Test
    void roe_requires_a_complete_year_of_income_even_if_year_end_equity_is_present() {
        List<QuarterlyFinancialFacts> facts = List.of(
                facts("2024Q4").incomeAfterTaxes("30").equity("1000").build()); // 只有Q4，缺Q1~Q3

        List<QuarterlyMetricValue> result = HealthCheckFinancialMetricsCalculator.roe(facts);

        assertThat(result).isEmpty();
    }

    @Test
    void totalPayoutRatio_matches_dividend_to_the_earnings_year_it_distributes_not_ex_date_year() {
        // DEC-058：台股慣例「N年度獲利，N+1年除息發放」，除息日西元年-1才是股利對應的獲利年度
        // （已對真實API驗證：2884除息日2026-07-01的股利，year欄位標示114年=西元2025，
        // 對應的是2025年度獲利，不是2026年度）。這裡除息日落在2025年的股利要對回2024年EPS。
        List<QuarterlyFinancialFacts> facts = List.of(
                facts("2024Q4").epsQuarter("1.0").build(),
                facts("2024Q3").epsQuarter("1.0").build(),
                facts("2024Q2").epsQuarter("1.0").build(),
                facts("2024Q1").epsQuarter("1.0").build());
        List<FinMindDividendDto> dividends = List.of(
                new FinMindDividendDto(LocalDate.of(2025, 6, 1), "2330", "113年", new BigDecimal("2.0"),
                        null, LocalDate.of(2025, 6, 18), null, null, null, null, null));

        List<QuarterlyMetricValue> result =
                HealthCheckFinancialMetricsCalculator.totalPayoutRatio(facts, dividends);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).period()).isEqualTo("2024Q4");
        assertThat(result.get(0).value()).isEqualByComparingTo("50.0000"); // 2.0 / 4.0 * 100
    }

    @Test
    void totalPayoutRatio_sums_cash_and_stock_dividends() {
        // 使用者要求盈餘總分配率改成(現金股利+股票股利)/EPS，不只算現金股利。
        List<QuarterlyFinancialFacts> facts = List.of(
                facts("2024Q4").epsQuarter("1.0").build(),
                facts("2024Q3").epsQuarter("1.0").build(),
                facts("2024Q2").epsQuarter("1.0").build(),
                facts("2024Q1").epsQuarter("1.0").build());
        List<FinMindDividendDto> dividends = List.of(
                new FinMindDividendDto(LocalDate.of(2025, 6, 1), "2330", "113年", new BigDecimal("1.5"),
                        null, LocalDate.of(2025, 6, 18), null, new BigDecimal("0.5"), null, null, null));

        List<QuarterlyMetricValue> result =
                HealthCheckFinancialMetricsCalculator.totalPayoutRatio(facts, dividends);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).period()).isEqualTo("2024Q4");
        assertThat(result.get(0).value()).isEqualByComparingTo("50.0000"); // (1.5 + 0.5) / 4.0 * 100
    }

    @Test
    void totalPayoutRatio_attaches_a_formula_breakdown_note() {
        // DEC-074：使用者要求筆記欄位自動帶入公式明細，而不只是最終百分比，方便核對數字怎麼算出來的。
        List<QuarterlyFinancialFacts> facts = List.of(
                facts("2024Q4").epsQuarter("0.5").build(),
                facts("2024Q3").epsQuarter("0.5").build(),
                facts("2024Q2").epsQuarter("0.5").build(),
                facts("2024Q1").epsQuarter("0.5").build());
        List<FinMindDividendDto> dividends = List.of(
                new FinMindDividendDto(LocalDate.of(2025, 6, 1), "2330", "113年", new BigDecimal("1.4"),
                        null, LocalDate.of(2025, 6, 18), null, new BigDecimal("0.4"), null, null, null));

        List<QuarterlyMetricValue> result =
                HealthCheckFinancialMetricsCalculator.totalPayoutRatio(facts, dividends);

        assertThat(result).hasSize(1);
        // (1.4+0.4) / 2.0 * 100 = 90%
        assertThat(result.get(0).value()).isEqualByComparingTo("90.0000");
        assertThat(result.get(0).note()).isEqualTo("現金股利1.4+股票股利0.4 / EPS2 = 90%");
    }

    @Test
    void totalPayoutRatio_counts_stock_only_dividend_when_no_cash_distribution() {
        List<QuarterlyFinancialFacts> facts = List.of(
                facts("2024Q4").epsQuarter("1.0").build(),
                facts("2024Q3").epsQuarter("1.0").build(),
                facts("2024Q2").epsQuarter("1.0").build(),
                facts("2024Q1").epsQuarter("1.0").build());
        List<FinMindDividendDto> dividends = List.of(
                new FinMindDividendDto(LocalDate.of(2025, 6, 1), "2330", "113年", null,
                        null, LocalDate.of(2025, 6, 18), null, new BigDecimal("2.0"), null, null, null));

        List<QuarterlyMetricValue> result =
                HealthCheckFinancialMetricsCalculator.totalPayoutRatio(facts, dividends);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).value()).isEqualByComparingTo("50.0000"); // 2.0 / 4.0 * 100
    }

    private static List<QuarterlyFinancialFacts> completeYears(
            int fromYearInclusive, int toYearInclusive, java.util.function.IntFunction<String> incomeForYear) {
        List<QuarterlyFinancialFacts> facts = new ArrayList<>();
        for (int year = toYearInclusive; year >= fromYearInclusive; year--) {
            for (int quarter = 4; quarter >= 1; quarter--) {
                facts.add(facts(year + "Q" + quarter).incomeAfterTaxes(incomeForYear.apply(year)).build());
            }
        }
        return facts;
    }

    private static FactsBuilder facts(String quarter) {
        return new FactsBuilder(quarter);
    }

    private static FinMindFinancialStatementDto fs(String date, String type, String value) {
        return new FinMindFinancialStatementDto(LocalDate.parse(date), "1216", type, new BigDecimal(value), null);
    }

    private static FinMindCashFlowsStatementDto cf(String date, String type, String value) {
        return new FinMindCashFlowsStatementDto(LocalDate.parse(date), "1216", type, new BigDecimal(value), null);
    }

    /** 減少測試裡建構 19 個欄位 record 的樣板碼。 */
    private static final class FactsBuilder {
        private final String quarter;
        private BigDecimal revenue;
        private BigDecimal epsQuarter;
        private BigDecimal operatingIncome;
        private BigDecimal preTaxIncome;
        private BigDecimal incomeAfterTaxes;
        private BigDecimal otherComprehensiveIncome;
        private BigDecimal grossProfit;
        private BigDecimal accountsReceivableNet;
        private BigDecimal inventories;
        private BigDecimal liabilities;
        private BigDecimal totalAssets;
        private BigDecimal currentAssets;
        private BigDecimal currentLiabilities;
        private BigDecimal capitalStock;
        private BigDecimal equity;
        private BigDecimal operatingCashFlow;
        private BigDecimal capex;
        private BigDecimal interestExpense;

        FactsBuilder(String quarter) {
            this.quarter = quarter;
        }

        FactsBuilder revenue(String v) { this.revenue = new BigDecimal(v); return this; }
        FactsBuilder epsQuarter(String v) { this.epsQuarter = new BigDecimal(v); return this; }
        FactsBuilder operatingIncome(String v) { this.operatingIncome = new BigDecimal(v); return this; }
        FactsBuilder preTaxIncome(String v) { this.preTaxIncome = new BigDecimal(v); return this; }
        FactsBuilder incomeAfterTaxes(String v) { this.incomeAfterTaxes = new BigDecimal(v); return this; }
        FactsBuilder accountsReceivableNet(String v) { this.accountsReceivableNet = new BigDecimal(v); return this; }
        FactsBuilder inventories(String v) { this.inventories = new BigDecimal(v); return this; }
        FactsBuilder capitalStock(String v) { this.capitalStock = new BigDecimal(v); return this; }
        FactsBuilder equity(String v) { this.equity = new BigDecimal(v); return this; }
        FactsBuilder operatingCashFlow(String v) { this.operatingCashFlow = new BigDecimal(v); return this; }
        FactsBuilder capex(String v) { this.capex = new BigDecimal(v); return this; }
        FactsBuilder interestExpense(String v) { this.interestExpense = new BigDecimal(v); return this; }

        QuarterlyFinancialFacts build() {
            return new QuarterlyFinancialFacts(
                    quarter, revenue, epsQuarter, operatingIncome, preTaxIncome, incomeAfterTaxes,
                    otherComprehensiveIncome, grossProfit, accountsReceivableNet, inventories, liabilities,
                    totalAssets, currentAssets, currentLiabilities, capitalStock, equity, operatingCashFlow,
                    capex, interestExpense);
        }
    }
}
