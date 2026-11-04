package com.adamchung.stockanalysis.groq.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * Groq API 連線設定（對應 {@code groq.*} 設定值）。
 *
 * @param baseUrl Groq API 的 base URL
 * @param apiKey Groq API key
 * @param models 依優先順序排列的候選模型名稱
 */
@ConfigurationProperties(prefix = "groq")
public record GroqProperties(String baseUrl, String apiKey, List<String> models) {
}
