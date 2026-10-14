package com.adamchung.stockanalysis.finmind.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * FinMind API 連線設定。
 *
 * @param baseUrl 官方 API base URL
 * @param token 選填 token；匿名額度不足且來源條款允許時才使用
 */
@ConfigurationProperties(prefix = "finmind")
public record FinMindProperties(String baseUrl, String token) {
}
