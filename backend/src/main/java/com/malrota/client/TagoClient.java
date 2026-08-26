package com.malrota.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.malrota.config.TagoProperties;
import com.malrota.dto.response.BusSchedule;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.util.*;

@Slf4j
@Component
public class TagoClient {

    private final TagoProperties properties;
    private final RestClient restClient = RestClient.create();
    private final ObjectMapper objectMapper = new ObjectMapper();

    // 전국 복수 세부 터미널 및 별칭 전체 매핑 테이블 (TAGO 고속버스 터미널 ID)
    private static final Map<String, String> TERMINAL_MAP = new LinkedHashMap<>();
    // 역방향 ID -> 터미널명 매핑 (Mock 생성 및 로깅용)
    private static final Map<String, String> ID_TO_NAME_MAP = new LinkedHashMap<>();
    // 대표 터미널명 -> 소속 도시 (다중 터미널 도시 판별 및 반문 생성용)
    private static final Map<String, String> CANONICAL_TO_CITY = new LinkedHashMap<>();
    // 도시 -> 소속 터미널 목록
    private static final Map<String, List<String>> CITY_TERMINALS = new LinkedHashMap<>();

    static {
        // [서울권]
        register("NAEK010", "서울", "서울경부", "강남", "고터", "강남고속", "서울고속");
        register("NAEK020", "서울", "센트럴시티", "센트럴", "강남호남", "호남선");
        register("NAEK030", "서울", "동서울", "강변");
        register("NAEK040", "서울", "서울남부", "남부터미널");

        // [대구권]
        register("NAEK801", "대구", "동대구", "동대구복합", "동대구환승센터", "대구고속");
        register("NAEK803", "대구", "서대구", "서대구고속", "만평");
        register("NAEK805", "대구", "대구북부", "북부정류장");
        register("NAEK807", "대구", "대구서부", "서부정류장");

        // [부산권]
        register("NAEK700", "부산", "부산종합", "부산", "부산노포", "노포", "노포동", "부산고속");
        register("NAEK703", "부산", "부산서부", "서부산", "사상", "사상터미널");
        register("NAEK705", "부산", "해운대", "해운대터미널");

        // [대전권]
        register("NAEK300", "대전", "대전복합", "동대전", "대전터미널");
        register("NAEK310", "대전", "유성고속", "유성", "유성터미널", "충남대");
        register("NAEK305", "대전", "대전청사", "정부청사", "둔산");

        // [광주권]
        register("NAEK500", "광주", "광주종합", "유스퀘어", "광주고속", "광천동");
        register("NAEK505", "광주", "광주송정", "송정");

        // [인천/경기권]
        register("NAEK100", "인천", "인천종합", "인천", "인천터미널", "관교동");
        register("NAEK110", "수원", "수원종합", "수원", "수원터미널");
        register("NAEK115", "수원", "서수원");
        register("NAEK120", "성남", "성남종합", "성남", "야탑", "분당");

        // [충청/전라/강원/경상권]
        register("NAEK320", "청주", "청주고속", "청주", "가경동");
        register("NAEK325", "청주", "북청주", "청주시외");
        register("NAEK340", "천안", "천안고속", "천안", "천안터미널");
        register("NAEK602", "전주", "전주고속", "전주", "전주터미널");
        register("NAEK200", "강릉", "강릉고속", "강릉", "강릉터미널");
        register("NAEK210", "원주", "원주고속", "원주", "원주터미널");
        register("NAEK230", "속초", "속초고속", "속초", "속초터미널");
        register("NAEK820", "포항", "포항고속", "포항", "포항터미널");
        register("NAEK710", "창원", "창원고속", "창원");
        register("NAEK715", "마산", "마산고속", "마산");
        register("NAEK560", "완도", "완도", "완도터미널");
    }

    private static void register(String id, String city, String canonicalName, String... aliases) {
        TERMINAL_MAP.put(canonicalName, id);
        ID_TO_NAME_MAP.put(id, canonicalName);
        CANONICAL_TO_CITY.put(canonicalName, city);
        CITY_TERMINALS.computeIfAbsent(city, k -> new ArrayList<>()).add(canonicalName);
        for (String alias : aliases) {
            TERMINAL_MAP.put(alias, id);
        }
    }

    public TagoClient(TagoProperties properties) {
        this.properties = properties;
    }

    /**
     * 터미널 이름 → TAGO 터미널ID 조회 (1순위 완전일치 -> 2순위 포함일치 -> 3순위 API 검색)
     */
    public String findTerminalId(String terminalName) {
        if (terminalName == null || terminalName.isBlank()) return "NAEK010";
        String clean = terminalName.trim().replaceAll("\\s+", "");

        String matched = matchTerminalId(clean);
        if (matched != null) return matched;

        // 3. 서비스키가 있으면 실제 TAGO 터미널 목록 검색 API 호출
        if (properties.serviceKey() != null && !properties.serviceKey().isBlank()) {
            try {
                String url = properties.baseUrl() + "/GetExpBusTrminlList"
                        + "?serviceKey=" + properties.serviceKey()
                        + "&terminalNm=" + clean
                        + "&numOfRows=1&pageNo=1&_type=json";

                String body = restClient.get().uri(URI.create(url)).retrieve().body(String.class);
                JsonNode item = objectMapper.readTree(body)
                        .path("response").path("body").path("items").path("item");

                if (item.isArray() && !item.isEmpty()) {
                    item = item.get(0);
                }
                String id = item.path("terminalId").asText(null);
                if (id != null && !id.isBlank()) return id;
            } catch (Exception e) {
                log.warn("TAGO 터미널 API 호출 실패({}), 기본값 적용", e.getMessage());
            }
        }

        return null;
    }

    /** 완전 일치 -> 포함 일치 순으로 터미널 ID 탐색 (매칭 안 되면 null) */
    private static String matchTerminalId(String clean) {
        if (clean == null || clean.isBlank()) return null;
        if (TERMINAL_MAP.containsKey(clean)) return TERMINAL_MAP.get(clean);
        for (Map.Entry<String, String> entry : TERMINAL_MAP.entrySet()) {
            if (clean.contains(entry.getKey()) || entry.getKey().contains(clean)) {
                return entry.getValue();
            }
        }
        return null;
    }

    /** 텍스트에서 정식 터미널명을 찾아 반환 (매칭 없으면 null — findTerminalId와 달리 기본값으로 대체하지 않음) */
    public static String resolveCanonicalName(String rawText) {
        if (rawText == null || rawText.isBlank()) return null;
        String clean = rawText.trim().replaceAll("\\s+", "");
        String id = matchTerminalId(clean);
        return id == null ? null : ID_TO_NAME_MAP.get(id);
    }

    /** 정식 터미널명이 속한 도시 */
    public static String cityOf(String canonicalName) {
        return CANONICAL_TO_CITY.get(canonicalName);
    }

    /** 도시에 속한 정식 터미널명 목록 */
    public static List<String> terminalsInCity(String city) {
        return CITY_TERMINALS.getOrDefault(city, List.of());
    }

    /** 도시에 세부 터미널이 2개 이상인지 (반문이 필요한 도시인지) */
    public static boolean isMultiTerminalCity(String city) {
        return terminalsInCity(city).size() > 1;
    }

    /** 등록된 모든 정식 터미널명 + 별칭 (정규식 생성용) */
    public static Set<String> allNamesAndAliases() {
        return TERMINAL_MAP.keySet();
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

        // 키가 없거나 API 호출 실패 시 실제 출발지/도착지에 맞춘 Mock 시간표 반환
        return getMockSchedules(depId, arrId, cleanDate);
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

    /**
     * 사용자가 요청한 실제 출발지/도착지 명칭에 맞춘 동적 Mock 시간표 생성
     */
    private List<BusSchedule> getMockSchedules(String depId, String arrId, String date) {
        String depName = ID_TO_NAME_MAP.getOrDefault(depId, "서울경부");
        String arrName = ID_TO_NAME_MAP.getOrDefault(arrId, "대전복합");

        return List.of(
                new BusSchedule("R01", "우등", depName, arrName, date + "0630", date + "0800", 16000),
                new BusSchedule("R02", "우등", depName, arrName, date + "0730", date + "0900", 16000),
                new BusSchedule("R03", "일반", depName, arrName, date + "0830", date + "1000", 11000),
                new BusSchedule("R04", "우등", depName, arrName, date + "0900", date + "1030", 16000),
                new BusSchedule("R05", "프리미엄", depName, arrName, date + "1030", date + "1200", 20800),
                new BusSchedule("R06", "우등", depName, arrName, date + "1200", date + "1330", 16000),
                new BusSchedule("R07", "일반", depName, arrName, date + "1330", date + "1500", 11000),
                new BusSchedule("R08", "우등", depName, arrName, date + "1500", date + "1630", 16000),
                new BusSchedule("R09", "프리미엄", depName, arrName, date + "1630", date + "1800", 20800),
                new BusSchedule("R10", "우등", depName, arrName, date + "1800", date + "1930", 16000),
                new BusSchedule("R11", "우등", depName, arrName, date + "1930", date + "2100", 16000),
                new BusSchedule("R12", "우등", depName, arrName, date + "2100", date + "2230", 16000)
        );
    }
}