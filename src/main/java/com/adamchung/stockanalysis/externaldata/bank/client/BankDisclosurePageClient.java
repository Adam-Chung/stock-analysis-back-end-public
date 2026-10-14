package com.adamchung.stockanalysis.externaldata.bank.client;

import com.adamchung.stockanalysis.externaldata.ExternalDataException;
import com.adamchung.stockanalysis.externaldata.ExternalDataHttpClient;
import com.adamchung.stockanalysis.externaldata.bank.dto.NplCoverageFileLink;

import org.springframework.stereotype.Service;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 銀行局「本國銀行資產品質評估分析統計表」揭露頁面連結清單。移植自
 * {@code src/clients/bank-disclosure-page.ts}——同一期若有修正版（檔名帶 {@code (n)}），
 * 取修正編號最大的一份。
 *
 * <p>DEC-080：補上「找到的期數不能太少」的合理性檢查——這個 client 是正則爬 HTML 找連結，
 * 原本只檢查「有沒有找到任何連結」，網站改版把 {@code href}／檔名格式改掉時，正則可能只
 * 意外命中極少數連結而不是完全零命中，這種情況原本不會被抓到。呼叫端
 * （{@code ExternalHealthDataSyncService}）固定只取最近5季，正常情況下頁面應該長期保留
 * 遠多於5期的歷史檔案，用這個當下限。
 */
@Service
public class BankDisclosurePageClient {

    private static final String DISCLOSURE_PAGE_URL =
            "https://www.banking.gov.tw/ch/home.jsp?id=591&parentpath=0%2C590&mcustomize=multimessage_view.jsp"
                    + "&dataserno=201202130001&dtable=Disclosure";
    private static final Pattern HREF_PATTERN =
            Pattern.compile("href=\"(https://www\\.fsc\\.gov\\.tw/userfiles/file/[^\"]*\\.xlsx)\"");
    private static final Pattern FILENAME_PATTERN =
            Pattern.compile("本國銀行資產品質評估分析統計表(\\d{3})_(\\d{2})(?:\\((\\d+)\\))?\\.xlsx$");
    private static final int MIN_EXPECTED_PERIODS = 5;

    private final ExternalDataHttpClient httpClient;

    public BankDisclosurePageClient(ExternalDataHttpClient httpClient) {
        this.httpClient = httpClient;
    }

    public List<NplCoverageFileLink> listNplCoverageFileLinks() {
        String html = httpClient.getText(DISCLOSURE_PAGE_URL);
        return parseDisclosurePage(html, DISCLOSURE_PAGE_URL);
    }

    static List<NplCoverageFileLink> parseDisclosurePage(String html, String sourceUrl) {
        Matcher hrefMatcher = HREF_PATTERN.matcher(html);

        record Candidate(
                NplCoverageFileLink link, // 檔案連結資訊
                int revision // 修正版編號
        ) {
        }
        Map<String, Candidate> byPeriod = new LinkedHashMap<>();

        while (hrefMatcher.find()) {
            String url = hrefMatcher.group(1).replace("&amp;", "&");
            String decoded = URLDecoder.decode(url, StandardCharsets.UTF_8);
            Matcher filenameMatcher = FILENAME_PATTERN.matcher(decoded);
            if (!filenameMatcher.find()) {
                continue;
            }
            int rocYear = Integer.parseInt(filenameMatcher.group(1));
            int rocMonth = Integer.parseInt(filenameMatcher.group(2));
            int revision = filenameMatcher.group(3) != null ? Integer.parseInt(filenameMatcher.group(3)) : 0;
            String key = rocYear + "-" + rocMonth;

            Candidate existing = byPeriod.get(key);
            if (existing == null || revision > existing.revision()) {
                byPeriod.put(key, new Candidate(new NplCoverageFileLink(url, rocYear, rocMonth), revision));
            }
        }

        List<NplCoverageFileLink> links = new ArrayList<>();
        for (Candidate candidate : byPeriod.values()) {
            links.add(candidate.link());
        }
        if (links.isEmpty()) {
            throw new ExternalDataException("銀行局揭露頁面找不到資產品質評估分析統計表連結：" + sourceUrl);
        }
        if (links.size() < MIN_EXPECTED_PERIODS) {
            throw new ExternalDataException(
                    "銀行局揭露頁面只找到 " + links.size() + " 個資產品質評估分析統計表連結，低於預期下限 "
                            + MIN_EXPECTED_PERIODS + "，疑似頁面格式已變更：" + sourceUrl);
        }
        return links;
    }
}
