package com.adamchung.stockanalysis.externaldata.twse.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * TWSE OpenAPI {@code t187ap11_L}（董監大股東持股）單筆原始資料。金額欄位是字串（TWSE
 * 開放資料的通例），呼叫端自行轉數字。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TwseDirectorHoldingDto(
        @JsonProperty("資料年月") String dataYearMonth, // 資料年月
        @JsonProperty("公司代號") String stockCode, // 公司代號
        @JsonProperty("公司名稱") String companyName, // 公司名稱
        @JsonProperty("職稱") String title, // 職稱
        /** 持股人姓名／名稱（自然人或法人）。用來把同一個股東佔多席董事的重複列去重（DEC-072）。 */
        @JsonProperty("姓名") String name,
        @JsonProperty("目前持股") String currentShares, // 目前持股數
        @JsonProperty("設質股數") String pledgedShares // 設質股數
) {
}
