package com.adamchung.stockanalysis.valuation;

import com.adamchung.stockanalysis.valuation.dto.TierPrice;
import com.adamchung.stockanalysis.valuation.dto.ValuationDataCoverage;
import com.adamchung.stockanalysis.valuation.dto.ValuationInput;
import com.adamchung.stockanalysis.valuation.dto.ValuationResult;
import com.adamchung.stockanalysis.valuation.dto.YearlyEps;
import com.adamchung.stockanalysis.valuation.dto.YearlyPriceStats;
import com.adamchung.stockanalysis.valuation.dto.YearlyValuationStats;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.offset;

/**
 * Fixture is a hand-picked, independently-computable dataset (round numbers chosen so the
 * expected tier prices can be verified by hand) rather than a reverse-engineering of the
 * prototype's mock numbers - see docs/spec/SA-01-stock-target-price.md 3.3 for the formulas
 * being verified.
 *
 * Fixture summary (5 years, 2021-2025):
 * - avgClosePrice=100, avgPbr=5 every year =&gt; BPS(X年平均) = 100/5 = 20
 * - lowPer=8 / avgPer=12 / highPer=16 every year =&gt; those ARE the 10年平均 figures
 * - lowPbr=4 / highPbr=6 every year =&gt; ditto
 * - EPS 12/年 平均 (10,11,12,13,14 加總/5 = 12)
 * - avgDividendYield=0.04 every year
 * - highPrice/lowPrice vary by year: low10=50 (2021), high10=300 (2025)
 *
 * =&gt; PER便宜=96, PER合理=144, PER昂貴=192; PBR便宜=80, PBR合理=100, PBR昂貴=120
 * =&gt; 1便宜價=88, 3合理價=122, 4昂貴價=156, 2次便宜=105
 * =&gt; 5超貴價=(300-156)/3+156=204, 6爆炸貴=(300-156)*2/3+156=252
 */
class TargetPriceValuationServiceTest {

    private final TargetPriceValuationService service = new TargetPriceValuationService();

    private List<YearlyPriceStats> prices;
    private List<YearlyValuationStats> valuations;
    private List<YearlyEps> eps;

    @BeforeEach
    void setUp() {
        prices = List.of(
                new YearlyPriceStats(2021, bd(100), bd(110), bd(50)),
                new YearlyPriceStats(2022, bd(100), bd(120), bd(90)),
                new YearlyPriceStats(2023, bd(100), bd(150), bd(130)),
                new YearlyPriceStats(2024, bd(100), bd(250), bd(200)),
                new YearlyPriceStats(2025, bd(100), bd(300), bd(220)));

        valuations = List.of(
                valuationYear(2021), valuationYear(2022), valuationYear(2023),
                valuationYear(2024), valuationYear(2025));

        eps = List.of(
                new YearlyEps(2021, bd(10)), new YearlyEps(2022, bd(11)), new YearlyEps(2023, bd(12)),
                new YearlyEps(2024, bd(13)), new YearlyEps(2025, bd(14)));
    }

    private YearlyValuationStats valuationYear(int year) {
        return new YearlyValuationStats(year, bd(12), bd(8), bd(16), bd(5), bd(4), bd(6), bd(0.04));
    }

    private ValuationInput input(BigDecimal currentPrice, int targetPriceLevel) {
        return new ValuationInput(currentPrice, 5, 5, targetPriceLevel, prices, valuations, eps, null, null);
    }

    @Test
    void computes_all_six_tiers_from_per_and_pbr_averages() {
        ValuationResult result = service.calculate(input(bd(200), 3));

        assertThat(result.tiers()).hasSize(6);
        assertThat(priceOf(result, 1)).isEqualByComparingTo(bd(88));
        assertThat(priceOf(result, 2)).isEqualByComparingTo(bd(105));
        assertThat(priceOf(result, 3)).isEqualByComparingTo(bd(122));
        assertThat(priceOf(result, 4)).isEqualByComparingTo(bd(156));
        assertThat(priceOf(result, 5)).isEqualByComparingTo(bd(204));
        assertThat(priceOf(result, 6)).isEqualByComparingTo(bd(252));

        assertThat(result.low10()).isEqualByComparingTo(bd(50));
        assertThat(result.high10()).isEqualByComparingTo(bd(300));
        assertThat(result.tenYearAvgYield()).isEqualByComparingTo(bd(0.04));
        assertThat(result.dataCoverage().avgPerYears()).isEqualTo(5);
        assertThat(result.dataCoverage().avgPbrYears()).isEqualTo(5);
        assertThat(result.dataCoverage().epsYears()).isEqualTo(5);
        assertThat(result.dataCoverage().bpsYears()).isEqualTo(5);
    }

    @Test
    void excludes_eps_branch_without_dividing_the_bps_valuation_by_two() {
        ValuationInput bpsOnly = new ValuationInput(bd(200), 0, 5, 3, prices, valuations, eps, null, null);

        ValuationResult result = service.calculate(bpsOnly);

        assertThat(priceOf(result, 1)).isEqualByComparingTo(bd(80));
        assertThat(priceOf(result, 3)).isEqualByComparingTo(bd(100));
        assertThat(priceOf(result, 4)).isEqualByComparingTo(bd(120));
        assertThat(result.dataCoverage().epsYears()).isZero();
    }

    @Test
    void excludes_bps_branch_without_dividing_the_eps_valuation_by_two() {
        ValuationInput epsOnly = new ValuationInput(bd(200), 5, 0, 3, prices, valuations, eps, null, null);

        ValuationResult result = service.calculate(epsOnly);

        assertThat(priceOf(result, 1)).isEqualByComparingTo(bd(96));
        assertThat(priceOf(result, 3)).isEqualByComparingTo(bd(144));
        assertThat(priceOf(result, 4)).isEqualByComparingTo(bd(192));
        assertThat(result.dataCoverage().bpsYears()).isZero();
    }

    @Test
    void rejects_excluding_both_eps_and_bps_branches() {
        ValuationInput noValuationBranch = new ValuationInput(bd(200), 0, 0, 3, prices, valuations, eps, null, null);

        assertThatThrownBy(() -> service.calculate(noValuationBranch))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cannot both be excluded");
    }

    @Test
    void 目標價_selects_the_tier_matching_targetPriceLevel() {
        assertThat(service.calculate(input(bd(200), 1)).targetPrice()).isEqualByComparingTo(bd(88));
        assertThat(service.calculate(input(bd(200), 4)).targetPrice()).isEqualByComparingTo(bd(156));
        assertThat(service.calculate(input(bd(200), 6)).targetPrice()).isEqualByComparingTo(bd(252));
    }

    @Test
    void 與目標價差比_and_加一年股利價_DEC1() {
        ValuationResult result = service.calculate(input(bd(200), 3)); // target = 122

        // 200/122 - 1 = 0.63934426...; result is rounded to 4dp (RESULT_SCALE), so tolerance
        // must cover that rounding, not just floating-point noise.
        assertThat(result.gapRatio().doubleValue()).isCloseTo(0.63934426, offset(1e-4));
        // 122 * 1.04 = 126.88
        assertThat(result.plusOneYearDividendPrice()).isEqualByComparingTo(bd(126.88));
    }

    @Test
    void X年內買回_uses_log_compounding_and_is_null_once_achieved_DEC1() {
        ValuationResult expensive = service.calculate(input(bd(200), 3)); // 現價 200 > 目標價 122
        double expectedYears = Math.log(1 + 200.0 / 122 - 1) / Math.log(1.04);
        assertThat(expensive.yearsToRecover().doubleValue()).isCloseTo(expectedYears, offset(0.01));

        // 現價 200 <= 目標價 252 (6爆炸貴) => already achieved, null not negative
        ValuationResult achieved = service.calculate(input(bd(200), 6));
        assertThat(achieved.yearsToRecover()).isNull();
    }

    @Test
    void 賣賺幾年_is_always_based_on_3合理價_regardless_of_target_selection_DEC7() {
        ValuationResult atTier1 = service.calculate(input(bd(200), 1));
        ValuationResult atTier4 = service.calculate(input(bd(200), 4));

        assertThat(atTier1.yearsOfProfitIfSold()).isEqualByComparingTo(atTier4.yearsOfProfitIfSold());

        // (200/122 - 1) / 0.04
        double expected = (200.0 / 122 - 1) / 0.04;
        assertThat(atTier1.yearsOfProfitIfSold().doubleValue()).isCloseTo(expected, offset(0.01));
    }

    @Test
    void 賣賺幾年_is_null_once_現價未超過合理價_DEC7() {
        ValuationResult result = service.calculate(input(bd(100), 3)); // 100 <= 122
        assertThat(result.yearsOfProfitIfSold()).isNull();
    }

    @Test
    void 前目標價出現_finds_the_most_recent_year_whose_low_price_undercut_the_target() {
        // target(3合理價)=122; lows by year, most recent first: 2025=220, 2024=200, 2023=130,
        // 2022=90, 2021=50 - first (most recent) year with low <= 122 is 2022 (90 <= 122).
        ValuationResult result = service.calculate(input(bd(200), 3));
        assertThat(result.frontYear()).isEqualTo(2022);
    }

    @Test
    void 前目標價出現_prefers_the_current_year_when_its_running_low_already_undercuts_the_target() {
        // target(3合理價)=122; complete years' most recent match is 2022 (low=90), but the
        // in-progress current year's running low (100) also already undercuts it, so the
        // current year should win instead of waiting for it to become a complete year.
        ValuationInput withCurrentYearLow =
                new ValuationInput(bd(200), 5, 5, 3, prices, valuations, eps, null, bd(100));
        ValuationResult result = service.calculate(withCurrentYearLow);
        assertThat(result.frontYear()).isEqualTo(LocalDate.now().getYear());
    }

    @Test
    void 前目標價出現_falls_back_to_complete_years_when_the_current_year_has_not_undercut_the_target() {
        // current-year running low of 150 has not undercut the target(3合理價)=122, so the
        // most recent complete-year match (2022, low=90) is used instead.
        ValuationInput withHigherCurrentYearLow =
                new ValuationInput(bd(200), 5, 5, 3, prices, valuations, eps, null, bd(150));
        ValuationResult result = service.calculate(withHigherCurrentYearLow);
        assertThat(result.frontYear()).isEqualTo(2022);
    }

    @Test
    void 高10_picks_up_a_new_high_set_in_the_in_progress_current_year() {
        // complete-years high10 would be 300 (2025); a current-year high of 350 must win.
        ValuationInput withHigherCurrentYear =
                new ValuationInput(bd(200), 5, 5, 3, prices, valuations, eps, bd(350), null);
        ValuationResult result = service.calculate(withHigherCurrentYear);
        assertThat(result.high10()).isEqualByComparingTo(bd(350));
    }

    @Test
    void 高10_keeps_the_complete_years_high_when_the_current_year_has_not_exceeded_it() {
        // current-year high of 120 is still below the complete-years high10 of 300.
        ValuationInput withLowerCurrentYear =
                new ValuationInput(bd(200), 5, 5, 3, prices, valuations, eps, bd(120), null);
        ValuationResult result = service.calculate(withLowerCurrentYear);
        assertThat(result.high10()).isEqualByComparingTo(bd(300));
    }

    @Test
    void 低10_picks_up_a_new_low_set_in_the_in_progress_current_year() {
        // complete-years low10 would be 50 (2021); a current-year low of 30 must win.
        ValuationInput withLowerCurrentYear =
                new ValuationInput(bd(200), 5, 5, 3, prices, valuations, eps, null, bd(30));
        ValuationResult result = service.calculate(withLowerCurrentYear);
        assertThat(result.low10()).isEqualByComparingTo(bd(30));
    }

    @Test
    void 高10_and_低10_ignore_current_year_extremes_when_none_have_been_synced_yet() {
        ValuationResult result = service.calculate(input(bd(200), 3));
        assertThat(result.high10()).isEqualByComparingTo(bd(300));
        assertThat(result.low10()).isEqualByComparingTo(bd(50));
    }

    @Test
    void 超貴價與爆炸貴_shift_when_the_current_year_high_is_higher() {
        // 4昂貴價=156 either way; with high10=300: 5超貴=(300-156)/3+156=204, 6爆炸貴=252 (see
        // the class-level fixture summary). Bumping high10 to 450 via a current-year high must
        // shift both, proving they read the merged high10, not the stale complete-years-only one.
        ValuationInput withHigherCurrentYear =
                new ValuationInput(bd(200), 5, 5, 3, prices, valuations, eps, bd(450), null);
        ValuationResult result = service.calculate(withHigherCurrentYear);

        // (450-156)/3+156=254, (450-156)*2/3+156=352
        assertThat(priceOf(result, 5)).isEqualByComparingTo(bd(254));
        assertThat(priceOf(result, 6)).isEqualByComparingTo(bd(352));
    }

    @Test
    void eps和bps取X年平均_respects_the_selected_window() {
        ValuationInput fiveYear = new ValuationInput(bd(200), 5, 5, 3, prices, valuations, eps, null, null);
        ValuationInput threeYear = new ValuationInput(bd(200), 3, 5, 3, prices, valuations, eps, null, null);

        // EPS(5年平均) = (10+11+12+13+14)/5 = 12 -> PER合理 = 12*12 = 144
        // EPS(3年平均, 最近三年 2023/2024/2025) = (12+13+14)/3 = 13 -> PER合理 = 13*12 = 156
        ValuationResult five = service.calculate(fiveYear);
        ValuationResult three = service.calculate(threeYear);

        assertThat(five.targetPrice()).isNotEqualByComparingTo(three.targetPrice());
    }

    @Test
    void throws_when_target_price_level_out_of_range() {
        assertThatThrownBy(() -> service.calculate(input(bd(200), 0)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.calculate(input(bd(200), 7)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void throws_when_no_eps_data_available() {
        ValuationInput noEps = new ValuationInput(bd(200), 5, 5, 3, prices, valuations, List.of(), null, null);
        assertThatThrownBy(() -> service.calculate(noEps))
                .isInstanceOf(InsufficientValuationDataException.class);
    }

    @Test
    void bps_derivation_skips_years_with_non_positive_pbr_instead_of_reaching_further_back() {
        List<YearlyValuationStats> withOneZeroPbrYear = List.of(
                new YearlyValuationStats(2021, bd(12), bd(8), bd(16), bd(5), bd(4), bd(6), bd(0.04)),
                new YearlyValuationStats(2022, bd(12), bd(8), bd(16), BigDecimal.ZERO, bd(4), bd(6), bd(0.04)),
                new YearlyValuationStats(2023, bd(12), bd(8), bd(16), bd(5), bd(4), bd(6), bd(0.04)),
                new YearlyValuationStats(2024, bd(12), bd(8), bd(16), bd(5), bd(4), bd(6), bd(0.04)),
                new YearlyValuationStats(2025, bd(12), bd(8), bd(16), bd(5), bd(4), bd(6), bd(0.04)));

        ValuationInput withGap = new ValuationInput(bd(200), 5, 5, 3, prices, withOneZeroPbrYear, eps, null, null);

        // Should not throw: BPS(5年平均) is still derivable from the 4 remaining valid years,
        // each giving avgClosePrice/avgPbr = 100/5 = 20, so BPS stays 20 (unaffected).
        // Separately - and this is expected, not a bug - 10年平均PBR itself is a plain average of
        // avgPbr across all 5 years including the 0, so it drops from 5 to (5+0+5+5+5)/5=4;
        // that's a different calculation from the BPS-derivation exclusion this test targets.
        // PER合理=12*12=144 (unaffected), PBR合理=20*4=80 => 3合理價=(144+80)/2=112.
        ValuationResult result = service.calculate(withGap);
        assertThat(priceOf(result, 3)).isEqualByComparingTo(bd(112));
    }

    @Test
    void per_and_pbr_each_exclude_only_their_own_missing_years() {
        List<YearlyValuationStats> withIndependentGaps = List.of(
                new YearlyValuationStats(2021, null, null, null, bd(5), bd(4), bd(6), bd(0.04)),
                new YearlyValuationStats(2022, bd(12), bd(8), bd(16), null, null, null, bd(0.04)),
                valuationYear(2023),
                valuationYear(2024),
                valuationYear(2025));

        ValuationResult result = service.calculate(
                new ValuationInput(bd(200), 5, 5, 3, prices, withIndependentGaps, eps, null, null));

        assertThat(result.dataCoverage().avgPerYears()).isEqualTo(4);
        assertThat(result.dataCoverage().avgPbrYears()).isEqualTo(4);
        assertThat(result.dataCoverage().bpsYears()).isEqualTo(4);
        assertThat(priceOf(result, 3)).isEqualByComparingTo(bd(122));
    }

    @Test
    void throws_insufficient_data_when_one_required_metric_has_no_usable_year() {
        List<YearlyValuationStats> noPer = valuations.stream()
                .map(row -> new YearlyValuationStats(
                        row.year(),
                        null,
                        null,
                        null,
                        row.avgPbr(),
                        row.lowPbr(),
                        row.highPbr(),
                        row.avgDividendYield()))
                .toList();

        assertThatThrownBy(() -> service.calculate(
                new ValuationInput(bd(200), 5, 5, 3, prices, noPer, eps, null, null)))
                .isInstanceOf(InsufficientValuationDataException.class)
                .hasMessageContaining("PER");
    }

    @Test
    void throws_when_a_computed_tier_price_is_not_positive_for_a_loss_making_stock() {
        // DEC-078：連續虧損股，EPS(5年平均) 為負；PER 推導出的便宜價(=-50*8=-400)跟 PBR
        // 推導出的便宜價(=20*4=80，不受EPS影響)平均後仍是負的((-400+80)/2=-160)，1便宜價
        // ≤0，必須在進入任何除法之前就攔下來，而不是讓 gapRatio/sellRatio 拿負值/零值當分母。
        List<YearlyEps> lossYears = List.of(
                new YearlyEps(2021, bd(-50)), new YearlyEps(2022, bd(-50)), new YearlyEps(2023, bd(-50)),
                new YearlyEps(2024, bd(-50)), new YearlyEps(2025, bd(-50)));

        assertThatThrownBy(() -> service.calculate(
                new ValuationInput(bd(200), 5, 5, 1, prices, valuations, lossYears, null, null)))
                .isInstanceOf(InsufficientValuationDataException.class)
                .hasMessageContaining("not positive");
    }

    private static BigDecimal priceOf(ValuationResult result, int level) {
        return result.tiers().stream()
                .filter(t -> t.level() == level)
                .findFirst()
                .orElseThrow()
                .price();
    }

    private static BigDecimal bd(double value) {
        return BigDecimal.valueOf(value);
    }
}
