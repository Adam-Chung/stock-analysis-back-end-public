package com.adamchung.stockanalysis.externaldata.twse.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/** TWSE OpenAPI {@code t187ap03_L}（公司基本資料）單筆原始資料，這裡只取已發行股數。 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TwseCompanyProfileDto(
        @JsonProperty("公司代號") String stockCode, // 公司代號
        @JsonProperty("公司名稱") String companyName, // 公司名稱
        @JsonProperty("已發行普通股數或TDR原股發行股數") String issuedShares // 已發行股數
) {
}
