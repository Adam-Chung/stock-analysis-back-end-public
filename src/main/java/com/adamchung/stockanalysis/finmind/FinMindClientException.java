package com.adamchung.stockanalysis.finmind;

/** 當 FinMind 回傳非 200 狀態碼，或回應內容意外為空時拋出。 */
public class FinMindClientException extends RuntimeException {

    public FinMindClientException(String message) {
        super(message);
    }

    public FinMindClientException(String message, Throwable cause) {
        super(message, cause);
    }
}
