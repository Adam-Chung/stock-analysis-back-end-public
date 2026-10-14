package com.adamchung.stockanalysis.finmind.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/** FinMind API 使用的 {@link RestClient} 設定。 */
@Configuration
@EnableConfigurationProperties(FinMindProperties.class)
public class FinMindConfig {

    /**
     * 建立指向 FinMind base URL 的 {@link RestClient}。
     *
     * @param builder Spring Boot 自動組裝的 {@link RestClient.Builder}
     * @param properties FinMind 連線設定
     * @return 設定好 base URL 的 {@link RestClient}
     */
    @Bean
    RestClient finMindRestClient(RestClient.Builder builder, FinMindProperties properties) {
        return builder.baseUrl(properties.baseUrl()).build();
    }
}
