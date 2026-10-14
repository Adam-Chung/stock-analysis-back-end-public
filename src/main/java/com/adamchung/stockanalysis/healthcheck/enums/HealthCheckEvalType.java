package com.adamchung.stockanalysis.healthcheck.enums;

/** 細項的達標判定方式（SA-04）。 */
public enum HealthCheckEvalType {
    /** 數值非零視為達標（例如「10年來是否年年賺錢」以 1/0 存）。 */
    BOOLEAN,
    /** 數值 &gt; 門檻視為達標。 */
    GREATER_THAN,
    /** 數值 &lt; 門檻視為達標。 */
    LESS_THAN,
    /** 沒有明確二元門檻，僅供趨勢參考，不計入達標統計（SA-03 §4.2）。 */
    JUDGMENT
}
