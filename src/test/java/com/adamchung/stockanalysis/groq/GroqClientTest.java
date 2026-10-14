package com.adamchung.stockanalysis.groq;

import com.adamchung.stockanalysis.groq.config.GroqProperties;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class GroqClientTest {

    private static final String BASE_URL = "https://api.groq.com/openai/v1";

    @Test
    void generate_uses_compound_mini_chat_completions_and_extracts_text() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        GroqClient client = new GroqClient(builder.build(), properties("secret-key"));
        String response = """
                {
                  "model": "groq/compound-mini",
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
                .andExpect(jsonPath("$.model").value("groq/compound-mini"))
                .andExpect(jsonPath("$.messages[1].content").value("test prompt"))
                .andRespond(withSuccess(response, MediaType.APPLICATION_JSON));

        GroqClient.GeneratedSuggestion result = client.generate("test prompt");

        assertThat(result.text()).contains("景氣風險");
        assertThat(result.modelName()).isEqualTo("groq/compound-mini");
        server.verify();
    }

    @Test
    void generate_rejects_missing_api_key_before_http_call() {
        GroqClient client = new GroqClient(
                RestClient.builder().baseUrl(BASE_URL).build(),
                properties(""));

        assertThatThrownBy(() -> client.generate("test prompt"))
                .isInstanceOf(GroqClientException.class)
                .hasMessageContaining("GROQ_API_KEY");
    }

    private GroqProperties properties(String apiKey) {
        return new GroqProperties(BASE_URL, apiKey, "groq/compound-mini");
    }
}
