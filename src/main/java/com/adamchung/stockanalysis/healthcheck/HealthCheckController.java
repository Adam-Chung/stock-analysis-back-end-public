package com.adamchung.stockanalysis.healthcheck;

import com.adamchung.stockanalysis.healthcheck.dto.HealthCheckProfileRequest;
import com.adamchung.stockanalysis.healthcheck.dto.HealthCheckResponse;
import com.adamchung.stockanalysis.healthcheck.dto.HealthCheckSnapshotRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.NoSuchElementException;

/** SA-03 企業體質檢視畫面的 HTTP 邊界。 */
@RestController
@RequestMapping("/api/stocks/{stockId}/health-check")
@Tag(name = "企業體質檢視", description = "SA-03：依產業模板顯示財務體質檢視項目，v1 全部由使用者手動輸入（DEC-030）")
public class HealthCheckController {

    private final HealthCheckApplicationService applicationService;
    private final HealthCheckAiSuggestionService aiSuggestionService;

    public HealthCheckController(
            HealthCheckApplicationService applicationService,
            HealthCheckAiSuggestionService aiSuggestionService) {
        this.applicationService = applicationService;
        this.aiSuggestionService = aiSuggestionService;
    }

    /**
     * 載入完整畫面資料，不觸發任何 FinMind 呼叫。
     *
     * @param stockId 台股代號
     * @return 完整畫面回應
     */
    @Operation(summary = "載入企業體質檢視畫面")
    @GetMapping
    public HealthCheckResponse get(@PathVariable String stockId) {
        return applicationService.get(stockId);
    }

    /**
     * 更新公司簡介／整體備註。
     *
     * @param stockId 台股代號
     * @param request 已驗證的內容
     * @return 更新後的完整畫面回應
     */
    @Operation(summary = "更新公司簡介／整體備註")
    @PutMapping("/profile")
    public HealthCheckResponse updateProfile(
            @PathVariable String stockId, @Valid @RequestBody HealthCheckProfileRequest request) {
        return applicationService.updateProfile(stockId, request);
    }

    /**
     * 「儲存」按鈕：更新當季（或當月）筆記。
     *
     * @param stockId 台股代號
     * @param request 已驗證的當季數值/筆記
     * @return 更新後的完整畫面回應
     */
    @Operation(summary = "更新本季筆記")
    @PutMapping("/snapshot")
    public HealthCheckResponse updateSnapshot(
            @PathVariable String stockId, @Valid @RequestBody HealthCheckSnapshotRequest request) {
        return applicationService.updateSnapshot(stockId, request);
    }

    /**
     * 只有使用者明確操作時才產生並快取新的 AI 投資建議（DEC-068 後續實作）。
     *
     * @param stockId 台股代號
     * @return 新產生並已快取的 AI 建議
     */
    @Operation(summary = "產生 AI 投資建議", description = "只有使用者明確操作時才產生並快取新的 AI 建議")
    @PostMapping("/ai-suggestion")
    public HealthCheckResponse.AiSuggestionView generateAiSuggestion(@PathVariable String stockId) {
        return aiSuggestionService.generate(stockId);
    }

    /**
     * 將股票代號不存在的情況映射為 HTTP 404（比照 {@code FavoriteStockController} 既有做法）。
     *
     * @return 404 Not Found
     */
    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<Void> handleUnknownStock() {
        return ResponseEntity.notFound().build();
    }

    /**
     * 將不屬於這支股票所套用模板的 metric_key 映射為 HTTP 400。
     *
     * @return 400 Bad Request
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<String> handleInvalidMetricKey(IllegalArgumentException exception) {
        return ResponseEntity.badRequest().body(exception.getMessage());
    }
}
