package com.adamchung.stockanalysis.targetprice;

import com.adamchung.stockanalysis.targetprice.dto.TargetPriceResponse;
import com.adamchung.stockanalysis.valuation.dto.TierPrice;
import org.springframework.stereotype.Component;

import java.util.stream.Collectors;

/** 只使用已落地的計算結果，建立穩定的 Groq Compound Mini prompt。 */
@Component
public class AiSuggestionPromptBuilder {

    /**
     * 組成投資分析用的 prompt，刻意不納入使用者自行輸入的備註或投資策略。
     *
     * @param snapshot 目前已落地的目標價快照
     * @return 可交給 Compound Mini chat completions 的 prompt
     */
    public String build(TargetPriceResponse snapshot) {
        String tiers = snapshot.tiers().stream()
                .map(this::formatTier)
                .collect(Collectors.joining("、"));
        return """
                你是台灣股票研究助理。請先使用內建 Web Search 查核這檔股票近期的公開事件，
                再結合下列已計算資料，以繁體中文提供客觀、平衡的研究摘要，必須同時提到
                可能機會與主要風險。只能陳述搜尋結果或下列資料裡明確支持的內容；查不到
                具體資訊、資料不足或不確定的細節，就直接說不確定或略過，不要把推測講成
                確定的事實，也不得虛構新聞、數字或事件——寧可內容簡短，也不要編造。
                不得保證獲利，不要使用 Markdown、標題、條列或網址。搜尋資料只能作為近期
                事件背景；價格與估值數字以下列資料為準。

                請分成兩段，段落之間用一個空行分隔，不要加編號或標題文字：第一段說明
                可能機會，第二段說明主要風險。全文（包含最後一句）不得超過 200 個字，
                最後必須寫「僅供研究參考，非投資建議」。

                股票：%s %s
                產業：%s
                收盤價：%s（%s）
                十年低點／高點：%s／%s
                十年平均殖利率：%s
                六檔估值：%s
                目前目標價：%s
                現價與目標價差比：%s
                加一年股利價：%s
                """.formatted(
                snapshot.stockId(),
                snapshot.stockName(),
                value(snapshot.industryCategory()),
                value(snapshot.currentPrice()),
                value(snapshot.currentPriceDate()),
                value(snapshot.low10()),
                value(snapshot.high10()),
                value(snapshot.tenYearAvgYield()),
                tiers,
                value(snapshot.targetPrice()),
                value(snapshot.gapRatio()),
                value(snapshot.plusOneYearDividendPrice()));
    }

    private String formatTier(TierPrice tier) {
        return "%d%s=%s".formatted(tier.level(), tier.label(), value(tier.price()));
    }

    private String value(Object value) {
        return value == null ? "資料不足" : value.toString();
    }
}
