package com.adamchung.stockanalysis.healthcheck.dto;

import jakarta.validation.constraints.Size;

/** 公司資訊區塊的可編輯欄位（SA-03 §4.1）。不分季度，只存最新一份（DEC-029）。 */
public record HealthCheckProfileRequest(
        @Size(max = 5000) String profile, // 公司概況說明文字
        @Size(max = 5000) String customNote // 自訂備註
) {
}
