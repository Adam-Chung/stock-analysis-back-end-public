package com.adamchung.stockanalysis.finmind.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/** 每個 FinMind /data 回應都會包在這層外殼裡。 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record FinMindResponseDto<T>(
        @JsonProperty("msg") String msg, // 回應訊息
        @JsonProperty("status") int status, // 狀態碼
        @JsonProperty("data") List<T> data // 資料列清單
) {
}
