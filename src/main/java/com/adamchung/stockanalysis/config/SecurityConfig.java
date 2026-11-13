package com.adamchung.stockanalysis.config;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.web.context.SecurityContextHolderFilter;
import com.adamchung.stockanalysis.auth.AccountEnabledFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.session.web.http.DefaultCookieSerializer;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;
import java.time.Duration;

/** Google session、CSRF 與 API 存取規則。 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    /**
     * 只公開登入前必要端點；其他 API 必須使用資料庫 session 登入。
     *
     * @param http Spring Security 設定入口
     * @param objectMapper JSON 序列化器
     * @return 安全 filter chain
     * @throws Exception Spring Security 設定失敗時拋出
     */
    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            ObjectMapper objectMapper,
            AccountEnabledFilter accountEnabledFilter) throws Exception {
        CookieCsrfTokenRepository csrfRepository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        csrfRepository.setCookiePath("/");
        return http
                .cors(cors -> { })
                .csrf(csrf -> csrf
                        .csrfTokenRepository(csrfRepository)
                        .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler()))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(
                                "/api/auth/config", "/api/auth/csrf", "/api/auth/google", "/api/health/liveness",
                                "/actuator/health", "/swagger-ui/**", "/v3/api-docs/**")
                        .permitAll()
                        .requestMatchers("/api/**").authenticated()
                        .anyRequest().permitAll())
                .addFilterAfter(accountEnabledFilter, SecurityContextHolderFilter.class)
                .exceptionHandling(errors -> errors.authenticationEntryPoint((request, response, exception) -> {
                    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                    objectMapper.writeValue(response.getOutputStream(), Map.of(
                            "code", "AUTHENTICATION_REQUIRED",
                            "message", "請先使用 Google 帳號登入"));
                }))
                .build();
    }

    /**
     * 設定 session cookie；正式環境必須把 AUTH_SECURE_COOKIE 設為 true。
     *
     * @param secureCookie 是否只允許 HTTPS 傳送
     * @return Spring Session cookie serializer
     */
    @Bean
    DefaultCookieSerializer cookieSerializer(
            @Value("${app.auth.secure-cookie:false}") boolean secureCookie) {
        DefaultCookieSerializer serializer = new DefaultCookieSerializer();
        serializer.setCookieName("STOCK_ANALYSIS_SESSION");
        serializer.setCookiePath("/");
        serializer.setUseHttpOnlyCookie(true);
        serializer.setUseSecureCookie(secureCookie);
        serializer.setSameSite("Lax");
        serializer.setCookieMaxAge((int) Duration.ofDays(30).toSeconds());
        return serializer;
    }

    /**
     * AccountEnabledFilter 只由 Spring Security chain 執行，避免被 Servlet 容器提早執行後跳過安全上下文。
     *
     * @param filter 帳號狀態 filter
     * @return 停用容器自動註冊的設定
     */
    @Bean
    FilterRegistrationBean<AccountEnabledFilter> disableContainerRegistration(AccountEnabledFilter filter) {
        FilterRegistrationBean<AccountEnabledFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }
}
