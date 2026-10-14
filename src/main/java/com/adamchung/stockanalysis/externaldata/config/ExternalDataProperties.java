package com.adamchung.stockanalysis.externaldata.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** 公開資料來源的保留設定節點；Public mirror 僅允許直接連線。 */
@ConfigurationProperties(prefix = "external-data")
public record ExternalDataProperties() {
}
