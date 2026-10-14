package com.adamchung.stockanalysis.stock.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

/** 股票基本資料 + 最新已知成交價（DB-01 4.1，資料來源：TaiwanStockInfo）。 */
@Entity
@Table(name = "stock")
@Getter
@Setter
public class StockEntity extends Auditable {

    @Id
    @Column(name = "stock_id", length = 10)
    private String stockId;

    @Column(name = "stock_name", nullable = false, length = 100)
    private String stockName;

    @Column(name = "industry_category", length = 100)
    private String industryCategory;

    @Column(name = "market_type", length = 20)
    private String marketType;

    @Column(name = "latest_close_price", precision = 12, scale = 4)
    private BigDecimal latestClosePrice;

    @Column(name = "latest_price_date")
    private LocalDate latestPriceDate;

    /**
     * 當年度（尚未結束）至今的即時最高/最低價，跟 {@code latestClosePrice} 一起更新
     * （見 {@code FinMindSyncService.refreshLatestPriceIfStale}）。這兩個欄位不屬於
     * stock_price_yearly，因為那張表只存完整年度（DEC-24）——沒有這兩個欄位的話，
     * 10年高/10年低會悄悄忽略掉今年創的新高/新低。
     */
    @Column(name = "current_year_high_price", precision = 12, scale = 4)
    private BigDecimal currentYearHighPrice;

    @Column(name = "current_year_low_price", precision = 12, scale = 4)
    private BigDecimal currentYearLowPrice;
}
