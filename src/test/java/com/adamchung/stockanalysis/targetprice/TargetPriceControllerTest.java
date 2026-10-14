package com.adamchung.stockanalysis.targetprice;

import com.adamchung.stockanalysis.targetprice.dto.TargetPriceResponse;
import com.adamchung.stockanalysis.targetprice.dto.TargetPriceSettingsRequest;
import com.adamchung.stockanalysis.targetprice.service.AiSuggestionService;
import com.adamchung.stockanalysis.targetprice.service.TargetPriceApplicationService;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TargetPriceControllerTest {

    private final TargetPriceApplicationService applicationService =
            mock(TargetPriceApplicationService.class);
    private final AiSuggestionService aiSuggestionService = mock(AiSuggestionService.class);
    private final TargetPriceController controller =
            new TargetPriceController(applicationService, aiSuggestionService);

    @Test
    void get_delegates_stock_id_to_application_service() {
        TargetPriceResponse expected = mock(TargetPriceResponse.class);
        when(applicationService.get("2330")).thenReturn(expected);

        assertThat(controller.get("2330")).isSameAs(expected);
        verify(applicationService).get("2330");
    }

    @Test
    void update_settings_delegates_request_without_refreshing_market_data() {
        TargetPriceSettingsRequest request =
                new TargetPriceSettingsRequest("長期持有", 5, 5, "設定備註", 3);
        TargetPriceResponse expected = mock(TargetPriceResponse.class);
        when(applicationService.updateSettings("2330", request)).thenReturn(expected);

        assertThat(controller.updateSettings("2330", request)).isSameAs(expected);
        verify(applicationService).updateSettings("2330", request);
    }

    @Test
    void refresh_delegates_to_manual_update_use_case() {
        TargetPriceResponse expected = mock(TargetPriceResponse.class);
        when(applicationService.refresh("2330")).thenReturn(expected);

        assertThat(controller.refresh("2330")).isSameAs(expected);
        verify(applicationService).refresh("2330");
    }

    @Test
    void generate_ai_suggestion_delegates_without_refreshing_market_data() {
        TargetPriceResponse.AiSuggestionView expected =
                new TargetPriceResponse.AiSuggestionView("僅供研究參考，非投資建議", null);
        when(aiSuggestionService.generate("2330")).thenReturn(expected);

        assertThat(controller.generateAiSuggestion("2330")).isSameAs(expected);
        verify(aiSuggestionService).generate("2330");
    }
}
