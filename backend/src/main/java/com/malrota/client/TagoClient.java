package com.malrota.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.malrota.config.TagoProperties;
import com.malrota.dto.response.BusSchedule;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class TagoClient {

    private final TagoProperties properties;
    private final RestClient restClient = RestClient.create();
    private final ObjectMapper objectMapper = new ObjectMapper();

    // Map.ofEntries로 10개 초과 오류 해결
    private static final Map<String, String> TERMINAL_MAP = Map.ofEntries(
            Map.entry("서울", "NAEK010"),
            Map.entry("서울경부", "NAEK010"),
            Map.entry("센트럴시티", "NAEK020"),
            Map.entry("동서울", "NAEK030"),
            Map.entry("대전", "NAEK300"),
            Map.entry("대전복합", "NAEK300"),
            Map.entry("부산", "NAEK700"),
            Map.entry("부산노포", "NAEK700"),
            Map.entry("서부산", "NAEK705"),
            Map.entry("대구", "NAEK800"),
            Map.entry("광주", "NAEK500")
    );

    public TagoClient(TagoProperties properties) {
        this.properties = properties;
    }

    /**
     * 터미널 이름 → 터미널ID 조회
     */
    public String findTerminalId(String terminalName) {
        if (terminalName == null || terminalName.isBlank()) return "NAEK010";

        // 1. 사전 매핑 테이블에서 먼저 매칭
        for (Map.Entry<String, String> entry : TERMINAL_MAP.entrySet()) {
            if (terminalName.contains(entry.getKey())) {
                return entry.getValue();
            }
        }

        // 2. 서비스키가 없으면 기본값 반환
        if (properties.serviceKey() == null || properties.serviceKey().isBlank()) {
            return "NAEK010";
        }

        try {
            String url = properties.baseUrl() + "/GetExpBusTrminlList"
                    + "?serviceKey=" + properties.serviceKey()
                    + "&terminalNm=" + terminalName
                    + "&numOfRows=1&pageNo=1&_type=json";

            String body = restClient.get().uri(URI.create(url)).retrieve().body(String.class);
            JsonNode item = objectMapper.readTree(body)
                    .path("response").path("body").path("items").path("item");

            if (item.isArray() && !item.isEmpty()) {
                item = item.get(0);
            }
            String id = item.path("terminalId").asText(null);
            return (id != null) ? id : "NAEK010";
        } catch (Exception e) {
            log.warn("TAGO 터미널 API 호출 실패({}), 기본 매핑값 적용", e.getMessage());
            return "NAEK010";
        }
    }

    /**
     * 출발/도착 터미널ID + 날짜로 운행편 조회
     */
    public List<BusSchedule> searchBuses(String depId, String arrId, String date) {
        String cleanDate = (date != null) ? date.replace("-", "") : "20260825";

        // 서비스키가 있으면 실제 TAGO API 호출 시도
        if (properties.serviceKey() != null && !properties.serviceKey().isBlank()) {
            try {
                String url = properties.baseUrl() + "/GetStrtpntAlocFndExpbusInfo"
                        + "?serviceKey=" + properties.serviceKey()
                        + "&depTerminalId=" + depId
                        + "&arrTerminalId=" + arrId
                        + "&depPlandTime=" + cleanDate
                        + "&numOfRows=30&pageNo=1&_type=json";

                String body = restClient.get().uri(URI.create(url)).retrieve().body(String.class);
                List<BusSchedule> result = new ArrayList<>();
                JsonNode items = objectMapper.readTree(body)
                        .path("response").path("body").path("items").path("item");

                if (!items.isMissingNode()) {
                    if (items.isObject()) {
                        result.add(toBusSchedule(items));
                    } else {
                        for (JsonNode node : items) {
                            result.add(toBusSchedule(node));
                        }
                    }
                    if (!result.isEmpty()) return result;
                }
            } catch (Exception e) {
                log.warn("TAGO 버스 API 호출 실패({}), Mock 시간표 반환", e.getMessage());
            }
        }

        // 키가 없거나 API 호출 실패 시 반환할 Mock 시간표 데이터
        return getMockSchedules(cleanDate);
    }

    private BusSchedule toBusSchedule(JsonNode node) {
        return new BusSchedule(
                node.path("routeId").asText("ROUTE-01"),
                node.path("gradeNm").asText("우등"),
                node.path("depPlaceNm").asText("서울경부"),
                node.path("arrPlaceNm").asText("대전복합"),
                node.path("depPlandTime").asText("202608250900"),
                node.path("arrPlandTime").asText("202608251030"),
                node.path("charge").asInt(16000)
        );
    }

    /** 해커톤 시연용 Mock 버스 시간표 */
    private List<BusSchedule> getMockSchedules(String date) {
        return List.of(
                new BusSchedule("R01", "우등", "서울경부", "대전복합", date + "0700", date + "0830", 16000),
                new BusSchedule("R02", "우등", "서울경부", "대전복합", date + "0800", date + "0930", 16000),
                new BusSchedule("R03", "우등", "서울경부", "대전복합", date + "0900", date + "1030", 16000),
                new BusSchedule("R04", "일반", "서울경부", "대전복합", date + "1000", date + "1130", 11000),
                new BusSchedule("R05", "프리미엄", "서울경부", "대전복합", date + "1130", date + "1300", 20800),
                new BusSchedule("R06", "우등", "서울경부", "대전복합", date + "1400", date + "1530", 16000),
                new BusSchedule("R07", "우등", "서울경부", "대전복합", date + "1900", date + "2030", 16000),
                new BusSchedule("R08", "우등", "서울경부", "대전복합", date + "2100", date + "2230", 16000)
        );
    }
}
