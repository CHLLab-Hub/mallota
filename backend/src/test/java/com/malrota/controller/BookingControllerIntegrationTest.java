package com.malrota.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "watsonx.enabled=false")
class BookingControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void persists_lists_and_cancels_a_booking_for_its_owner() throws Exception {
        String ownerId = "browser-" + UUID.randomUUID();
        String request = """
                {
                  "ownerId":"%s",
                  "routeId":"route-1",
                  "grade":"우등",
                  "departure":"서울경부",
                  "arrival":"대전복합",
                  "departureTime":"202608280900",
                  "arrivalTime":"202608281030",
                  "charge":16600,
                  "seatNo":"3A, 3B",
                  "passengers":2,
                  "totalFare":33200
                }
                """.formatted(ownerId);

        MvcResult createResult = mockMvc.perform(post("/api/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.bus.departure").value("서울경부"))
                .andExpect(jsonPath("$.passengers").value(2))
                .andExpect(jsonPath("$.totalFare").value(33200))
                .andReturn();

        Matcher idMatcher = Pattern.compile("\\\"id\\\":\\\"([^\\\"]+)\\\"")
                .matcher(createResult.getResponse().getContentAsString());
        if (!idMatcher.find()) {
            throw new AssertionError("예매 생성 응답에 id가 없습니다.");
        }
        String bookingId = idMatcher.group(1);

        mockMvc.perform(get("/api/bookings").param("ownerId", ownerId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(bookingId))
                .andExpect(jsonPath("$[0].seatNo").value("3A, 3B"));

        mockMvc.perform(delete("/api/bookings/{bookingId}", bookingId).param("ownerId", ownerId))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/bookings").param("ownerId", ownerId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void rejects_a_total_fare_that_does_not_match_passengers() throws Exception {
        String request = """
                {
                  "ownerId":"browser-test", "routeId":"route-1", "grade":"우등",
                  "departure":"서울경부", "arrival":"대전복합",
                  "departureTime":"202608280900", "arrivalTime":"202608281030",
                  "charge":16600, "seatNo":"3A", "passengers":2, "totalFare":16600
                }
                """;

        mockMvc.perform(post("/api/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("총 요금이 인원수와 1인 요금에 맞지 않습니다."));
    }
}
