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

        // "서울"/"대전"은 둘 다 세부 터미널이 여러 개인 도시라, 실제 피드백에 따라 날짜/시간보다
        // 세부 터미널부터 먼저 확정한다.
        mockMvc.perform(post("/api/conversation/parse")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"서울에서 대전 가고 싶어요\",\"sessionId\":\"" + sessionId + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("COLLECTING_CONDITIONS"))
                .andExpect(jsonPath("$.departure").value("서울"))
                .andExpect(jsonPath("$.arrival").value("대전"))
                .andExpect(jsonPath("$.clarificationPrompt").value("서울 어느 터미널로 원하시나요? 서울경부, 센트럴시티, 동서울 중 편하신 곳을 말씀해 주세요."));

        mockMvc.perform(post("/api/conversation/parse")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"서울경부\",\"sessionId\":\"" + sessionId + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("COLLECTING_CONDITIONS"))
                .andExpect(jsonPath("$.departure").value("서울경부"))
                .andExpect(jsonPath("$.clarificationPrompt").value("대전 어느 터미널로 원하시나요? 대전복합, 대전청사 중 편하신 곳을 말씀해 주세요."));

        mockMvc.perform(post("/api/conversation/parse")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"대전복합\",\"sessionId\":\"" + sessionId + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("COLLECTING_CONDITIONS"))
                .andExpect(jsonPath("$.arrival").value("대전복합"))
                .andExpect(jsonPath("$.clarificationPrompt").value("언제 출발하시나요? '내일 아침', '이번 주말 오후'처럼 날짜와 시간대를 편하게 말씀해 주세요."));

        mockMvc.perform(post("/api/conversation/parse")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"내일 오전 9시에 한 명이고 다리가 불편해서 앞쪽 통로로 갈게요\",\"sessionId\":\"" + sessionId + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("READY_TO_SEARCH"))
                .andExpect(jsonPath("$.date").isNotEmpty())
                .andExpect(jsonPath("$.timePreference").value("MORNING"))
                .andExpect(jsonPath("$.seatPreferences").value(containsInAnyOrder("FRONT", "AISLE")))
                .andExpect(jsonPath("$.accessibilityNeeds[0]").value("WALKING_DIFFICULTY"));
    }
}
