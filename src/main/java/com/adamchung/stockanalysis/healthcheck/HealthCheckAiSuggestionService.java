package com.adamchung.stockanalysis.healthcheck;

import com.adamchung.stockanalysis.common.AuditActors;
import com.adamchung.stockanalysis.groq.GroqClient;
import com.adamchung.stockanalysis.groq.GroqClientException;
import com.adamchung.stockanalysis.healthcheck.dto.HealthCheckResponse;
import com.adamchung.stockanalysis.healthcheck.entity.StockHealthAiSuggestionEntity;
import com.adamchung.stockanalysis.healthcheck.repository.StockHealthAiSuggestionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 產生並快取每檔股票最新的企業體質檢視 AI 投資建議（DEC-068 後續實作）。
 * 逐字比照 {@code targetprice.service.AiSuggestionService}，長度上限拉高到 400字，
 * 因為除了機會/風險摘要外還要求「不達標原因」＋「投資／財報體質」兩個明確結論。
 */
@Service
public class HealthCheckAiSuggestionService {

    static final int MAX_SUGGESTION_LENGTH = 400;
    private static final int MAX_MODEL_NAME_LENGTH = 50;

    private final HealthCheckApplicationService healthCheckService;
    private final HealthCheckAiSuggestionPromptBuilder promptBuilder;
    private final GroqClient groqClient;
    private final StockHealthAiSuggestionRepository repository;

    public HealthCheckAiSuggestionService(
            HealthCheckApplicationService healthCheckService,
            HealthCheckAiSuggestionPromptBuilder promptBuilder,
            GroqClient groqClient,
            StockHealthAiSuggestionRepository repository) {
        this.healthCheckService = healthCheckService;
        this.promptBuilder = promptBuilder;
        this.groqClient = groqClient;
        this.repository = repository;
    }

    /**
     * 根據已落地的畫面資料產生新建議，通過驗證後才覆蓋既有快取。
     *
     * @param stockId 台股代號
     * @return 新產生並已快取的建議
     */
    @Transactional
    public HealthCheckResponse.AiSuggestionView generate(String stockId) {
        HealthCheckResponse snapshot = healthCheckService.get(stockId);
        GroqClient.GeneratedSuggestion generated =
                groqClient.generate(promptBuilder.build(snapshot));
        String text = normalize(generated.text());
        validate(text, generated.modelName());

        StockHealthAiSuggestionEntity suggestion = repository.findById(stockId)
                .orElseGet(StockHealthAiSuggestionEntity::new);
        boolean isNew = suggestion.getStockId() == null;
        suggestion.setStockId(stockId);
        suggestion.setSuggestionText(text);
        suggestion.setModelName(generated.modelName());
        if (isNew) {
            suggestion.setCreatedBy(AuditActors.HEALTH_CHECK_AI_SUGGESTION);
        }
        suggestion.setUpdatedBy(AuditActors.HEALTH_CHECK_AI_SUGGESTION);
        StockHealthAiSuggestionEntity saved = repository.saveAndFlush(suggestion);
        return new HealthCheckResponse.AiSuggestionView(saved.getSuggestionText(), saved.getUpdatedAt());
    }

    /**
     * 把 Groq 回應整理成乾淨的段落文字。{@code text.strip().replaceAll("\\s+", " ")}
     * 這種單純壓平所有空白字元的寫法會把 prompt 要求的段落換行也一併壓成一個空格，
     * 讓「分成三段」的要求形同虛設（前端 {@code .ai-text} 用 {@code white-space: pre-line}
     * 保留換行，但如果這裡先把換行壓掉，前端也沒有東西可以保留）。改成先依任意換行序列
     * 切段、段落內部才壓平空白，再用固定的 {@code \n\n} 重新組回——不論 Groq 實際用單一
     * 換行還是空行分隔段落，輸出格式都一致。
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
