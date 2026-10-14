package com.adamchung.stockanalysis.finmind.sync.service;

import com.adamchung.stockanalysis.common.AuditActors;
import com.adamchung.stockanalysis.stock.entity.StockEntity;
import com.adamchung.stockanalysis.stock.entity.StockPriceYearlyEntity;
import com.adamchung.stockanalysis.stock.entity.StockYearId;
import com.adamchung.stockanalysis.finmind.FinMindClient;
import com.adamchung.stockanalysis.finmind.FinMindClientException;
import com.adamchung.stockanalysis.finmind.dto.FinMindStockPriceDto;
import com.adamchung.stockanalysis.finmind.sync.FinmindSyncLogEntity;
import com.adamchung.stockanalysis.finmind.sync.FinmindSyncLogRepository;
import com.adamchung.stockanalysis.stock.repository.StockPriceYearlyRepository;
import com.adamchung.stockanalysis.stock.repository.StockRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 只涵蓋 {@link FinMindSyncService} 的協調邏輯（何時委派、委派哪個日期區間）——實際「抓取＋
 * 聚合＋upsert」的行為由 {@link FinMindDatasetSyncExecutorTest} 涵蓋。
 */
@ExtendWith(MockitoExtension.class)
class FinMindSyncServiceTest {

    private static final int CURRENT_YEAR = LocalDate.now().getYear();
    private static final int LAST_COMPLETE_YEAR = CURRENT_YEAR - 1;

    @Mock
    private FinMindClient finMindClient;
    @Mock
    private StockRepository stockRepository;
    @Mock
    private StockPriceYearlyRepository priceYearlyRepository;
    @Mock
    private FinmindSyncLogRepository syncLogRepository;
    @Mock
    private FinMindDatasetSyncExecutor datasetSyncExecutor;

    @InjectMocks
    private FinMindSyncService service;

    @Captor
    private ArgumentCaptor<StockEntity> stockCaptor;
    @Captor
    private ArgumentCaptor<LocalDate> startDateCaptor;

    @Test
    void hasSyncHistory_is_false_when_no_log_rows_exist() {
        when(syncLogRepository.findByStockId("2330")).thenReturn(List.of());
        assertThat(service.hasSyncHistory("2330")).isFalse();
    }

    @Test
    void hasSyncHistory_is_true_when_any_log_row_exists() {
        when(syncLogRepository.findByStockId("2330")).thenReturn(List.of(new FinmindSyncLogEntity()));
        assertThat(service.hasSyncHistory("2330")).isTrue();
    }

    @Test
    void fullInitSync_delegates_profile_creation_when_missing_from_TaiwanStockInfo() {
        when(stockRepository.existsById("2330")).thenReturn(false);

        service.fullInitSync("2330");

        verify(datasetSyncExecutor).syncProfile("2330");
    }

    @Test
    void fullInitSync_does_not_sync_profile_when_the_stock_already_exists() {
        when(stockRepository.existsById("2330")).thenReturn(true);

        service.fullInitSync("2330");

        verify(datasetSyncExecutor, never()).syncProfile(any());
    }

    @Test
    void fullInitSync_delegates_all_four_datasets_with_the_ten_year_window() {
        when(stockRepository.existsById("2330")).thenReturn(true);

        service.fullInitSync("2330");

        LocalDate expectedStart = LocalDate.of(CURRENT_YEAR - 10, 1, 1);
        LocalDate expectedEnd = LocalDate.of(CURRENT_YEAR - 1, 12, 31);
        verify(datasetSyncExecutor).syncYearlyPrice("2330", expectedStart, expectedEnd, CURRENT_YEAR);
        verify(datasetSyncExecutor).syncYearlyValuation("2330", expectedStart, expectedEnd, CURRENT_YEAR);
        verify(datasetSyncExecutor).syncDividends("2330");
        verify(datasetSyncExecutor).syncFinancialStatements("2330");
    }

    @Test
    void fullInitSync_propagates_the_executor_failure_when_FinMind_has_no_info_for_an_unknown_stock_id() {
        when(stockRepository.existsById("9999")).thenReturn(false);
        when(datasetSyncExecutor.syncProfile("9999"))
                .thenThrow(new FinMindClientException("FinMind has no TaiwanStockInfo row for stock_id=9999"));

        assertThatThrownBy(() -> service.fullInitSync("9999")).isInstanceOf(FinMindClientException.class);
    }

    @Test
    void incrementalUpdate_starts_the_year_after_the_last_persisted_complete_year() {
        when(stockRepository.existsById("2330")).thenReturn(true);
        StockPriceYearlyEntity existing = yearlyPrice(LAST_COMPLETE_YEAR, 100, 120, 80);
        when(priceYearlyRepository.findByIdStockIdOrderByIdYearDesc("2330")).thenReturn(List.of(existing));

        service.incrementalUpdate("2330");

        verify(datasetSyncExecutor).syncYearlyPrice(
                eq("2330"), startDateCaptor.capture(), any(), eq(CURRENT_YEAR));
        assertThat(startDateCaptor.getValue()).isEqualTo(LocalDate.of(LAST_COMPLETE_YEAR + 1, 1, 1));
    }

    @Test
    void incrementalUpdate_falls_back_to_the_ten_year_window_when_never_synced() {
        when(stockRepository.existsById("2330")).thenReturn(true);
        when(priceYearlyRepository.findByIdStockIdOrderByIdYearDesc("2330")).thenReturn(List.of());

        service.incrementalUpdate("2330");

        verify(datasetSyncExecutor).syncYearlyPrice(
                eq("2330"), startDateCaptor.capture(), any(), eq(CURRENT_YEAR));
        assertThat(startDateCaptor.getValue()).isEqualTo(LocalDate.of(CURRENT_YEAR - 10, 1, 1));
    }

    @Test
    void incrementalUpdate_restarts_from_the_earliest_invalid_price_year() {
        when(stockRepository.existsById("2330")).thenReturn(true);
        StockPriceYearlyEntity latestValid = yearlyPrice(CURRENT_YEAR - 1, 100, 120, 80);
        StockPriceYearlyEntity invalid = yearlyPrice(CURRENT_YEAR - 3, 90, 110, 0);
        when(priceYearlyRepository.findByIdStockIdOrderByIdYearDesc("2330"))
                .thenReturn(List.of(latestValid, invalid));

        service.incrementalUpdate("2330");

        verify(datasetSyncExecutor).syncYearlyPrice(
                eq("2330"), startDateCaptor.capture(), any(), eq(CURRENT_YEAR));
        assertThat(startDateCaptor.getValue()).isEqualTo(LocalDate.of(CURRENT_YEAR - 3, 1, 1));
    }

    @Test
    void incrementalUpdate_delegates_dividend_and_financial_statement_sync() {
        when(stockRepository.existsById("2330")).thenReturn(true);
        when(priceYearlyRepository.findByIdStockIdOrderByIdYearDesc("2330")).thenReturn(List.of());

        service.incrementalUpdate("2330");

        verify(datasetSyncExecutor).syncDividends("2330");
        verify(datasetSyncExecutor).syncFinancialStatements("2330");
    }

    @Test
    void refreshLatestPriceIfStale_skips_the_FinMind_call_when_already_fresh_today() {
        StockEntity stock = new StockEntity();
        stock.setStockId("2330");
        stock.setLatestPriceDate(LocalDate.now());
        when(stockRepository.findById("2330")).thenReturn(Optional.of(stock));

        service.refreshLatestPriceIfStale("2330");

        verify(finMindClient, never()).fetchStockPrice(any(), any(), any());
        verify(stockRepository, never()).save(any());
    }

    @Test
    void refreshLatestPriceIfStale_updates_the_latest_price_when_stale() {
        StockEntity stock = new StockEntity();
        stock.setStockId("2330");
        stock.setLatestPriceDate(LocalDate.now().minusDays(3));
        when(stockRepository.findById("2330")).thenReturn(Optional.of(stock));

        LocalDate today = LocalDate.now();
        when(finMindClient.fetchStockPrice(eq("2330"), any(), any())).thenReturn(List.of(
                new FinMindStockPriceDto(
                        today.minusDays(1), "2330", 1L, 1L,
                        BigDecimal.valueOf(90), BigDecimal.valueOf(95), BigDecimal.valueOf(88),
                        BigDecimal.valueOf(92), BigDecimal.ZERO, 1L),
                new FinMindStockPriceDto(
                        today, "2330", 1L, 1L,
                        BigDecimal.valueOf(93), BigDecimal.valueOf(98), BigDecimal.valueOf(91),
                        BigDecimal.valueOf(95), BigDecimal.ZERO, 1L)));

        service.refreshLatestPriceIfStale("2330");

        verify(stockRepository).save(stockCaptor.capture());
        StockEntity saved = stockCaptor.getValue();
        assertThat(saved.getLatestPriceDate()).isEqualTo(today);
        assertThat(saved.getLatestClosePrice()).isEqualByComparingTo(BigDecimal.valueOf(95));
        assertThat(saved.getUpdatedBy()).isEqualTo(AuditActors.FINMIND_SYNC);
    }

    @Test
    void refreshLatestPriceIfStale_fetches_from_january_first_of_the_current_year_not_just_a_lookback_window() {
        StockEntity stock = new StockEntity();
        stock.setStockId("2330");
        stock.setLatestPriceDate(LocalDate.now().minusDays(3));
        when(stockRepository.findById("2330")).thenReturn(Optional.of(stock));
        when(finMindClient.fetchStockPrice(eq("2330"), startDateCaptor.capture(), any())).thenReturn(List.of());

        service.refreshLatestPriceIfStale("2330");

        assertThat(startDateCaptor.getValue()).isEqualTo(LocalDate.of(CURRENT_YEAR, 1, 1));
    }

    @Test
    void refreshLatestPriceIfStale_derives_current_year_high_low_from_every_returned_row_not_just_the_latest() {
        StockEntity stock = new StockEntity();
        stock.setStockId("2330");
        stock.setLatestPriceDate(LocalDate.now().minusDays(3));
        when(stockRepository.findById("2330")).thenReturn(Optional.of(stock));

        LocalDate today = LocalDate.now();
        when(finMindClient.fetchStockPrice(eq("2330"), any(), any())).thenReturn(List.of(
                // an earlier-in-the-year row sets the high; today's row is not the extreme
                new FinMindStockPriceDto(
                        today.minusDays(30), "2330", 1L, 1L,
                        BigDecimal.valueOf(100), BigDecimal.valueOf(500), BigDecimal.valueOf(20),
                        BigDecimal.valueOf(100), BigDecimal.ZERO, 1L),
                new FinMindStockPriceDto(
                        today, "2330", 1L, 1L,
                        BigDecimal.valueOf(93), BigDecimal.valueOf(98), BigDecimal.valueOf(91),
                        BigDecimal.valueOf(95), BigDecimal.ZERO, 1L)));

        service.refreshLatestPriceIfStale("2330");

        verify(stockRepository).save(stockCaptor.capture());
        StockEntity saved = stockCaptor.getValue();
        assertThat(saved.getCurrentYearHighPrice()).isEqualByComparingTo(BigDecimal.valueOf(500));
        assertThat(saved.getCurrentYearLowPrice()).isEqualByComparingTo(BigDecimal.valueOf(20));
    }

    @Test
    void refreshLatestPriceIfStale_leaves_the_stock_untouched_when_FinMind_has_no_recent_rows() {
        StockEntity stock = new StockEntity();
        stock.setStockId("2330");
        stock.setLatestPriceDate(LocalDate.now().minusDays(10));
        when(stockRepository.findById("2330")).thenReturn(Optional.of(stock));
        when(finMindClient.fetchStockPrice(eq("2330"), any(), any())).thenReturn(List.of());

        service.refreshLatestPriceIfStale("2330");

        verify(stockRepository, never()).save(any());
    }

    /** 建立年度價格測試資料。 */
    private StockPriceYearlyEntity yearlyPrice(int year, double average, double high, double low) {
        StockPriceYearlyEntity row = new StockPriceYearlyEntity();
        row.setId(new StockYearId("2330", year));
        row.setAvgClosePrice(BigDecimal.valueOf(average));
        row.setHighPrice(BigDecimal.valueOf(high));
        row.setLowPrice(BigDecimal.valueOf(low));
        return row;
    }
}
