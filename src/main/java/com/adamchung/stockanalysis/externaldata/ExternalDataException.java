package com.adamchung.stockanalysis.externaldata;

/** 外部資料來源（TWSE OpenAPI／銀行局／金管會）呼叫或解析失敗時拋出。 */
public class ExternalDataException extends RuntimeException {

    public ExternalDataException(String message) {
        super(message);
    }

    public ExternalDataException(String message, Throwable cause) {
        super(message, cause);
    }
}
