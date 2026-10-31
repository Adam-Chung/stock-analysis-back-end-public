package com.adamchung.stockanalysis.health;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 提供不依賴外部服務的應用程式存活檢查。 */
@RestController
@RequestMapping("/api/health")
public class LivenessController {

    /**
     * 確認 Spring Boot 應用程式已啟動並可接收 HTTP 請求。
     *
     * <p>此端點不查詢資料庫，也不呼叫 FinMind、Groq 或任何同步流程。</p>
     */
    @GetMapping("/liveness")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void liveness() {
        // 只要請求能進入此方法，即代表應用程式仍可接收 HTTP 請求。
    }
}
