package com.adamchung.stockanalysis.valuation.calculator;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.offset;

class DividendYieldCalculatorTest {

    @Test
    void returns_null_when_avgClosePrice_is_null() {
        assertThat(DividendYieldCalculator.calculate(null, BigDecimal.TEN, BigDecimal.ZERO)).isNull();
    }

    @Test
    void returns_null_when_avgClosePrice_is_zero_or_negative() {
        assertThat(DividendYieldCalculator.calculate(BigDecimal.ZERO, BigDecimal.ONE, BigDecimal.ZERO)).isNull();
        assertThat(DividendYieldCalculator.calculate(BigDecimal.valueOf(-10), BigDecimal.ONE, BigDecimal.ZERO))
                .isNull();
    }

    @Test
    void treats_null_cash_and_stock_dividend_as_no_distribution() {
        BigDecimal yield = DividendYieldCalculator.calculate(BigDecimal.valueOf(100), null, null);
        assertThat(yield).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void pure_cash_dividend_matches_the_naive_cash_over_price_ratio() {
        // no stock dividend means the ex-rights reference price collapses to avgPrice - cash,
        // so the formula reduces to the plain cash/price ratio.
        BigDecimal yield = DividendYieldCalculator.calculate(
                BigDecimal.valueOf(100), BigDecimal.valueOf(5), BigDecimal.ZERO);
        assertThat(yield).isEqualByComparingTo(BigDecimal.valueOf(0.05));
    }

    @Test
    void pure_stock_dividend_accounts_for_dilution_instead_of_using_the_naive_rate() {
        // a 100% stock dividend (10 yuan of stock dividend per 10-yuan par value = 1.0 rate)
        // doubles the share count; the ex-rights reference price halves (200 -> 100), so only
        // half of the naive "100%" rate is realized as yield - this is the whole point of using
        // the ex-rights formula instead of just summing 現金股利/股價 + 股票股利/10.
        BigDecimal yield = DividendYieldCalculator.calculate(
                BigDecimal.valueOf(200), BigDecimal.ZERO, BigDecimal.TEN);
        assertThat(yield).isEqualByComparingTo(BigDecimal.valueOf(0.5));
    }

    @Test
    void combines_cash_and_stock_dividend_in_one_ex_rights_calculation() {
        // avgPrice=100, cash=2, stock=1 (10% stock dividend rate)
        // exRightsReferencePrice = (100-2)/1.1 = 89.09090909
        // yield = (100-89.09090909)/100 = 0.10909091
        BigDecimal yield = DividendYieldCalculator.calculate(
                BigDecimal.valueOf(100), BigDecimal.valueOf(2), BigDecimal.ONE);
        assertThat(yield.doubleValue()).isCloseTo(0.10909091, offset(1e-6));
    }

    @Test
    void no_distribution_at_all_yields_exactly_zero_not_null() {
        // a complete year with a valid avgClosePrice but zero dividends is a legitimate 0%
        // yield, not missing data - distinct from returning null for a missing avgClosePrice.
        BigDecimal yield = DividendYieldCalculator.calculate(
                BigDecimal.valueOf(150), BigDecimal.ZERO, BigDecimal.ZERO);
        assertThat(yield).isEqualByComparingTo(BigDecimal.ZERO);
    }
}
