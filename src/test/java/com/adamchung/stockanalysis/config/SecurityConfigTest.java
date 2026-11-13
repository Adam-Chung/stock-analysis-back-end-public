package com.adamchung.stockanalysis.config;

import com.adamchung.stockanalysis.auth.AccountEnabledFilter;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration;
import org.springframework.boot.security.autoconfigure.web.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/** 驗證 Boot 4.1 的 Jackson 3 與自訂 Spring Security 設定可共同啟動。 */
class SecurityConfigTest {

    private final WebApplicationContextRunner contextRunner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    JacksonAutoConfiguration.class,
                    SecurityAutoConfiguration.class,
                    ServletWebSecurityAutoConfiguration.class,
                    SecurityFilterAutoConfiguration.class))
            .withUserConfiguration(SecurityConfig.class, TestBeans.class);

    /** 確認 Boot 提供的 Jackson 3 ObjectMapper 能建立完整安全 filter chain。 */
    @Test
    void createsSecurityFilterChainWithBootObjectMapper() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(ObjectMapper.class);
            assertThat(context).hasSingleBean(SecurityFilterChain.class);
        });
    }

    /** 提供測試安全設定所需、但不會真的存取資料庫的 filter。 */
    @TestConfiguration(proxyBeanMethods = false)
    static class TestBeans {

        /** 建立不會實際送出 SQL 的帳號啟用狀態 filter。 */
        @Bean
        AccountEnabledFilter accountEnabledFilter() {
            return new AccountEnabledFilter(mock(JdbcClient.class));
        }

        /** 建立測試 filter chain 所需的最小 CORS source；正式環境仍由 MVC 設定提供。 */
        @Bean
        CorsConfigurationSource corsConfigurationSource() {
            return request -> new CorsConfiguration();
        }
    }
}
