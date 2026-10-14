package com.adamchung.stockanalysis.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

/** 集中記錄 API request 的狀態與耗時，並以 correlation ID 串接前後端 log。 */
@Component
public class ApiRequestLoggingFilter extends OncePerRequestFilter {

    static final String CORRELATION_HEADER = "X-Correlation-ID";
    private static final String MDC_KEY = "correlationId";
    private static final Pattern SAFE_CORRELATION_ID = Pattern.compile("[A-Za-z0-9._-]{1,64}");
    private static final Logger LOGGER = LoggerFactory.getLogger(ApiRequestLoggingFilter.class);

    /**
     * 只處理 API request，避免靜態資源與健康檢查產生大量雜訊。
     *
     * @param request 目前的 HTTP request
     * @return 這個 request 是否應該略過此 filter
     */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/");
    }

    /**
     * 記錄 method、path、status、耗時與例外類型；不記錄 query、headers 或 body。
     *
     * @param request 目前的 HTTP request
     * @param response 目前的 HTTP response
     * @param filterChain 剩餘的 filter chain
     * @throws ServletException filter chain 執行失敗時拋出
     * @throws IOException filter chain 執行發生 I/O 錯誤時拋出
     */
    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        String correlationId = resolveCorrelationId(request.getHeader(CORRELATION_HEADER));
        long startedAt = System.nanoTime();
        response.setHeader(CORRELATION_HEADER, correlationId);
        MDC.put(MDC_KEY, correlationId);

        LOGGER.debug("API request started: method={}, path={}", request.getMethod(), request.getRequestURI());
        try {
            filterChain.doFilter(request, response);
            LOGGER.info(
                    "API request completed: method={}, path={}, status={}, durationMs={}",
                    request.getMethod(),
                    request.getRequestURI(),
                    response.getStatus(),
                    elapsedMillis(startedAt));
        } catch (RuntimeException | ServletException | IOException exception) {
            LOGGER.error(
                    "API request failed: method={}, path={}, status={}, durationMs={}, errorType={}",
                    request.getMethod(),
                    request.getRequestURI(),
                    response.getStatus(),
                    elapsedMillis(startedAt),
                    exception.getClass().getSimpleName(),
                    exception);
            throw exception;
        } finally {
            MDC.remove(MDC_KEY);
        }
    }

    /**
     * 接受格式安全的前端 correlation ID，否則由後端重新產生。
     *
     * @param candidate 前端傳入的 correlation ID
     * @return 可安全使用的 correlation ID
     */
    private String resolveCorrelationId(String candidate) {
        if (candidate != null && SAFE_CORRELATION_ID.matcher(candidate).matches()) {
            return candidate;
        }
        return UUID.randomUUID().toString();
    }

    /**
     * 將 monotonic elapsed time 轉成毫秒。
     *
     * @param startedAt {@link System#nanoTime()} 記錄的起始時間點
     * @return 經過的毫秒數
     */
    private long elapsedMillis(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000;
    }
}
