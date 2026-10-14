package com.adamchung.stockanalysis.finmind;

import com.adamchung.stockanalysis.finmind.config.FinMindProperties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.headerDoesNotExist;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

class FinMindClientFallbackTest {

    private static final String BASE_URL = "https://api.finmindtrade.com/api/v4";
    private static final String STOCK_INFO_URL =
            BASE_URL + "/data?dataset=TaiwanStockInfo&data_id=2330";
    private static final String EMPTY_SUCCESS = """
            {"msg":"success","status":200,"data":[]}
            """;

    /** Verifies that a configured token is not sent while anonymous quota remains available. */
    @Test
    void usesAnonymousRequestFirstEvenWhenTokenIsConfigured() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        FinMindClient client = new FinMindClient(builder.build(), properties("secret-token"));

        server.expect(once(), requestTo(STOCK_INFO_URL))
                .andExpect(method(HttpMethod.GET))
                .andExpect(headerDoesNotExist("Authorization"))
                .andRespond(withSuccess(EMPTY_SUCCESS, MediaType.APPLICATION_JSON));

        assertThat(client.fetchStockInfo("2330")).isEmpty();
        server.verify();
    }

    /** 驗證每次呼叫都先匿名，只有該次匿名額度不足時才使用 token 重試。 */
    @Test
    void retriesWithTokenPerRequestAfterAnonymousQuotaIsExhausted() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        FinMindClient client = new FinMindClient(builder.build(), properties("secret-token"));

        server.expect(once(), requestTo(STOCK_INFO_URL))
                .andExpect(headerDoesNotExist("Authorization"))
                .andRespond(withStatus(HttpStatus.PAYMENT_REQUIRED));
        server.expect(once(), requestTo(STOCK_INFO_URL))
                .andExpect(header("Authorization", "Bearer secret-token"))
                .andRespond(withSuccess(EMPTY_SUCCESS, MediaType.APPLICATION_JSON));
        server.expect(once(), requestTo(STOCK_INFO_URL))
                .andExpect(headerDoesNotExist("Authorization"))
                .andRespond(withSuccess(EMPTY_SUCCESS, MediaType.APPLICATION_JSON));

        assertThat(client.fetchStockInfo("2330")).isEmpty();
        assertThat(client.fetchStockInfo("2330")).isEmpty();
        server.verify();
    }

    /** Verifies that errors other than quota exhaustion never expose the token through a retry. */
    @Test
    void doesNotRetryWithTokenForOtherErrors() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        FinMindClient client = new FinMindClient(builder.build(), properties("secret-token"));

        server.expect(once(), requestTo(STOCK_INFO_URL))
                .andExpect(headerDoesNotExist("Authorization"))
                .andRespond(withStatus(HttpStatus.FORBIDDEN));

        assertThatThrownBy(() -> client.fetchStockInfo("2330"))
                .isInstanceOf(RestClientResponseException.class)
                .satisfies(exception -> assertThat(
                                ((RestClientResponseException) exception).getStatusCode())
                        .isEqualTo(HttpStatus.FORBIDDEN));
        server.verify();
    }

    /** Verifies that anonymous quota exhaustion is propagated when no fallback token exists. */
    @Test
    void propagatesQuotaErrorWhenTokenIsBlank() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        FinMindClient client = new FinMindClient(builder.build(), properties(""));

        server.expect(once(), requestTo(STOCK_INFO_URL))
                .andExpect(headerDoesNotExist("Authorization"))
                .andRespond(withStatus(HttpStatus.PAYMENT_REQUIRED));

        assertThatThrownBy(() -> client.fetchStockInfo("2330"))
                .isInstanceOf(RestClientResponseException.class);
        server.verify();
    }

    /** Creates the minimal FinMind configuration used by a test client. */
    private FinMindProperties properties(String token) {
        return new FinMindProperties(BASE_URL, token);
    }
}
