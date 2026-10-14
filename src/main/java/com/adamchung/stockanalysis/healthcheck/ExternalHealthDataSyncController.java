package com.adamchung.stockanalysis.healthcheck;

import com.adamchung.stockanalysis.externaldata.ExternalDataException;
import com.adamchung.stockanalysis.finmind.FinMindClientException;
import com.adamchung.stockanalysis.healthcheck.healthchecksync.ExternalHealthDataSyncService;
import com.adamchung.stockanalysis.healthcheck.healthchecksync.dto.ExternalSyncResult;
import com.adamchung.stockanalysis.healthcheck.healthchecksync.dto.FinMindHealthCheckSyncResult;
import com.adamchung.stockanalysis.healthcheck.healthchecksync.FinMindHealthCheckSyncService;
import com.adamchung.stockanalysis.healthcheck.healthchecksync.calculator.QuarterlyFactsJoiner;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClientResponseException;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;

/**
 * SA-03 企業體質檢視「更新本季資料」按鈕觸發的外部同步端點，整合兩個獨立的資料來源：
 * TWSE OpenAPI／銀行局／金管會（{@link ExternalHealthDataSyncService}，DEC-046~049，涵蓋
 * 董監持股/質押、法人持股比季變化、銀行監理三指標共 6 個細項）與一般 FinMind dataset
 * （{@link FinMindHealthCheckSyncService}，DEC-053，涵蓋其餘約 24 個細項）。呼叫這個端點會
 * 對兩個來源各自發出多次請求，單次同步可能耗時數十秒——呼叫端應設定足夠寬鬆的逾時，或視為
 * 背景操作、之後再呼叫既有的 {@code GET /api/stocks/{stockId}/health-check} 確認結果。
 *
 * <p>兩個來源各自獨立呼叫、各自可能失敗（DEC-053）：任一來源成功就回 HTTP 200，
 * {@link ExternalSyncResult#syncedMetricKeys()} 合併兩邊成功的細項，失敗的來源記錄在
 * {@link ExternalSyncResult#warnings()}；兩個來源都失敗才回 502。股票代號本身不存在時
 * （兩個來源共用同一個 {@code stock} 表查找）直接回 404，不當作單一來源的失敗警告處理。
 */
@RestController
@RequestMapping("/api/stocks/{stockId}/health-check/external-sync")
@Tag(name = "企業體質檢視外部資料同步", description = "董監持股/質押、法人持股比季變化、銀行監理指標，以及其餘細項的 FinMind 自動同步")
public class ExternalHealthDataSyncController {

    private final ExternalHealthDataSyncService externalSyncService;
    private final FinMindHealthCheckSyncService finMindSyncService;
    private final HealthCheckApplicationService healthCheckApplicationService;

    public ExternalHealthDataSyncController(
            ExternalHealthDataSyncService externalSyncService,
            FinMindHealthCheckSyncService finMindSyncService,
            HealthCheckApplicationService healthCheckApplicationService) {
        this.externalSyncService = externalSyncService;
        this.finMindSyncService = finMindSyncService;
        this.healthCheckApplicationService = healthCheckApplicationService;
    }

    @Operation(summary = "同步外部資料來源",
            description = "從 TWSE OpenAPI／銀行局／金管會，以及 FinMind 財報/月營收/籌碼類 dataset 抓取資料並寫入快照；"
                    + "任一來源成功即回 200，失敗來源記錄於 warnings")
    @PostMapping
    public ResponseEntity<ExternalSyncResult> sync(@PathVariable String stockId) {
        List<String> syncedMetricKeys = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        boolean anySourceSucceeded = false;
        String latestDataQuarter = null;

        try {
            ExternalSyncResult twseResult = externalSyncService.sync(stockId);
            syncedMetricKeys.addAll(twseResult.syncedMetricKeys());
            warnings.addAll(twseResult.warnings());
            anySourceSucceeded = true;
        } catch (ExternalDataException exception) {
            warnings.add("TWSE/銀行局同步失敗：" + exception.getMessage());
        }

        try {
            FinMindHealthCheckSyncResult finMindResult = finMindSyncService.sync(stockId);
            syncedMetricKeys.addAll(finMindResult.syncedMetricKeys());
            latestDataQuarter = finMindResult.latestDataQuarter();
            anySourceSucceeded = true;
        } catch (FinMindClientException | RestClientResponseException exception) {
            warnings.add("FinMind 同步失敗：" + exception.getMessage());
        }

        if (anySourceSucceeded) {
            // 「資料季度」優先採用這次 FinMind 財報三表實際抓到的最新季度（DEC-067）；
            // 只有 TWSE/銀行局來源成功、FinMind 沒有回傳任何財報資料時，才退回用今天的曆季推算
            // （銀行監理指標本身就沒有「財報季度」概念，用曆季當作保守估計）。
            String reviewedQuarter = latestDataQuarter != null
                    ? latestDataQuarter
                    : QuarterlyFactsJoiner.quarterLabel(LocalDate.now());
            healthCheckApplicationService.touchProfileAfterExternalSync(stockId, reviewedQuarter);
        }

        ExternalSyncResult result = new ExternalSyncResult(stockId, syncedMetricKeys, warnings);
        return anySourceSucceeded ? ResponseEntity.ok(result) : ResponseEntity.status(502).body(result);
    }

    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<Void> handleUnknownStock() {
        return ResponseEntity.notFound().build();
    }
}
