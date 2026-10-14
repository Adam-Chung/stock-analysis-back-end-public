package com.adamchung.stockanalysis.healthcheck.enums;

/** 細項單季的達標狀態（SA-03 §4.2）。永遠即時計算，不落地存表（DEC-039）。 */
public enum HealthCheckStatus {
    PASS,
    FAIL,
    JUDGMENT,
    /** 該季尚未輸入數值。 */
    NA
}
