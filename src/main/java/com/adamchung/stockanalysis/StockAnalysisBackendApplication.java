package com.adamchung.stockanalysis;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** 股票分析後端服務的 Spring Boot 進入點。 */
@SpringBootApplication
public class StockAnalysisBackendApplication {

	/**
	 * 啟動 Spring Boot 應用程式。
	 *
	 * @param args 命令列參數
	 */
	public static void main(String[] args) {
		SpringApplication.run(StockAnalysisBackendApplication.class, args);
	}

}
