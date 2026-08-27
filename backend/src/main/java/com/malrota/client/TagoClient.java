package com.malrota.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.malrota.config.TagoProperties;
import com.malrota.dto.response.BusSchedule;
import com.malrota.util.KoreanVowelFold;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.util.*;

@Slf4j
@Component
public class TagoClient {

    private final TagoProperties properties;
    // 타임아웃을 안 걸어두면 TAGO 서버가 느려질 때(예: 짧은 시간 안에 요청을 몰아서 보냈을 때) 요청이
    // 무한정 걸려서, 프론트가 30초 뒤 자체적으로 요청을 끊을 때까지 화면이 멈춰버린다. IBM STT/TTS
    // 클라이언트와 같은 이유로 연결/응답 각각 상한을 둬서 실패라도 빨리 나게 한다.
    private final RestClient restClient = RestClient.builder().requestFactory(timeoutRequestFactory()).build();
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static SimpleClientHttpRequestFactory timeoutRequestFactory() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5_000);
        factory.setReadTimeout(8_000);
        return factory;
    }

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
        register("NAEK010", "서울", "서울경부", "강남", "고터", "강남고속", "서울고속", "고속터미널");
        register("NAEK021", "서울", "센트럴시티", "센트럴", "강남호남", "호남선");
        register("NAEK032", "서울", "동서울", "강변");

        // [대구권]
        register("NAEK801", "대구", "동대구", "동대구복합", "동대구환승센터", "대구고속");
        register("NAEK805", "대구", "서대구", "서대구고속", "만평", "대구북부", "북부정류장");
        register("NAEK807", "대구", "대구용계", "대구서부", "서부정류장");

        // [부산권]
        register("NAEK700", "부산", "부산종합", "부산", "부산노포", "노포", "노포동", "부산고속", "해운대", "해운대터미널");
        register("NAEK703", "부산", "부산서부", "서부산", "사상", "사상터미널");

        // [대전권]
        register("NAEK300", "대전", "대전복합", "동대전", "대전터미널");
        register("NAEK305", "대전", "대전청사", "정부청사", "둔산");

        // [광주권]
        register("NAEK500", "광주", "광주종합", "광주", "유스퀘어", "광주고속", "광천동");

        // [인천/경기권]
        register("NAEK100", "인천", "인천종합", "인천", "인천터미널", "관교동");
        register("NAEK120", "성남", "성남종합", "성남", "야탑", "분당");

        // [충청/전라/강원/경상권]
        register("NAEK400", "청주", "청주고속", "청주", "가경동");
        register("NAEK310", "천안", "천안고속", "천안", "천안터미널");
        register("NAEK602", "전주", "전주고속", "전주", "전주터미널");
        register("NAEK200", "강릉", "강릉고속", "강릉", "강릉터미널");
        register("NAEK240", "원주", "원주고속", "원주", "원주터미널");
        register("NAEK230", "속초", "속초고속", "속초", "속초터미널");
        register("NAEK830", "포항", "포항고속", "포항", "포항터미널");
        register("NAEK710", "창원", "창원고속", "창원");
        register("NAEK705", "마산", "마산고속", "마산");
        register("NAEK715", "울산", "울산고속", "울산", "울산터미널");
        register("NAEK250", "춘천", "춘천고속", "춘천", "춘천터미널");
        register("NAEK352", "세종", "세종터미널", "세종", "세종청사");
        register("NAEK722", "진주", "진주고속", "진주", "진주터미널");
        register("NAEK510", "여수", "여수고속", "여수", "여수터미널");
        register("NAEK515", "순천", "순천고속", "순천", "순천터미널");
        register("NAEK505", "목포", "목포고속", "목포", "목포터미널");
        register("NAEK815", "경주", "경주고속", "경주", "경주터미널");
        register("NAEK840", "안동", "안동고속", "안동", "안동터미널");
        register("NAEK735", "김해", "김해고속", "김해", "김해터미널");
        register("NAEK810", "구미", "구미고속", "구미", "구미터미널");
        register("NAEK730", "통영", "통영고속", "통영", "통영터미널");
        register("NAEK180", "평택", "평택고속", "평택", "평택터미널");
        register("NAEK340", "아산", "아산고속", "아산", "온양", "아산온양");
        register("NAEK270", "양양", "양양고속", "양양", "양양터미널");
        register("NAEK320", "공주", "공주고속", "공주", "공주터미널");
        register("NAEK130", "안성", "안성고속", "안성", "안성터미널");
        register("NAEK450", "제천", "제천고속", "제천", "제천터미널");
        register("NAEK140", "여주", "여주고속", "여주", "여주터미널");
        register("NAEK238", "횡성", "횡성고속", "횡성", "횡성터미널");
        register("NAEK150", "용인", "용인고속", "용인", "용인터미널");
        register("NAEK160", "이천", "이천고속", "이천", "이천터미널");
        register("NAEK220", "삼척", "삼척고속", "삼척", "삼척터미널");
        register("NAEK210", "동해", "동해고속", "동해", "동해터미널");
        register("NAEK825", "상주", "상주고속", "상주", "상주터미널");
        register("NAEK835", "영주", "영주고속", "영주", "영주터미널");
        register("NAEK850", "문경", "점촌", "문경", "문경터미널", "점촌터미널");
        register("NAEK330", "금산", "금산고속", "금산", "금산터미널");
        register("NAEK394", "태안", "태안고속", "태안", "태안터미널");
        register("NAEK851", "예천", "예천고속", "예천", "예천터미널");
        register("NAEK520", "광양", "광양고속", "광양", "광양터미널");
        register("NAEK146", "포천", "포천고속", "포천", "포천터미널");
        register("NAEK148", "철원", "철원고속", "철원", "철원터미널");
        register("NAEK272", "영월", "영월고속", "영월", "영월터미널");
        register("NAEK372", "부여", "부여고속", "부여", "부여터미널");
        register("NAEK274", "태백", "태백고속", "태백", "태백터미널");
        register("NAEK222", "정선", "정선고속", "정선", "정선터미널");
        register("NAEK845", "영천", "영천고속", "영천", "영천터미널");
        register("NAEK843", "영덕", "영덕고속", "영덕", "영덕터미널");
        register("NAEK389", "홍성", "홍성고속", "홍성", "홍성터미널");
        register("NAEK853", "울진", "울진고속", "울진", "울진터미널");
        register("NAEK858", "봉화", "봉화고속", "봉화", "봉화터미널");
        register("NAEK393", "서산", "서산고속", "서산", "서산터미널");
        register("NAEK312", "당진", "당진고속", "당진", "당진터미널");
        register("NAEK370", "논산", "논산고속", "논산", "논산터미널");
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

        // 서비스키가 있으면 실제 TAGO 터미널 목록 검색 API 호출
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

    /** 완전 일치 -> 모음 혼동 보정 일치 -> 포함 일치 순으로 터미널 ID 탐색 (매칭 안 되면 null) */
    private static String matchTerminalId(String clean) {
        if (clean == null || clean.isBlank()) return null;
        if (TERMINAL_MAP.containsKey(clean)) return TERMINAL_MAP.get(clean);

        // 실제 보고된 사례: 음성 인식이 "센트럴시티"를 "샌트럴시티"로 받아쓴다. 현대 한국어 발음에서
        // 근접 모음끼리 거의 구별되지 않아 STT가 자주 혼동하는 흔한 오인식 패턴이라, 특정 단어 하나를
        // 별칭으로 추가하는 대신 모든 터미널명에 공통으로 적용되도록 일반화해서 고정한다.
        String folded = KoreanVowelFold.fold(clean);
        for (Map.Entry<String, String> entry : TERMINAL_MAP.entrySet()) {
            if (KoreanVowelFold.fold(entry.getKey()).equals(folded)) {
                return entry.getValue();
            }
        }

        // 포함 일치는 여러 터미널에 동시에 걸릴 수 있다 — 예를 들어 "서울"은 일부러 별칭으로 등록해
        // 두지 않았는데도(세부 터미널을 되묻기 위해서) "서울경부"/"서울고속"/"동서울" 전부에 부분
        // 일치해 버린다. 실제로 보고된 사고: 되묻는 질문("서울 어느 터미널로?")에 다시 "서울"이라고
        // 답하면, 서로 다른 터미널(예: 서울경부 vs 동서울) 중 하나가 임의로 골라져 조용히 확정돼
        // 버렸다. 서로 다른 터미널로 갈리면 매칭 없음(null)으로 처리해 세부 터미널을 다시 묻게
        // 한다 — 실제로 같은 터미널의 별칭끼리만 겹치는 경우(모호하지 않음)는 그대로 허용한다.
        String uniqueMatch = null;
        for (Map.Entry<String, String> entry : TERMINAL_MAP.entrySet()) {
            if (clean.contains(entry.getKey()) || entry.getKey().contains(clean)) {
                if (uniqueMatch == null) {
                    uniqueMatch = entry.getValue();
                } else if (!uniqueMatch.equals(entry.getValue())) {
                    return null;
                }
            }
        }
        return uniqueMatch;
    }

    /** 텍스트에서 정식 터미널명을 찾아 반환 (매칭 없으면 null — findTerminalId와 달리 기본값으로 대체하지 않음) */
    public static String resolveCanonicalName(String rawText) {
        if (rawText == null || rawText.isBlank()) return null;
        String clean = rawText.trim().replaceAll("\\s+", "");
        String id = matchTerminalId(clean);
        return id == null ? null : ID_TO_NAME_MAP.get(id);
    }

    /**
     * 정식 터미널명이 속한 도시. 인자가 이미 도시명 그 자체(예: 세부 터미널이 특정되지 않은 "대구")면
     * CANONICAL_TO_CITY에 그 이름으로 등록된 터미널이 없어 null이 나오므로, 그 경우엔 자기 자신을
     * 도시로 반환한다 — "서울 말고 대구로"처럼 도시명만으로 정정할 때도 도시 판단이 되어야 한다.
     */
    public static String cityOf(String canonicalName) {
        if (canonicalName != null && CITY_TERMINALS.containsKey(canonicalName)) return canonicalName;
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
     * 등록된 모든 도시명 (정규식 생성용). 복수 터미널 도시(서울/대구/대전 등)는 도시명 자체가
     * 어느 터미널의 별칭으로도 등록돼 있지 않아 allNamesAndAliases()에 안 잡힌다 — "서울 말고
     * 대구로"처럼 세부 터미널 없이 도시명만으로 정정하는 표현을 잡으려면 이 목록이 따로 필요하다.
     */
    public static Set<String> allCities() {
        return CITY_TERMINALS.keySet();
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
                }
                // API 호출 자체는 성공했으므로, 결과가 비어 있어도(=이 두 터미널 사이에 직행 노선이
                // 없다는 뜻) 그대로 반환한다. 여기서 Mock으로 대체하면 "노선이 없다"는 사실이 조용히
                // 가짜 시간표로 둔갑해 버린다 — 우리는 직행 노선만 다루므로 없으면 없다고 해야 한다.
                return result;
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