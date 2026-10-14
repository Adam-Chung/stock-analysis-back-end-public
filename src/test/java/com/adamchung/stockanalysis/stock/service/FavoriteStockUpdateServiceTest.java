package com.adamchung.stockanalysis.stock.service;

import com.adamchung.stockanalysis.stock.dto.StockSummary;
import com.adamchung.stockanalysis.finmind.FinMindClient;
import com.adamchung.stockanalysis.finmind.dto.FinMindBalanceSheetDto;
import com.adamchung.stockanalysis.targetprice.dto.TargetPriceResponse;
import com.adamchung.stockanalysis.targetprice.service.TargetPriceApplicationService;
import com.adamchung.stockanalysis.valuation.dto.TierPrice;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;

@ExtendWith(MockitoExtension.class)
class FavoriteStockUpdateServiceTest {
    @Mock FavoriteStockService favoriteStockService;
    @Mock TargetPriceApplicationService targetPriceApplicationService;
    @Mock FinMindClient finMindClient;

    @Test
    void refreshAll_returns_recalculated_rows_and_continues_after_one_failure() {
        when(favoriteStockService.list()).thenReturn(List.of(
                new StockSummary("2330", "台積電", null), new StockSummary("2317", "鴻海", null)));
        TargetPriceResponse response = mock(TargetPriceResponse.class);
        when(response.stockId()).thenReturn("2330");
        when(response.stockName()).thenReturn("台積電");
        when(response.currentPrice()).thenReturn(BigDecimal.valueOf(100));
        when(response.targetPrice()).thenReturn(BigDecimal.valueOf(80));
        when(response.targetPriceLevel()).thenReturn(3);
        when(response.tiers()).thenReturn(List.of(new TierPrice(3, "3合理價", BigDecimal.valueOf(80))));
        when(response.gapRatio()).thenReturn(BigDecimal.valueOf(.25));
        when(response.investmentStrategy()).thenReturn("長期持有");
        when(targetPriceApplicationService.refresh("2330")).thenReturn(response);
        when(targetPriceApplicationService.refresh("2317")).thenThrow(new RuntimeException("quota"));
        when(finMindClient.fetchBalanceSheet(eq("2330"), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(List.of(new FinMindBalanceSheetDto(
                        LocalDate.of(2026, 6, 30), "2330", "Assets", BigDecimal.ONE, "資產")));

        var result = new FavoriteStockUpdateService(
                favoriteStockService, targetPriceApplicationService, finMindClient).refreshAll();

        assertThat(result).hasSize(2);
        assertThat(result.getFirst().latestDataQuarter()).isEqualTo("2026 Q2");
        assertThat(result.getFirst().targetPriceLabel()).isEqualTo("合理價");
        assertThat(result.getFirst().investmentStrategy()).isEqualTo("長期持有");
        assertThat(result.getLast().errorMessage()).isNotNull();
    }
}
