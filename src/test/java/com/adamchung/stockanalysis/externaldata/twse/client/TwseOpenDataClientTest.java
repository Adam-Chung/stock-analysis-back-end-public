package com.adamchung.stockanalysis.externaldata.twse.client;

import com.adamchung.stockanalysis.externaldata.ExternalDataException;
import com.adamchung.stockanalysis.externaldata.twse.dto.DirectorHoldingsRow;
import com.adamchung.stockanalysis.externaldata.twse.dto.TwseCompanyProfileDto;
import com.adamchung.stockanalysis.externaldata.twse.dto.TwseDirectorHoldingDto;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TwseOpenDataClientTest {

    private static final TwseCompanyProfileDto PROFILE =
            new TwseCompanyProfileDto("2884", "玉山金", "13000000000");

    @Test
    void aggregates_shares_across_all_director_and_supervisor_seat_holder_rows_and_computes_percentages() {
        List<TwseDirectorHoldingDto> records = List.of(
                new TwseDirectorHoldingDto("11506", "2884", "玉山金", "董事本人", "王一", "500000000", "50000000"),
                new TwseDirectorHoldingDto("11506", "2884", "玉山金", "監察人本人", "王二", "100000000", "0"),
                // 非董監職稱不應計入
                new TwseDirectorHoldingDto("11506", "2884", "玉山金", "經理人本人", "王三", "999999999", "999999999"));

        DirectorHoldingsRow row = TwseOpenDataClient.aggregateDirectorHoldings("2884", records, PROFILE);

        assertThat(row.stockCode()).isEqualTo("2884");
        assertThat(row.companyName()).isEqualTo("玉山金");
        // (500,000,000 + 100,000,000) / 13,000,000,000 * 100
        assertThat(row.directorShareholdingPercent()).isCloseTo(4.6153846, org.assertj.core.api.Assertions.within(1e-5));
        // 50,000,000 / 600,000,000 * 100
        assertThat(row.directorPledgeRatioPercent()).isCloseTo(8.3333333, org.assertj.core.api.Assertions.within(1e-5));
        assertThat(row.dataDate()).isEqualTo("2026-06");
        assertThat(row.sourceUrl()).isEqualTo(TwseOpenDataClient.DIRECTOR_HOLDINGS_URL);
    }

    @Test
    void does_not_double_count_a_shareholder_that_holds_multiple_director_seats() {
        // DEC-072：真實 1216 統一資料驗證，同一個法人股東（高權投資）拿下 3 席董事時，
        // TWSE 開放資料把它的持股數在每一席各重複列一次，姓名與目前持股完全相同。
        List<TwseDirectorHoldingDto> records = List.of(
                new TwseDirectorHoldingDto("11506", "1216", "統一", "董事長本人", "高權投資股份有限公司", "284330536", "80030000"),
                new TwseDirectorHoldingDto("11506", "1216", "統一", "董事本人", "高權投資股份有限公司", "284330536", "80030000"),
                new TwseDirectorHoldingDto("11506", "1216", "統一", "董事本人", "高權投資股份有限公司", "284330536", "80030000"),
                new TwseDirectorHoldingDto("11506", "1216", "統一", "董事本人", "侯博裕", "141697024", "0"));
        TwseCompanyProfileDto profile = new TwseCompanyProfileDto("1216", "統一", "5682015421");

        DirectorHoldingsRow row = TwseOpenDataClient.aggregateDirectorHoldings("1216", records, profile);

        // (284,330,536 + 141,697,024) / 5,682,015,421 * 100 ≈ 7.5%，不是把高權投資算 3 次的 12.7%
        assertThat(row.directorShareholdingPercent()).isCloseTo(7.497825, org.assertj.core.api.Assertions.within(1e-5));
        // 80,030,000 / (284,330,536 + 141,697,024) * 100
        assertThat(row.directorPledgeRatioPercent()).isCloseTo(18.785170, org.assertj.core.api.Assertions.within(1e-5));
    }

    @Test
    void excludes_法人代表人_rows_since_they_report_the_representative_own_personal_shares() {
        // 「董事之法人代表人」列揭露的是「這席法人董事由哪位自然人代表出席」，「目前持股」是
        // 代表人自己的個人持股，跟法人本身的持股是兩回事，不該併入董監持股比。
        List<TwseDirectorHoldingDto> records = List.of(
                new TwseDirectorHoldingDto("11506", "1216", "統一", "董事長本人", "高權投資股份有限公司", "284330536", "80030000"),
                new TwseDirectorHoldingDto("11506", "1216", "統一", "董事長之法人代表人", "羅智先", "4059095", "0"));
        TwseCompanyProfileDto profile = new TwseCompanyProfileDto("1216", "統一", "5682015421");

        DirectorHoldingsRow row = TwseOpenDataClient.aggregateDirectorHoldings("1216", records, profile);

        // 只算高權投資本人這一列，不含法人代表人羅智先的個人持股
        assertThat(row.directorShareholdingPercent()).isCloseTo(5.004044, org.assertj.core.api.Assertions.within(1e-5));
    }

    @Test
    void pledge_ratio_is_zero_not_NaN_when_total_shares_is_zero() {
        List<TwseDirectorHoldingDto> records = List.of(
                new TwseDirectorHoldingDto("11506", "2884", "玉山金", "董事本人", "王一", "0", "0"));

        DirectorHoldingsRow row = TwseOpenDataClient.aggregateDirectorHoldings("2884", records, PROFILE);

        assertThat(row.directorPledgeRatioPercent()).isZero();
    }

    @Test
    void throws_when_no_row_has_a_director_or_supervisor_seat_holder_title() {
        List<TwseDirectorHoldingDto> records = List.of(
                new TwseDirectorHoldingDto("11506", "2884", "玉山金", "經理人本人", "王一", "1", "0"),
                // 法人代表人列雖然職稱含「董事」，但不是席次本身的持股人，同樣不算數
                new TwseDirectorHoldingDto("11506", "2884", "玉山金", "董事之法人代表人", "王二", "1", "0"));

        assertThatThrownBy(() -> TwseOpenDataClient.aggregateDirectorHoldings("2884", records, PROFILE))
                .isInstanceOf(ExternalDataException.class);
    }
}
