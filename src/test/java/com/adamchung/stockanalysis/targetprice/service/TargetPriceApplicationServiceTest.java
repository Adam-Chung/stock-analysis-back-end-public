package com.adamchung.stockanalysis.targetprice.service;

import com.adamchung.stockanalysis.finmind.sync.FinMindDataset;
import com.adamchung.stockanalysis.finmind.sync.FinmindSyncLogEntity;
import com.adamchung.stockanalysis.targetprice.dto.TargetPriceResponse;
import com.adamchung.stockanalysis.targetprice.dto.TargetPriceSettingsRequest;
import com.adamchung.stockanalysis.targetprice.entity.StockAiSuggestionEntity;
import com.adamchung.stockanalysis.targetprice.entity.StockTargetPriceSettingEntity;
import com.adamchung.stockanalysis.targetprice.repository.StockAiSuggestionRepository;
import com.adamchung.stockanalysis.targetprice.repository.StockTargetPriceSettingRepository;
import com.adamchung.stockanalysis.stock.entity.StockEntity;
import com.adamchung.stockanalysis.stock.entity.StockDividendEntity;
import com.adamchung.stockanalysis.stock.entity.StockFinancialStatementEntity;
import com.adamchung.stockanalysis.stock.entity.StockPriceYearlyEntity;
import com.adamchung.stockanalysis.stock.entity.StockValuationYearlyEntity;
import com.adamchung.stockanalysis.stock.entity.StockYearId;
import com.adamchung.stockanalysis.finmind.sync.FinmindSyncLogRepository;
import com.adamchung.stockanalysis.stock.repository.StockDividendRepository;
import com.adamchung.stockanalysis.stock.repository.StockFinancialStatementRepository;
import com.adamchung.stockanalysis.stock.repository.StockPriceYearlyRepository;
import com.adamchung.stockanalysis.stock.repository.StockRepository;
import com.adamchung.stockanalysis.stock.repository.StockValuationYearlyRepository;
import com.adamchung.stockanalysis.finmind.sync.service.FinMindSyncService;
import com.adamchung.stockanalysis.valuation.TargetPriceValuationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("unchecked")
class TargetPriceApplicationServiceTest {

    @Mock
    private FinMindSyncService syncService;
    @Mock
    private StockRepository stockRepository;
    @Mock
    private StockTargetPriceSettingRepository settingRepository;
    @Mock
    private StockPriceYearlyRepository priceRepository;
    @Mock
    private StockValuationYearlyRepository valuationRepository;
    @Mock
    private StockFinancialStatementRepository financialStatementRepository;
    @Mock
    private StockDividendRepository dividendRepository;
    @Mock
    private FinmindSyncLogRepository syncLogRepository;
    @Mock
    private StockAiSuggestionRepository aiSuggestionRepository;

    private TargetPriceApplicationService service;

    @BeforeEach
    void setUp() {
        service = new TargetPriceApplicationService(
                syncService,
                new TargetPriceValuationService(),
                stockRepository,
                settingRepository,
                priceRepository,
                valuationRepository,
                financialStatementRepository,
                dividendRepository,
                syncLogRepository,
                aiSuggestionRepository);
    }

    @Test
    void get_initializes_unsynced_stock_and_reports_independent_data_coverage() {
        stubCompleteScreenData(false);

        TargetPriceResponse response = service.get("2330");

        verify(syncService).fullInitSync("2330");
        verify(syncService).refreshLatestPriceIfStale("2330");
        assertThat(response.stockName()).isEqualTo("台積電");
        assertThat(response.targetPrice()).isPositive();
        assertThat(response.dataCoverage().avgPerYears()).isEqualTo(1);
        assertThat(response.dataCoverage().avgPbrYears()).isEqualTo(1);
        assertThat(response.dividendDate()).isEqualTo(LocalDate.now().plusDays(5));
        assertThat(response.lastUpdated()).isEqualTo(Instant.parse("2026-07-29T01:00:00Z"));
    }

    @Test
    void get_does_not_full_initialize_a_stock_with_sync_history() {
        stubCompleteScreenData(true);

        service.get("2330");

        verify(syncService, never()).fullInitSync("2330");
        verify(syncService).refreshLatestPriceIfStale("2330");
    }

    @Test
    void update_settings_persists_only_allowed_values_and_recalculates() {
        when(stockRepository.existsById("2330")).thenReturn(true);
        when(settingRepository.findById("2330")).thenReturn(Optional.empty(), Optional.of(savedSetting()));
        stubCompleteScreenDataExceptSetting();
        when(syncLogRepository.findByStockId("2330")).thenReturn(syncLogs());

        TargetPriceSettingsRequest request =
                new TargetPriceSettingsRequest("長期持有", 3, 10, "自訂備註", 2);
        TargetPriceResponse response = service.updateSettings("2330", request);

        ArgumentCaptor<StockTargetPriceSettingEntity> captor =
                ArgumentCaptor.forClass(StockTargetPriceSettingEntity.class);
        verify(settingRepository).save(captor.capture());
        assertThat(captor.getValue().getCreatedBy()).isEqualTo("default-user");
        assertThat(captor.getValue().getUpdatedBy()).isEqualTo("default-user");
        assertThat(response.epsAvgYears()).isEqualTo(3);
        assertThat(response.bpsAvgYears()).isEqualTo(10);
        verify(syncService, never()).incrementalUpdate("2330");
    }

    @Test
    void update_settings_rejects_a_window_outside_3_5_10() {
        TargetPriceSettingsRequest request =
                new TargetPriceSettingsRequest(null, 4, 5, null, 3);

        assertThatThrownBy(() -> service.updateSettings("2330", request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("3, 5, 10");
        verify(settingRepository, never()).save(any(StockTargetPriceSettingEntity.class));
    }

    @Test
    void refresh_calls_incremental_sync_without_saving_settings() {
        stubCompleteScreenData(true);

        service.refresh("2330");

        verify(syncService).incrementalUpdate("2330");
        verify(syncService).refreshLatestPriceIfStale("2330");
        verify(settingRepository, never()).save(any(StockTargetPriceSettingEntity.class));
    }

    @Test
    void get_persisted_folds_the_stocks_current_year_high_into_high10() {
        stubCompleteScreenData(true);
        // price(2025) fixture's highPrice is 200; a running current-year high of 260 must win.
        StockEntity stockWithNewHigh = stock();
        stockWithNewHigh.setCurrentYearHighPrice(BigDecimal.valueOf(260));
        when(stockRepository.findById("2330")).thenReturn(Optional.of(stockWithNewHigh));

        TargetPriceResponse response = service.getPersisted("2330");

        assertThat(response.high10()).isEqualByComparingTo(BigDecimal.valueOf(260));
    }

    /** 驗證前端可以不重新呼叫後端、只靠這份清單即時重算「前目標價出現」。 */
    @Test
    void get_persisted_exposes_yearly_low_prices_with_the_current_year_first_when_available() {
        stubCompleteScreenData(true);
        StockEntity stockWithCurrentYearLow = stock();
        stockWithCurrentYearLow.setCurrentYearLowPrice(BigDecimal.valueOf(40));
        when(stockRepository.findById("2330")).thenReturn(Optional.of(stockWithCurrentYearLow));

        TargetPriceResponse response = service.getPersisted("2330");

        assertThat(response.yearlyLowPrices()).hasSize(2);
        assertThat(response.yearlyLowPrices().get(0).year()).isEqualTo(LocalDate.now().getYear());
        assertThat(response.yearlyLowPrices().get(0).lowPrice()).isEqualByComparingTo(BigDecimal.valueOf(40));
        assertThat(response.yearlyLowPrices().get(1).year()).isEqualTo(2025);
        assertThat(response.yearlyLowPrices().get(1).lowPrice()).isEqualByComparingTo(BigDecimal.valueOf(50));
    }

    @Test
    void get_persisted_exposes_only_complete_years_when_no_current_year_low_is_tracked_yet() {
        stubCompleteScreenData(true);

        TargetPriceResponse response = service.getPersisted("2330");

        assertThat(response.yearlyLowPrices()).singleElement().satisfies(entry -> {
            assertThat(entry.year()).isEqualTo(2025);
            assertThat(entry.lowPrice()).isEqualByComparingTo(BigDecimal.valueOf(50));
        });
    }

    @Test
    void get_persisted_assembles_screen_without_calling_finMind() {
        stubCompleteScreenData(true);

        TargetPriceResponse response = service.getPersisted("2330");

        assertThat(response.stockId()).isEqualTo("2330");
        verify(syncService, never()).fullInitSync("2330");
        verify(syncService, never()).incrementalUpdate("2330");
        verify(syncService, never()).refreshLatestPriceIfStale("2330");
    }

    /** Stubs all repositories required to assemble one deterministic screen. */
    private void stubCompleteScreenData(boolean completeHistoryOnFirstRead) {
        when(settingRepository.findById("2330")).thenReturn(Optional.of(savedSetting()));
        stubCompleteScreenDataExceptSetting();
        if (completeHistoryOnFirstRead) {
            when(syncLogRepository.findByStockId("2330")).thenReturn(syncLogs());
        } else {
            when(syncLogRepository.findByStockId("2330")).thenReturn(List.of(), syncLogs());
        }
    }

    /** Stubs screen repositories other than the setting lookup. */
    private void stubCompleteScreenDataExceptSetting() {
        when(stockRepository.findById("2330")).thenReturn(Optional.of(stock()));
        when(priceRepository.findByIdStockIdOrderByIdYearDesc("2330"))
                .thenReturn(List.of(price(2025)));
        when(valuationRepository.findByIdStockIdOrderByIdYearDesc("2330"))
                .thenReturn(List.of(valuation(2025)));
        when(financialStatementRepository.findByStockIdAndTypeOrderByStatementDateDesc("2330", "EPS"))
                .thenReturn(epsRows());
        when(dividendRepository.findByStockIdOrderByAnnouncementDateDesc("2330"))
                .thenReturn(List.of(dividend(LocalDate.now().minusDays(3)), dividend(LocalDate.now().plusDays(5))));
        when(aiSuggestionRepository.findById("2330")).thenReturn(Optional.empty());
    }

    /** Creates a stock fixture with a current price. */
    private StockEntity stock() {
        StockEntity stock = new StockEntity();
        stock.setStockId("2330");
        stock.setStockName("台積電");
        stock.setIndustryCategory("半導體業");
        stock.setLatestClosePrice(BigDecimal.valueOf(100));
        stock.setLatestPriceDate(LocalDate.now());
        return stock;
    }

    /** Creates persisted user settings. */
    private StockTargetPriceSettingEntity savedSetting() {
        StockTargetPriceSettingEntity setting = new StockTargetPriceSettingEntity();
        setting.setStockId("2330");
        setting.setEpsAvgYears((short) 3);
        setting.setBpsAvgYears((short) 10);
        setting.setSettingNote("自訂備註");
        setting.setTargetPriceLevel((short) 2);
        return setting;
    }

    /** Creates one complete annual price row. */
    private StockPriceYearlyEntity price(int year) {
        StockPriceYearlyEntity row = new StockPriceYearlyEntity();
        row.setId(new StockYearId("2330", year));
        row.setAvgClosePrice(BigDecimal.valueOf(100));
        row.setLowPrice(BigDecimal.valueOf(50));
        row.setHighPrice(BigDecimal.valueOf(200));
        return row;
    }

    /** Creates one complete annual valuation row. */
    private StockValuationYearlyEntity valuation(int year) {
        StockValuationYearlyEntity row = new StockValuationYearlyEntity();
        row.setId(new StockYearId("2330", year));
        row.setLowPer(BigDecimal.valueOf(8));
        row.setAvgPer(BigDecimal.valueOf(10));
        row.setHighPer(BigDecimal.valueOf(12));
        row.setLowPbr(BigDecimal.valueOf(2));
        row.setAvgPbr(BigDecimal.valueOf(2.5));
        row.setHighPbr(BigDecimal.valueOf(3));
        return row;
    }

    /** Creates four quarterly EPS records so EpsAggregator emits one complete year. */
    private List<StockFinancialStatementEntity> epsRows() {
        return List.of(1, 4, 7, 10).stream()
                .map(month -> {
                    StockFinancialStatementEntity row = new StockFinancialStatementEntity();
                    row.setStockId("2330");
                    row.setType("EPS");
                    row.setStatementDate(LocalDate.of(2025, month, 1));
                    row.setValue(BigDecimal.valueOf(2));
                    return row;
                })
                .toList();
    }

    /**
     * Creates a dividend fixture with the requested ex-dividend date, tagged to the 2025 price
     * fixture's year so it participates in the request-time dividend-yield calculation
     * (avgClosePrice=100, cumulative cash=4 -&gt; yield=0.04, matching the old hardcoded fixture).
     */
    private StockDividendEntity dividend(LocalDate exDate) {
        StockDividendEntity row = new StockDividendEntity();
        row.setCashExDividendTradingDate(exDate);
        row.setYear(2025);
        row.setAnnouncementDate(exDate);
        row.setCumulativeCashDividend(BigDecimal.valueOf(4));
        row.setCumulativeStockDividend(BigDecimal.ZERO);
        return row;
    }

    /** Creates relevant sync logs whose oldest updated time is deterministic. */
    private List<FinmindSyncLogEntity> syncLogs() {
        FinmindSyncLogEntity price = syncLog(FinMindDataset.TaiwanStockPrice, "2026-07-29T02:00:00Z");
        FinmindSyncLogEntity valuation = syncLog(FinMindDataset.TaiwanStockPER, "2026-07-29T01:00:00Z");
        FinmindSyncLogEntity dividend = syncLog(FinMindDataset.TaiwanStockDividend, "2026-07-29T03:00:00Z");
        FinmindSyncLogEntity statements =
                syncLog(FinMindDataset.TaiwanStockFinancialStatements, "2026-07-29T04:00:00Z");
        return List.of(price, valuation, dividend, statements);
    }

    /** Creates one successful sync log. */
    private FinmindSyncLogEntity syncLog(FinMindDataset dataset, String updatedAt) {
        FinmindSyncLogEntity log = new FinmindSyncLogEntity();
        log.setDataset(dataset);
        log.setStatus("ok");
        log.setUpdatedAt(Instant.parse(updatedAt));
        return log;
    }
}
