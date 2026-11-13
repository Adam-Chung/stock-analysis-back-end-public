package com.adamchung.stockanalysis.quota;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** 將每日額度耗盡映射為穩定的 HTTP 429 回應。 */
@RestControllerAdvice
public class QuotaExceptionHandler {

    /**
     * 回傳 Too Many Requests，前端可直接顯示 detail。
     *
     * @param exception 額度耗盡錯誤
     * @return RFC 9457 Problem Details
     */
    @ExceptionHandler(DailyQuotaExceededException.class)
    ProblemDetail handleQuotaExceeded(DailyQuotaExceededException exception) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(HttpStatus.TOO_MANY_REQUESTS, exception.getMessage());
        detail.setTitle("今日免費額度已用完");
        detail.setProperty("code", "DAILY_QUOTA_EXCEEDED");
        return detail;
    }
}
