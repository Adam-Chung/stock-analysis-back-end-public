package com.adamchung.stockanalysis.stock.entity;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

/**
 * 每張資料表都必須具備的標準稽核欄位（DB-01 DEC-25）。
 *
 * created_at/updated_at 由 Hibernate 自動填入。created_by/updated_by 不會自動填入——
 * 不同的寫入路徑對應不同的操作者（FinMind 同步作業、Groq 建議作業、使用者編輯設定），
 * 呼叫端服務必須在儲存前自行明確設定這兩個欄位（見 docs/agent/DECISIONS.md DEC-004）。
 */
@MappedSuperclass
@Getter
@Setter
public abstract class Auditable {

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "created_by", nullable = false, updatable = false)
    private String createdBy;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "updated_by", nullable = false)
    private String updatedBy;
}
