package com.adamchung.stockanalysis.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 設定 springdoc-openapi 產生的 API 文件（Swagger UI）基本資訊。 */
@Configuration
public class OpenApiConfig {

    /**
     * 定義 Swagger UI／OpenAPI 文件顯示的標題、版本與說明。
     *
     * @return API 文件的基本描述資訊
     */
    @Bean
    public OpenAPI stockAnalysisOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("股票分析後端 API")
                        .version("v1")
                        .description("提供股票目標價（SA-01）、我的最愛、股票搜尋與 FinMind 資料"
                                + "同步等功能的 REST API。詳細計算邏輯與資料庫設計請參考 "
                                + "docs/spec/ 底下的 SA-01/SA-02/DB-01 文件。"));
    }
}
