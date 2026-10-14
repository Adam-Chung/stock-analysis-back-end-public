package com.adamchung.stockanalysis.externaldata;

import com.adamchung.stockanalysis.externaldata.dto.QuarterRange;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class QuarterRangesTest {

    @Test
    void returns_the_5_most_recently_completed_quarter_ends_newest_first() {
        List<LocalDate> dates = QuarterRanges.recentQuarterEndDates(5, LocalDate.of(2026, 8, 1));
        assertThat(dates).containsExactly(
                LocalDate.of(2026, 6, 30),
                LocalDate.of(2026, 3, 31),
                LocalDate.of(2025, 12, 31),
                LocalDate.of(2025, 9, 30),
                LocalDate.of(2025, 6, 30));
    }

    @Test
    void does_not_include_a_quarter_end_that_has_not_happened_yet() {
        List<LocalDate> dates = QuarterRanges.recentQuarterEndDates(1, LocalDate.of(2026, 6, 15));
        assertThat(dates.get(0)).isEqualTo(LocalDate.of(2026, 3, 31));
    }

    @Test
    void treats_the_quarter_end_date_itself_as_already_completed() {
        List<LocalDate> dates = QuarterRanges.recentQuarterEndDates(1, LocalDate.of(2026, 6, 30));
        assertThat(dates.get(0)).isEqualTo(LocalDate.of(2026, 6, 30));
    }

    @Test
    void pairs_each_quarter_end_with_its_own_quarter_start_date_and_a_q_label() {
        List<QuarterRange> ranges = QuarterRanges.recentQuarterRanges(2, LocalDate.of(2026, 8, 1));
        assertThat(ranges).containsExactly(
                new QuarterRange(LocalDate.of(2026, 4, 1), LocalDate.of(2026, 6, 30), "2026Q2"),
                new QuarterRange(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 31), "2026Q1"));
    }

    @Test
    void labels_a_quarter_spanning_a_year_boundary_correctly() {
        List<QuarterRange> ranges = QuarterRanges.recentQuarterRanges(3, LocalDate.of(2026, 8, 1));
        assertThat(ranges.get(2)).isEqualTo(
                new QuarterRange(LocalDate.of(2025, 10, 1), LocalDate.of(2025, 12, 31), "2025Q4"));
    }
}
