package com.adamchung.stockanalysis.healthcheck;

import com.adamchung.stockanalysis.common.HealthCheckMetricKeys.Insider;
import com.adamchung.stockanalysis.common.HealthCheckMetricKeys.Operations;
import com.adamchung.stockanalysis.externaldata.ExternalDataException;
import com.adamchung.stockanalysis.finmind.FinMindClientException;
import com.adamchung.stockanalysis.healthcheck.healthchecksync.ExternalHealthDataSyncService;
import com.adamchung.stockanalysis.healthcheck.healthchecksync.dto.ExternalSyncResult;
import com.adamchung.stockanalysis.healthcheck.healthchecksync.dto.FinMindHealthCheckSyncResult;
import com.adamchung.stockanalysis.healthcheck.healthchecksync.FinMindHealthCheckSyncService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.NoSuchElementException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 涵蓋 DEC-053 的部分失敗語意：TWSE/銀行局來源與 FinMind 來源各自獨立呼叫，任一來源成功即回
 * 200，兩者都失敗才回 502；股票代號不存在時交給既有的 {@code @ExceptionHandler} 處理，
 * 不當成單一來源失敗。
 */
@ExtendWith(MockitoExtension.class)
class ExternalHealthDataSyncControllerTest {

    @Mock
    private ExternalHealthDataSyncService syncService;
    @Mock
    private FinMindHealthCheckSyncService finMindSyncService;
    @Mock
    private HealthCheckApplicationService healthCheckApplicationService;

    private final ExternalHealthDataSyncController controller() {
        return new ExternalHealthDataSyncController(syncService, finMindSyncService, healthCheckApplicationService);
    }

    @Test
    void both_sources_succeed_returns_200_with_merged_metric_keys_and_no_warnings() {
        when(syncService.sync("2884")).thenReturn(
                new ExternalSyncResult("2884", List.of(Insider.DIRECTOR_SHAREHOLDING_RATIO), List.of()));
        when(finMindSyncService.sync("2884")).thenReturn(
                new FinMindHealthCheckSyncResult("2884", List.of(Operations.EPS_QUARTER), "2025Q4"));

        ResponseEntity<ExternalSyncResult> response = controller().sync("2884");

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody().syncedMetricKeys())
                .containsExactlyInAnyOrder(Insider.DIRECTOR_SHAREHOLDING_RATIO, Operations.EPS_QUARTER);
        assertThat(response.getBody().warnings()).isEmpty();
        verify(healthCheckApplicationService).touchProfileAfterExternalSync(eq("2884"), eq("2025Q4"));
    }

    @Test
    void successful_twse_sync_merges_service_level_warnings_into_the_response() {
        when(syncService.sync("2884")).thenReturn(new ExternalSyncResult(
                "2884",
                List.of(Insider.DIRECTOR_SHAREHOLDING_RATIO),
                List.of("銀行局資本適足率有 1 筆高於 100%")));
        when(finMindSyncService.sync("2884")).thenReturn(
                new FinMindHealthCheckSyncResult("2884", List.of(Operations.EPS_QUARTER), "2025Q4"));

        ResponseEntity<ExternalSyncResult> response = controller().sync("2884");

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody().warnings()).containsExactly("銀行局資本適足率有 1 筆高於 100%");
    }

    @Test
    void finMind_failure_with_twse_success_returns_200_with_twse_keys_and_a_warning() {
        when(syncService.sync("2884")).thenReturn(
                new ExternalSyncResult("2884", List.of(Insider.DIRECTOR_SHAREHOLDING_RATIO), List.of()));
        when(finMindSyncService.sync("2884")).thenThrow(new FinMindClientException("timeout"));

        ResponseEntity<ExternalSyncResult> response = controller().sync("2884");

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody().syncedMetricKeys()).containsExactly(Insider.DIRECTOR_SHAREHOLDING_RATIO);
        assertThat(response.getBody().warnings()).hasSize(1);
        assertThat(response.getBody().warnings().get(0)).contains("FinMind");
    }

    @Test
    void twse_failure_with_finMind_success_returns_200_with_finMind_keys_and_a_warning() {
        when(syncService.sync("2884")).thenThrow(new ExternalDataException("找不到銀行名稱對照"));
        when(finMindSyncService.sync("2884")).thenReturn(
                new FinMindHealthCheckSyncResult("2884", List.of(Operations.EPS_QUARTER), "2025Q4"));

        ResponseEntity<ExternalSyncResult> response = controller().sync("2884");

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody().syncedMetricKeys()).containsExactly(Operations.EPS_QUARTER);
        assertThat(response.getBody().warnings()).hasSize(1);
        assertThat(response.getBody().warnings().get(0)).contains("TWSE");
        verify(healthCheckApplicationService).touchProfileAfterExternalSync(eq("2884"), eq("2025Q4"));
    }

    @Test
    void finMind_success_without_any_financial_data_falls_back_to_current_calendar_quarter() {
        when(syncService.sync("2884")).thenThrow(new ExternalDataException("找不到銀行名稱對照"));
        when(finMindSyncService.sync("2884")).thenReturn(
                new FinMindHealthCheckSyncResult("2884", List.of(), null));

        controller().sync("2884");

        verify(healthCheckApplicationService).touchProfileAfterExternalSync(eq("2884"), anyString());
    }

    @Test
    void both_sources_fail_returns_502_with_both_warnings() {
        when(syncService.sync("2884")).thenThrow(new ExternalDataException("找不到銀行名稱對照"));
        when(finMindSyncService.sync("2884")).thenThrow(new FinMindClientException("timeout"));

        ResponseEntity<ExternalSyncResult> response = controller().sync("2884");

        HttpStatusCode status = response.getStatusCode();
        assertThat(status.value()).isEqualTo(502);
        assertThat(response.getBody().syncedMetricKeys()).isEmpty();
        assertThat(response.getBody().warnings()).hasSize(2);
        verify(healthCheckApplicationService, never()).touchProfileAfterExternalSync(anyString(), anyString());
    }

    @Test
    void unknown_stock_propagates_NoSuchElementException_not_treated_as_a_source_warning() {
        when(syncService.sync("9999")).thenThrow(new NoSuchElementException("Unknown stock_id: 9999"));

        assertThatThrownBy(() -> controller().sync("9999")).isInstanceOf(NoSuchElementException.class);
    }
}
