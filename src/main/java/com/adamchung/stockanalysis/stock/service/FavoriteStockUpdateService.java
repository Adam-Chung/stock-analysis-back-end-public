package com.adamchung.stockanalysis.stock.service;

import com.adamchung.stockanalysis.stock.dto.FavoriteStockUpdateResponse;
import com.adamchung.stockanalysis.stock.dto.StockSummary;
import com.adamchung.stockanalysis.finmind.FinMindClient;
import com.adamchung.stockanalysis.finmind.dto.FinMindBalanceSheetDto;
import com.adamchung.stockanalysis.targetprice.dto.TargetPriceResponse;
import com.adamchung.stockanalysis.targetprice.service.TargetPriceApplicationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.time.LocalDate;

/** 依收藏順序逐檔同步並彙整目標價摘要。 */
@Service
public class FavoriteStockUpdateService {

    private static final Logger log = LoggerFactory.getLogger(FavoriteStockUpdateService.class);
    private static final int BALANCE_SHEET_LOOKBACK_YEARS = 2;
    private final FavoriteStockService favoriteStockService;
    private final TargetPriceApplicationService targetPriceApplicationService;
    private final FinMindClient finMindClient;

    public FavoriteStockUpdateService(
            FavoriteStockService favoriteStockService,
            TargetPriceApplicationService targetPriceApplicationService,
            FinMindClient finMindClient) {
        this.favoriteStockService = favoriteStockService;
        this.targetPriceApplicationService = targetPriceApplicationService;
        this.finMindClient = finMindClient;
    }

    /**
     * 逐檔更新所有收藏股票；單檔失敗時記錄錯誤並繼續下一檔。
     *
     * @return 依收藏順序排列的更新結果
     */
    public List<FavoriteStockUpdateResponse> refreshAll() {
        List<FavoriteStockUpdateResponse> results = new ArrayList<>();
        for (StockSummary favorite : favoriteStockService.list()) {
            try {
                TargetPriceResponse response = targetPriceApplicationService.refresh(favorite.stockId());
                String latestQuarter = latestBalanceSheetQuarter(favorite.stockId());
                results.add(toResponse(response, latestQuarter));
            } catch (RuntimeException exception) {
                log.warn("Favorite stock refresh failed: stockId={}", favorite.stockId(), exception);
                results.add(new FavoriteStockUpdateResponse(
                        favorite.stockId(), favorite.stockName(), null, null, null, null, null, null,
                        "更新失敗，請稍後重試"));
            }
        }
        return results;
    }

    /** 將目標價結果與最新財報季度組成表格資料。 */
    private FavoriteStockUpdateResponse toResponse(TargetPriceResponse response, String latestQuarter) {
        return new FavoriteStockUpdateResponse(
                response.stockId(), response.stockName(), response.currentPrice(), response.targetPrice(),
                response.tiers().stream().filter(tier -> tier.level() == response.targetPriceLevel())
                        .map(tier -> tier.label().replaceFirst("^\\d+", "")).findFirst().orElse(null),
                response.gapRatio(), formatQuarter(latestQuarter), response.investmentStrategy(), null);
    }

    /** 只查詢 BalanceSheet，取得該股票最新資產負債表季度。 */
    private String latestBalanceSheetQuarter(String stockId) {
        LocalDate now = LocalDate.now();
        return finMindClient.fetchBalanceSheet(stockId, now.minusYears(BALANCE_SHEET_LOOKBACK_YEARS), now).stream()
                .map(FinMindBalanceSheetDto::date).filter(java.util.Objects::nonNull).max(LocalDate::compareTo)
                .map(date -> date.getYear() + "Q" + ((date.getMonthValue() - 1) / 3 + 1)).orElse(null);
    }

    /** 將內部 {@code YYYYQ#} 格式轉成表格使用的 {@code YYYY Q#}。 */
    private String formatQuarter(String quarter) {
        return quarter == null ? null : quarter.replace("Q", " Q");
    }

}
