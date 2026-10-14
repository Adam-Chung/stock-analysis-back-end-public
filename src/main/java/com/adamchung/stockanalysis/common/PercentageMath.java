package com.adamchung.stockanalysis.common;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 全系統共用的百分比／捨入計算（DEC-079）。在這之前，{@code percentRatio()}
 * （{@code healthcheck.healthchecksync.calculator.HealthCheckFinancialMetricsCalculator}）、
 * {@code growthRatio()}（{@code MonthRevenueMetricsCalculator}）、{@code round2()}
 * （{@code externaldata.twse.InstitutionalQuarterlyChangeCalculator}）三處各自手刻同一類
 * 「算比率→乘100→四捨五入」邏輯，其中 {@code round2()} 用 {@code double + Math.round}，
 * 是全系統唯一一處沒有用 {@link BigDecimal} 的地方，跟其餘刻意用 BigDecimal 避免二進位浮點
 * 誤差的原則矛盾。
 *
 * <p>放在 {@code common} 底下的理由：這是純數學工具，沒有單一「擁有者」功能——健康檢查、
 * 外部資料同步都各自用得到，不屬於任何一個 feature 套件，放進任一方都會造成單向依賴之外的
 * 一個新的跨套件依賴方向（DEC-081）。
 */
public final class PercentageMath {

    private PercentageMath() {
    }

    /**
     * numerator ÷ denominator × 100，四捨五入到 {@code scale} 位小數。分子/分母缺值、或
     * 分母為0時回傳 {@code null}，不拋例外。刻意先把原始分數捨入到 {@code scale} 位、
     * 才乘以100（不是先算出未捨入的真實百分比再捨入到 {@code scale} 位）——這是既有
     * {@code percentRatio()} 系列 helper 就有的既有行為（雙重捨入），這裡只是把它抽出來
     * 給多處共用，行為本身不變。
     */
    public static BigDecimal ratioAsPercent(BigDecimal numerator, BigDecimal denominator, int scale) {
        if (numerator == null || denominator == null || denominator.signum() == 0) {
            return null;
        }
        BigDecimal ratio = numerator.divide(denominator, scale, RoundingMode.HALF_UP);
        return ratio.multiply(BigDecimal.valueOf(100)).setScale(scale, RoundingMode.HALF_UP);
    }

    /**
     * 對 {@code double} 值四捨五入到指定小數位。用 {@link BigDecimal} 而非
     * {@code Math.round(value * 10^scale) / 10^scale}，避免二進位浮點數在做這種先放大、
     * 取整、再縮小的運算時，對某些十進位無法精確表示的邊界值（例如 x.xx5）產生跟十進位
     * 四捨五入不一致的結果。
     */
    public static double round(double value, int scale) {
        return BigDecimal.valueOf(value).setScale(scale, RoundingMode.HALF_UP).doubleValue();
    }
}
