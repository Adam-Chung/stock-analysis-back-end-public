package com.adamchung.stockanalysis.healthcheck;

import com.adamchung.stockanalysis.groq.GroqClientException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;

/**
 * 企業體質檢視 AI 建議相關請求的穩定 API 錯誤映射（比照
 * {@code targetprice.TargetPriceExceptionHandler} 對應 Groq 失敗的那一段）。
 * {@link HealthCheckController} 既有的 404／400 inline handler 只處理自己丟出的例外類型，
 * 不受這個 advice 影響，兩者可以並存。
 */
@RestControllerAdvice(assignableTypes = HealthCheckController.class)
public class HealthCheckExceptionHandler {

    /**
     * 將 Groq 無法使用或格式不合的回覆映射為上游服務錯誤。
     *
     * @param exception Groq client 例外
     * @return 502 錯誤回應
     */
    @ExceptionHandler(GroqClientException.class)
    public ResponseEntity<ApiError> handleGroqFailure(GroqClientException exception) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(new ApiError("AI_SUGGESTION_UNAVAILABLE", exception.getMessage(), Instant.now()));
    }

    /** 機器可讀的錯誤回應。 */
    public record ApiError(
            String code, // 機器可讀錯誤代碼
            String message, // 錯誤訊息文字
            Instant timestamp // 發生時間
    ) {
    }
}
