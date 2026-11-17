package com.adamchung.stockanalysis.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

/** 驗證正式 application 設定使用 Spring Boot 4.1 支援的 session timeout 屬性。 */
class SessionConfigurationTest {

    /** 後端 session 閒置期限必須綁定為 30 天，不可落回預設 30 分鐘。 */
    @Test
    void configuresThirtyDayServletSessionTimeout() throws IOException {
        PropertySource<?> properties = new YamlPropertySourceLoader()
                .load("application", new ClassPathResource("application.yml"))
                .getFirst();

        assertThat(properties.getProperty("server.servlet.session.timeout")).isEqualTo("30d");
        assertThat(properties.getProperty("spring.session.timeout")).isNull();
    }
}
