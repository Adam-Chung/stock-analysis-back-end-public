package com.adamchung.stockanalysis.stock.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * 每一列對應一組 (stock_id, year)，由 FinMind 逐日的 TaiwanStockPER 聚合而成（DB-01 4.3）。
 *
 * <p>不包含殖利率欄位——十年平均殖利率改為請求當下即時計算（見
 * {@link com.adamchung.stockanalysis.valuation.calculator.DividendYieldCalculator}），不再信任
 * FinMind 的 dividend_yield，也不落地存表。
 */
@Entity
@Table(name = "stock_valuation_yearly")
@Getter
@Setter
public class StockValuationYearlyEntity extends Auditable {

    @EmbeddedId
    private StockYearId id;

    @Column(name = "avg_per", precision = 10, scale = 4)
    private BigDecimal avgPer;

    @Column(name = "low_per", precision = 10, scale = 4)
    private BigDecimal lowPer;

    @Column(name = "high_per", precision = 10, scale = 4)
    private BigDecimal highPer;

    @Column(name = "avg_pbr", precision = 10, scale = 4)
    private BigDecimal avgPbr;

    @Column(name = "low_pbr", precision = 10, scale = 4)
    private BigDecimal lowPbr;

    @Column(name = "high_pbr", precision = 10, scale = 4)
    private BigDecimal highPbr;
}
