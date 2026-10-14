package com.adamchung.stockanalysis.externaldata;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class ExternalDataHttpClientTest {

    /** 驗證 Public client 直接呼叫指定來源，不做 URL 代理重寫。 */
    @Test
    void callsTheRequestedSourceDirectly() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        ExternalDataHttpClient client = new ExternalDataHttpClient(builder);
        String url = "https://data.example.test/health";

        server.expect(once(), requestTo(url)).andRespond(withSuccess("ok", null));

        client.getText(url);
        server.verify();
    }
}
