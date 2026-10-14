package com.adamchung.stockanalysis.finmind.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.restclient.autoconfigure.RestClientAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;

class FinMindConfigTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(RestClientAutoConfiguration.class))
            .withUserConfiguration(FinMindConfig.class)
            .withPropertyValues("finmind.base-url=https://api.finmindtrade.com/api/v4");

    /** Verifies that the Boot 4 RestClient starter supplies the builder required by FinMindConfig. */
    @Test
    void creates_finMindRestClient_from_auto_configured_builder() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(RestClient.Builder.class);
            assertThat(context).hasBean("finMindRestClient");
            assertThat(context.getBean("finMindRestClient")).isInstanceOf(RestClient.class);
        });
    }
}
