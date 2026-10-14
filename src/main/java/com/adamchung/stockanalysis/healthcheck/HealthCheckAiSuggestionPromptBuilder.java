package com.adamchung.stockanalysis.healthcheck;

import com.adamchung.stockanalysis.healthcheck.dto.HealthCheckResponse;
import com.adamchung.stockanalysis.healthcheck.enums.HealthCheckStatus;
import com.adamchung.stockanalysis.healthcheck.enums.HealthCheckTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 只使用已落地的體質檢視資料建立穩定的 Groq Compound Mini prompt（比照
 * {@code targetprice.AiSuggestionPromptBuilder}）。刻意排除使用者自行輸入的
 * {@code profile.customNote} 與各細項筆記，避免 prompt-injection（比照 DEC-016）。
 */
@Component
public class HealthCheckAiSuggestionPromptBuilder {

    /**
     * 組成投資分析用的 prompt：本季不達標細項清單＋整體達標統計＋股價趨勢背景，
     * 要求 Groq 用內建 Web Search 查證不達標原因並給出投資與財報體質兩個結論。
     *
     * @param snapshot 目前已落地的企業體質檢視快照
     * @return 可交給 Compound Mini chat completions 的 prompt
     */
    public String build(HealthCheckResponse snapshot) {
        Overview overview = summarize(snapshot);
        String failingList = overview.failingItems().isEmpty()
                ? "（本季已判定項目全數達標，無不達標細項）"
                : overview.failingItems().stream().collect(Collectors.joining("；"));
        String priceTrend = summarizePriceTrend(snapshot.priceTrend());

        return """
                你是台灣股票研究助理。這支股票本季「企業體質檢視」出現下列不達標細項，
                請先使用內建 Web Search 查證這些不達標項目可能的具體原因（近期新聞、公開資訊），
                再綜合搜尋到的時事、下列已計算的基本面數據，以及股價趨勢等技術面背景（可用
                Web Search 補充近期技術面觀察），以繁體中文給出客觀、平衡的研究摘要，
                並明確表達兩個結論：這支股票值不值得投資、財務報表體質有沒有問題。
                只能陳述搜尋結果或下列資料裡明確支持的內容；查不到具體原因、資料不足或
                不確定的細節，就直接說不確定或略過，不要把推測講成確定的事實，也不得
                虛構新聞、數字或事件——寧可內容簡短，也不要編造。不得保證獲利，不要使用
                Markdown、標題、條列或網址。搜尋資料只能作為原因查證與近期背景；體質數據
                以下列資料為準。使用者自行輸入的筆記/備註不在下列資料中，不需要也不應該
                引用未提供的內容。

                請分成三段，段落之間用一個空行分隔，不要加編號或標題文字：第一段說明
                不達標細項的可能原因；第二段給出「值不值得投資」的結論；第三段給出
                「財報體質有沒有問題」的結論。全文（包含最後一句）不得超過 400 個字，
                最後必須寫「僅供研究參考，非投資建議」。

                股票：%s %s
                產業：%s（%s模板）
                收盤價：%s
                整體達標情形：%d 項中 %d 項達標
                本季不達標細項：%s
                股價趨勢背景（近年年均價／年均PER／年均PBR，新到舊）：%s
                """.formatted(
                snapshot.stockId(),
                snapshot.stockName(),
                value(snapshot.industryCategory()),
                templateLabel(snapshot.template()),
                value(snapshot.currentPrice()),
                overview.evaluated(),
                overview.pass(),
                failingList,
                priceTrend);
    }

    private Overview summarize(HealthCheckResponse snapshot) {
        int pass = 0;
        int evaluated = 0;
        List<String> failingItems = new ArrayList<>();
        for (HealthCheckResponse.CategoryView category : snapshot.categories()) {
            for (HealthCheckResponse.MetricView metric : category.metrics()) {
                if (metric.periods().isEmpty()) {
                    continue;
                }
                HealthCheckResponse.PeriodValueView latest = metric.periods().getFirst();
                if (latest.status() == HealthCheckStatus.PASS || latest.status() == HealthCheckStatus.FAIL) {
                    evaluated++;
                    if (latest.status() == HealthCheckStatus.PASS) {
                        pass++;
                    }
                }
                if (latest.status() == HealthCheckStatus.FAIL) {
                    failingItems.add(formatFailingItem(category, metric, latest));
                }
            }
        }
        return new Overview(pass, evaluated, failingItems);
    }

    private String formatFailingItem(
            HealthCheckResponse.CategoryView category,
            HealthCheckResponse.MetricView metric,
            HealthCheckResponse.PeriodValueView latest) {
        return "%s／%s：%s（門檻%s，%s）".formatted(
                category.name(),
                metric.name(),
                value(latest.value()) + value(metric.unit()),
                value(metric.thresholdLabel()),
                latest.period());
    }

    private String summarizePriceTrend(List<HealthCheckResponse.YearlyPriceTrendView> priceTrend) {
        if (priceTrend == null || priceTrend.isEmpty()) {
            return "（無年度股價趨勢資料）";
        }
        return priceTrend.stream()
                .limit(3)
                .map(row -> "%d年均價%s/PER%s/PBR%s".formatted(
                        row.year(), value(row.avgClosePrice()), value(row.avgPer()), value(row.avgPbr())))
                .collect(Collectors.joining("、"));
    }

    private String templateLabel(HealthCheckTemplate template) {
        return template == HealthCheckTemplate.BANK ? "銀行" : "一般";
    }

    private String value(Object value) {
        return value == null ? "資料不足" : value.toString();
    }

    private record Overview(int pass, int evaluated, List<String> failingItems) {
    }
}
