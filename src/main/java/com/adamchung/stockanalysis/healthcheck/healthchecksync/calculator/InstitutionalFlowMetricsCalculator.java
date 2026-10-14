package com.adamchung.stockanalysis.healthcheck.healthchecksync.calculator;

import com.adamchung.stockanalysis.externaldata.dto.QuarterRange;
import com.adamchung.stockanalysis.finmind.dto.FinMindInstitutionalInvestorsBuySellDto;
import com.adamchung.stockanalysis.finmind.dto.FinMindShareholdingDto;
import com.adamchung.stockanalysis.healthcheck.healthchecksync.dto.QuarterlyMetricValue;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/**
 * 銀行模板「籌碼變化(季)」大項的三個細項（SA-04 §3），純函式。已對真實 API 驗證
 * {@code TaiwanStockInstitutionalInvestorsBuySell} 的 {@code name} 分類值：
 * {@code Foreign_Investor}／{@code Foreign_Dealer_Self}（合稱外資及陸資）、
 * {@code Investment_Trust}（投信）、{@code Dealer_self}／{@code Dealer_Hedging}（合稱自營商）。
 */
public final class InstitutionalFlowMetricsCalculator {

    private static final Set<String> FOREIGN_CATEGORIES = Set.of("Foreign_Investor", "Foreign_Dealer_Self");
    private static final BigDecimal SHARES_PER_LOT = BigDecimal.valueOf(1000);
    private static final int SCALE = 4;

    private InstitutionalFlowMetricsCalculator() {
    }

    public static List<QuarterlyMetricValue> foreignNetBuySellLots(
            List<FinMindInstitutionalInvestorsBuySellDto> rows, List<QuarterRange> quarters) {
        return netBuySellByQuarter(rows, quarters, row -> FOREIGN_CATEGORIES.contains(row.name()));
    }

    public static List<QuarterlyMetricValue> institutionalNetBuySellLots(
            List<FinMindInstitutionalInvestorsBuySellDto> rows, List<QuarterRange> quarters) {
        return netBuySellByQuarter(rows, quarters, row -> true);
    }

    private static List<QuarterlyMetricValue> netBuySellByQuarter(
            List<FinMindInstitutionalInvestorsBuySellDto> rows, List<QuarterRange> quarters,
            java.util.function.Predicate<FinMindInstitutionalInvestorsBuySellDto> filter) {
        List<QuarterlyMetricValue> results = new ArrayList<>();
        for (QuarterRange quarter : quarters) {
            long netShares = rows.stream()
                    .filter(row -> filter.test(row) && withinRange(row.date(), quarter))
                    .mapToLong(row -> (row.buy() == null ? 0 : row.buy()) - (row.sell() == null ? 0 : row.sell()))
                    .sum();
            BigDecimal netLots = BigDecimal.valueOf(netShares).divide(SHARES_PER_LOT, SCALE, RoundingMode.HALF_UP);
            results.add(new QuarterlyMetricValue(quarter.label(), netLots));
        }
        return results;
    }

    /** 外資持股(%)：取每季季底（或季底前最近一筆有資料的交易日）的值。 */
    public static List<QuarterlyMetricValue> foreignShareholdingRatio(
            List<FinMindShareholdingDto> rows, List<QuarterRange> quarters) {
        List<QuarterlyMetricValue> results = new ArrayList<>();
        for (QuarterRange quarter : quarters) {
            rows.stream()
                    .filter(row -> row.date() != null && !row.date().isAfter(quarter.end())
                            && row.foreignInvestmentSharesRatio() != null)
                    .max(Comparator.comparing(FinMindShareholdingDto::date))
                    .ifPresent(latest -> results.add(new QuarterlyMetricValue(quarter.label(), latest.foreignInvestmentSharesRatio())));
        }
        return results;
    }

    private static boolean withinRange(java.time.LocalDate date, QuarterRange quarter) {
        return date != null && !date.isBefore(quarter.start()) && !date.isAfter(quarter.end());
    }
}
