package com.adamchung.stockanalysis.healthcheck.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import com.adamchung.stockanalysis.stock.entity.Auditable;
import lombok.Getter;
import lombok.Setter;

/**
 * 快取的企業體質檢視 AI 投資建議（DEC-068 後續實作）。每檔股票只保留最新一筆，
 * 逐字比照目標價頁的 {@code stock_ai_suggestion}／{@code StockAiSuggestionEntity}，
 * 獨立一張表存放。
 */
@Entity
@Table(name = "stock_health_ai_suggestion")
@Getter
@Setter
public class StockHealthAiSuggestionEntity extends Auditable {

    @Id
    @Column(name = "stock_id", length = 10)
    private String stockId;

    @Column(name = "suggestion_text", columnDefinition = "text")
    private String suggestionText;

    @Column(name = "model_name", length = 50)
    private String modelName;
}
