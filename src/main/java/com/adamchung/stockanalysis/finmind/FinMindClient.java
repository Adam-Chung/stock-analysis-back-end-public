package com.adamchung.stockanalysis.finmind;

import com.adamchung.stockanalysis.finmind.config.FinMindProperties;
import com.adamchung.stockanalysis.finmind.dto.FinMindBalanceSheetDto;
import com.adamchung.stockanalysis.finmind.dto.FinMindCashFlowsStatementDto;
import com.adamchung.stockanalysis.finmind.dto.FinMindDividendDto;
import com.adamchung.stockanalysis.finmind.dto.FinMindFinancialStatementDto;
import com.adamchung.stockanalysis.finmind.dto.FinMindInstitutionalInvestorsBuySellDto;
import com.adamchung.stockanalysis.finmind.dto.FinMindMonthRevenueDto;
import com.adamchung.stockanalysis.finmind.dto.FinMindResponseDto;
import com.adamchung.stockanalysis.finmind.dto.FinMindShareholdingDto;
import com.adamchung.stockanalysis.finmind.dto.FinMindStockInfoDto;
import com.adamchung.stockanalysis.finmind.dto.FinMindStockPriceDto;
import com.adamchung.stockanalysis.finmind.dto.FinMindStockValuationDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.time.LocalDate;
import java.util.List;

/**
 * 對 FinMind /data 端點的輕量封裝。呼叫端自行決定日期區間與是否要打這次呼叫——這個類別本身
 * 沒有任何額度保護或快取邏輯（那些邏輯放在同步服務裡，遵循
 * docs/spec/DB-01-database-schema.md 的讀取/更新流程）。
 */
@Service
public class FinMindClient {

    private static final Logger log = LoggerFactory.getLogger(FinMindClient.class);

    private final RestClient restClient;
    private final String token;

    /**
     * 建立 FinMind client，token 僅在該次匿名請求額度不足時使用。啟動時記錄 token 是否有設定
     * （只記長度，不記內容），方便從部署環境的 log 直接確認環境變數有沒有真的帶進來
     * （DECISIONS.md DEC-021）。
     *
     * @param restClient 已設定好 base URL 的 {@link RestClient}
     * @param properties FinMind 連線設定（base URL、token）
     */
    public FinMindClient(@Qualifier("finMindRestClient") RestClient restClient, FinMindProperties properties) {
        this.restClient = restClient;
        this.token = properties.token();
        log.info("FinMind client initialized: tokenConfigured={}, tokenLength={}, tokenTail={}",
                StringUtils.hasText(token), token == null ? 0 : token.length(), maskedTokenTail());
    }

    /**
     * 只取最後幾碼，呼應 FinMind 自己的錯誤回應本來就會揭露的 {@code token_tail}——讓部署環境
     * 的 token 可以跟本機的 token 互相比對，而不必揭露完整密鑰（DEC-023）。
     *
     * @return 遮蔽過的 token 尾碼；未設定 token 時回傳 {@code null}
     */
    private String maskedTokenTail() {
        if (!StringUtils.hasText(token)) {
            return null;
        }
        int tailLength = 6;
        return token.length() <= tailLength ? "***" : "..." + token.substring(token.length() - tailLength);
    }

    /**
     * 查詢指定股票的基本資料。
     *
     * @param stockId 股票代號
     * @return TaiwanStockInfo 資料列
     */
    public List<FinMindStockInfoDto> fetchStockInfo(String stockId) {
        return get("TaiwanStockInfo", stockId, null, null,
                new ParameterizedTypeReference<FinMindResponseDto<FinMindStockInfoDto>>() {
                });
    }

    /**
     * DB-01 8.1/DEC-26：不帶 data_id 呼叫會一次回傳 FinMind 的全市場清單（截至 2026-07-29
     * 對真實 API 驗證約 4,295 筆），用來一次性建立本地股票搜尋目錄，不是每次搜尋都呼叫。
     *
     * @return 全市場 TaiwanStockInfo 資料列
     */
    public List<FinMindStockInfoDto> fetchAllStockInfo() {
        return get("TaiwanStockInfo", null, null, null,
                new ParameterizedTypeReference<FinMindResponseDto<FinMindStockInfoDto>>() {
                });
    }

    /**
     * 查詢指定日期區間的每日股價。
     *
     * @param stockId 股票代號
     * @param startDate 查詢起始日
     * @param endDate 查詢結束日
     * @return TaiwanStockPrice 資料列
     */
    public List<FinMindStockPriceDto> fetchStockPrice(String stockId, LocalDate startDate, LocalDate endDate) {
        return get("TaiwanStockPrice", stockId, startDate, endDate,
                new ParameterizedTypeReference<FinMindResponseDto<FinMindStockPriceDto>>() {
                });
    }

    /**
     * 查詢指定日期區間的每日估值資料。
     *
     * @param stockId 股票代號
     * @param startDate 查詢起始日
     * @param endDate 查詢結束日
     * @return TaiwanStockPER 資料列
     */
    public List<FinMindStockValuationDto> fetchStockValuation(String stockId, LocalDate startDate, LocalDate endDate) {
        return get("TaiwanStockPER", stockId, startDate, endDate,
                new ParameterizedTypeReference<FinMindResponseDto<FinMindStockValuationDto>>() {
                });
    }

    /**
     * 查詢指定日期區間的股利資料。
     *
     * @param stockId 股票代號
     * @param startDate 查詢起始日
     * @param endDate 查詢結束日
     * @return TaiwanStockDividend 資料列
     */
    public List<FinMindDividendDto> fetchDividend(String stockId, LocalDate startDate, LocalDate endDate) {
        return get("TaiwanStockDividend", stockId, startDate, endDate,
                new ParameterizedTypeReference<FinMindResponseDto<FinMindDividendDto>>() {
                });
    }

    /**
     * 查詢指定日期區間的財務報表資料。
     *
     * @param stockId 股票代號
     * @param startDate 查詢起始日
     * @param endDate 查詢結束日
     * @return TaiwanStockFinancialStatements 資料列
     */
    public List<FinMindFinancialStatementDto> fetchFinancialStatement(String stockId, LocalDate startDate, LocalDate endDate) {
        return get("TaiwanStockFinancialStatements", stockId, startDate, endDate,
                new ParameterizedTypeReference<FinMindResponseDto<FinMindFinancialStatementDto>>() {
                });
    }

    /**
     * 查詢指定日期區間的資產負債表資料，供企業體質檢視使用（SA-04）。
     *
     * @param stockId 股票代號
     * @param startDate 查詢起始日
     * @param endDate 查詢結束日
     * @return TaiwanStockBalanceSheet 資料列
     */
    public List<FinMindBalanceSheetDto> fetchBalanceSheet(String stockId, LocalDate startDate, LocalDate endDate) {
        return get("TaiwanStockBalanceSheet", stockId, startDate, endDate,
                new ParameterizedTypeReference<FinMindResponseDto<FinMindBalanceSheetDto>>() {
                });
    }

    /**
     * 查詢指定日期區間的現金流量表資料，供企業體質檢視使用（SA-04）。
     *
     * @param stockId 股票代號
     * @param startDate 查詢起始日
     * @param endDate 查詢結束日
     * @return TaiwanStockCashFlowsStatement 資料列
     */
    public List<FinMindCashFlowsStatementDto> fetchCashFlowsStatement(String stockId, LocalDate startDate, LocalDate endDate) {
        return get("TaiwanStockCashFlowsStatement", stockId, startDate, endDate,
                new ParameterizedTypeReference<FinMindResponseDto<FinMindCashFlowsStatementDto>>() {
                });
    }

    /**
     * 查詢指定日期區間的月營收資料，供企業體質檢視銀行模板使用（SA-04）。
     *
     * @param stockId 股票代號
     * @param startDate 查詢起始日
     * @param endDate 查詢結束日
     * @return TaiwanStockMonthRevenue 資料列
     */
    public List<FinMindMonthRevenueDto> fetchMonthRevenue(String stockId, LocalDate startDate, LocalDate endDate) {
        return get("TaiwanStockMonthRevenue", stockId, startDate, endDate,
                new ParameterizedTypeReference<FinMindResponseDto<FinMindMonthRevenueDto>>() {
                });
    }

    /**
     * 查詢指定日期區間的三大法人逐日買賣超資料，供企業體質檢視銀行模板使用（SA-04）。
     *
     * @param stockId 股票代號
     * @param startDate 查詢起始日
     * @param endDate 查詢結束日
     * @return TaiwanStockInstitutionalInvestorsBuySell 資料列
     */
    public List<FinMindInstitutionalInvestorsBuySellDto> fetchInstitutionalInvestorsBuySell(
            String stockId, LocalDate startDate, LocalDate endDate) {
        return get("TaiwanStockInstitutionalInvestorsBuySell", stockId, startDate, endDate,
                new ParameterizedTypeReference<FinMindResponseDto<FinMindInstitutionalInvestorsBuySellDto>>() {
                });
    }

    /**
     * 查詢指定日期區間的外資持股比例資料，供企業體質檢視銀行模板使用（SA-04）。
     *
     * @param stockId 股票代號
     * @param startDate 查詢起始日
     * @param endDate 查詢結束日
     * @return TaiwanStockShareholding 資料列
     */
    public List<FinMindShareholdingDto> fetchShareholding(String stockId, LocalDate startDate, LocalDate endDate) {
        return get("TaiwanStockShareholding", stockId, startDate, endDate,
                new ParameterizedTypeReference<FinMindResponseDto<FinMindShareholdingDto>>() {
                });
    }

    /**
     * 每次呼叫都優先使用匿名額度，只有該次匿名請求回傳 402 時才以 token 重試一次。加上 log
     * 才能從部署環境直接分辨：匿名額度是否用盡、有沒有真的重試 token、token 重試本身是否也
     * 額度用盡（DEC-021）。
     *
     * @param dataset FinMind 資料集名稱
     * @param stockId 股票代號，可為 {@code null}
     * @param startDate 查詢起始日，可為 {@code null}
     * @param endDate 查詢結束日，可為 {@code null}
     * @param responseType 預期的回應型別
     * @return FinMind 回應的資料列
     * @param <T> 資料列型別
     */
    private <T> List<T> get(String dataset, String stockId, LocalDate startDate, LocalDate endDate,
                             ParameterizedTypeReference<FinMindResponseDto<T>> responseType) {
        try {
            return executeGet(dataset, stockId, startDate, endDate, responseType, false);
        } catch (RestClientResponseException exception) {
            boolean quotaExceeded = exception.getStatusCode() == HttpStatus.PAYMENT_REQUIRED;
            if (!quotaExceeded || !StringUtils.hasText(token)) {
                log.warn(
                        "FinMind anonymous request failed for dataset={} status={}; not retrying with token"
                                + " (quotaExceeded={}, tokenConfigured={})",
                        dataset, exception.getStatusCode(), quotaExceeded, StringUtils.hasText(token));
                throw exception;
            }

            log.warn("FinMind anonymous quota exhausted for dataset={}; retrying with configured token", dataset);
            try {
                List<T> result = executeGet(dataset, stockId, startDate, endDate, responseType, true);
                log.info("FinMind token retry succeeded for dataset={}", dataset);
                return result;
            } catch (RestClientResponseException tokenException) {
                log.error(
                        "FinMind token retry ALSO failed for dataset={} status={} - token's own quota is likely"
                                + " exhausted too",
                        dataset, tokenException.getStatusCode());
                throw tokenException;
            }
        }
    }

    /**
     * 執行一次 FinMind 請求，可選擇是否帶入設定的 token 做驗證。
     *
     * @param dataset FinMind 資料集名稱
     * @param stockId 股票代號，可為 {@code null}
     * @param startDate 查詢起始日，可為 {@code null}
     * @param endDate 查詢結束日，可為 {@code null}
     * @param responseType 預期的回應型別
     * @param authenticated 是否要帶入設定的 bearer token
     * @return 回應的資料列
     * @param <T> 資料列型別
     */
    private <T> List<T> executeGet(
            String dataset,
            String stockId,
            LocalDate startDate,
            LocalDate endDate,
            ParameterizedTypeReference<FinMindResponseDto<T>> responseType,
            boolean authenticated) {
        FinMindResponseDto<T> response = restClient.get()
                .uri(uriBuilder -> {
                    uriBuilder.path("/data").queryParam("dataset", dataset);
                    if (StringUtils.hasText(stockId)) {
                        uriBuilder.queryParam("data_id", stockId);
                    }
                    if (startDate != null) {
                        uriBuilder.queryParam("start_date", startDate);
                    }
                    if (endDate != null) {
                        uriBuilder.queryParam("end_date", endDate);
                    }
                    return uriBuilder.build();
                })
                .headers(headers -> {
                    if (authenticated) {
                        headers.set(HttpHeaders.AUTHORIZATION, "Bearer " + token);
                    }
                })
                .retrieve()
                .body(responseType);

        if (response == null || response.data() == null) {
            throw new FinMindClientException(
                    "FinMind returned an empty response for dataset=%s, stock_id=%s".formatted(dataset, stockId));
        }
        if (response.status() != 200) {
            throw new FinMindClientException(
                    "FinMind error for dataset=%s, stock_id=%s: %s".formatted(dataset, stockId, response.msg()));
        }
        return response.data();
    }
}
