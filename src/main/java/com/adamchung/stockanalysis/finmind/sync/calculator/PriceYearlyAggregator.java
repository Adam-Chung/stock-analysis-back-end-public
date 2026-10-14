package com.adamchung.stockanalysis.finmind.sync.calculator;

import com.adamchung.stockanalysis.finmind.dto.FinMindStockPriceDto;
import com.adamchung.stockanalysis.valuation.dto.YearlyPriceStats;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 將 FinMind 每日 TaiwanStockPrice 聚合為 stock_price_yearly 年度統計（DB-01 4.2／DEC-24）。
 *
 * <p>每日 close、max、min 必須全部大於 0 才是有效交易日；無效日期不會進入平均收盤價的分子或
 * 分母，也不會影響年度最高／最低價。呼叫端仍負責決定哪些完整年度可以落地。
 */
public final class PriceYearlyAggregator {

    private static final int SCALE = 4;
    private static final RoundingMode ROUNDING = RoundingMode.HALF_UP;

    /** 工具類別不允許建立實例。 */
    private PriceYearlyAggregator() {
    }

    /**
     * 將有效的每日股價依年份聚合。
     *
     * @param dailyPrices FinMind 每日股價
     * @return 按年份遞增排序的年度統計
     */
    public static List<YearlyPriceStats> aggregate(List<FinMindStockPriceDto> dailyPrices) {
        Map<Integer, List<FinMindStockPriceDto>> byYear =
                dailyPrices.stream()
                        .filter(PriceYearlyAggregator::hasValidPrices)
                        .collect(Collectors.groupingBy(p -> p.date().getYear()));

        return byYear.entrySet().stream()
                .map(entry -> toYearlyStats(entry.getKey(), entry.getValue()))
                .sorted(Comparator.comparingInt(YearlyPriceStats::year))
                .toList();
    }

    /** 計算單一年度的平均收盤價、最高價與最低價。 */
    private static YearlyPriceStats toYearlyStats(int year, List<FinMindStockPriceDto> rows) {
        List<BigDecimal> closes = rows.stream().map(FinMindStockPriceDto::close).toList();
        List<BigDecimal> highs = rows.stream().map(FinMindStockPriceDto::max).toList();
        List<BigDecimal> lows = rows.stream().map(FinMindStockPriceDto::min).toList();
        BigDecimal avgClose = average(closes);
        BigDecimal high = highs.stream().max(BigDecimal::compareTo).orElseThrow();
        BigDecimal low = lows.stream().min(BigDecimal::compareTo).orElseThrow();

        return new YearlyPriceStats(year, avgClose, high.setScale(SCALE, ROUNDING), low.setScale(SCALE, ROUNDING));
    }

    /** 只有年度統計會使用的三個價格都為正數時，該日才可參與聚合。 */
    private static boolean hasValidPrices(FinMindStockPriceDto row) {
        return isPositive(row.close()) && isPositive(row.max()) && isPositive(row.min());
    }

    /** 判斷價格是否存在且大於 0。 */
    private static boolean isPositive(BigDecimal value) {
        return value != null && value.compareTo(BigDecimal.ZERO) > 0;
    }

    /** 以有效交易日數量作為分母計算平均值。 */
    private static BigDecimal average(List<BigDecimal> values) {
        BigDecimal sum = values.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        return sum.divide(BigDecimal.valueOf(values.size()), SCALE, ROUNDING);
    }
}
