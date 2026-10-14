package com.adamchung.stockanalysis.groq;

import com.adamchung.stockanalysis.groq.config.GroqProperties;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.util.List;

/** Groq Compound Mini chat completions API 的 HTTP 邊界。 */
@Service
public class GroqClient {

    private final RestClient restClient;
    private final GroqProperties properties;

    public GroqClient(
            @Qualifier("groqRestClient") RestClient restClient,
            GroqProperties properties) {
        this.restClient = restClient;
        this.properties = properties;
    }

    /**
     * 讓 Compound Mini 視需要使用內建 Web Search，產生一段研究摘要。
     *
     * @param prompt 由本地估值資料組成的可信 prompt
     * @return Groq 產生的文字及實際 model 名稱
     */
    public GeneratedSuggestion generate(String prompt) {
        if (!StringUtils.hasText(properties.apiKey())) {
            throw new GroqClientException("GROQ_API_KEY is not configured");
        }
        if (!StringUtils.hasText(properties.model())) {
            throw new GroqClientException("Groq model is not configured");
        }

        ChatCompletionRequest request = new ChatCompletionRequest(
                properties.model(),
                List.of(
                        new Message(
                                "system",
                                "你是台灣股票研究助理。必須遵守使用者提供的資料邊界與輸出格式。"),
                        new Message("user", prompt)));

        try {
            ChatCompletionResponse response = restClient.post()
                    .uri("/chat/completions")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + properties.apiKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(ChatCompletionResponse.class);
            return extract(response);
        } catch (RestClientResponseException exception) {
            throw new GroqClientException(
                    "Groq request failed with HTTP %d: %s".formatted(
                            exception.getStatusCode().value(),
                            summarizeError(exception.getResponseBodyAsString())),
                    exception);
        } catch (RestClientException exception) {
            throw new GroqClientException("Groq request failed", exception);
        }
    }

    private GeneratedSuggestion extract(ChatCompletionResponse response) {
        if (response == null || response.choices() == null || response.choices().isEmpty()) {
            throw new GroqClientException("Groq returned no choice");
        }
        Message message = response.choices().getFirst().message();
        if (message == null || !StringUtils.hasText(message.content())) {
            throw new GroqClientException("Groq returned empty text");
        }
        String modelName = StringUtils.hasText(response.model())
                ? response.model()
                : properties.model();
        return new GeneratedSuggestion(message.content(), modelName);
    }

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
            List<Message> messages // 對話訊息清單
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
