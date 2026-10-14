package com.adamchung.stockanalysis.externaldata.bank.client;

import com.adamchung.stockanalysis.externaldata.ExternalDataException;
import com.adamchung.stockanalysis.externaldata.bank.dto.NplCoverageFileLink;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** DEC-080：新增的「找到的期數不能太少」合理性檢查。 */
class BankDisclosurePageClientTest {

    @Test
    void parses_links_for_each_distinct_period() {
        String html = htmlWithPeriods(1, 2, 3, 4, 5);

        List<NplCoverageFileLink> links = BankDisclosurePageClient.parseDisclosurePage(html, "http://example.test/page");

        assertThat(links).hasSize(5);
    }

    @Test
    void keeps_only_the_highest_revision_when_a_period_has_a_correction() {
        String html = "<a href=\"" + hrefFor(114, 9, 0) + "\">a</a>"
                + "<a href=\"" + hrefFor(114, 9, 2) + "\">b</a>"
                + "<a href=\"" + hrefFor(114, 9, 1) + "\">c</a>"
                + hrefsFor(1, 2, 3, 4); // 湊滿 MIN_EXPECTED_PERIODS=5 個不同期別，避免被下限擋下

        List<NplCoverageFileLink> links = BankDisclosurePageClient.parseDisclosurePage(html, "http://example.test/page");

        assertThat(links).hasSize(5);
        assertThat(links).filteredOn(l -> l.rocYear() == 114 && l.rocMonth() == 9)
                .hasSize(1)
                .first()
                .satisfies(l -> assertThat(l.url()).contains("(2)"));
    }

    @Test
    void rejects_when_fewer_periods_than_expected_are_found() {
        String html = htmlWithPeriods(1, 2); // 遠低於 MIN_EXPECTED_PERIODS=5

        assertThatThrownBy(() -> BankDisclosurePageClient.parseDisclosurePage(html, "http://example.test/page"))
                .isInstanceOf(ExternalDataException.class)
                .hasMessageContaining("低於預期下限");
    }

    @Test
    void rejects_when_no_link_matches_the_expected_filename_pattern() {
        String html = "<a href=\"https://www.fsc.gov.tw/userfiles/file/something-else.xlsx\">a</a>";

        assertThatThrownBy(() -> BankDisclosurePageClient.parseDisclosurePage(html, "http://example.test/page"))
                .isInstanceOf(ExternalDataException.class)
                .hasMessageContaining("找不到");
    }

    private static String htmlWithPeriods(int... months) {
        return hrefsFor(months);
    }

    private static String hrefsFor(int... months) {
        return IntStream.of(months)
                .mapToObj(m -> "<a href=\"" + hrefFor(114, m, 0) + "\">連結</a>")
                .reduce("", String::concat);
    }

    private static String hrefFor(int rocYear, int rocMonth, int revision) {
        String suffix = revision > 0 ? "(" + revision + ")" : "";
        return "https://www.fsc.gov.tw/userfiles/file/本國銀行資產品質評估分析統計表%d_%02d%s.xlsx"
                .formatted(rocYear, rocMonth, suffix);
    }
}
