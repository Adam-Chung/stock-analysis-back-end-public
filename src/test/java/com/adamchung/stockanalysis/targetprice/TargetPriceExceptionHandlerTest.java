package com.adamchung.stockanalysis.targetprice;

import com.adamchung.stockanalysis.finmind.FinMindClientException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;

import static org.assertj.core.api.Assertions.assertThat;

class TargetPriceExceptionHandlerTest {

    private final TargetPriceExceptionHandler handler = new TargetPriceExceptionHandler();

    @Test
    void maps_a_finmind_wrapper_failure_to_bad_gateway() {
        ResponseEntity<TargetPriceExceptionHandler.ApiError> response =
                handler.handleFinMindFailure(new FinMindClientException("empty response"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
        assertThat(response.getBody().code()).isEqualTo("FINMIND_UNAVAILABLE");
    }

    @Test
    void maps_a_raw_finmind_402_to_service_unavailable_with_a_quota_code() {
        // The 402 seen in production once both the anonymous and token quotas are exhausted
        // (DECISIONS.md DEC-020) - must be distinguishable from a generic FinMind outage.
        HttpClientErrorException exception = HttpClientErrorException.create(
                HttpStatus.PAYMENT_REQUIRED, "Payment Required", null, new byte[0], null);

        ResponseEntity<TargetPriceExceptionHandler.ApiError> response =
                handler.handleFinMindHttpFailure(exception);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody().code()).isEqualTo("FINMIND_QUOTA_EXCEEDED");
    }

    @Test
    void maps_any_other_finmind_http_error_to_bad_gateway() {
        HttpServerErrorException exception = HttpServerErrorException.create(
                HttpStatus.SERVICE_UNAVAILABLE, "Service Unavailable", null, new byte[0], null);

        ResponseEntity<TargetPriceExceptionHandler.ApiError> response =
                handler.handleFinMindHttpFailure(exception);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
        assertThat(response.getBody().code()).isEqualTo("FINMIND_UNAVAILABLE");
    }
}
