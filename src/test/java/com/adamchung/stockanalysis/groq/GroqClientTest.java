package com.adamchung.stockanalysis.groq;

import com.adamchung.stockanalysis.groq.config.GroqProperties;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

class GroqClientTest {

    private static final String BASE_URL = "https://api.groq.com/openai/v1";

    @Test
    void generate_uses_primary_model_with_browser_search_and_extracts_text() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        GroqClient client = new GroqClient(builder.build(), properties("secret-key"));
        String response = """
                {
                  "model": "openai/gpt-oss-20b",
                  "choices": [{
                    "message": {
                      "role": "assistant",
                      "content": "近期需求穩健，仍須留意景氣風險。僅供研究參考，非投資建議"
                    },
                    "finish_reason": "stop"
                  }]
                }
                """;

        server.expect(requestTo(BASE_URL + "/chat/completions"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer secret-key"))
                .andExpect(jsonPath("$.model").value("openai/gpt-oss-20b"))
                .andExpect(jsonPath("$.messages[1].content").value("test prompt"))
                .andExpect(jsonPath("$.tools[0].type").value("browser_search"))
                .andExpect(jsonPath("$.tool_choice").value("required"))
                .andExpect(jsonPath("$.reasoning_effort").value("low"))
                .andExpect(jsonPath("$.max_completion_tokens").value(1024))
                .andRespond(withSuccess(response, MediaType.APPLICATION_JSON));

        GroqClient.GeneratedSuggestion result = client.generate("test prompt");

        assertThat(result.text()).contains("景氣風險");
        assertThat(result.modelName()).isEqualTo("openai/gpt-oss-20b");
        server.verify();
    }

    /** 主要模型下架時切換到備援模型，後續 request 直接沿用已成功的模型。 */
    @Test
    void generate_falls_back_on_model_not_found_and_remembers_working_model() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        GroqClient client = new GroqClient(builder.build(), properties("secret-key"));
        String fallbackResponse = """
                {
                  "model": "openai/gpt-oss-120b",
                  "choices": [{
                    "message": {"role": "assistant", "content": "備援模型建議"},
                    "finish_reason": "stop"
                  }]
                }
                """;

        server.expect(requestTo(BASE_URL + "/chat/completions"))
                .andExpect(jsonPath("$.model").value("openai/gpt-oss-20b"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"error\":{\"code\":\"model_not_found\"}}"));
        server.expect(requestTo(BASE_URL + "/chat/completions"))
                .andExpect(jsonPath("$.model").value("openai/gpt-oss-120b"))
                .andRespond(withSuccess(fallbackResponse, MediaType.APPLICATION_JSON));
        server.expect(requestTo(BASE_URL + "/chat/completions"))
                .andExpect(jsonPath("$.model").value("openai/gpt-oss-120b"))
                .andRespond(withSuccess(fallbackResponse, MediaType.APPLICATION_JSON));

        GroqClient.GeneratedSuggestion first = client.generate("first prompt");
        GroqClient.GeneratedSuggestion second = client.generate("second prompt");

        assertThat(first.modelName()).isEqualTo("openai/gpt-oss-120b");
        assertThat(second.modelName()).isEqualTo("openai/gpt-oss-120b");
        server.verify();
    }

    /** 免費額度用完時保留 429，不切換模型重複消耗其他候選模型額度。 */
    @Test
    void generate_does_not_fall_back_on_rate_limit() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        GroqClient client = new GroqClient(builder.build(), properties("secret-key"));

        server.expect(requestTo(BASE_URL + "/chat/completions"))
                .andExpect(jsonPath("$.model").value("openai/gpt-oss-20b"))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"error\":{\"code\":\"rate_limit_exceeded\"}}"));

        assertThatThrownBy(() -> client.generate("test prompt"))
                .isInstanceOf(GroqClientException.class)
                .hasMessageContaining("HTTP 429");
        server.verify();
    }

    /** 全部候選模型都失效時回傳清楚錯誤，不無限重試或改選未知模型。 */
    @Test
    void generate_reports_when_all_configured_models_are_unavailable() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        GroqClient client = new GroqClient(builder.build(), properties("secret-key"));
        String errorBody = "{\"error\":{\"code\":\"model_not_found\"}}";

        server.expect(requestTo(BASE_URL + "/chat/completions"))
                .andExpect(jsonPath("$.model").value("openai/gpt-oss-20b"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(errorBody));
        server.expect(requestTo(BASE_URL + "/chat/completions"))
                .andExpect(jsonPath("$.model").value("openai/gpt-oss-120b"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(errorBody));

        assertThatThrownBy(() -> client.generate("test prompt"))
                .isInstanceOf(GroqClientException.class)
                .hasMessageContaining("No configured Groq model is available")
                .hasMessageContaining("openai/gpt-oss-20b")
                .hasMessageContaining("openai/gpt-oss-120b");
        server.verify();
    }

    @Test
    void generate_rejects_missing_api_key_before_http_call() {
        GroqClient client = new GroqClient(
                mock(RestClient.class),
                properties(""));

        assertThatThrownBy(() -> client.generate("test prompt"))
                .isInstanceOf(GroqClientException.class)
                .hasMessageContaining("GROQ_API_KEY");
    }

    private GroqProperties properties(String apiKey) {
        return new GroqProperties(
                BASE_URL,
                apiKey,
                List.of("openai/gpt-oss-20b", "openai/gpt-oss-120b"));
    }
}
