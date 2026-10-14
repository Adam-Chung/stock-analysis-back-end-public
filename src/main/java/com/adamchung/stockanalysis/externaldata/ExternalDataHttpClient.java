package com.adamchung.stockanalysis.externaldata;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * 對公開資料來源提供節流與有限重試的共用 HTTP 存取層。
 *
 * <p>每個 host 都有獨立的最短請求間隔；只有 502、503、504 會固定延遲後重試一次。429 或
 * 其他拒絕狀態不重試，避免規避來源限制。Public mirror 僅允許直接連線，不提供代理繞道功能。
 */
@Service
public class ExternalDataHttpClient {

    private static final String USER_AGENT = "stock-analysis-back-end-public/0.1 (+external-data-sync)";
    private static final long MIN_INTERVAL_MS = 1_500;
    private static final long TRANSIENT_ERROR_RETRY_DELAY_MS = 1_000;
    private static final Set<Integer> TRANSIENT_ERROR_STATUS_CODES = Set.of(502, 503, 504);

    private final RestClient restClient;
    private final Map<String, Long> nextRequestAtMillisByHost = new ConcurrentHashMap<>();

    /** 建立只會直接連線到呼叫端指定 URL 的 client。 */
    public ExternalDataHttpClient(RestClient.Builder builder) {
        this.restClient = builder.build();
    }

    /** 取得 JSON，並套用 per-host 節流與有限重試。 */
    public <T> T getJson(String url, ParameterizedTypeReference<T> responseType) {
        waitForRateLimit(url);
        return withOneRetryOnTransientError(url, () -> restClient.get()
                .uri(URI.create(url))
                .header(HttpHeaders.USER_AGENT, USER_AGENT)
                .header(HttpHeaders.ACCEPT, "application/json")
                .retrieve()
                .body(responseType));
    }

    /** 取得文字內容，並套用 per-host 節流與有限重試。 */
    public String getText(String url) {
        waitForRateLimit(url);
        return withOneRetryOnTransientError(url, () -> restClient.get()
                .uri(URI.create(url))
                .header(HttpHeaders.USER_AGENT, USER_AGENT)
                .header(HttpHeaders.ACCEPT, "text/html")
                .retrieve()
                .body(String.class));
    }

    /** 取得二進位內容，並拒絕明顯不是檔案的過小回應。 */
    public byte[] getBytes(String url) {
        waitForRateLimit(url);
        byte[] body = withOneRetryOnTransientError(url, () -> restClient.get()
                .uri(URI.create(url))
                .header(HttpHeaders.USER_AGENT, USER_AGENT)
                .retrieve()
                .body(byte[].class));
        if (body == null || body.length < 512) {
            throw new ExternalDataException(
                    "下載檔案過小（%d bytes），疑似錯誤回應而非實際檔案：%s"
                            .formatted(body == null ? 0 : body.length, url));
        }
        return body;
    }

    /** 對暫時性 server error 固定延遲後重試一次。 */
    private <T> T withOneRetryOnTransientError(String url, Supplier<T> request) {
        try {
            return request.get();
        } catch (HttpServerErrorException exception) {
            if (!isTransientErrorStatus(exception.getStatusCode())) {
                throw exception;
            }
            sleepBeforeRetry(url);
            return request.get();
        }
    }

    /** 判斷狀態碼是否屬於允許單次重試的暫時性 server error。 */
    private static boolean isTransientErrorStatus(HttpStatusCode status) {
        return TRANSIENT_ERROR_STATUS_CODES.contains(status.value());
    }

    /** 在暫時性錯誤後等待固定時間，並正確保留中斷狀態。 */
    private void sleepBeforeRetry(String url) {
        try {
            Thread.sleep(TRANSIENT_ERROR_RETRY_DELAY_MS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ExternalDataException("等待重試間隔時被中斷：" + url, exception);
        }
    }

    /** 依 host 排定下一個可請求時間，避免短時間連續呼叫同一來源。 */
    private synchronized void waitForRateLimit(String url) {
        String host = URI.create(url).getHost();
        long now = System.currentTimeMillis();
        long requestAt = Math.max(now, nextRequestAtMillisByHost.getOrDefault(host, now));
        nextRequestAtMillisByHost.put(host, requestAt + MIN_INTERVAL_MS);
        long delayMs = requestAt - now;
        if (delayMs > 0) {
            try {
                Thread.sleep(delayMs);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new ExternalDataException("等待節流間隔時被中斷：" + url, exception);
            }
        }
    }
}
