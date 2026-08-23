package com.malrota.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.malrota.config.TagoProperties;
import com.malrota.dto.response.BusSchedule;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;

@Component
public class TagoClient {

    private final TagoProperties properties;
    private final RestClient restClient = RestClient.create();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public TagoClient(TagoProperties properties) {
        this.properties = properties;
    }

    // 터미널 이름 → 터미널ID 조회
    public String findTerminalId(String terminalName) {
        String url = properties.baseUrl() + "/GetExpBusTrminlList"
                + "?serviceKey=" + properties.serviceKey()
                + "&terminalNm=" + terminalName
                + "&numOfRows=1&pageNo=1&_type=json";

        String body = restClient.get().uri(url).retrieve().body(String.class);

        try {
            JsonNode item = objectMapper.readTree(body)
                    .path("response").path("body").path("items").path("item");
            // item이 배열이면 첫 번째, 객체면 그대로
            if (item.isArray()) {
                item = item.get(0);
            }
            return item.path("terminalId").asText(null);
        } catch (Exception e) {
            throw new RuntimeException("터미널 조회 실패: " + terminalName, e);
        }
    }

    // 출발/도착 터미널ID + 날짜로 운행편 조회
    public List<BusSchedule> searchBuses(String depId, String arrId, String date) {
        String url = properties.baseUrl() + "/GetStrtpntAlocFndExpbusInfo"
                + "?serviceKey=" + properties.serviceKey()
                + "&depTerminalId=" + depId
                + "&arrTerminalId=" + arrId
                + "&depPlandTime=" + date
                + "&numOfRows=30&pageNo=1&_type=json";

        String body = restClient.get().uri(url).retrieve().body(String.class);

        List<BusSchedule> result = new ArrayList<>();
        try {
            JsonNode items = objectMapper.readTree(body)
                    .path("response").path("body").path("items").path("item");

            if (items.isMissingNode()) {
                return result; // 결과 없음
            }
            // 하나면 객체, 여러 개면 배열로 옴 → 통일
            if (items.isObject()) {
                result.add(toBusSchedule(items));
            } else {
                for (JsonNode node : items) {
                    result.add(toBusSchedule(node));
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("운행편 조회 실패", e);
        }
        return result;
    }

    private BusSchedule toBusSchedule(JsonNode node) {
        return new BusSchedule(
                node.path("routeId").asText(null),
                node.path("gradeNm").asText(null),
                node.path("depPlaceNm").asText(null),
                node.path("arrPlaceNm").asText(null),
                node.path("depPlandTime").asText(null),
                node.path("arrPlandTime").asText(null),
                node.path("charge").asInt(0)
        );
    }
}