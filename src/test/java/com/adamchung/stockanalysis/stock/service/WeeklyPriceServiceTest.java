package com.adamchung.stockanalysis.stock.service;

import com.adamchung.stockanalysis.finmind.FinMindClient;
import com.adamchung.stockanalysis.finmind.dto.FinMindStockPriceDto;
import com.adamchung.stockanalysis.stock.dto.WeeklyPriceCandleResponse;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WeeklyPriceServiceTest {

    @Test
    void aggregates_daily_prices_into_weekly_ohlc_and_skips_zero_prices() {
        FinMindClient client = mock(FinMindClient.class);
        when(client.fetchStockPrice(eq("2330"), any(), any())).thenReturn(List.of(
                row("2026-07-06", 100, 110, 98, 105),
                row("2026-07-08", 106, 115, 102, 112),
                row("2026-07-10", 111, 114, 108, 109),
                row("2026-07-13", 110, 120, 107, 118),
                row("2026-07-14", 0, 0, 0, 0)));

        List<WeeklyPriceCandleResponse> result = new WeeklyPriceService(client).getWeeklyPrices("2330");

        assertThat(result).hasSize(2);
        assertThat(result.get(0)).isEqualTo(new WeeklyPriceCandleResponse(
                LocalDate.of(2026, 7, 6), bd(100), bd(115), bd(98), bd(109)));
        assertThat(result.get(1)).isEqualTo(new WeeklyPriceCandleResponse(
                LocalDate.of(2026, 7, 13), bd(110), bd(120), bd(107), bd(118)));
    }

    @Test
    void reuses_the_same_stock_result_within_the_cache_window() {
        FinMindClient client = mock(FinMindClient.class);
        when(client.fetchStockPrice(eq("2330"), any(), any())).thenReturn(List.of(
                row("2026-07-06", 100, 110, 98, 105)));
        WeeklyPriceService service = new WeeklyPriceService(client);

        service.getWeeklyPrices("2330");
        service.getWeeklyPrices("2330");

        verify(client, times(1)).fetchStockPrice(eq("2330"), any(), any());
    }

    /** 建立週 K 聚合測試使用的日價資料。 */
    private static FinMindStockPriceDto row(
            String date, double open, double high, double low, double close) {
        return new FinMindStockPriceDto(
                LocalDate.parse(date), "2330", 1L, 1L, bd(open), bd(high), bd(low), bd(close), bd(0), 1L);
    }

    /** 將測試數字轉為不帶浮點誤差的 BigDecimal。 */
    private static BigDecimal bd(double value) {
        return BigDecimal.valueOf(value);
    }
}
