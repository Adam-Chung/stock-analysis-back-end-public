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
 * 季度財務報表資料列（DB-01 4.4，資料來源：TaiwanStockFinancialStatements）。
 * 目前的同步流程只保存 {@code type == "EPS"} 的資料，因為目標價估值計算用不到其他上千種
 * 財報科目（DEC-014）。
 */
@Entity
@Table(
        name = "stock_financial_statement",
        uniqueConstraints = @UniqueConstraint(columnNames = {"stock_id", "statement_date", "type"}))
@Getter
@Setter
public class StockFinancialStatementEntity extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "stock_id", nullable = false, length = 10)
    private String stockId;

    @Column(name = "statement_date", nullable = false)
    private LocalDate statementDate;

    @Column(name = "type", nullable = false, length = 50)
    private String type;

    @Column(name = "value", nullable = false, precision = 18, scale = 4)
    private BigDecimal value;

    @Column(name = "origin_name", length = 100)
    private String originName;
}
