package com.adamchung.stockanalysis.externaldata.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** 啟用 {@link ExternalDataProperties}（{@code external-data.*} 設定值）。 */
@Configuration
@EnableConfigurationProperties(ExternalDataProperties.class)
class ExternalDataConfig {
}
