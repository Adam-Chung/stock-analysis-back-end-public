package com.adamchung.stockanalysis.stock.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * 每一列對應一組 (stock_id, year)——只存完整曆年，絕不存正在進行中的當年度
 * （DB-01 DEC-24：不保留逐日股價歷史，只保留年度聚合結果）。
 */
@Entity
@Table(name = "stock_price_yearly")
@Getter
@Setter
public class StockPriceYearlyEntity extends Auditable {

    @EmbeddedId
    private StockYearId id;

    @Column(name = "avg_close_price", nullable = false, precision = 12, scale = 4)
    private BigDecimal avgClosePrice;

    @Column(name = "high_price", nullable = false, precision = 12, scale = 4)
    private BigDecimal highPrice;

    @Column(name = "low_price", nullable = false, precision = 12, scale = 4)
    private BigDecimal lowPrice;
}
