package com.adamchung.stockanalysis.finmind.sync.calculator;

import com.adamchung.stockanalysis.finmind.dto.FinMindStockValuationDto;
import com.adamchung.stockanalysis.valuation.dto.YearlyValuationStats;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 把 FinMind 逐日的 TaiwanStockPER 資料列（PER/PBR 都是 FinMind 官方欄位）轉成
 * stock_valuation_yearly 形狀的年度統計（DB-01 4.3／DEC-24）。某年度若某個指標完全沒有非 null
 * 值，該欄位就留 null，不會預設成 0——虧損股某一年沒有正的 PER 是合理狀態。
 *
 * <p>不處理 dividend_yield：十年平均殖利率改為請求當下依 {@link com.adamchung.stockanalysis
 * .valuation.DividendYieldCalculator} 即時計算，不再信任 FinMind 這個欄位（見
 * docs/agent/DECISIONS.md 對應決策），這裡回傳的 {@link YearlyValuationStats#avgDividendYield()}
 * 一律是 {@code null}，呼叫端在組裝計算輸入時會另外覆寫。
 */
public final class ValuationYearlyAggregator {

    private static final int SCALE = 4;
    private static final RoundingMode ROUNDING = RoundingMode.HALF_UP;

    private ValuationYearlyAggregator() {
    }

    /**
     * 將逐日估值資料聚合成年度統計。
     *
     * @param dailyValuations 逐日估值資料列
     * @return 依年度排序的年度統計清單
     */
    public static List<YearlyValuationStats> aggregate(List<FinMindStockValuationDto> dailyValuations) {
        Map<Integer, List<FinMindStockValuationDto>> byYear =
                dailyValuations.stream().collect(Collectors.groupingBy(v -> v.date().getYear()));

        return byYear.entrySet().stream()
                .map(entry -> toYearlyStats(entry.getKey(), entry.getValue()))
                .sorted(Comparator.comparingInt(YearlyValuationStats::year))
                .toList();
    }

    private static YearlyValuationStats toYearlyStats(int year, List<FinMindStockValuationDto> rows) {
        List<BigDecimal> pers = values(rows, FinMindStockValuationDto::per);
        List<BigDecimal> pbrs = values(rows, FinMindStockValuationDto::pbr);

        return new YearlyValuationStats(
                year,
                average(pers),
                min(pers),
                max(pers),
                average(pbrs),
                min(pbrs),
                max(pbrs),
                null);
    }

    private static List<BigDecimal> values(
            List<FinMindStockValuationDto> rows, Function<FinMindStockValuationDto, BigDecimal> extractor) {
        return rows.stream().map(extractor).filter(Objects::nonNull).toList();
    }

    private static BigDecimal average(List<BigDecimal> values) {
        if (values.isEmpty()) {
            return null;
        }
        BigDecimal sum = values.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        return sum.divide(BigDecimal.valueOf(values.size()), SCALE, ROUNDING);
    }

    private static BigDecimal min(List<BigDecimal> values) {
        return values.isEmpty() ? null : values.stream().min(BigDecimal::compareTo).orElseThrow().setScale(SCALE, ROUNDING);
    }

    private static BigDecimal max(List<BigDecimal> values) {
        return values.isEmpty() ? null : values.stream().max(BigDecimal::compareTo).orElseThrow().setScale(SCALE, ROUNDING);
    }
}
