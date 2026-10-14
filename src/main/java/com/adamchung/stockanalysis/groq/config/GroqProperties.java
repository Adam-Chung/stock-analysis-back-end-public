package com.adamchung.stockanalysis.groq.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Groq API 連線設定（對應 {@code groq.*} 設定值）。
 *
 * @param baseUrl Groq API 的 base URL
 * @param apiKey Groq API key
 * @param model 使用的模型名稱（例如 groq/compound-mini）
 */
@ConfigurationProperties(prefix = "groq")
public record GroqProperties(String baseUrl, String apiKey, String model) {
}
