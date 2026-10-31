package com.adamchung.stockanalysis.health;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class LivenessControllerTest {

    /** 存活檢查不需要服務依賴，且只回傳 204 空回應。 */
    @Test
    void liveness_returns_no_content() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new LivenessController()).build();

        mockMvc.perform(get("/api/health/liveness"))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));
    }
}
