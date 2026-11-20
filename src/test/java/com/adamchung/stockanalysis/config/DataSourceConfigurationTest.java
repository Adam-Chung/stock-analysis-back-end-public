package com.adamchung.stockanalysis.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

/** 驗證正式資料庫連線池不會以閒置連線阻止 serverless PostgreSQL 休眠。 */
class DataSourceConfigurationTest {

    /** 閒置連線必須完全釋放，同時保留足以服務小型網站的有限連線數。 */
    @Test
    void releasesIdleConnectionsForServerlessDatabase() throws IOException {
        PropertySource<?> properties = new YamlPropertySourceLoader()
                .load("application", new ClassPathResource("application.yml"))
                .getFirst();

        assertThat(properties.getProperty("spring.datasource.hikari.minimum-idle")).isEqualTo(0);
        assertThat(properties.getProperty("spring.datasource.hikari.maximum-pool-size")).isEqualTo(3);
        assertThat(properties.getProperty("spring.datasource.hikari.idle-timeout")).isEqualTo(60_000);
        assertThat(properties.getProperty("spring.datasource.hikari.keepalive-time")).isEqualTo(0);
    }
}
