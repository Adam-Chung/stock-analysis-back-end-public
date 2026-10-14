package com.adamchung.stockanalysis.targetprice;

import com.adamchung.stockanalysis.finmind.FinMindClientException;
import com.adamchung.stockanalysis.groq.GroqClientException;
import com.adamchung.stockanalysis.valuation.InsufficientValuationDataException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.RestClientResponseException;

import java.time.Instant;
import java.util.NoSuchElementException;

/** 目標價相關請求的穩定 API 錯誤映射。 */
@RestControllerAdvice(assignableTypes = TargetPriceController.class)
public class TargetPriceExceptionHandler {

    /**
     * 將本地找不到的股票映射為 HTTP 404。
     *
     * @param exception 找不到股票的例外
     * @return 404 錯誤回應
     */
    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<ApiError> handleNotFound(NoSuchElementException exception) {
        return error(HttpStatus.NOT_FOUND, "STOCK_NOT_FOUND", exception.getMessage());
    }

    /**
     * 將無效設定映射為 HTTP 400。
     *
     * @param exception 驗證失敗的例外
     * @return 400 錯誤回應
     */
    @ExceptionHandler({IllegalArgumentException.class, MethodArgumentNotValidException.class})
    public ResponseEntity<ApiError> handleBadRequest(Exception exception) {
        return error(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", exception.getMessage());
    }

    /**
     * 將財務資料不足（但請求本身合法）映射為 HTTP 422。
     *
     * @param exception 資料不足的例外
     * @return 422 錯誤回應
     */
    @ExceptionHandler(InsufficientValuationDataException.class)
    public ResponseEntity<ApiError> handleInsufficientData(InsufficientValuationDataException exception) {
        return error(HttpStatus.UNPROCESSABLE_CONTENT, "INSUFFICIENT_VALUATION_DATA", exception.getMessage());
    }

    /**
     * 將我方 FinMind 包裝層的失敗（空回應／非200）映射為 HTTP 502。
     *
     * @param exception FinMind client 例外
     * @return 502 錯誤回應
     */
    @ExceptionHandler(FinMindClientException.class)
    public ResponseEntity<ApiError> handleFinMindFailure(FinMindClientException exception) {
        return error(HttpStatus.BAD_GATEWAY, "FINMIND_UNAVAILABLE", exception.getMessage());
    }

    /**
     * 將 FinMind 原始 HTTP 錯誤（由 RestClient 在我方程式碼執行前拋出）映射為對客戶端友善的
     * 錯誤，而非模糊不清的 500。402 特別代表匿名額度與已設定 token 的額度都已用盡
     * （見 DECISIONS.md DEC-020）——對外呈現為 503，讓呼叫端／監控可以區分「FinMind 額度用完」
     * 與「FinMind 本身故障」（502）。
     *
     * @param exception FinMind 回傳的原始 HTTP 錯誤
     * @return 502 或 503 錯誤回應
     */
    @ExceptionHandler(RestClientResponseException.class)
    public ResponseEntity<ApiError> handleFinMindHttpFailure(RestClientResponseException exception) {
        boolean quotaExceeded = exception.getStatusCode().value() == HttpStatus.PAYMENT_REQUIRED.value();
        HttpStatus status = quotaExceeded ? HttpStatus.SERVICE_UNAVAILABLE : HttpStatus.BAD_GATEWAY;
        String code = quotaExceeded ? "FINMIND_QUOTA_EXCEEDED" : "FINMIND_UNAVAILABLE";
        return error(status, code, "FinMind API error: " + exception.getStatusCode());
    }

    /**
     * 將 Groq 無法使用或格式不合的回覆映射為上游服務錯誤。
     *
     * @param exception Groq client 例外
     * @return 502 錯誤回應
     */
    @ExceptionHandler(GroqClientException.class)
    public ResponseEntity<ApiError> handleGroqFailure(GroqClientException exception) {
        return error(HttpStatus.BAD_GATEWAY, "AI_SUGGESTION_UNAVAILABLE", exception.getMessage());
    }

    /**
     * 建立共用的錯誤回應內容。
     *
     * @param status HTTP 狀態碼
     * @param code 機器可讀的錯誤代碼
     * @param message 錯誤訊息
     * @return 組好的錯誤回應
     */
    private ResponseEntity<ApiError> error(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status).body(new ApiError(code, message, Instant.now()));
    }

    /** 機器可讀的錯誤回應。 */
    public record ApiError(
            String code, // 機器可讀錯誤代碼
            String message, // 錯誤訊息文字
            Instant timestamp // 發生時間
    ) {
    }
}
