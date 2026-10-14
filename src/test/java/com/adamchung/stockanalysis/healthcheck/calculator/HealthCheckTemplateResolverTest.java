package com.adamchung.stockanalysis.healthcheck.calculator;

import org.junit.jupiter.api.Test;
import com.adamchung.stockanalysis.healthcheck.enums.HealthCheckTemplate;
import static org.assertj.core.api.Assertions.assertThat;

class HealthCheckTemplateResolverTest {

    @Test
    void resolves_bank_template_for_known_financial_keywords() {
        assertThat(HealthCheckTemplateResolver.resolve("銀行業")).isEqualTo(HealthCheckTemplate.BANK);
        assertThat(HealthCheckTemplateResolver.resolve("金融業（金控）")).isEqualTo(HealthCheckTemplate.BANK);
        assertThat(HealthCheckTemplateResolver.resolve("人壽保險")).isEqualTo(HealthCheckTemplate.BANK);
        assertThat(HealthCheckTemplateResolver.resolve("證券業")).isEqualTo(HealthCheckTemplate.BANK);
    }

    @Test
    void resolves_general_template_for_everything_else() {
        assertThat(HealthCheckTemplateResolver.resolve("食品工業")).isEqualTo(HealthCheckTemplate.GENERAL);
        assertThat(HealthCheckTemplateResolver.resolve("半導體業")).isEqualTo(HealthCheckTemplate.GENERAL);
    }

    @Test
    void resolves_general_template_when_industry_category_is_null() {
        assertThat(HealthCheckTemplateResolver.resolve(null)).isEqualTo(HealthCheckTemplate.GENERAL);
    }
}
