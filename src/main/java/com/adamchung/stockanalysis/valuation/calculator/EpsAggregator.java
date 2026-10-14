package com.adamchung.stockanalysis.valuation.calculator;

import com.adamchung.stockanalysis.valuation.TargetPriceValuationService;
import com.adamchung.stockanalysis.valuation.dto.QuarterlyEps;
import com.adamchung.stockanalysis.valuation.dto.YearlyEps;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 把原始的季度 EPS 資料（stock_financial_statement，type='EPS'）轉成年度 EPS 數字。
 * 獨立拆出來跟 {@link TargetPriceValuationService} 分開，讓季轉年的加總邏輯可以單獨測試。
 *
 * 只有四季都齊全的年度才會產出——SA-01 DEC-9 規定任何「近X年平均」計算都要排除不完整/TTM
 * 的年度。
 */
public final class EpsAggregator {

    private static final int QUARTERS_PER_YEAR = 4;

    private EpsAggregator() {
    }

    /**
     * 把季度 EPS 加總成年度 EPS，只保留四季齊全的年度。
     *
     * @param quarterlyEps 季度 EPS 資料
     * @return 依年度排序的年度 EPS 清單
     */
    public static List<YearlyEps> toAnnual(List<QuarterlyEps> quarterlyEps) {
        Map<Integer, List<QuarterlyEps>> byYear = quarterlyEps.stream()
                .collect(Collectors.groupingBy(q -> q.statementDate().getYear()));

        return byYear.entrySet().stream()
                .filter(entry -> entry.getValue().size() == QUARTERS_PER_YEAR)
                .map(entry -> new YearlyEps(
                        entry.getKey(),
                        entry.getValue().stream()
                                .map(QuarterlyEps::eps)
                                .reduce(BigDecimal.ZERO, BigDecimal::add)))
                .sorted(Comparator.comparingInt(YearlyEps::year))
                .toList();
    }
}
