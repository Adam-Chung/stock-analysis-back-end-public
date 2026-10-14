package com.adamchung.stockanalysis.targetprice.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import com.adamchung.stockanalysis.stock.entity.Auditable;
import lombok.Getter;
import lombok.Setter;

/**
 * 快取的 AI 投資建議（DB-01 4.8、SA-01 3.4）。每檔股票只保留最新一筆建議；
 * 畫面上的「上次更新時間」就是這一列的 {@code updated_at} 稽核欄位。
 */
@Entity
@Table(name = "stock_ai_suggestion")
@Getter
@Setter
public class StockAiSuggestionEntity extends Auditable {

    @Id
    @Column(name = "stock_id", length = 10)
    private String stockId;

    @Column(name = "suggestion_text", columnDefinition = "text")
    private String suggestionText;

    @Column(name = "model_name", length = 50)
    private String modelName;
}
