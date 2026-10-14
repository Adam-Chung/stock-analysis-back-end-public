package com.adamchung.stockanalysis.healthcheck.healthchecksync.calculator;

import com.adamchung.stockanalysis.finmind.dto.FinMindBalanceSheetDto;
import com.adamchung.stockanalysis.finmind.dto.FinMindCashFlowsStatementDto;
import com.adamchung.stockanalysis.finmind.dto.FinMindFinancialStatementDto;
import com.adamchung.stockanalysis.healthcheck.healthchecksync.dto.QuarterlyFinancialFacts;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class QuarterlyFactsJoinerTest {

    @Test
    void joins_three_sources_by_quarter_label_newest_first() {
        List<FinMindFinancialStatementDto> statements = List.of(
                fs("2024-12-31", "Revenue", "100"),
                fs("2024-09-30", "Revenue", "90"));
        List<FinMindBalanceSheetDto> balanceSheet = List.of(
                bs("2024-12-31", "TotalAssets", "1000"));
        // Q3→Q4 相鄰累計值，才能讓 Q4 的年度累計差分正確換算（DEC-057）。
        List<FinMindCashFlowsStatementDto> cashFlows = List.of(
                cf("2024-09-30", "NetCashInflowFromOperatingActivities", "30"),
                cf("2024-12-31", "NetCashInflowFromOperatingActivities", "50"));

        List<QuarterlyFinancialFacts> facts = QuarterlyFactsJoiner.join(statements, balanceSheet, cashFlows);

        assertThat(facts).hasSize(2);
        assertThat(facts.get(0).quarter()).isEqualTo("2024Q4");
        assertThat(facts.get(0).revenue()).isEqualByComparingTo("100");
        assertThat(facts.get(0).totalAssets()).isEqualByComparingTo("1000");
        assertThat(facts.get(0).operatingCashFlow()).isEqualByComparingTo("20"); // 50(累計Q4)-30(累計Q3)
        assertThat(facts.get(1).quarter()).isEqualTo("2024Q3");
        assertThat(facts.get(1).totalAssets()).isNull();
        assertThat(facts.get(1).operatingCashFlow()).isNull(); // Q3 沒有 Q2 前值可比對，無法可靠換算
    }

    @Test
    void cash_flow_statement_values_are_decoded_from_year_to_date_cumulative_to_single_quarter() {
        // 真實驗證：2884，2025年 NetCashInflowFromOperatingActivities 逐季原始值
        // 234.08986億(Q1) → 29.69273億(Q2) → 212.45281億(Q3) → 365.61011億(Q4)，
        // 呈現年度累計走勢；換算後單季 Q4 應為 365.61011-212.45281=153.1573億，
        // 與使用者回報的正確值 153.2億吻合。
        List<FinMindCashFlowsStatementDto> cashFlows = List.of(
                cf("2025-03-31", "NetCashInflowFromOperatingActivities", "23408986000"),
                cf("2025-06-30", "NetCashInflowFromOperatingActivities", "2969273000"),
                cf("2025-09-30", "NetCashInflowFromOperatingActivities", "21245281000"),
                cf("2025-12-31", "NetCashInflowFromOperatingActivities", "36561011000"));

        List<QuarterlyFinancialFacts> facts = QuarterlyFactsJoiner.join(List.of(), List.of(), cashFlows);

        QuarterlyFinancialFacts q1 = factsFor(facts, "2025Q1");
        QuarterlyFinancialFacts q2 = factsFor(facts, "2025Q2");
        QuarterlyFinancialFacts q3 = factsFor(facts, "2025Q3");
        QuarterlyFinancialFacts q4 = factsFor(facts, "2025Q4");
        assertThat(q1.operatingCashFlow()).isEqualByComparingTo("23408986000"); // Q1累計＝Q1單季本身
        assertThat(q2.operatingCashFlow()).isEqualByComparingTo("-20439713000"); // 2969273000-23408986000
        assertThat(q3.operatingCashFlow()).isEqualByComparingTo("18276008000"); // 21245281000-2969273000
        assertThat(q4.operatingCashFlow()).isEqualByComparingTo("15315730000"); // 36561011000-21245281000
    }

    @Test
    void cumulative_value_resets_at_the_start_of_each_fiscal_year() {
        List<FinMindCashFlowsStatementDto> cashFlows = List.of(
                cf("2024-03-31", "NetCashInflowFromOperatingActivities", "10"),
                cf("2024-06-30", "NetCashInflowFromOperatingActivities", "25"),
                cf("2024-09-30", "NetCashInflowFromOperatingActivities", "40"),
                cf("2024-12-31", "NetCashInflowFromOperatingActivities", "59294132000"), // 2024全年累計
                cf("2025-03-31", "NetCashInflowFromOperatingActivities", "23408986000")); // 2025Q1，累計重置後的獨立起點

        List<QuarterlyFinancialFacts> facts = QuarterlyFactsJoiner.join(List.of(), List.of(), cashFlows);

        // 2025Q1 沿用原始值，不會被誤認為要跟 2024Q4 的巨大累計值做差分
        assertThat(factsFor(facts, "2025Q1").operatingCashFlow()).isEqualByComparingTo("23408986000");
        // 2024Q4 有完整的年內 Q1→Q4 累計鏈，正常換算＝2024累計－2024Q3累計
        assertThat(factsFor(facts, "2024Q4").operatingCashFlow()).isEqualByComparingTo("59294131960");
    }

    private QuarterlyFinancialFacts factsFor(List<QuarterlyFinancialFacts> facts, String quarter) {
        return facts.stream().filter(f -> f.quarter().equals(quarter)).findFirst()
                .orElseThrow(() -> new AssertionError("No facts for quarter " + quarter));
    }

    @Test
    void missing_type_in_a_quarter_yields_null_field_not_exception() {
        List<FinMindFinancialStatementDto> statements = List.of(fs("2024-12-31", "Revenue", "100"));

        List<QuarterlyFinancialFacts> facts = QuarterlyFactsJoiner.join(statements, List.of(), List.of());

        assertThat(facts).hasSize(1);
        assertThat(facts.get(0).incomeAfterTaxes()).isNull();
        assertThat(facts.get(0).capitalStock()).isNull();
    }

    @Test
    void falls_back_to_bank_field_name_for_income_after_taxes() {
        List<FinMindFinancialStatementDto> statements = List.of(fs("2024-12-31", "IncomeAfterTax", "500"));

        List<QuarterlyFinancialFacts> facts = QuarterlyFactsJoiner.join(statements, List.of(), List.of());

        assertThat(facts.get(0).incomeAfterTaxes()).isEqualByComparingTo("500");
    }

    @Test
    void prefers_general_income_after_taxes_field_over_bank_alias_when_both_present() {
        List<FinMindFinancialStatementDto> statements = List.of(
                fs("2024-12-31", "IncomeAfterTaxes", "500"),
                fs("2024-12-31", "IncomeAfterTax", "999"));

        List<QuarterlyFinancialFacts> facts = QuarterlyFactsJoiner.join(statements, List.of(), List.of());

        assertThat(facts.get(0).incomeAfterTaxes()).isEqualByComparingTo("500");
    }

    @Test
    void falls_back_to_bank_field_name_for_capital_stock() {
        List<FinMindBalanceSheetDto> balanceSheet = List.of(bs("2024-12-31", "OrdinaryShare", "15664000000"));

        List<QuarterlyFinancialFacts> facts = QuarterlyFactsJoiner.join(List.of(), balanceSheet, List.of());

        assertThat(facts.get(0).capitalStock()).isEqualByComparingTo("15664000000");
    }

    @Test
    void quarterLabel_computes_calendar_quarter_from_date() {
        assertThat(QuarterlyFactsJoiner.quarterLabel(LocalDate.of(2024, 3, 31))).isEqualTo("2024Q1");
        assertThat(QuarterlyFactsJoiner.quarterLabel(LocalDate.of(2024, 4, 1))).isEqualTo("2024Q2");
        assertThat(QuarterlyFactsJoiner.quarterLabel(LocalDate.of(2024, 12, 31))).isEqualTo("2024Q4");
    }

    private FinMindFinancialStatementDto fs(String date, String type, String value) {
        return new FinMindFinancialStatementDto(LocalDate.parse(date), "2330", type, new BigDecimal(value), null);
    }

    private FinMindBalanceSheetDto bs(String date, String type, String value) {
        return new FinMindBalanceSheetDto(LocalDate.parse(date), "2330", type, new BigDecimal(value), null);
    }

    private FinMindCashFlowsStatementDto cf(String date, String type, String value) {
        return new FinMindCashFlowsStatementDto(LocalDate.parse(date), "2330", type, new BigDecimal(value), null);
    }
}
