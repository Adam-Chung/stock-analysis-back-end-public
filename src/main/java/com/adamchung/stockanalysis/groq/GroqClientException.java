package com.adamchung.stockanalysis.groq;

/** 表示 Groq 無法產生可用的投資研究摘要。 */
public class GroqClientException extends RuntimeException {

    public GroqClientException(String message) {
        super(message);
    }

    public GroqClientException(String message, Throwable cause) {
        super(message, cause);
    }
}
