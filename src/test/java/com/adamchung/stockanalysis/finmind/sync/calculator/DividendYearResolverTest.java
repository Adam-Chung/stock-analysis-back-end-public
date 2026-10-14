package com.adamchung.stockanalysis.finmind.sync.calculator;

import com.adamchung.stockanalysis.finmind.dto.FinMindDividendDto;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class DividendYearResolverTest {

    private static FinMindDividendDto dividend(LocalDate cashExDate, LocalDate announcementDate) {
        return new FinMindDividendDto(
                null, "2330", "114年第2季", null, null, cashExDate, null, null, null, null, announcementDate);
    }

    @Test
    void uses_the_cash_ex_dividend_trading_date_year_when_present() {
        FinMindDividendDto row = dividend(LocalDate.of(2025, 7, 15), LocalDate.of(2025, 5, 1));
        assertThat(DividendYearResolver.resolve(row)).isEqualTo(2025);
    }

    @Test
    void falls_back_to_announcement_date_year_for_stock_only_dividend_years() {
        FinMindDividendDto row = dividend(null, LocalDate.of(2025, 5, 1));
        assertThat(DividendYearResolver.resolve(row)).isEqualTo(2025);
    }

    @Test
    void returns_null_when_neither_date_is_present() {
        FinMindDividendDto row = dividend(null, null);
        assertThat(DividendYearResolver.resolve(row)).isNull();
    }
}
