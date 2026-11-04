package com.adamchung.stockanalysis.groq;

import com.adamchung.stockanalysis.groq.config.GroqProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/** Groq chat completions API 的 HTTP 邊界，支援 Web Search 與候選模型 fallback。 */
@Service
public class GroqClient {

    private static final Logger log = LoggerFactory.getLogger(GroqClient.class);
    private static final String MODEL_NOT_FOUND = "model_not_found";
    private static final int MAX_COMPLETION_TOKENS = 1024;

    private final RestClient restClient;
    private final GroqProperties properties;
    private final AtomicReference<String> preferredModel = new AtomicReference<>();

    public GroqClient(
            @Qualifier("groqRestClient") RestClient restClient,
            GroqProperties properties) {
        this.restClient = restClient;
        this.properties = properties;
    }

    /**
     * 依候選順序呼叫 Groq，強制使用內建 Browser Search；只有 model_not_found 才切換模型。
     *
     * @param prompt 由本地估值資料組成的可信 prompt
     * @return Groq 產生的文字及實際 model 名稱
     * @throws GroqClientException API key、候選模型或 Groq request 無法使用時
     */
    public GeneratedSuggestion generate(String prompt) {
        if (!StringUtils.hasText(properties.apiKey())) {
            throw new GroqClientException("GROQ_API_KEY is not configured");
        }
        List<String> models = orderedModels();
        RestClientResponseException lastModelNotFound = null;

        for (String model : models) {
            try {
                GeneratedSuggestion suggestion = generateWithModel(prompt, model);
                preferredModel.set(model);
                return suggestion;
            } catch (RestClientResponseException exception) {
                if (!isModelNotFound(exception)) {
                    throw responseException(exception);
                }
                preferredModel.compareAndSet(model, null);
                lastModelNotFound = exception;
                log.warn("Groq model is unavailable; trying the next configured model: {}", model);
            } catch (RestClientException exception) {
                throw new GroqClientException("Groq request failed", exception);
            }
        }

        throw new GroqClientException(
                "No configured Groq model is available: " + String.join(", ", models),
                lastModelNotFound);
    }

    /** 將上次成功模型排在最前面，避免每次 request 都重試已失效的主要模型。 */
    private List<String> orderedModels() {
        List<String> configuredModels = properties.models() == null
                ? List.of()
                : properties.models().stream()
                        .filter(StringUtils::hasText)
                        .distinct()
                        .toList();
        if (configuredModels.isEmpty()) {
            throw new GroqClientException("Groq models are not configured");
        }

        String preferred = preferredModel.get();
        if (!StringUtils.hasText(preferred) || !configuredModels.contains(preferred)) {
            return configuredModels;
        }

        List<String> ordered = new ArrayList<>(configuredModels.size());
        ordered.add(preferred);
        configuredModels.stream()
                .filter(model -> !model.equals(preferred))
                .forEach(ordered::add);
        return List.copyOf(ordered);
    }

    /** 使用指定模型送出一次強制 Browser Search 的 chat completion request。 */
    private GeneratedSuggestion generateWithModel(String prompt, String model) {
        ChatCompletionRequest request = new ChatCompletionRequest(
                model,
                List.of(
                        new Message(
                                "system",
                                "你是台灣股票研究助理。必須遵守使用者提供的資料邊界與輸出格式。"),
                        new Message("user", prompt)),
                List.of(new Tool("browser_search")),
                "required",
                "low",
                MAX_COMPLETION_TOKENS);

        ChatCompletionResponse response = restClient.post()
                .uri("/chat/completions")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + properties.apiKey())
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(ChatCompletionResponse.class);
        return extract(response, model);
    }

    /** 判斷 Groq 是否明確回報候選模型不存在或帳戶無權使用。 */
    private boolean isModelNotFound(RestClientResponseException exception) {
        return exception.getStatusCode().value() == 404
                && exception.getResponseBodyAsString().contains(MODEL_NOT_FOUND);
    }

    /** 將非 fallback 類型的 HTTP error 保留 status 與精簡 response body 後轉成 domain exception。 */
    private GroqClientException responseException(RestClientResponseException exception) {
        return new GroqClientException(
                "Groq request failed with HTTP %d: %s".formatted(
                        exception.getStatusCode().value(),
                        summarizeError(exception.getResponseBodyAsString())),
                exception);
    }

    /** 驗證 response 並取出建議文字及實際使用的模型名稱。 */
    private GeneratedSuggestion extract(ChatCompletionResponse response, String requestedModel) {
        if (response == null || response.choices() == null || response.choices().isEmpty()) {
            throw new GroqClientException("Groq returned no choice");
        }
        Message message = response.choices().getFirst().message();
        if (message == null || !StringUtils.hasText(message.content())) {
            throw new GroqClientException("Groq returned empty text");
        }
        String modelName = StringUtils.hasText(response.model())
                ? response.model()
                : requestedModel;
        return new GeneratedSuggestion(message.content(), modelName);
    }

    /** 將 Groq error body 壓成單行且限制長度，避免 API response 洩漏過多外部內容。 */
    private String summarizeError(String responseBody) {
        if (!StringUtils.hasText(responseBody)) {
            return "empty error response";
        }
        String normalized = responseBody.replaceAll("\\s+", " ").strip();
        return normalized.substring(0, Math.min(normalized.length(), 500));
    }

    public record GeneratedSuggestion(
            String text, // AI產生的文字
            String modelName // 實際使用的模型名稱
    ) {
    }

    record ChatCompletionRequest(
            String model, // 模型名稱
            List<Message> messages, // 對話訊息清單
            List<Tool> tools, // Groq 內建工具清單
            @JsonProperty("tool_choice") String toolChoice, // 工具使用策略
            @JsonProperty("reasoning_effort") String reasoningEffort, // 推理強度
            @JsonProperty("max_completion_tokens") int maxCompletionTokens // 最大輸出token數
    ) {
    }

    record Tool(
            String type // Groq 內建工具類型
    ) {
    }

    record Message(
            String role, // 角色（system/user）
            String content // 訊息內容
    ) {
    }

    record ChatCompletionResponse(
            String model, // 實際使用的模型
            List<Choice> choices // 回應候選清單
    ) {
    }

    record Choice(
            Message message, // 回覆訊息
            String finishReason // 結束原因
    ) {
    }
}
