package com.adamchung.stockanalysis.healthcheck.healthchecksync.calculator;

import com.adamchung.stockanalysis.finmind.dto.FinMindMonthRevenueDto;
import com.adamchung.stockanalysis.common.PercentageMath;
import com.adamchung.stockanalysis.healthcheck.healthchecksync.dto.QuarterlyMetricValue;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * 銀行模板「月營收」大項的三個細項（SA-04 §3），純函式。{@code stock_health_snapshot.quarter}
 * 對這三個 metric_key 例外存 {@code 'YYYY-MM'}（{@link com.adamchung.stockanalysis.entity.StockHealthSnapshotEntity}
 * 類別註解）。月增/年增缺對照月份時回傳 {@code null}（該期不落地），不是丟例外或除以零。
 */
public final class MonthRevenueMetricsCalculator {

    private static final BigDecimal HUNDRED_MILLION = BigDecimal.valueOf(100_000_000);
    private static final int SCALE = 4;

    private MonthRevenueMetricsCalculator() {
    }

    /** 依 revenue_year/revenue_month 分月彙整（同月可能有多筆修正資料，取最後一筆）。 */
    private static Map<String, FinMindMonthRevenueDto> byMonth(List<FinMindMonthRevenueDto> rows) {
        Map<String, FinMindMonthRevenueDto> byMonth = new TreeMap<>();
        for (FinMindMonthRevenueDto row : rows) {
            if (row.revenueYear() == null || row.revenueMonth() == null || row.revenue() == null) {
                continue;
            }
            byMonth.put(monthLabel(row.revenueYear(), row.revenueMonth()), row);
        }
        return byMonth;
    }

    public static List<QuarterlyMetricValue> monthlyRevenue(List<FinMindMonthRevenueDto> rows) {
        Map<String, FinMindMonthRevenueDto> byMonth = byMonth(rows);
        List<QuarterlyMetricValue> results = new ArrayList<>();
        for (java.util.Map.Entry<String, FinMindMonthRevenueDto> entry : byMonth.entrySet()) {
            BigDecimal billion = BigDecimal.valueOf(entry.getValue().revenue())
                    .divide(HUNDRED_MILLION, SCALE, RoundingMode.HALF_UP);
            results.add(new QuarterlyMetricValue(entry.getKey(), billion));
        }
        return descending(results);
    }

    public static List<QuarterlyMetricValue> momGrowth(List<FinMindMonthRevenueDto> rows) {
        Map<String, FinMindMonthRevenueDto> byMonth = byMonth(rows);
        List<QuarterlyMetricValue> results = new ArrayList<>();
        for (java.util.Map.Entry<String, FinMindMonthRevenueDto> entry : byMonth.entrySet()) {
            FinMindMonthRevenueDto current = entry.getValue();
            FinMindMonthRevenueDto prior = byMonth.get(previousMonthLabel(current.revenueYear(), current.revenueMonth()));
            BigDecimal ratio = growthRatio(current, prior);
            if (ratio != null) {
                results.add(new QuarterlyMetricValue(entry.getKey(), ratio));
            }
        }
        return descending(results);
    }

    public static List<QuarterlyMetricValue> yoyGrowth(List<FinMindMonthRevenueDto> rows) {
        Map<String, FinMindMonthRevenueDto> byMonth = byMonth(rows);
        List<QuarterlyMetricValue> results = new ArrayList<>();
        for (java.util.Map.Entry<String, FinMindMonthRevenueDto> entry : byMonth.entrySet()) {
            FinMindMonthRevenueDto current = entry.getValue();
            FinMindMonthRevenueDto prior = byMonth.get(monthLabel(current.revenueYear() - 1, current.revenueMonth()));
            BigDecimal ratio = growthRatio(current, prior);
            if (ratio != null) {
                results.add(new QuarterlyMetricValue(entry.getKey(), ratio));
            }
        }
        return descending(results);
    }

    private static BigDecimal growthRatio(FinMindMonthRevenueDto current, FinMindMonthRevenueDto prior) {
        if (prior == null || prior.revenue() == null || prior.revenue() == 0 || current.revenue() == null) {
            return null;
        }
        BigDecimal currentValue = BigDecimal.valueOf(current.revenue());
        BigDecimal priorValue = BigDecimal.valueOf(prior.revenue());
        return PercentageMath.ratioAsPercent(currentValue.subtract(priorValue), priorValue, SCALE);
    }

    private static String monthLabel(int year, int month) {
        return "%04d-%02d".formatted(year, month);
    }

    private static String previousMonthLabel(int year, int month) {
        return month == 1 ? monthLabel(year - 1, 12) : monthLabel(year, month - 1);
    }

    private static List<QuarterlyMetricValue> descending(List<QuarterlyMetricValue> results) {
        results.sort((a, b) -> b.period().compareTo(a.period()));
        return results;
    }
}
