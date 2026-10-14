package com.adamchung.stockanalysis.externaldata.dto;

import java.time.LocalDate;

/**
 * 一個曆年季度的起訖日與標籤（例如 {@code 2026Q2}）。
 *
 * @param start 該季第一天
 * @param end   該季最後一天
 * @param label {@code YYYYQ#} 格式標籤，與 {@code stock_health_snapshot.quarter} 格式一致
 */
public record QuarterRange(LocalDate start, LocalDate end, String label) {
}
