package com.adamchung.stockanalysis.healthcheck.definition;

import com.adamchung.stockanalysis.common.HealthCheckMetricKeys.CashFlow;
import com.adamchung.stockanalysis.common.HealthCheckMetricKeys.Operations;
import com.adamchung.stockanalysis.healthcheck.enums.HealthCheckEvalType;
import com.adamchung.stockanalysis.healthcheck.enums.HealthCheckTemplate;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class HealthCheckTemplateDefinitionsTest {

    @Test
    void general_template_has_all_twelve_categories_from_SA04() {
        List<HealthCheckCategoryDefinition> categories =
                HealthCheckTemplateDefinitions.categoriesFor(HealthCheckTemplate.GENERAL);

        assertThat(categories).extracting(HealthCheckCategoryDefinition::key).containsExactly(
                "profit", "reinvest", "core", "nonOperating", "revenueInflation", "cashFlow",
                "debt", "capital", "insider", "payout", "margin", "operations");
    }

    @Test
    void bank_template_removes_categories_not_applicable_to_banks_and_adds_bank_specific_ones() {
        List<HealthCheckCategoryDefinition> categories =
                HealthCheckTemplateDefinitions.categoriesFor(HealthCheckTemplate.BANK);

        assertThat(categories).extracting(HealthCheckCategoryDefinition::key).containsExactly(
                "profit", "cashFlow", "capital", "insider", "payout", "operations",
                "bankRegulatory", "bankFlows", "bankRevenue");

        // SA-03 §2: reinvest/core/nonOperating/revenueInflation/debt/margin 整組不適用銀行股
        assertThat(categories).extracting(HealthCheckCategoryDefinition::key)
                .doesNotContain("reinvest", "core", "nonOperating", "revenueInflation", "debt", "margin");
    }

    @Test
    void categories_shared_between_both_templates_have_identical_metric_definitions_except_bank_gaps() {
        // 股本膨脹率/內行人持有率/盈餘分配率：一般與銀行模板共用同一份細項定義，只有實際輸入
        // 的數值不同，這裡確保沒有意外分岔成兩份定義。
        List<HealthCheckCategoryDefinition> general =
                HealthCheckTemplateDefinitions.categoriesFor(HealthCheckTemplate.GENERAL);
        List<HealthCheckCategoryDefinition> bank =
                HealthCheckTemplateDefinitions.categoriesFor(HealthCheckTemplate.BANK);

        for (String sharedKey : List.of("profit", "capital", "insider", "payout")) {
            HealthCheckCategoryDefinition generalCategory = findByKey(general, sharedKey);
            HealthCheckCategoryDefinition bankCategory = findByKey(bank, sharedKey);
            assertThat(bankCategory).isEqualTo(generalCategory);
        }
    }

    @Test
    void bank_cashFlow_and_operations_omit_metrics_with_no_bank_financial_statement_source() {
        // DEC-055：銀行財報結構沒有 PropertyAndPlantAndEquipment（資本支出）／OperatingIncome，
        // cashFlow.free／operations.operatingIncome 這兩個細項對銀行股永遠算不出來，銀行模板
        // 直接不定義這兩個細項，而不是定義了卻永遠顯示「尚無資料」。其餘細項維持跟一般模板
        // 完全相同的定義（key/name/unit/evalType/threshold），只是少了這兩個。
        List<HealthCheckCategoryDefinition> general =
                HealthCheckTemplateDefinitions.categoriesFor(HealthCheckTemplate.GENERAL);
        List<HealthCheckCategoryDefinition> bank =
                HealthCheckTemplateDefinitions.categoriesFor(HealthCheckTemplate.BANK);

        for (String sharedKey : List.of("cashFlow", "operations")) {
            HealthCheckCategoryDefinition generalCategory = findByKey(general, sharedKey);
            HealthCheckCategoryDefinition bankCategory = findByKey(bank, sharedKey);
            assertThat(bankCategory.metrics()).isSubsetOf(generalCategory.metrics());
        }

        assertThat(findByKey(bank, "cashFlow").metrics())
                .extracting(HealthCheckMetricDefinition::key).doesNotContain(CashFlow.FREE);
        assertThat(findByKey(bank, "operations").metrics())
                .extracting(HealthCheckMetricDefinition::key).doesNotContain(Operations.OPERATING_INCOME);
        assertThat(findByKey(general, "cashFlow").metrics())
                .extracting(HealthCheckMetricDefinition::key).contains(CashFlow.FREE);
        assertThat(findByKey(general, "operations").metrics())
                .extracting(HealthCheckMetricDefinition::key).contains(Operations.OPERATING_INCOME);
    }

    @Test
    void metric_keys_are_globally_unique_within_each_template() {
        for (HealthCheckTemplate template : HealthCheckTemplate.values()) {
            List<String> keys = HealthCheckTemplateDefinitions.categoriesFor(template).stream()
                    .flatMap(category -> category.metrics().stream())
                    .map(HealthCheckMetricDefinition::key)
                    .toList();
            Set<String> uniqueKeys = new java.util.HashSet<>(keys);
            assertThat(uniqueKeys).as("no duplicate metric_key within " + template).hasSameSizeAs(keys);
        }
    }

    @Test
    void metrics_with_a_numeric_threshold_are_never_JUDGMENT_or_BOOLEAN_type() {
        for (HealthCheckTemplate template : HealthCheckTemplate.values()) {
            HealthCheckTemplateDefinitions.categoriesFor(template).stream()
                    .flatMap(category -> category.metrics().stream())
                    .forEach(metric -> {
                        if (metric.threshold() != null) {
                            assertThat(metric.evalType())
                                    .as(metric.key() + " has a threshold so must be GT/LT")
                                    .isIn(HealthCheckEvalType.GREATER_THAN, HealthCheckEvalType.LESS_THAN);
                        }
                    });
        }
    }

    private HealthCheckCategoryDefinition findByKey(List<HealthCheckCategoryDefinition> categories, String key) {
        return categories.stream()
                .filter(category -> category.key().equals(key))
                .collect(Collectors.collectingAndThen(Collectors.toList(), list -> {
                    assertThat(list).hasSize(1);
                    return list.get(0);
                }));
    }
}
