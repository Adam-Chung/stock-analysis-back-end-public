package com.adamchung.stockanalysis.healthcheck.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import com.adamchung.stockanalysis.stock.entity.Auditable;
import lombok.Getter;
import lombok.Setter;

/**
 * SA-03 企業體質檢視的公司資訊（DB-01 §10.1）。不分季度，只存最新一份
 * （DEC-029，比照 SA-01 投資策略欄位的做法）。
 */
@Entity
@Table(name = "stock_health_profile")
@Getter
@Setter
public class StockHealthProfileEntity extends Auditable {

    @Id
    @Column(name = "stock_id", length = 10)
    private String stockId;

    @Column(name = "profile", columnDefinition = "text")
    private String profile;

    @Column(name = "custom_note", columnDefinition = "text")
    private String customNote;

    /** 例如 {@code '2024Q4'}，上次檢視對應的季度標籤。 */
    @Column(name = "last_reviewed_quarter", length = 10)
    private String lastReviewedQuarter;
}
