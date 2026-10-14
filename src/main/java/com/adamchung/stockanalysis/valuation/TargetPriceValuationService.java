package com.adamchung.stockanalysis.valuation;

import com.adamchung.stockanalysis.valuation.dto.TierPrice;
import com.adamchung.stockanalysis.valuation.dto.ValuationDataCoverage;
import com.adamchung.stockanalysis.valuation.dto.ValuationInput;
import com.adamchung.stockanalysis.valuation.dto.ValuationResult;
import com.adamchung.stockanalysis.valuation.dto.YearlyEps;
import com.adamchung.stockanalysis.valuation.dto.YearlyPriceStats;
import com.adamchung.stockanalysis.valuation.dto.YearlyValuationStats;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.ToIntFunction;
import java.util.stream.Collectors;

/**
 * 純計算服務，實作 SA-01「股票目標價」的公式（docs/spec/SA-01-stock-target-price.md 3.3）。
 * 輸入是已經聚合好的年度資料，形狀對應 DB-01 的
 * stock_price_yearly/stock_valuation_yearly/stock_financial_statement——這個類別沒有任何
 * DB/HTTP 相依，可以完全獨立做單元測試。
 */
@Service
public class TargetPriceValuationService {

    private static final int CALC_SCALE = 8;
    private static final int RESULT_SCALE = 4;
    private static final RoundingMode ROUNDING = RoundingMode.HALF_UP;
    private static final int TEN_YEAR_WINDOW = 10;
    private static final int REASONABLE_TIER_LEVEL = 3;

    private static final String[] TIER_LABELS = {
            "1便宜價", "2次便宜", "3合理價", "4昂貴價", "5超貴價", "6爆炸貴",
    };

    /**
     * 計算單一股票的 SA-01 3.3 目標價結果。
     *
     * @param input 已聚合的年度資料與使用者設定
     * @return 目標價計算結果
     * @throws IllegalArgumentException 設定值超出合法範圍時拋出
     * @throws InsufficientValuationDataException 缺乏足夠年度資料時拋出
     */
    public ValuationResult calculate(ValuationInput input) {
        validate(input);

        // 最近10年價格和估值統計
        List<YearlyPriceStats> prices = recentYears(input.yearlyPrices(), YearlyPriceStats::year, TEN_YEAR_WINDOW);
        List<YearlyValuationStats> valuations =
                recentYears(input.yearlyValuations(), YearlyValuationStats::year, TEN_YEAR_WINDOW);

        if (prices.isEmpty() || valuations.isEmpty()) {
            throw new InsufficientValuationDataException(
                    "No complete-year price/valuation data available to compute target prices");
        }

        // 10年低和10年高
        BigDecimal low10 = minWithCurrentYear(
                prices.stream().map(YearlyPriceStats::lowPrice).min(BigDecimal::compareTo).orElseThrow(),
                input.currentYearLowPrice());
        BigDecimal high10 = maxWithCurrentYear(
                prices.stream().map(YearlyPriceStats::highPrice).max(BigDecimal::compareTo).orElseThrow(),
                input.currentYearHighPrice());
        // 10年平均殖利率
        List<BigDecimal> yields = usableValues(
                valuations.stream().map(YearlyValuationStats::avgDividendYield).toList());
        // 10年低PER
        List<BigDecimal> lowPers = usableValues(valuations.stream().map(YearlyValuationStats::lowPer).toList());
        // 10年平均PER
        List<BigDecimal> avgPers = usableValues(valuations.stream().map(YearlyValuationStats::avgPer).toList());
        // 10年高PER
        List<BigDecimal> highPers = usableValues(valuations.stream().map(YearlyValuationStats::highPer).toList());
        // 10年低PBR
        List<BigDecimal> lowPbrs = usableValues(valuations.stream().map(YearlyValuationStats::lowPbr).toList());
        // 10年平均PBR
        List<BigDecimal> avgPbrs = usableValues(valuations.stream().map(YearlyValuationStats::avgPbr).toList());
        // 10年高PBR
        List<BigDecimal> highPbrs = usableValues(valuations.stream().map(YearlyValuationStats::highPbr).toList());

        BigDecimal tenYearAvgYield = average(yields, "dividend yield");
        boolean useEps = input.epsAvgYears() > 0;
        boolean useBps = input.bpsAvgYears() > 0;
        BigDecimal tenYearAvgLowPer = useEps ? average(lowPers, "low PER") : null;
        BigDecimal tenYearAvgPer = useEps ? average(avgPers, "average PER") : null;
        BigDecimal tenYearAvgHighPer = useEps ? average(highPers, "high PER") : null;
        BigDecimal tenYearAvgLowPbr = useBps ? average(lowPbrs, "low PBR") : null;
        BigDecimal tenYearAvgPbr = useBps ? average(avgPbrs, "average PBR") : null;
        BigDecimal tenYearAvgHighPbr = useBps ? average(highPbrs, "high PBR") : null;

        List<BigDecimal> epsValues = useEps
                ? recentEpsValues(input.yearlyEps(), input.epsAvgYears())
                : List.of();
        List<BigDecimal> bpsValues = useBps
                ? recentDerivedBpsValues(prices, valuations, input.bpsAvgYears())
                : List.of();
        BigDecimal epsXYearAvg = useEps ? average(epsValues, "annual EPS") : null;
        BigDecimal bpsXYearAvg = useBps ? average(bpsValues, "derived BPS") : null;

        // 兩個分支都啟用時取平均；任一分支不參考時，直接採另一分支且不除以2。
        BigDecimal cheapPrice = combineValuationBranches(
                epsXYearAvg, tenYearAvgLowPer, bpsXYearAvg, tenYearAvgLowPbr);
        BigDecimal reasonablePrice = combineValuationBranches(
                epsXYearAvg, tenYearAvgPer, bpsXYearAvg, tenYearAvgPbr);
        BigDecimal expensivePrice = combineValuationBranches(
                epsXYearAvg, tenYearAvgHighPer, bpsXYearAvg, tenYearAvgHighPbr);
        // 次便宜價 = (便宜價 + 合理價) / 2
        BigDecimal semiCheapPrice = averageOfTwo(cheapPrice, reasonablePrice);
        // 超貴價 = 10年高 - 昂貴價
        BigDecimal highMinusExpensive = high10.subtract(expensivePrice);
        // 爆炸貴價 = (10年高 - 昂貴價) * 2 / 3 + 昂貴價
        BigDecimal veryExpensivePrice = highMinusExpensive
                .divide(BigDecimal.valueOf(3), CALC_SCALE, ROUNDING)
                .add(expensivePrice);
        BigDecimal explosiveExpensivePrice = highMinusExpensive
                .multiply(BigDecimal.valueOf(2))
                .divide(BigDecimal.valueOf(3), CALC_SCALE, ROUNDING)
                .add(expensivePrice);

        List<TierPrice> tiers = List.of(
                tier(1, cheapPrice),
                tier(2, semiCheapPrice),
                tier(3, reasonablePrice),
                tier(4, expensivePrice),
                tier(5, veryExpensivePrice),
                tier(6, explosiveExpensivePrice));
        requirePositiveTiers(tiers);

        // 後續公式引用的是已經四捨五入、顯示用的檔位價格，不是內部尚未四捨五入的原始數值——
        // 這對應 SA-01 的用字（「與目標價差比 = ... ÷ 目標價」），這裡的「目標價」指的是
        // 已經決定好的顯示值，不是隱藏的內部數字。
        BigDecimal targetPrice = tiers.get(input.targetPriceLevel() - 1).price();
        BigDecimal reasonablePriceForSellCalc = tiers.get(REASONABLE_TIER_LEVEL - 1).price();

        BigDecimal gapRatio = input.currentPrice().divide(targetPrice, CALC_SCALE, ROUNDING).subtract(BigDecimal.ONE);
        BigDecimal plusOneYearDividendPrice = targetPrice.multiply(BigDecimal.ONE.add(tenYearAvgYield));
        BigDecimal yearsToRecover = computeYearsViaYield(gapRatio, tenYearAvgYield);

        BigDecimal sellRatio = input.currentPrice()
                .divide(reasonablePriceForSellCalc, CALC_SCALE, ROUNDING)
                .subtract(BigDecimal.ONE);
        BigDecimal yearsOfProfitIfSold = computeLinearYears(sellRatio, tenYearAvgYield);

        Integer frontYear = findFrontYear(prices, targetPrice, input.currentYearLowPrice());

        return new ValuationResult(
                round(low10),
                round(high10),
                round(tenYearAvgYield),
                tiers,
                targetPrice,
                round(gapRatio),
                round(plusOneYearDividendPrice),
                yearsToRecover,
                yearsOfProfitIfSold,
                frontYear,
                new ValuationDataCoverage(
                        useEps ? lowPers.size() : 0,
                        useEps ? avgPers.size() : 0,
                        useEps ? highPers.size() : 0,
                        useBps ? lowPbrs.size() : 0,
                        useBps ? avgPbrs.size() : 0,
                        useBps ? highPbrs.size() : 0,
                        yields.size(),
                        epsValues.size(),
                        bpsValues.size()));
    }

    /**
     * 把當年度（尚未結束）的即時最低價併入10年低，讓今年創的新低不會被忽略。
     *
     * @param completeYearsLow 完整曆年算出的最低價
     * @param currentYearLow 當年度至今的即時最低價，可為 {@code null}
     * @return 併入當年度後的10年低
     */
    private BigDecimal minWithCurrentYear(BigDecimal completeYearsLow, BigDecimal currentYearLow) {
        if (currentYearLow == null) {
            return completeYearsLow;
        }
        return completeYearsLow.min(currentYearLow);
    }

    /**
     * 把當年度（尚未結束）的即時最高價併入10年高，讓今年創的新高不會被忽略。
     *
     * @param completeYearsHigh 完整曆年算出的最高價
     * @param currentYearHigh 當年度至今的即時最高價，可為 {@code null}
     * @return 併入當年度後的10年高
     */
    private BigDecimal maxWithCurrentYear(BigDecimal completeYearsHigh, BigDecimal currentYearHigh) {
        if (currentYearHigh == null) {
            return completeYearsHigh;
        }
        return completeYearsHigh.max(currentYearHigh);
    }

    /**
     * 六檔目標價都必須是正值才有意義（DEC-078）。連續虧損股的 EPS(X年平均)／推導 BPS 可能是
     * 負值，若因此算出 ≤0 的檔位，後續 {@code gapRatio}／{@code sellRatio} 這些除法會拿
     * ≤0 的檔位當分母：等於0時 {@link BigDecimal#divide} 直接丟未受控的
     * {@link ArithmeticException}（對外變成 500）；小於0時則不會拋例外，但算出的
     * 「與目標價差比」「X年內買回」「賣賺幾年」全部語意錯誤（虧損股的「目標價」變成負的，
     * 畫面會顯示很奇怪的數字卻不會被察覺）。這裡在檔位算完、進入任何除法之前就先攔下來，
     * 用跟「資料不足」同一類的受控例外（{@link InsufficientValuationDataException}），
     * 而不是讓下游默默吃到負值/零值或直接對外噴 500。
     *
     * @param tiers 六檔目標價
     * @throws InsufficientValuationDataException 任一檔位 ≤0 時拋出
     */
    private void requirePositiveTiers(List<TierPrice> tiers) {
        for (TierPrice tier : tiers) {
            if (tier.price().compareTo(BigDecimal.ZERO) <= 0) {
                throw new InsufficientValuationDataException(
                        "Computed target price tier " + tier.level() + " (" + tier.label() + ") is not positive ("
                                + tier.price() + "); EPS/BPS averages are likely negative for a loss-making stock");
            }
        }
    }

    /**
     * 驗證輸入設定值是否在合法範圍內。
     *
     * @param input 待驗證的輸入
     * @throws IllegalArgumentException 設定值超出合法範圍時拋出
     */
    private void validate(ValuationInput input) {
        if (input.targetPriceLevel() < 1 || input.targetPriceLevel() > 6) {
            throw new IllegalArgumentException("targetPriceLevel must be 1~6, got " + input.targetPriceLevel());
        }
        if (input.epsAvgYears() < 0 || input.bpsAvgYears() < 0) {
            throw new IllegalArgumentException("epsAvgYears/bpsAvgYears must not be negative");
        }
        if (input.epsAvgYears() == 0 && input.bpsAvgYears() == 0) {
            throw new IllegalArgumentException("EPS and BPS cannot both be excluded from valuation");
        }
    }

    /**
     * 計算單一（已獨立篩選過）指標序列的平均值。
     *
     * @param values 該指標的可用數值
     * @param metricName 指標名稱，資料不足時用於錯誤訊息
     * @return 算術平均值
     * @throws InsufficientValuationDataException 該指標完全沒有可用年度時拋出
     */
    private BigDecimal average(List<BigDecimal> values, String metricName) {
        if (values.isEmpty()) {
            throw new InsufficientValuationDataException("No usable " + metricName + " data available");
        }
        BigDecimal sum = values.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        return sum.divide(BigDecimal.valueOf(values.size()), CALC_SCALE, ROUNDING);
    }

    /**
     * 移除缺值，同時不讓原本各自獨立的 PER/PBR 指標序列互相影響。
     *
     * @param values 固定近年窗口內的指標數值
     * @return 保持原始順序的非 null 數值
     */
    private List<BigDecimal> usableValues(List<BigDecimal> values) {
        return values.stream().filter(Objects::nonNull).toList();
    }

    /**
     * 計算兩個估值分支的平均值。
     *
     * @param a PER 推導出的數值
     * @param b PBR 推導出的數值
     * @return 兩個分支的算術平均值
     */
    private BigDecimal averageOfTwo(BigDecimal a, BigDecimal b) {
        return a.add(b).divide(BigDecimal.valueOf(2), CALC_SCALE, ROUNDING);
    }

    /**
     * 合併 PER 與 PBR 估值分支；兩者皆有值時取平均，只啟用一者時直接回傳該分支結果。
     *
     * @param epsAverage EPS平均值；不參考 EPS 時為 {@code null}
     * @param perMultiple PER倍數；不參考 EPS 時為 {@code null}
     * @param bpsAverage BPS平均值；不參考 BPS 時為 {@code null}
     * @param pbrMultiple PBR倍數；不參考 BPS 時為 {@code null}
     * @return 依啟用分支合併後的估值
     */
    private BigDecimal combineValuationBranches(
            BigDecimal epsAverage, BigDecimal perMultiple, BigDecimal bpsAverage, BigDecimal pbrMultiple) {
        BigDecimal perValue = epsAverage == null ? null : epsAverage.multiply(perMultiple);
        BigDecimal pbrValue = bpsAverage == null ? null : bpsAverage.multiply(pbrMultiple);
        if (perValue == null) {
            return Objects.requireNonNull(pbrValue);
        }
        if (pbrValue == null) {
            return perValue;
        }
        return averageOfTwo(perValue, pbrValue);
    }

    /**
     * 從最近X個完整年度中選取 EPS 數值（DEC-9）。
     *
     * @param yearlyEps 完整的年度 EPS 資料
     * @param windowYears 設定的 3/5/10 年窗口
     * @return 可用的 EPS 數值
     */
    private List<BigDecimal> recentEpsValues(List<YearlyEps> yearlyEps, int windowYears) {
        return yearlyEps.stream()
                .sorted(Comparator.comparingInt(YearlyEps::year).reversed())
                .limit(windowYears)
                .map(YearlyEps::eps)
                .filter(Objects::nonNull)
                .toList();
    }

    /**
     * BPS(X年平均)。FinMind 沒有直接的 BPS 資料集（SA-02）——每年的 BPS 是用
     * avgClosePrice ÷ avgPbr 推導出來的，再取最近X年的平均。窗口內 avgPbr 缺值或非正值的
     * 年度會被排除，而不是往更早的年份補齊（窗口本身固定是「最近X年」）。
     *
     * @param prices 完整年度股價統計
     * @param valuations 完整年度估值統計
     * @param windowYears 設定的 3/5/10 年窗口
     * @return 可用的推導 BPS 數值
     */
    private List<BigDecimal> recentDerivedBpsValues(
            List<YearlyPriceStats> prices, List<YearlyValuationStats> valuations, int windowYears) {
        Map<Integer, BigDecimal> avgCloseByYear = prices.stream()
                .collect(Collectors.toMap(YearlyPriceStats::year, YearlyPriceStats::avgClosePrice));

        List<YearlyValuationStats> recentValuations = valuations.stream()
                .sorted(Comparator.comparingInt(YearlyValuationStats::year).reversed())
                .limit(windowYears)
                .toList();

        return recentValuations.stream()
                .filter(v -> v.avgPbr() != null && v.avgPbr().compareTo(BigDecimal.ZERO) > 0)
                .filter(v -> avgCloseByYear.containsKey(v.year()))
                .map(v -> avgCloseByYear.get(v.year()).divide(v.avgPbr(), CALC_SCALE, ROUNDING))
                .toList();
    }

    /**
     * X年內買回（DEC-1）。
     *
     * @param gapRatio 與目標價差比
     * @param yield 十年平均殖利率
     * @return 買回所需年數；{@code null} 代表「0／已達成」
     */
    private BigDecimal computeYearsViaYield(BigDecimal gapRatio, BigDecimal yield) {
        if (gapRatio.compareTo(BigDecimal.ZERO) <= 0) {
            // gapRatio<=0 代表現價已經達到或低於目標價，不需要計算買回年數
            return null;
        }
        if (yield.compareTo(BigDecimal.ZERO) <= 0) {
            // yield<=0 代表沒有殖利率可以拿來複利計算，缺少複利成長的基礎
            return null;
        }
        // 股息用殖利率逐年複利再投入，是指數成長而非線性成長，所以要解
        // (1+yield)^years = 1+gapRatio 這條複利方程式，換底公式即為 log(1+gapRatio) / log(1+yield)
        double years = Math.log(1 + gapRatio.doubleValue()) / Math.log(1 + yield.doubleValue());
        return BigDecimal.valueOf(years).setScale(RESULT_SCALE, ROUNDING);
    }

    /**
     * 賣賺幾年（DEC-7）。
     *
     * @param ratio 現價相對合理價的比例
     * @param yield 十年平均殖利率
     * @return 賣出獲利所需年數；{@code null} 代表「尚未獲利」
     */
    private BigDecimal computeLinearYears(BigDecimal ratio, BigDecimal yield) {
        if (ratio.compareTo(BigDecimal.ZERO) <= 0) {
            // ratio<=0 代表現價尚未超過合理價，賣出並未獲利，不需要計算年數
            return null;
        }
        if (yield.compareTo(BigDecimal.ZERO) <= 0) {
            // yield<=0 代表沒有殖利率可以拿來換算，缺少計算基礎
            return null;
        }
        // 這裡算的是「賣掉、把價差當成股息逐年領」要花幾年才能領完，是把已實現的價差平均攤提在
        // 每年的股息上，屬於單純攤提而非複利再投入，所以用線性的 ratio ÷ yield，
        // 而不是像 computeYearsViaYield 那樣用 log 比例去解複利方程式
        return ratio.divide(yield, CALC_SCALE, ROUNDING).setScale(RESULT_SCALE, ROUNDING);
    }

    /**
     * 前目標價出現：近10年中，最近一次「該年度最低價 ≤ 目標價」的年份。若當年度（尚未結束）
     * 至今的即時最低價已經 ≤ 目標價，優先回傳今年——不用等到年度結束、完整年度落地後才看得到
     * （比照 low10/high10 併入當年度的做法，見 {@link #minWithCurrentYear}）。
     *
     * @param prices 完整年度股價統計
     * @param targetPrice 目標價
     * @param currentYearLowPrice 當年度至今的即時最低價，可為 {@code null}
     * @return 最近一次符合條件的年份；沒有任何一年符合時回傳 {@code null}
     */
    private Integer findFrontYear(List<YearlyPriceStats> prices, BigDecimal targetPrice, BigDecimal currentYearLowPrice) {
        if (currentYearLowPrice != null && currentYearLowPrice.compareTo(targetPrice) <= 0) {
            return LocalDate.now().getYear();
        }
        return prices.stream()
                .sorted(Comparator.comparingInt(YearlyPriceStats::year).reversed())
                .filter(p -> p.lowPrice().compareTo(targetPrice) <= 0)
                .map(YearlyPriceStats::year)
                .findFirst()
                .orElse(null);
    }

    /**
     * 建立單一檔位的目標價物件。
     *
     * @param level 檔位（1~6）
     * @param price 該檔位的原始價格
     * @return 四捨五入後的檔位物件
     */
    private TierPrice tier(int level, BigDecimal price) {
        return new TierPrice(level, TIER_LABELS[level - 1], round(price));
    }

    /**
     * 依統一規則四捨五入至顯示精度。
     *
     * @param value 原始數值
     * @return 四捨五入後的數值
     */
    private BigDecimal round(BigDecimal value) {
        return value.setScale(RESULT_SCALE, ROUNDING);
    }

    /**
     * 依年份排序並取最近N年。
     *
     * @param items 待篩選的資料
     * @param yearExtractor 從資料項目取出年份的函式
     * @param windowYears 窗口年數
     * @return 依年份新到舊排序、限制在窗口年數內的清單
     * @param <T> 資料項目型別
     */
    private <T> List<T> recentYears(List<T> items, ToIntFunction<T> yearExtractor, int windowYears) {
        return items.stream()
                .sorted(Comparator.comparingInt(yearExtractor::applyAsInt).reversed())
                .limit(windowYears)
                .toList();
    }
}
