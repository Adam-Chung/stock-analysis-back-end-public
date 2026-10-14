package com.adamchung.stockanalysis.common;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class PercentageMathTest {

    @Test
    void ratioAsPercent_divides_then_multiplies_by_hundred_with_scale() {
        BigDecimal result = PercentageMath.ratioAsPercent(new BigDecimal("1"), new BigDecimal("4"), 4);

        assertThat(result).isEqualByComparingTo("25.0000");
    }

    @Test
    void ratioAsPercent_returns_null_for_missing_or_zero_denominator() {
        assertThat(PercentageMath.ratioAsPercent(null, new BigDecimal("4"), 4)).isNull();
        assertThat(PercentageMath.ratioAsPercent(new BigDecimal("1"), null, 4)).isNull();
        assertThat(PercentageMath.ratioAsPercent(new BigDecimal("1"), BigDecimal.ZERO, 4)).isNull();
    }

    @Test
    void round_matches_decimal_half_up_on_a_binary_representation_edge_case() {
        // DEC-079：2.675 在二進位浮點數裡實際存的是 2.67499999999999982236431605997495353221893310546875，
        // 舊版 Math.round(value*100)/100.0 會因為這個誤差捨去成 2.67，而不是十進位四捨五入預期
        // 的 2.68——這正是改用 BigDecimal 要避免的問題。
        assertThat(PercentageMath.round(2.675, 2)).isEqualTo(2.68);
    }
}
