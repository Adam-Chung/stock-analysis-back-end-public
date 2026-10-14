package com.adamchung.stockanalysis.finmind;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.adamchung.stockanalysis.finmind.config.FinMindProperties;
import com.adamchung.stockanalysis.finmind.dto.FinMindBalanceSheetDto;
import com.adamchung.stockanalysis.finmind.dto.FinMindCashFlowsStatementDto;
import com.adamchung.stockanalysis.finmind.dto.FinMindInstitutionalInvestorsBuySellDto;
import com.adamchung.stockanalysis.finmind.dto.FinMindMonthRevenueDto;
import com.adamchung.stockanalysis.finmind.dto.FinMindShareholdingDto;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.LocalDate;
import java.util.List;

/**
 * 驗證企業體質檢視這輪新增的 5 個 FinMind dataset 方法的 URL 組裝與 JSON→record 映射。
 * anonymous/token fallback 邏輯已透過 {@link FinMindClientFallbackTest} 對
 * {@code fetchStockInfo} 驗證過、是所有方法共用的邏輯，這裡不重複測。
 */
class FinMindClientDatasetTest {

    private static final String BASE_URL = "https://api.finmindtrade.com/api/v4";

    @Test
    void fetchBalanceSheetMapsFields() {
        RestClient.Builder builder = builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        FinMindClient client = new FinMindClient(builder.build(), properties());

        server.expect(once(), requestTo(BASE_URL
                        + "/data?dataset=TaiwanStockBalanceSheet&data_id=2330&start_date=2024-09-30&end_date=2024-12-31"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        {"msg":"success","status":200,"data":[
                          {"date":"2024-12-31","stock_id":"2330","type":"CapitalStock","value":259303805,"origin_name":"股本"}
                        ]}
                        """, MediaType.APPLICATION_JSON));

        List<FinMindBalanceSheetDto> rows = client.fetchBalanceSheet("2330",
                LocalDate.of(2024, 9, 30), LocalDate.of(2024, 12, 31));

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).type()).isEqualTo("CapitalStock");
        assertThat(rows.get(0).value()).isEqualByComparingTo("259303805");
        server.verify();
    }

    @Test
    void fetchCashFlowsStatementMapsFields() {
        RestClient.Builder builder = builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        FinMindClient client = new FinMindClient(builder.build(), properties());

        server.expect(once(), requestTo(BASE_URL
                        + "/data?dataset=TaiwanStockCashFlowsStatement&data_id=2330&start_date=2024-09-30&end_date=2024-12-31"))
                .andRespond(withSuccess("""
                        {"msg":"success","status":200,"data":[
                          {"date":"2024-12-31","stock_id":"2330","type":"NetCashInflowFromOperatingActivities","value":100000,"origin_name":"營業活動之淨現金流入"}
                        ]}
                        """, MediaType.APPLICATION_JSON));

        List<FinMindCashFlowsStatementDto> rows = client.fetchCashFlowsStatement("2330",
                LocalDate.of(2024, 9, 30), LocalDate.of(2024, 12, 31));

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).type()).isEqualTo("NetCashInflowFromOperatingActivities");
        server.verify();
    }

    @Test
    void fetchMonthRevenueMapsFields() {
        RestClient.Builder builder = builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        FinMindClient client = new FinMindClient(builder.build(), properties());

        server.expect(once(), requestTo(BASE_URL
                        + "/data?dataset=TaiwanStockMonthRevenue&data_id=2884&start_date=2024-01-01&end_date=2024-03-31"))
                .andRespond(withSuccess("""
                        {"msg":"success","status":200,"data":[
                          {"date":"2024-02-01","stock_id":"2884","country":"Taiwan","revenue":6420000000,"revenue_month":1,"revenue_year":2024,"create_time":""}
                        ]}
                        """, MediaType.APPLICATION_JSON));

        List<FinMindMonthRevenueDto> rows = client.fetchMonthRevenue("2884",
                LocalDate.of(2024, 1, 1), LocalDate.of(2024, 3, 31));

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).revenue()).isEqualTo(6420000000L);
        assertThat(rows.get(0).revenueMonth()).isEqualTo(1);
        assertThat(rows.get(0).revenueYear()).isEqualTo(2024);
        server.verify();
    }

    @Test
    void fetchInstitutionalInvestorsBuySellMapsFields() {
        RestClient.Builder builder = builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        FinMindClient client = new FinMindClient(builder.build(), properties());

        server.expect(once(), requestTo(BASE_URL
                        + "/data?dataset=TaiwanStockInstitutionalInvestorsBuySell&data_id=2884&start_date=2024-01-02&end_date=2024-01-02"))
                .andRespond(withSuccess("""
                        {"msg":"success","status":200,"data":[
                          {"date":"2024-01-02","stock_id":"2884","buy":7893037,"name":"Foreign_Investor","sell":2000055}
                        ]}
                        """, MediaType.APPLICATION_JSON));

        List<FinMindInstitutionalInvestorsBuySellDto> rows = client.fetchInstitutionalInvestorsBuySell("2884",
                LocalDate.of(2024, 1, 2), LocalDate.of(2024, 1, 2));

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).name()).isEqualTo("Foreign_Investor");
        assertThat(rows.get(0).buy()).isEqualTo(7893037L);
        assertThat(rows.get(0).sell()).isEqualTo(2000055L);
        server.verify();
    }

    @Test
    void fetchShareholdingMapsFields() {
        RestClient.Builder builder = builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        FinMindClient client = new FinMindClient(builder.build(), properties());

        server.expect(once(), requestTo(BASE_URL
                        + "/data?dataset=TaiwanStockShareholding&data_id=2884&start_date=2024-01-02&end_date=2024-01-02"))
                .andRespond(withSuccess("""
                        {"msg":"success","status":200,"data":[
                          {"date":"2024-01-02","stock_id":"2884","stock_name":"玉山金","ForeignInvestmentSharesRatio":31.6}
                        ]}
                        """, MediaType.APPLICATION_JSON));

        List<FinMindShareholdingDto> rows = client.fetchShareholding("2884",
                LocalDate.of(2024, 1, 2), LocalDate.of(2024, 1, 2));

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).foreignInvestmentSharesRatio()).isEqualByComparingTo("31.6");
        server.verify();
    }

    private RestClient.Builder builder() {
        return RestClient.builder().baseUrl(BASE_URL);
    }

    private FinMindProperties properties() {
        return new FinMindProperties(BASE_URL, "");
    }
}
