package com.malrota.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.hamcrest.Matchers.containsInAnyOrder;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "watsonx.enabled=false")
class ConversationControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void accumulates_conditions_across_requests_and_returns_clarification() throws Exception {
        String sessionId = "nlu-integration-test";

        mockMvc.perform(post("/api/conversation/parse")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"서울에서 대전 가고 싶어요\",\"sessionId\":\"" + sessionId + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("COLLECTING_CONDITIONS"))
                .andExpect(jsonPath("$.departure").value("서울"))
                .andExpect(jsonPath("$.arrival").value("대전"))
                .andExpect(jsonPath("$.clarificationPrompt").value("언제 출발하시나요? 오늘, 내일, 이번 주 토요일처럼 말씀해 주세요."));

        mockMvc.perform(post("/api/conversation/parse")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"내일 오전에 다리가 불편해서 앞쪽 통로로 갈게요\",\"sessionId\":\"" + sessionId + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("READY_TO_SEARCH"))
                .andExpect(jsonPath("$.date").isNotEmpty())
                .andExpect(jsonPath("$.timePreference").value("MORNING"))
                .andExpect(jsonPath("$.seatPreferences").value(containsInAnyOrder("FRONT", "AISLE")))
                .andExpect(jsonPath("$.accessibilityNeeds[0]").value("WALKING_DIFFICULTY"));
    }
}
