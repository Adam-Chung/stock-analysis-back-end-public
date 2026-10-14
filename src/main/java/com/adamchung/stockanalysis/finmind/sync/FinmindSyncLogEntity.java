package com.adamchung.stockanalysis.finmind.sync;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import com.adamchung.stockanalysis.stock.entity.Auditable;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

/**
 * 依 (stock_id, dataset) 追蹤 FinMind 同步進度到哪裡了（DB-01 4.6）。畫面上的「上次同步時間」
 * 就是這一列的 {@code updated_at} 稽核欄位——沒有另外設 last_synced_at 欄位
 * （DEC-25 認為那是多餘欄位而移除）。
 */
@Entity
@Table(name = "finmind_sync_log", uniqueConstraints = @UniqueConstraint(columnNames = {"stock_id", "dataset"}))
@Getter
@Setter
public class FinmindSyncLogEntity extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "stock_id", nullable = false, length = 10)
    private String stockId;

    @Enumerated(EnumType.STRING)
    @Column(name = "dataset", nullable = false, length = 50)
    private FinMindDataset dataset;

    /** 對於年度粒度的資料集（Price/PER），這是最後一個完整同步年度的 12/31。 */
    @Column(name = "last_data_date")
    private LocalDate lastDataDate;

    @Column(name = "status", nullable = false, length = 20)
    private String status = "ok";
}
