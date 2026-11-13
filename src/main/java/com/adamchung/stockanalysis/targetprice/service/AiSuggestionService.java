package com.adamchung.stockanalysis.targetprice.service;

import com.adamchung.stockanalysis.auth.CurrentUser;
import com.adamchung.stockanalysis.groq.GroqClient;
import com.adamchung.stockanalysis.groq.GroqClientException;
import com.adamchung.stockanalysis.targetprice.AiSuggestionPromptBuilder;
import com.adamchung.stockanalysis.targetprice.dto.TargetPriceResponse;
import com.adamchung.stockanalysis.userdata.UserDataStore;
import com.adamchung.stockanalysis.quota.DailyUsageService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 產生並快取每檔股票最新的 Groq AI 建議。prompt 的期望長度為 200 字；
 * 由於模型不保證嚴格遵守字數，後端另以 800 字作為防止異常大量輸出的安全上限。
 */
@Service
public class AiSuggestionService {

    static final int MAX_SUGGESTION_LENGTH = 800;
    private static final int MAX_MODEL_NAME_LENGTH = 50;

    private final TargetPriceApplicationService targetPriceService;
    private final AiSuggestionPromptBuilder promptBuilder;
    private final GroqClient groqClient;
    private final UserDataStore userDataStore;
    private final DailyUsageService dailyUsageService;

    public AiSuggestionService(
            TargetPriceApplicationService targetPriceService,
            AiSuggestionPromptBuilder promptBuilder,
            GroqClient groqClient,
            UserDataStore userDataStore,
            DailyUsageService dailyUsageService) {
        this.targetPriceService = targetPriceService;
        this.promptBuilder = promptBuilder;
        this.groqClient = groqClient;
        this.userDataStore = userDataStore;
        this.dailyUsageService = dailyUsageService;
    }

    /**
     * 根據已落地的畫面資料產生新建議，通過驗證後才覆蓋既有快取。
     *
     * @param stockId 台股代號
     * @return 新產生並已快取的建議
     */
    @Transactional(rollbackFor = Throwable.class)
    public TargetPriceResponse.AiSuggestionView generate(String stockId) {
        dailyUsageService.reserveAiSuggestion();
        TargetPriceResponse snapshot = targetPriceService.getPersisted(stockId);
        GroqClient.GeneratedSuggestion generated =
                groqClient.generate(promptBuilder.build(snapshot));
        String text = normalize(generated.text());
        validate(text, generated.modelName());

        UserDataStore.AiSuggestion saved = userDataStore.saveTargetAiSuggestion(
                CurrentUser.id(), stockId, text, generated.modelName());
        return new TargetPriceResponse.AiSuggestionView(saved.suggestionText(), saved.updatedAt());
    }

    /**
     * 把 Groq 回應整理成乾淨的段落文字。{@code text.strip().replaceAll("\\s+", " ")}
     * 這種單純壓平所有空白字元的寫法會把 prompt 要求的段落換行也一併壓成一個空格，
     * 讓「分成兩段」的要求形同虛設（前端 {@code .ai-text} 用 {@code white-space: pre-line}
     * 保留換行，但如果這裡先把換行壓掉，前端也沒有東西可以保留）。改成先依任意換行序列
     * 切段、段落內部才壓平空白，再用固定的 {@code \n\n} 重新組回——不論 Groq 實際用單一
     * 換行還是空行分隔段落，輸出格式都一致（逐字比照
     * {@code healthcheck.HealthCheckAiSuggestionService} 同一輪修正）。
     */
    private String normalize(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        return java.util.Arrays.stream(text.strip().split("\\R+"))
                .map(paragraph -> paragraph.strip().replaceAll("\\s+", " "))
                .filter(paragraph -> !paragraph.isBlank())
                .collect(java.util.stream.Collectors.joining("\n\n"));
    }

    private void validate(String text, String modelName) {
        int length = text.codePointCount(0, text.length());
        if (text.isBlank()) {
            throw new GroqClientException("Groq suggestion is empty");
        }
        if (length > MAX_SUGGESTION_LENGTH) {
            throw new GroqClientException(
                    "Groq suggestion exceeds %d characters".formatted(MAX_SUGGESTION_LENGTH));
        }
        if (modelName == null || modelName.isBlank() || modelName.length() > MAX_MODEL_NAME_LENGTH) {
            throw new GroqClientException("Groq returned an invalid model name");
        }
    }
}
