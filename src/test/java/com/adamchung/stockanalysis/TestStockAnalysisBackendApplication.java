package com.adamchung.stockanalysis;

import org.springframework.boot.SpringApplication;

public class TestStockAnalysisBackendApplication {

	public static void main(String[] args) {
		SpringApplication.from(StockAnalysisBackendApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
