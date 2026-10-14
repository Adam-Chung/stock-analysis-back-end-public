package com.adamchung.stockanalysis.healthcheck;

import com.adamchung.stockanalysis.common.AuditActors;
import com.adamchung.stockanalysis.common.HealthCheckMetricKeys.BankRegulatory;
import com.adamchung.stockanalysis.common.HealthCheckMetricKeys.Profit;
import com.adamchung.stockanalysis.common.HealthCheckMetricKeys.Reinvest;
import com.adamchung.stockanalysis.healthcheck.dto.HealthCheckProfileRequest;
import com.adamchung.stockanalysis.healthcheck.dto.HealthCheckResponse;
import com.adamchung.stockanalysis.healthcheck.dto.HealthCheckSnapshotRequest;
import com.adamchung.stockanalysis.healthcheck.entity.StockHealthAiSuggestionEntity;
import com.adamchung.stockanalysis.healthcheck.entity.StockHealthProfileEntity;
import com.adamchung.stockanalysis.healthcheck.entity.StockHealthSnapshotEntity;
import com.adamchung.stockanalysis.healthcheck.enums.HealthCheckStatus;
import com.adamchung.stockanalysis.healthcheck.enums.HealthCheckTemplate;
import com.adamchung.stockanalysis.healthcheck.repository.StockHealthAiSuggestionRepository;
import com.adamchung.stockanalysis.healthcheck.repository.StockHealthProfileRepository;
import com.adamchung.stockanalysis.healthcheck.repository.StockHealthSnapshotRepository;
import com.adamchung.stockanalysis.stock.entity.StockEntity;
import com.adamchung.stockanalysis.stock.entity.StockPriceYearlyEntity;
import com.adamchung.stockanalysis.stock.entity.StockValuationYearlyEntity;
import com.adamchung.stockanalysis.stock.entity.StockYearId;
import com.adamchung.stockanalysis.stock.repository.StockPriceYearlyRepository;
import com.adamchung.stockanalysis.stock.repository.StockRepository;
import com.adamchung.stockanalysis.stock.repository.StockValuationYearlyRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HealthCheckApplicationServiceTest {

    @Mock
    private StockRepository stockRepository;
    @Mock
    private StockHealthProfileRepository profileRepository;
    @Mock
    private StockHealthSnapshotRepository snapshotRepository;
    @Mock
    private StockPriceYearlyRepository priceYearlyRepository;
    @Mock
    private StockValuationYearlyRepository valuationYearlyRepository;
    @Mock
    private StockHealthAiSuggestionRepository aiSuggestionRepository;

    @InjectMocks
    private HealthCheckApplicationService service;

    @Captor
    private ArgumentCaptor<StockHealthSnapshotEntity> snapshotCaptor;
    @Captor
    private ArgumentCaptor<StockHealthProfileEntity> profileCaptor;

    private static StockEntity generalStock() {
        StockEntity stock = new StockEntity();
        stock.setStockId("1216");
        stock.setStockName("統一");
        stock.setIndustryCategory("食品工業");
        stock.setLatestClosePrice(BigDecimal.valueOf(73.8));
        return stock;
    }

    private static StockEntity bankStock() {
        StockEntity stock = new StockEntity();
        stock.setStockId("2884");
        stock.setStockName("玉山金");
        stock.setIndustryCategory("金融業（銀行）");
        return stock;
    }

    @Test
    void get_throws_for_an_unknown_stock_id() {
        when(stockRepository.findById("9999")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get("9999")).isInstanceOf(java.util.NoSuchElementException.class);
    }

    @Test
    void get_resolves_general_template_and_omits_price_trend() {
        when(stockRepository.findById("1216")).thenReturn(Optional.of(generalStock()));
        when(profileRepository.findById("1216")).thenReturn(Optional.empty());

        HealthCheckResponse response = service.get("1216");

        assertThat(response.template()).isEqualTo(HealthCheckTemplate.GENERAL);
        assertThat(response.priceTrend()).isEmpty();
        assertThat(response.categories()).extracting(HealthCheckResponse.CategoryView::key).contains("debt", "margin");
        // no profile row yet -> empty-but-present profile view, not a null field
        assertThat(response.profile().profile()).isNull();
        assertThat(response.profile().lastReviewedQuarter()).isNull();
    }

    @Test
    void get_resolves_bank_template_and_joins_price_and_valuation_yearly_by_year() {
        when(stockRepository.findById("2884")).thenReturn(Optional.of(bankStock()));
        when(profileRepository.findById("2884")).thenReturn(Optional.empty());

        StockPriceYearlyEntity price2024 = yearlyPrice("2884", 2024, BigDecimal.valueOf(26.4));
        StockValuationYearlyEntity valuation2024 = yearlyValuation("2884", 2024, BigDecimal.valueOf(19.6), BigDecimal.valueOf(1.82));
        when(priceYearlyRepository.findByIdStockIdOrderByIdYearDesc("2884")).thenReturn(List.of(price2024));
        when(valuationYearlyRepository.findByIdStockIdOrderByIdYearDesc("2884")).thenReturn(List.of(valuation2024));

        HealthCheckResponse response = service.get("2884");

        assertThat(response.template()).isEqualTo(HealthCheckTemplate.BANK);
        assertThat(response.categories()).extracting(HealthCheckResponse.CategoryView::key)
                .contains("bankRegulatory", "bankFlows", "bankRevenue")
                .doesNotContain("debt", "margin", "reinvest");
        assertThat(response.priceTrend()).hasSize(1);
        assertThat(response.priceTrend().get(0).year()).isEqualTo(2024);
        assertThat(response.priceTrend().get(0).avgClosePrice()).isEqualByComparingTo("26.4");
        assertThat(response.priceTrend().get(0).avgPer()).isEqualByComparingTo("19.6");
        assertThat(response.priceTrend().get(0).avgPbr()).isEqualByComparingTo("1.82");
    }

    @Test
    void get_evaluates_status_from_stored_values_against_the_metric_threshold() {
        when(stockRepository.findById("1216")).thenReturn(Optional.of(generalStock()));
        when(profileRepository.findById("1216")).thenReturn(Optional.empty());
        // 其餘 ~23 個細項本輪不關心，一律回空清單；下面針對目標細項覆寫成有資料
        when(snapshotRepository.findByStockIdAndMetricKeyOrderByQuarterDesc(anyString(), anyString()))
                .thenReturn(List.of());

        StockHealthSnapshotEntity passing = snapshotRow("1216", "2024Q4", Reinvest.REINVESTMENT_RATIO, BigDecimal.valueOf(123.27));
        StockHealthSnapshotEntity failing = snapshotRow("1216", "2023Q4", Reinvest.REINVESTMENT_RATIO, BigDecimal.valueOf(250));
        when(snapshotRepository.findByStockIdAndMetricKeyOrderByQuarterDesc("1216", Reinvest.REINVESTMENT_RATIO))
                .thenReturn(List.of(passing, failing));

        HealthCheckResponse response = service.get("1216");

        HealthCheckResponse.MetricView reinvestMetric = response.categories().stream()
                .filter(c -> c.key().equals("reinvest")).findFirst().orElseThrow()
                .metrics().get(0);
        assertThat(reinvestMetric.periods()).hasSize(2);
        assertThat(reinvestMetric.periods().get(0).status()).isEqualTo(HealthCheckStatus.PASS); // 123.27 < 200
        assertThat(reinvestMetric.periods().get(1).status()).isEqualTo(HealthCheckStatus.FAIL); // 250 >= 200
    }

    @Test
    void get_limits_period_history_to_the_category_history_limit() {
        when(stockRepository.findById("1216")).thenReturn(Optional.of(generalStock()));
        when(profileRepository.findById("1216")).thenReturn(Optional.empty());
        when(snapshotRepository.findByStockIdAndMetricKeyOrderByQuarterDesc(anyString(), anyString()))
                .thenReturn(List.of());

        List<StockHealthSnapshotEntity> sixRows = List.of(
                snapshotRow("1216", "2024Q4", Profit.TEN_YEAR_PROFITABLE, BigDecimal.ONE),
                snapshotRow("1216", "2024Q3", Profit.TEN_YEAR_PROFITABLE, BigDecimal.ONE),
                snapshotRow("1216", "2024Q2", Profit.TEN_YEAR_PROFITABLE, BigDecimal.ONE),
                snapshotRow("1216", "2024Q1", Profit.TEN_YEAR_PROFITABLE, BigDecimal.ONE),
                snapshotRow("1216", "2023Q4", Profit.TEN_YEAR_PROFITABLE, BigDecimal.ONE),
                snapshotRow("1216", "2023Q3", Profit.TEN_YEAR_PROFITABLE, BigDecimal.ONE));
        when(snapshotRepository.findByStockIdAndMetricKeyOrderByQuarterDesc("1216", Profit.TEN_YEAR_PROFITABLE))
                .thenReturn(sixRows);

        HealthCheckResponse response = service.get("1216");

        HealthCheckResponse.MetricView profitMetric = response.categories().stream()
                .filter(c -> c.key().equals("profit")).findFirst().orElseThrow()
                .metrics().get(0);
        assertThat(profitMetric.periods()).hasSize(5); // 一般大項預設「近五季」，第6筆不應出現
    }

    @Test
    void updateSnapshot_rejects_a_metric_key_that_does_not_belong_to_the_stocks_template() {
        when(stockRepository.findById("1216")).thenReturn(Optional.of(generalStock()));

        HealthCheckSnapshotRequest request = new HealthCheckSnapshotRequest(
                "2024Q4",
                List.of(new HealthCheckSnapshotRequest.Entry(BankRegulatory.CAPITAL_ADEQUACY_RATIO, "2024Q4", BigDecimal.TEN, null)));

        assertThatThrownBy(() -> service.updateSnapshot("1216", request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(BankRegulatory.CAPITAL_ADEQUACY_RATIO);
        verify(snapshotRepository, never()).save(any());
    }

    @Test
    void updateSnapshot_creates_a_new_row_and_records_the_default_actor_and_last_reviewed_quarter() {
        when(stockRepository.findById("1216")).thenReturn(Optional.of(generalStock()));
        when(snapshotRepository.findByStockIdAndQuarterAndMetricKey("1216", "2024Q4", Profit.TEN_YEAR_PROFITABLE))
                .thenReturn(Optional.empty());
        when(profileRepository.findById("1216")).thenReturn(Optional.empty());

        HealthCheckSnapshotRequest request = new HealthCheckSnapshotRequest(
                "2024Q4",
                List.of(new HealthCheckSnapshotRequest.Entry(Profit.TEN_YEAR_PROFITABLE, "2024Q4", BigDecimal.ONE, "備註")));

        service.updateSnapshot("1216", request);

        verify(snapshotRepository).save(snapshotCaptor.capture());
        StockHealthSnapshotEntity saved = snapshotCaptor.getValue();
        assertThat(saved.getStockId()).isEqualTo("1216");
        assertThat(saved.getQuarter()).isEqualTo("2024Q4");
        assertThat(saved.getMetricKey()).isEqualTo(Profit.TEN_YEAR_PROFITABLE);
        assertThat(saved.getValue()).isNull();
        assertThat(saved.getNote()).isEqualTo("備註");
        assertThat(saved.getCreatedBy()).isEqualTo(AuditActors.DEFAULT_USER);
        assertThat(saved.getUpdatedBy()).isEqualTo(AuditActors.DEFAULT_USER);

        verify(profileRepository).save(profileCaptor.capture());
        assertThat(profileCaptor.getValue().getLastReviewedQuarter()).isEqualTo("2024Q4");
    }

    @Test
    void updateSnapshot_updates_an_existing_row_in_place_instead_of_inserting_a_duplicate() {
        when(stockRepository.findById("1216")).thenReturn(Optional.of(generalStock()));
        StockHealthSnapshotEntity existing = snapshotRow("1216", "2024Q4", Profit.TEN_YEAR_PROFITABLE, BigDecimal.ZERO);
        when(snapshotRepository.findByStockIdAndQuarterAndMetricKey("1216", "2024Q4", Profit.TEN_YEAR_PROFITABLE))
                .thenReturn(Optional.of(existing));
        when(profileRepository.findById("1216")).thenReturn(Optional.empty());

        HealthCheckSnapshotRequest request = new HealthCheckSnapshotRequest(
                "2024Q4",
                List.of(new HealthCheckSnapshotRequest.Entry(Profit.TEN_YEAR_PROFITABLE, "2024Q4", BigDecimal.ONE, null)));

        service.updateSnapshot("1216", request);

        verify(snapshotRepository, times(1)).save(eq(existing));
        assertThat(existing.getValue()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void updateProfile_creates_a_new_profile_row_when_none_exists_yet() {
        when(stockRepository.findById("1216")).thenReturn(Optional.of(generalStock()));
        when(profileRepository.findById("1216")).thenReturn(Optional.empty());

        service.updateProfile("1216", new HealthCheckProfileRequest("公司簡介", "整體評語"));

        verify(profileRepository).save(profileCaptor.capture());
        StockHealthProfileEntity saved = profileCaptor.getValue();
        assertThat(saved.getStockId()).isEqualTo("1216");
        assertThat(saved.getProfile()).isEqualTo("公司簡介");
        assertThat(saved.getCustomNote()).isEqualTo("整體評語");
        assertThat(saved.getCreatedBy()).isEqualTo(AuditActors.DEFAULT_USER);
        assertThat(saved.getUpdatedBy()).isEqualTo(AuditActors.DEFAULT_USER);
    }

    @Test
    void touchProfileAfterExternalSync_bumps_updatedAt_even_when_reviewedQuarter_is_unchanged() {
        // DEC-071：使用者回報連按兩次「更新本季資料」（同一天、FinMind 最新可用季度沒有變化）
        // 之後「更新時間」沒有跟著刷新。根因是 Hibernate 的 dirty checking：如果
        // lastReviewedQuarter／updatedBy 這次跟已存在的值完全一樣，實體就不算 dirty，
        // @UpdateTimestamp 不會被觸發。這裡模擬「已存在一筆跟這次要寫入的值完全相同」的
        // profile，驗證服務層會明確呼叫 setUpdatedAt 強制刷新，不能只依賴 Hibernate 被動偵測。
        when(stockRepository.findById("1216")).thenReturn(Optional.of(generalStock()));
        StockHealthProfileEntity existing = new StockHealthProfileEntity();
        existing.setStockId("1216");
        existing.setLastReviewedQuarter("2026Q1");
        existing.setUpdatedBy(AuditActors.EXTERNAL_SYNC);
        existing.setUpdatedAt(Instant.parse("2026-08-03T03:59:00Z"));
        when(profileRepository.findById("1216")).thenReturn(Optional.of(existing));

        Instant beforeCall = Instant.now();
        service.touchProfileAfterExternalSync("1216", "2026Q1");

        verify(profileRepository).save(profileCaptor.capture());
        StockHealthProfileEntity saved = profileCaptor.getValue();
        assertThat(saved.getLastReviewedQuarter()).isEqualTo("2026Q1");
        assertThat(saved.getUpdatedBy()).isEqualTo(AuditActors.EXTERNAL_SYNC);
        assertThat(saved.getUpdatedAt()).isAfterOrEqualTo(beforeCall);
    }

    @Test
    void get_returns_null_ai_suggestion_when_none_has_been_cached_yet() {
        when(stockRepository.findById("1216")).thenReturn(Optional.of(generalStock()));
        when(profileRepository.findById("1216")).thenReturn(Optional.empty());
        when(aiSuggestionRepository.findById("1216")).thenReturn(Optional.empty());

        HealthCheckResponse response = service.get("1216");

        assertThat(response.aiSuggestion()).isNull();
    }

    @Test
    void get_returns_the_cached_ai_suggestion_when_one_exists() {
        when(stockRepository.findById("1216")).thenReturn(Optional.of(generalStock()));
        when(profileRepository.findById("1216")).thenReturn(Optional.empty());
        StockHealthAiSuggestionEntity cached = new StockHealthAiSuggestionEntity();
        cached.setStockId("1216");
        cached.setSuggestionText("既有的 AI 建議文字");
        Instant updatedAt = Instant.parse("2026-08-05T02:00:00Z");
        cached.setUpdatedAt(updatedAt);
        when(aiSuggestionRepository.findById("1216")).thenReturn(Optional.of(cached));

        HealthCheckResponse response = service.get("1216");

        assertThat(response.aiSuggestion().text()).isEqualTo("既有的 AI 建議文字");
        assertThat(response.aiSuggestion().updatedAt()).isEqualTo(updatedAt);
    }

    private static StockHealthSnapshotEntity snapshotRow(String stockId, String quarter, String metricKey, BigDecimal value) {
        StockHealthSnapshotEntity row = new StockHealthSnapshotEntity();
        row.setStockId(stockId);
        row.setQuarter(quarter);
        row.setMetricKey(metricKey);
        row.setValue(value);
        return row;
    }

    private static StockPriceYearlyEntity yearlyPrice(String stockId, int year, BigDecimal avgClosePrice) {
        StockPriceYearlyEntity price = new StockPriceYearlyEntity();
        price.setId(new StockYearId(stockId, year));
        price.setAvgClosePrice(avgClosePrice);
        return price;
    }

    private static StockValuationYearlyEntity yearlyValuation(String stockId, int year, BigDecimal avgPer, BigDecimal avgPbr) {
        StockValuationYearlyEntity valuation = new StockValuationYearlyEntity();
        valuation.setId(new StockYearId(stockId, year));
        valuation.setAvgPer(avgPer);
        valuation.setAvgPbr(avgPbr);
        return valuation;
    }
}
