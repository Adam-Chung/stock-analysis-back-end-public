package com.adamchung.stockanalysis.finmind.sync.service;

import com.adamchung.stockanalysis.common.AuditActors;
import com.adamchung.stockanalysis.stock.entity.StockEntity;
import com.adamchung.stockanalysis.stock.entity.StockDividendEntity;
import com.adamchung.stockanalysis.stock.entity.StockFinancialStatementEntity;
import com.adamchung.stockanalysis.stock.entity.StockPriceYearlyEntity;
import com.adamchung.stockanalysis.stock.entity.StockValuationYearlyEntity;
import com.adamchung.stockanalysis.stock.entity.StockYearId;
import com.adamchung.stockanalysis.finmind.FinMindClient;
import com.adamchung.stockanalysis.finmind.FinMindClientException;
import com.adamchung.stockanalysis.finmind.dto.FinMindDividendDto;
import com.adamchung.stockanalysis.finmind.dto.FinMindFinancialStatementDto;
import com.adamchung.stockanalysis.finmind.dto.FinMindStockInfoDto;
import com.adamchung.stockanalysis.finmind.dto.FinMindStockPriceDto;
import com.adamchung.stockanalysis.finmind.dto.FinMindStockValuationDto;
import com.adamchung.stockanalysis.finmind.sync.FinMindDataset;
import com.adamchung.stockanalysis.finmind.sync.FinmindSyncLogEntity;
import com.adamchung.stockanalysis.finmind.sync.FinmindSyncLogRepository;
import com.adamchung.stockanalysis.stock.repository.StockDividendRepository;
import com.adamchung.stockanalysis.stock.repository.StockFinancialStatementRepository;
import com.adamchung.stockanalysis.stock.repository.StockPriceYearlyRepository;
import com.adamchung.stockanalysis.stock.repository.StockRepository;
import com.adamchung.stockanalysis.stock.repository.StockValuationYearlyRepository;
import com.adamchung.stockanalysis.valuation.dto.YearlyPriceStats;
import com.adamchung.stockanalysis.valuation.dto.YearlyValuationStats;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FinMindDatasetSyncExecutorTest {

    private static final int CURRENT_YEAR = LocalDate.now().getYear();
    private static final int LAST_COMPLETE_YEAR = CURRENT_YEAR - 1;
    private static final LocalDate FETCH_LOOKBACK_START_DATE = LocalDate.now().minusYears(11);

    @Mock
    private FinMindClient finMindClient;
    @Mock
    private StockRepository stockRepository;
    @Mock
    private StockPriceYearlyRepository priceYearlyRepository;
    @Mock
    private StockValuationYearlyRepository valuationYearlyRepository;
    @Mock
    private StockDividendRepository dividendRepository;
    @Mock
    private StockFinancialStatementRepository financialStatementRepository;
    @Mock
    private FinmindSyncLogRepository syncLogRepository;

    @InjectMocks
    private FinMindDatasetSyncExecutor executor;

    @Captor
    private ArgumentCaptor<StockPriceYearlyEntity> priceCaptor;
    @Captor
    private ArgumentCaptor<StockValuationYearlyEntity> valuationCaptor;
    @Captor
    private ArgumentCaptor<FinmindSyncLogEntity> syncLogCaptor;

    @Test
    void syncProfile_creates_a_new_stock_from_TaiwanStockInfo() {
        when(finMindClient.fetchStockInfo("2330"))
                .thenReturn(List.of(new FinMindStockInfoDto("半導體業", "2330", "台積電", "twse")));
        when(stockRepository.findById("2330")).thenReturn(Optional.empty());
        when(stockRepository.save(any(StockEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        StockEntity saved = executor.syncProfile("2330");

        assertThat(saved.getStockId()).isEqualTo("2330");
        assertThat(saved.getStockName()).isEqualTo("台積電");
        assertThat(saved.getCreatedBy()).isEqualTo(AuditActors.FINMIND_SYNC);
    }

    @Test
    void syncProfile_throws_when_FinMind_has_no_info_for_an_unknown_stock_id() {
        when(finMindClient.fetchStockInfo("9999")).thenReturn(List.of());

        assertThatThrownBy(() -> executor.syncProfile("9999")).isInstanceOf(FinMindClientException.class);
    }

    @Test
    void syncProfile_refreshes_identity_without_overwriting_existing_price() {
        StockEntity existing = new StockEntity();
        existing.setStockId("2330");
        existing.setLatestClosePrice(BigDecimal.valueOf(185));
        existing.setLatestPriceDate(LocalDate.of(2026, 7, 29));
        when(finMindClient.fetchStockInfo("2330"))
                .thenReturn(List.of(new FinMindStockInfoDto("電子零組件業", "2330", "台積電", "twse")));
        when(stockRepository.findById("2330")).thenReturn(Optional.of(existing));
        when(stockRepository.save(existing)).thenReturn(existing);

        executor.syncProfile("2330");

        assertThat(existing.getLatestClosePrice()).isEqualByComparingTo("185");
        assertThat(existing.getIndustryCategory()).isEqualTo("電子零組件業");
    }

    @Test
    void syncYearlyPrice_aggregates_daily_price_into_a_complete_year_and_upserts_it() {
        when(finMindClient.fetchStockPrice(eq("2330"), any(), any())).thenReturn(List.of(
                new FinMindStockPriceDto(
                        LocalDate.of(LAST_COMPLETE_YEAR, 3, 1), "2330", 1L, 1L,
                        BigDecimal.valueOf(100), BigDecimal.valueOf(110), BigDecimal.valueOf(90),
                        BigDecimal.valueOf(100), BigDecimal.ZERO, 1L)));
        when(priceYearlyRepository.findById(any())).thenReturn(Optional.empty());
        when(syncLogRepository.findByStockIdAndDataset(any(), any())).thenReturn(Optional.empty());

        executor.syncYearlyPrice("2330", LocalDate.of(CURRENT_YEAR - 10, 1, 1), LocalDate.now(), CURRENT_YEAR);

        org.mockito.Mockito.verify(priceYearlyRepository).save(priceCaptor.capture());
        StockPriceYearlyEntity saved = priceCaptor.getValue();
        assertThat(saved.getId()).isEqualTo(new StockYearId("2330", LAST_COMPLETE_YEAR));
        assertThat(saved.getHighPrice()).isEqualByComparingTo(BigDecimal.valueOf(110));
        assertThat(saved.getLowPrice()).isEqualByComparingTo(BigDecimal.valueOf(90));
        assertThat(saved.getCreatedBy()).isEqualTo(AuditActors.FINMIND_SYNC);
    }

    @Test
    void syncYearlyPrice_never_persists_the_in_progress_current_year() {
        // Simulate FinMind returning a stray row dated this year even though the requested range
        // should not include it - the executor must filter it out regardless.
        when(finMindClient.fetchStockPrice(eq("2330"), any(), any())).thenReturn(List.of(
                new FinMindStockPriceDto(
                        LocalDate.of(CURRENT_YEAR, 1, 5), "2330", 1L, 1L,
                        BigDecimal.valueOf(100), BigDecimal.valueOf(110), BigDecimal.valueOf(90),
                        BigDecimal.valueOf(100), BigDecimal.ZERO, 1L)));
        when(syncLogRepository.findByStockIdAndDataset(any(), any())).thenReturn(Optional.empty());

        List<YearlyPriceStats> result = executor.syncYearlyPrice(
                "2330", LocalDate.of(CURRENT_YEAR - 10, 1, 1), LocalDate.now(), CURRENT_YEAR);

        assertThat(result).isEmpty();
        org.mockito.Mockito.verify(priceYearlyRepository, org.mockito.Mockito.never()).save(any());
    }

    @Test
    void syncYearlyValuation_aggregates_daily_valuation_and_upserts_it() {
        when(finMindClient.fetchStockValuation(eq("2330"), any(), any())).thenReturn(List.of(
                new FinMindStockValuationDto(
                        LocalDate.of(LAST_COMPLETE_YEAR, 3, 1), "2330",
                        BigDecimal.valueOf(0.03), BigDecimal.valueOf(15), BigDecimal.valueOf(5))));
        when(valuationYearlyRepository.findById(any())).thenReturn(Optional.empty());
        when(syncLogRepository.findByStockIdAndDataset(any(), any())).thenReturn(Optional.empty());

        executor.syncYearlyValuation(
                "2330", LocalDate.of(CURRENT_YEAR - 10, 1, 1), LocalDate.now(), CURRENT_YEAR);

        org.mockito.Mockito.verify(valuationYearlyRepository).save(valuationCaptor.capture());
        StockValuationYearlyEntity saved = valuationCaptor.getValue();
        assertThat(saved.getId()).isEqualTo(new StockYearId("2330", LAST_COMPLETE_YEAR));
        assertThat(saved.getAvgPer()).isEqualByComparingTo(BigDecimal.valueOf(15));
        assertThat(saved.getAvgPbr()).isEqualByComparingTo(BigDecimal.valueOf(5));
    }

    @Test
    void syncDividends_upserts_a_dividend_row_keyed_by_the_unique_constraint() {
        when(dividendRepository.findByStockIdOrderByAnnouncementDateDesc("2330")).thenReturn(List.of());
        when(syncLogRepository.findByStockIdAndDataset(any(), any())).thenReturn(Optional.empty());

        LocalDate exDate = LocalDate.of(2025, 7, 15);
        LocalDate announceDate = LocalDate.of(2025, 5, 1);
        when(finMindClient.fetchDividend(eq("2330"), eq(FETCH_LOOKBACK_START_DATE), any())).thenReturn(List.of(
                new FinMindDividendDto(
                        null, "2330", "114年第2季",
                        BigDecimal.valueOf(2.5), BigDecimal.ZERO, exDate, null,
                        null, null, null, announceDate)));

        List<StockDividendEntity> result = executor.syncDividends("2330");

        assertThat(result).singleElement().satisfies(saved -> {
            assertThat(saved.getYear()).isEqualTo(2025);
            assertThat(saved.getCashEarningsDistribution()).isEqualByComparingTo(BigDecimal.valueOf(2.5));
            assertThat(saved.getCumulativeCashDividend()).isEqualByComparingTo(BigDecimal.valueOf(2.5));
            assertThat(saved.getCreatedBy()).isEqualTo(AuditActors.FINMIND_SYNC);
        });
        org.mockito.Mockito.verify(dividendRepository).saveAll(result);
    }

    /** 驗證同一年度配息兩次時，第二筆的累積股利＝兩筆加總，第一筆維持只有自己的量。 */
    @Test
    void syncDividends_accumulates_cash_and_stock_dividend_within_the_same_year_in_chronological_order() {
        when(dividendRepository.findByStockIdOrderByAnnouncementDateDesc("2330")).thenReturn(List.of());
        when(syncLogRepository.findByStockIdAndDataset(any(), any())).thenReturn(Optional.empty());

        LocalDate firstAnnounceDate = LocalDate.of(2025, 5, 1);
        LocalDate secondAnnounceDate = LocalDate.of(2025, 11, 1);
        // both ex-dividend dates fall within 2025, so DividendYearResolver groups them together.
        FinMindDividendDto firstHalf = new FinMindDividendDto(
                null, "2330", "114年第2季",
                BigDecimal.valueOf(1), BigDecimal.ZERO, LocalDate.of(2025, 7, 15), null,
                BigDecimal.valueOf(1), BigDecimal.ZERO, null, firstAnnounceDate);
        FinMindDividendDto secondHalf = new FinMindDividendDto(
                null, "2330", "114年第4季",
                BigDecimal.valueOf(2), BigDecimal.ZERO, LocalDate.of(2025, 12, 15), null,
                BigDecimal.valueOf(1), BigDecimal.ZERO, null, secondAnnounceDate);
        // returned out of chronological order on purpose - the executor must sort before summing.
        when(finMindClient.fetchDividend(eq("2330"), eq(FETCH_LOOKBACK_START_DATE), any()))
                .thenReturn(List.of(secondHalf, firstHalf));

        List<StockDividendEntity> result = executor.syncDividends("2330");

        StockDividendEntity first = result.stream()
                .filter(d -> d.getAnnouncementDate().equals(firstAnnounceDate)).findFirst().orElseThrow();
        StockDividendEntity second = result.stream()
                .filter(d -> d.getAnnouncementDate().equals(secondAnnounceDate)).findFirst().orElseThrow();

        assertThat(first.getCumulativeCashDividend()).isEqualByComparingTo(BigDecimal.valueOf(1));
        assertThat(first.getCumulativeStockDividend()).isEqualByComparingTo(BigDecimal.valueOf(1));
        assertThat(second.getCumulativeCashDividend()).isEqualByComparingTo(BigDecimal.valueOf(3));
        assertThat(second.getCumulativeStockDividend()).isEqualByComparingTo(BigDecimal.valueOf(2));
    }

    @Test
    void syncFinancialStatements_upserts_a_financial_statement_row_keyed_by_the_unique_constraint() {
        when(financialStatementRepository.findByStockIdAndTypeOrderByStatementDateDesc("2330", "EPS"))
                .thenReturn(List.of());
        when(syncLogRepository.findByStockIdAndDataset(any(), any())).thenReturn(Optional.empty());

        LocalDate statementDate = LocalDate.of(2025, 3, 31);
        when(finMindClient.fetchFinancialStatement(eq("2330"), eq(FETCH_LOOKBACK_START_DATE), any())).thenReturn(List.of(
                new FinMindFinancialStatementDto(statementDate, "2330", "EPS", BigDecimal.valueOf(13.95), "每股盈餘")));

        List<StockFinancialStatementEntity> result = executor.syncFinancialStatements("2330");

        assertThat(result).singleElement().satisfies(saved -> {
            assertThat(saved.getType()).isEqualTo("EPS");
            assertThat(saved.getValue()).isEqualByComparingTo(BigDecimal.valueOf(13.95));
        });
    }

    /** 驗證資料內容未變時，同步時間仍會向前推進，且既有的 last_data_date 不會被清空。 */
    @Test
    void syncYearlyPrice_touches_updated_at_but_preserves_last_data_date_when_no_new_complete_year_is_found() {
        when(finMindClient.fetchStockPrice(eq("2330"), any(), any())).thenReturn(List.of());

        FinmindSyncLogEntity existingPriceLog = new FinmindSyncLogEntity();
        existingPriceLog.setId(1L);
        existingPriceLog.setStockId("2330");
        existingPriceLog.setDataset(FinMindDataset.TaiwanStockPrice);
        existingPriceLog.setLastDataDate(LocalDate.of(LAST_COMPLETE_YEAR, 12, 31));
        Instant previousUpdate = Instant.parse("2026-07-29T01:00:00Z");
        existingPriceLog.setUpdatedAt(previousUpdate);
        when(syncLogRepository.findByStockIdAndDataset("2330", FinMindDataset.TaiwanStockPrice))
                .thenReturn(Optional.of(existingPriceLog));

        executor.syncYearlyPrice("2330", LocalDate.of(CURRENT_YEAR - 10, 1, 1), LocalDate.now(), CURRENT_YEAR);

        org.mockito.Mockito.verify(syncLogRepository).save(syncLogCaptor.capture());
        FinmindSyncLogEntity saved = syncLogCaptor.getValue();
        assertThat(saved.getLastDataDate()).isEqualTo(LocalDate.of(LAST_COMPLETE_YEAR, 12, 31));
        assertThat(saved.getUpdatedAt()).isAfter(previousUpdate);
        assertThat(saved.getUpdatedBy()).isEqualTo(AuditActors.FINMIND_SYNC);
    }
}
