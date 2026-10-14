package com.adamchung.stockanalysis.groq.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/** 設定 Groq Compound Mini 專用 HTTP client。 */
@Configuration
@EnableConfigurationProperties(GroqProperties.class)
public class GroqConfig {

    /**
     * 建立指向 Groq base URL 的 {@link RestClient}。
     *
     * @param builder Spring Boot 自動組裝的 {@link RestClient.Builder}
     * @param properties Groq 連線設定
     * @return 設定好 base URL 的 {@link RestClient}
     */
    @Bean
    RestClient groqRestClient(RestClient.Builder builder, GroqProperties properties) {
        return builder.baseUrl(properties.baseUrl()).build();
    }
}
