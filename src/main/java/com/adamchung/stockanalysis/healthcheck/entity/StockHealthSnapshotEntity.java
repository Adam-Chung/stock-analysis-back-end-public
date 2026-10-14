package com.adamchung.stockanalysis.healthcheck.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import com.adamchung.stockanalysis.stock.entity.Auditable;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * SA-03 企業體質檢視的檢視項目快照（DB-01 §10.2）。通用 EAV 結構：一列代表「某支股票、
 * 某一季（或月）、某個細項」的使用者輸入值＋筆記。細項本身的名稱/門檻/達標公式不落地存表，
 * 定義在 {@code com.adamchung.stockanalysis.healthcheck} 套件（DEC-039）。
 *
 * <p>{@code quarter} 一般情況存 {@code 'YYYYQ#'}（如 {@code '2024Q4'}）；銀行模板「月營收」
 * 相關 metric_key（{@link com.adamchung.stockanalysis.common.HealthCheckMetricKeys}
 * 裡 key 以 {@code bankRevenue.} 開頭者）例外存 {@code 'YYYY-MM'}（如 {@code '2025-01'}）。
 * 兩種格式都是固定寬度，字串排序恰好等於時間排序，沿用同一張表同一套查詢邏輯，不為月頻資料
 * 另開表。
 */
@Entity
@Table(
        name = "stock_health_snapshot",
        uniqueConstraints = @UniqueConstraint(columnNames = {"stock_id", "quarter", "metric_key"}))
@Getter
@Setter
public class StockHealthSnapshotEntity extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "stock_id", nullable = false, length = 10)
    private String stockId;

    @Column(name = "quarter", nullable = false, length = 10)
    private String quarter;

    @Column(name = "metric_key", nullable = false, length = 50)
    private String metricKey;

    @Column(name = "value", precision = 18, scale = 4)
    private BigDecimal value;

    @Column(name = "note", columnDefinition = "text")
    private String note;
}
