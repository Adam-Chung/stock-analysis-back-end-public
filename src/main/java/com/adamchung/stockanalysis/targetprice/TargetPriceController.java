package com.adamchung.stockanalysis.targetprice;

import com.adamchung.stockanalysis.targetprice.dto.TargetPriceResponse;
import com.adamchung.stockanalysis.targetprice.dto.TargetPriceSettingsRequest;
import com.adamchung.stockanalysis.targetprice.service.AiSuggestionService;
import com.adamchung.stockanalysis.targetprice.service.TargetPriceApplicationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** SA-01 目標價畫面的 HTTP 邊界。 */
@RestController
@RequestMapping("/api/stocks/{stockId}/target-price")
@Tag(name = "股票目標價", description = "SA-01：計算並顯示單一股票的目標價與相關指標")
public class TargetPriceController {

    private final TargetPriceApplicationService applicationService;
    private final AiSuggestionService aiSuggestionService;

    /**
     * 建立目標價使用案例的 controller。
     *
     * @param applicationService 目標價應用服務
     * @param aiSuggestionService AI 建議服務
     */
    public TargetPriceController(
            TargetPriceApplicationService applicationService,
            AiSuggestionService aiSuggestionService) {
        this.applicationService = applicationService;
        this.aiSuggestionService = aiSuggestionService;
    }

    /**
     * 載入完整畫面資料，並執行不超額耗用額度的進場同步。
     *
     * @param stockId 台股代號
     * @return 完整畫面回應
     */
    @Operation(summary = "載入目標價畫面", description = "載入完整畫面資料，並執行不超額耗用額度的進場同步")
    @GetMapping
    public TargetPriceResponse get(@PathVariable String stockId) {
        return applicationService.get(stockId);
    }

    /**
     * 更新使用者可編輯設定，不重新抓取 FinMind 市場資料。
     *
     * @param stockId 台股代號
     * @param request 已驗證的設定內容
     * @return 重新計算後的畫面回應
     */
    @Operation(summary = "更新設定", description = "更新使用者可編輯設定，不重新抓取 FinMind 市場資料")
    @PutMapping("/settings")
    public TargetPriceResponse updateSettings(
            @PathVariable String stockId,
            @Valid @RequestBody TargetPriceSettingsRequest request) {
        return applicationService.updateSettings(stockId, request);
    }

    /**
     * 執行使用者主動觸發的市場資料更新，並回傳重新計算後的結果。
     *
     * @param stockId 台股代號
     * @return 重新計算後的畫面回應
     */
    @Operation(summary = "手動更新市場資料", description = "執行使用者主動觸發的市場資料更新，並回傳重新計算後的結果")
    @PostMapping("/refresh")
    public TargetPriceResponse refresh(@PathVariable String stockId) {
        return applicationService.refresh(stockId);
    }

    /**
     * 只有使用者明確操作時才產生並快取新的 AI 建議。
     *
     * @param stockId 台股代號
     * @return 新產生並已快取的 AI 建議
     */
    @Operation(summary = "產生 AI 投資建議", description = "只有使用者明確操作時才產生並快取新的 AI 建議")
    @PostMapping("/ai-suggestion")
    public TargetPriceResponse.AiSuggestionView generateAiSuggestion(@PathVariable String stockId) {
        return aiSuggestionService.generate(stockId);
    }
}
