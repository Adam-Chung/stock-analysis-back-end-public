package com.adamchung.stockanalysis.targetprice.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import com.adamchung.stockanalysis.stock.entity.Auditable;
import lombok.Getter;
import lombok.Setter;

/** SA-01 目標價畫面的個股設定（DB-01 4.7）。屬於此功能專用，不與其他畫面共用。 */
@Entity
@Table(name = "stock_target_price_setting")
@Getter
@Setter
public class StockTargetPriceSettingEntity extends Auditable {

    @Id
    @Column(name = "stock_id", length = 10)
    private String stockId;

    @Column(name = "investment_strategy", columnDefinition = "text")
    private String investmentStrategy;

    /** 3、5 或 10（SA-01 DEC-3）；由資料庫的 CHECK 約束強制檢查。 */
    @Column(name = "eps_avg_years", nullable = false)
    private Short epsAvgYears = 5;

    /** 3、5 或 10（SA-01 DEC-3）；由資料庫的 CHECK 約束強制檢查。 */
    @Column(name = "bps_avg_years", nullable = false)
    private Short bpsAvgYears = 5;

    @Column(name = "setting_note", columnDefinition = "text")
    private String settingNote;

    /** 1~6，對應 1便宜價..6爆炸貴（SA-01 3.1）；由資料庫的 CHECK 約束強制檢查。 */
    @Column(name = "target_price_level", nullable = false)
    private Short targetPriceLevel = 3;
}
