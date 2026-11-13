package com.adamchung.stockanalysis.healthcheck;

import com.adamchung.stockanalysis.auth.TestSecurityContext;
import com.adamchung.stockanalysis.common.HealthCheckMetricKeys.Profit;
import com.adamchung.stockanalysis.healthcheck.dto.HealthCheckProfileRequest;
import com.adamchung.stockanalysis.healthcheck.dto.HealthCheckResponse;
import com.adamchung.stockanalysis.healthcheck.dto.HealthCheckSnapshotRequest;
import com.adamchung.stockanalysis.healthcheck.entity.StockHealthSnapshotEntity;
import com.adamchung.stockanalysis.healthcheck.repository.StockHealthSnapshotRepository;
import com.adamchung.stockanalysis.stock.entity.StockEntity;
import com.adamchung.stockanalysis.stock.repository.StockPriceYearlyRepository;
import com.adamchung.stockanalysis.stock.repository.StockRepository;
import com.adamchung.stockanalysis.stock.repository.StockValuationYearlyRepository;
import com.adamchung.stockanalysis.userdata.UserDataStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HealthCheckApplicationServiceTest {

    @Mock private StockRepository stockRepository;
    @Mock private UserDataStore userDataStore;
    @Mock private StockHealthSnapshotRepository snapshotRepository;
    @Mock private StockPriceYearlyRepository priceYearlyRepository;
    @Mock private StockValuationYearlyRepository valuationYearlyRepository;
    private HealthCheckApplicationService service;

    @BeforeEach
    void setUp() {
        TestSecurityContext.login(42L);
        service = new HealthCheckApplicationService(
                stockRepository, userDataStore, snapshotRepository, priceYearlyRepository, valuationYearlyRepository);
    }

    @Test
    void get_rejects_an_unknown_stock() {
        when(stockRepository.findById("9999")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.get("9999")).isInstanceOf(java.util.NoSuchElementException.class);
    }

    @Test
    void get_combines_shared_metric_values_with_the_current_users_note() {
        when(stockRepository.findById("1216")).thenReturn(Optional.of(generalStock()));
        when(userDataStore.healthProfile(42L, "1216")).thenReturn(Optional.empty());
        when(userDataStore.healthNotes(42L, "1216")).thenReturn(Map.of(
                new UserDataStore.HealthNoteKey("2025Q4", Profit.TEN_YEAR_PROFITABLE), "我的判斷"));
        when(userDataStore.healthAiSuggestion(42L, "1216")).thenReturn(Optional.empty());
        when(snapshotRepository.findByStockIdAndMetricKeyOrderByQuarterDesc(anyString(), anyString()))
                .thenReturn(List.of());
        when(snapshotRepository.findByStockIdAndMetricKeyOrderByQuarterDesc("1216", Profit.TEN_YEAR_PROFITABLE))
                .thenReturn(List.of(snapshot("1216", "2025Q4", Profit.TEN_YEAR_PROFITABLE, BigDecimal.ONE, "共用說明")));

        HealthCheckResponse response = service.get("1216");

        assertThat(response.categories().stream().flatMap(category -> category.metrics().stream())
                .filter(metric -> metric.key().equals(Profit.TEN_YEAR_PROFITABLE)).findFirst().orElseThrow()
                .periods().getFirst().note()).isEqualTo("我的判斷");
    }

    @Test
    void update_profile_writes_only_to_the_current_user() {
        when(stockRepository.findById("1216")).thenReturn(Optional.of(generalStock()));
        when(userDataStore.healthProfile(42L, "1216")).thenReturn(Optional.empty());
        when(userDataStore.healthNotes(42L, "1216")).thenReturn(Map.of());
        when(userDataStore.healthAiSuggestion(42L, "1216")).thenReturn(Optional.empty());
        when(snapshotRepository.findByStockIdAndMetricKeyOrderByQuarterDesc(anyString(), anyString()))
                .thenReturn(List.of());

        service.updateProfile("1216", new HealthCheckProfileRequest("簡介", "備註"));

        verify(userDataStore).saveHealthProfile(42L, "1216", "簡介", "備註");
    }

    @Test
    void update_snapshot_saves_personal_notes_without_overwriting_shared_values() {
        when(stockRepository.findById("1216")).thenReturn(Optional.of(generalStock()));
        when(userDataStore.healthProfile(42L, "1216")).thenReturn(Optional.empty());
        when(userDataStore.healthNotes(42L, "1216")).thenReturn(Map.of());
        when(userDataStore.healthAiSuggestion(42L, "1216")).thenReturn(Optional.empty());
        when(snapshotRepository.findByStockIdAndMetricKeyOrderByQuarterDesc(anyString(), anyString()))
                .thenReturn(List.of());
        HealthCheckSnapshotRequest request = new HealthCheckSnapshotRequest(
                "2025Q4", List.of(new HealthCheckSnapshotRequest.Entry(
                        Profit.TEN_YEAR_PROFITABLE, "2025Q4", null, "個人筆記")));

        service.updateSnapshot("1216", request);

        verify(userDataStore).saveHealthNote(42L, "1216", "2025Q4", Profit.TEN_YEAR_PROFITABLE, "個人筆記");
        verify(userDataStore).touchHealthReview(42L, "1216", "2025Q4");
        verify(snapshotRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    private static StockEntity generalStock() {
        StockEntity stock = new StockEntity();
        stock.setStockId("1216");
        stock.setStockName("統一");
        stock.setIndustryCategory("食品工業");
        stock.setLatestClosePrice(BigDecimal.valueOf(73.8));
        return stock;
    }

    private static StockHealthSnapshotEntity snapshot(
            String stockId, String period, String metricKey, BigDecimal value, String note) {
        StockHealthSnapshotEntity row = new StockHealthSnapshotEntity();
        row.setStockId(stockId);
        row.setQuarter(period);
        row.setMetricKey(metricKey);
        row.setValue(value);
        row.setNote(note);
        return row;
    }
}
