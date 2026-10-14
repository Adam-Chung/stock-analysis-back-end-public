package com.adamchung.stockanalysis.externaldata.twse;

import com.adamchung.stockanalysis.externaldata.ExternalDataException;
import com.adamchung.stockanalysis.externaldata.dto.QuarterRange;
import com.adamchung.stockanalysis.externaldata.twse.dto.InstitutionalFlowRow;
import com.adamchung.stockanalysis.externaldata.twse.dto.InstitutionalHoldingRow;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InstitutionalQuarterlyChangeCalculatorTest {

    private static final QuarterRange QUARTER =
            new QuarterRange(LocalDate.of(2026, 4, 1), LocalDate.of(2026, 6, 30), "2026Q2");

    @Test
    void combines_exact_foreign_level_delta_with_estimated_trust_and_dealer_flow() {
        List<InstitutionalHoldingRow> current = List.of(
                new InstitutionalHoldingRow("2884", "玉山金", "20260630", 40.0, "url-foreign"));
        List<InstitutionalHoldingRow> previous = List.of(
                new InstitutionalHoldingRow("2884", "玉山金", "20260331", 38.5, "url-foreign"));
        List<InstitutionalFlowRow> flows = List.of(
                new InstitutionalFlowRow("2884", "玉山金", "2026Q2", 0.3, -0.1, "url-flow"));

        List<InstitutionalQuarterlyChangeRow> rows =
                InstitutionalQuarterlyChangeCalculator.combineQuarter(QUARTER, current, previous, flows);

        assertThat(rows).hasSize(1);
        InstitutionalQuarterlyChangeRow row = rows.get(0);
        assertThat(row.stockCode()).isEqualTo("2884");
        assertThat(row.period()).isEqualTo("2026Q2");
        assertThat(row.foreignAndMainlandHoldingChangePercent()).isEqualTo(1.5); // 40.0 - 38.5, round2
        assertThat(row.investmentTrustNetBuyRatioChangePercent()).isEqualTo(0.3);
        assertThat(row.dealerNetBuyRatioChangePercent()).isEqualTo(-0.1);
        assertThat(row.totalChangePercent()).isCloseTo(1.7, org.assertj.core.api.Assertions.within(1e-9));
    }

    @Test
    void throws_when_a_stock_has_no_matching_previous_quarter_level() {
        List<InstitutionalHoldingRow> current = List.of(
                new InstitutionalHoldingRow("2884", "玉山金", "20260630", 40.0, "url-foreign"));
        List<InstitutionalFlowRow> flows = List.of(
                new InstitutionalFlowRow("2884", "玉山金", "2026Q2", 0.3, -0.1, "url-flow"));

        assertThatThrownBy(() -> InstitutionalQuarterlyChangeCalculator.combineQuarter(QUARTER, current, List.of(), flows))
                .isInstanceOf(ExternalDataException.class);
    }
}
