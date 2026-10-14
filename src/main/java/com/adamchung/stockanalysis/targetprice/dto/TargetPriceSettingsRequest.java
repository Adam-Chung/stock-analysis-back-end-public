package com.adamchung.stockanalysis.targetprice.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** 使用者可編輯的 SA-01 3.1 設定欄位。 */
public record TargetPriceSettingsRequest(
        @Size(max = 5000) String investmentStrategy, // 投資策略說明
        @NotNull Integer epsAvgYears, // EPS平均採計年數
        @NotNull Integer bpsAvgYears, // BPS平均採計年數
        @Size(max = 5000) String settingNote, // 設定備註
        @NotNull @Min(1) @Max(6) Integer targetPriceLevel // 目標價等級（1~6）
) {
}
