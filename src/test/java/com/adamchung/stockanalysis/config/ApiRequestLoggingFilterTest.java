package com.adamchung.stockanalysis.config;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class ApiRequestLoggingFilterTest {

    private final ApiRequestLoggingFilter filter = new ApiRequestLoggingFilter();

    /** 驗證安全的前端 correlation ID 會原樣回傳，方便對照前後端 log。 */
    @Test
    void preservesSafeCorrelationId() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/stocks/search");
        request.addHeader(ApiRequestLoggingFilter.CORRELATION_HEADER, "frontend-request-123");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertThat(response.getHeader(ApiRequestLoggingFilter.CORRELATION_HEADER))
                .isEqualTo("frontend-request-123");
        verify(chain).doFilter(request, response);
    }

    /** 驗證不安全的 correlation ID 不會被寫入 response 或 log context。 */
    @Test
    void replacesUnsafeCorrelationId() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/stocks/search");
        request.addHeader(ApiRequestLoggingFilter.CORRELATION_HEADER, "invalid value with spaces");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, mock(FilterChain.class));

        assertThat(response.getHeader(ApiRequestLoggingFilter.CORRELATION_HEADER))
                .matches("[0-9a-f-]{36}");
    }
}
