package com.adamchung.stockanalysis.auth;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SimpleAuthFilterTest {

    /** 有效 Token 可以繼續進入 API filter chain。 */
    @Test
    void allowsValidBearerToken() throws Exception {
        SimpleAuthService service = mock(SimpleAuthService.class);
        FilterChain chain = mock(FilterChain.class);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/stocks/search");
        request.addHeader("Authorization", "Bearer valid-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        when(service.isValid("valid-token")).thenReturn(true);

        new SimpleAuthFilter(service).doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
    }

    /** 缺少 Token 時拒絕呼叫受保護 API。 */
    @Test
    void rejectsMissingToken() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/stocks/search");
        MockHttpServletResponse response = new MockHttpServletResponse();

        new SimpleAuthFilter(mock(SimpleAuthService.class))
                .doFilter(request, response, mock(FilterChain.class));

        assertThat(response.getStatus()).isEqualTo(401);
    }
}
