package com.adamchung.stockanalysis.stock.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 股利政策紀錄（DB-01 4.5，資料來源：TaiwanStockDividend）。
 *
 * {@code year} 是由應用程式從 {@code cashExDividendTradingDate} 計算得出（缺值時退回
 * {@code announcementDate}）——**不是**直接複製 FinMind 原始的 "year" 欄位，因為那個欄位是
 * 民國年+季度的文字標籤（例如「112年第3季」），不是可用的整數（見
 * docs/agent/DECISIONS.md DEC-005）。
 *
 * {@code cumulativeCashDividend}/{@code cumulativeStockDividend} 是該筆資料所屬年度、依公告日
 * 由舊到新累加到「這一筆為止」的現金/股票股利合計（同一年配息多次時，第二筆＝第一筆＋第二筆
 * 自己的量，以此類推）。年度內公告日最新的一筆，其累積值即為該年度的完整總股利，計算殖利率
 * 時直接取用，不需要在應用層再重新分組加總。
 */
@Entity
@Table(
        name = "stock_dividend",
        uniqueConstraints = @UniqueConstraint(columnNames = {"stock_id", "year", "announcement_date"}))
@Getter
@Setter
public class StockDividendEntity extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "stock_id", nullable = false, length = 10)
    private String stockId;

    @Column(name = "year", nullable = false)
    private Integer year;

    @Column(name = "cash_earnings_distribution", precision = 10, scale = 4)
    private BigDecimal cashEarningsDistribution;

    @Column(name = "cash_statutory_surplus", precision = 10, scale = 4)
    private BigDecimal cashStatutorySurplus;

    @Column(name = "cash_ex_dividend_trading_date")
    private LocalDate cashExDividendTradingDate;

    @Column(name = "cash_dividend_payment_date")
    private LocalDate cashDividendPaymentDate;

    @Column(name = "stock_earnings_distribution", precision = 10, scale = 4)
    private BigDecimal stockEarningsDistribution;

    @Column(name = "stock_statutory_surplus", precision = 10, scale = 4)
    private BigDecimal stockStatutorySurplus;

    @Column(name = "stock_ex_dividend_trading_date")
    private LocalDate stockExDividendTradingDate;

    @Column(name = "announcement_date", nullable = false)
    private LocalDate announcementDate;

    @Column(name = "cumulative_cash_dividend", precision = 10, scale = 4)
    private BigDecimal cumulativeCashDividend;

    @Column(name = "cumulative_stock_dividend", precision = 10, scale = 4)
    private BigDecimal cumulativeStockDividend;
}
