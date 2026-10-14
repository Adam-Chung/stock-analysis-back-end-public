package com.adamchung.stockanalysis.valuation;

/** 當年度資料不足以計算 SA-01 目標價欄位時拋出。 */
public class InsufficientValuationDataException extends RuntimeException {

    public InsufficientValuationDataException(String message) {
        super(message);
    }
}
