package com.malrota.service;

import com.malrota.client.TagoClient;
import com.malrota.dto.request.BusSearchRequest;
import com.malrota.dto.response.BusRecommendation;
import com.malrota.dto.response.BusSchedule;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class BusSearchServiceTest {

    @Test
    void places_the_bus_closest_to_requested_departure_time_first() {
        TagoClient client = new TagoClient(null) {
            @Override
            public String findTerminalId(String terminalName) {
                return terminalName;
            }

            @Override
            public List<BusSchedule> searchBuses(String depId, String arrId, String date) {
                return List.of(
                        schedule("R01", "202608250600"),
                        schedule("R02", "202608251900"),
                        schedule("R03", "202608252100")
                );
            }
        };

        BusSearchService service = new BusSearchService(client);
        List<BusSchedule> result = service.search(new BusSearchRequest(
                "서울", "대전", "2026-08-25", "21:00", "NIGHT", "ANY", "ANY"));

        assertThat(result).first().extracting(BusSchedule::departureTime).isEqualTo("202608252100");
    }

    @Test
    void recommends_the_cheapest_bus_only_within_thirty_minutes_of_the_requested_time() {
        TagoClient client = new TagoClient(null) {
            @Override public String findTerminalId(String terminalName) { return terminalName; }

            @Override public List<BusSchedule> searchBuses(String depId, String arrId, String date) {
                return List.of(
                        schedule("VERY_CHEAP_BUT_FAR", "202608250600", 1_000),  // 15시간 일찍 — 항상 제외
                        schedule("CLOSEST", "202608252100", 16_000),            // 요청 시각과 정확히 일치
                        schedule("CHEAP_WITHIN_WINDOW", "202608252120", 10_000), // 20분 후, 30분 이내라 더 저렴하면 선택 가능
                        schedule("TOO_LATE", "202608252140", 9_000)             // 40분 후 — 30분/1시간 범위 밖
                );
            }
        };

        BusSearchService service = new BusSearchService(client);
        var result = service.recommend(new BusSearchRequest("서울", "대전", "2026-08-25", "21:00", "NIGHT", "ANY", "ANY"));

        assertThat(result).extracting(r -> r.bus().routeId()).doesNotContain("VERY_CHEAP_BUT_FAR", "TOO_LATE");
        assertThat(result).filteredOn(r -> r.label().equals("최저가"))
                .extracting(r -> r.bus().routeId()).containsExactly("CHEAP_WITHIN_WINDOW");
    }

    private static BusSchedule schedule(String routeId, String departureTime) {
        return schedule(routeId, departureTime, 16_000);
    }

    private static BusSchedule schedule(String routeId, String departureTime, int charge) {
        return new BusSchedule(routeId, "우등", "서울", "대전", departureTime, departureTime, charge);
    }

    @Test
    void cheapest_recommendation_stays_within_thirty_minutes_of_the_requested_time() {
        // 실제로 보고된 사고: "최저가"가 예매하려는 시간대와 완전히 동떨어진(예: 새벽) 가장 싼 버스를
        // 잡아버렸다. 아무리 싸도 요청 시각(또는 그 근처)에서 너무 멀리 벗어나면 최저가 후보에서
        // 제외해야 한다.
        TagoClient client = new TagoClient(null) {
            @Override
            public String findTerminalId(String terminalName) {
                return terminalName;
            }

            @Override
            public List<BusSchedule> searchBuses(String depId, String arrId, String date) {
                return List.of(
                        schedule("R01", "202608250900", 20000), // 요청 시각과 정확히 일치
                        schedule("R02", "202608250930", 18000), // 30분 이내라 최저가 후보 가능
                        schedule("R03", "202608252300", 5000)   // 훨씬 싸지만 요청 시각과 14시간 차이
                );
            }
        };

        BusSearchService service = new BusSearchService(client);
        List<BusRecommendation> recs = service.recommend(new BusSearchRequest(
                "서울", "대전", "2026-08-25", "09:00", "ANY", "ANY", "ANY"));

        BusRecommendation cheapest = recs.stream().filter(r -> r.label().equals("최저가")).findFirst().orElseThrow();
        assertThat(cheapest.bus().departureTime()).isEqualTo("202608250930");
        // R03(새벽 5,000원)은 훨씬 싸지만 요청 시각과 너무 동떨어져 있어 "최저가"로는 추천되지 않는다.
        assertThat(recs).noneMatch(r -> r.label().equals("최저가") && r.bus().departureTime().equals("202608252300"));
    }

    @Test
    void last_bus_recommendation_clusters_near_the_actual_latest_bus_not_the_cheapest_of_the_day() {
        // 실제로 보고된 사고: servicePreference=LAST("막차")인데 departureTime이 없다는 이유로
        // 시간창 필터링이 통째로 무력화되어, 대낮에 출발하는 아무 저렴한 버스가 "막차" 추천에
        // 섞여 나왔다. 그날 실제 가장 늦은 버스를 기준으로 근처(1시간 전까지)만 추천해야 한다.
        TagoClient client = new TagoClient(null) {
            @Override
            public String findTerminalId(String terminalName) {
                return terminalName;
            }

            @Override
            public List<BusSchedule> searchBuses(String depId, String arrId, String date) {
                return List.of(
                        schedule("CHEAP_BUT_EARLY", "202608251330", 5000), // 훨씬 싸지만 낮 시간대
                        schedule("NEAR_LAST", "202608252015", 16000),      // 실제 막차 45분 전 (1시간 이내)
                        schedule("ACTUAL_LAST", "202608252100", 22000)     // 그날 가장 늦은 버스
                );
            }
        };

        BusSearchService service = new BusSearchService(client);
        List<BusRecommendation> recs = service.recommend(new BusSearchRequest(
                "서울", "대전", "2026-08-25", null, "ANY", "LAST", "ANY"));

        assertThat(recs).extracting(r -> r.bus().routeId()).doesNotContain("CHEAP_BUT_EARLY");
        assertThat(recs).extracting(r -> r.bus().routeId()).contains("NEAR_LAST");
    }

    @Test
    void other_time_card_widens_from_thirty_minutes_to_one_hour_when_nothing_closer_exists() {
        // "최저가랑 조건에 맞는 가장 가까운 시간 +-30분으로, 없으면 1시간까지"라는 요청에 따라,
        // 30분 이내에 "최저가"와 구분되는 다른 후보가 없으면 1시간까지 범위를 넓혀서 "추천 시간"을
        // 찾아야 한다. ONLY_WITHIN_30(요청 시각과 정확히 일치)은 유일한 30분 이내 후보라 "최저가"로
        // 소진되고, WITHIN_HOUR(45분 후)가 그다음으로 넓은 범위에서 "추천 시간"으로 나와야 한다.
        TagoClient client = new TagoClient(null) {
            @Override public String findTerminalId(String terminalName) { return terminalName; }

            @Override public List<BusSchedule> searchBuses(String depId, String arrId, String date) {
                return List.of(
                        schedule("ONLY_WITHIN_30", "202608250900", 5000),
                        schedule("WITHIN_HOUR", "202608250945", 15000)
                );
            }
        };

        BusSearchService service = new BusSearchService(client);
        List<BusRecommendation> recs = service.recommend(new BusSearchRequest(
                "서울", "대전", "2026-08-25", "09:00", "ANY", "ANY", "ANY"));

        assertThat(recs).filteredOn(r -> r.label().equals("최저가"))
                .extracting(r -> r.bus().routeId()).containsExactly("ONLY_WITHIN_30");
        assertThat(recs).filteredOn(r -> r.label().equals("추천 시간"))
                .extracting(r -> r.bus().routeId()).containsExactly("WITHIN_HOUR");
    }

    @Test
    void other_time_card_is_omitted_when_nothing_exists_within_one_hour() {
        // TOO_FAR는 요청 시각보다 90분 이르다 — search() 단계의 2시간 이내 허용 범위는 통과하지만,
        // "추천 시간" 카드의 1시간 확장 범위보다는 멀어서 억지로 끼워 넣으면 안 된다.
        TagoClient client = new TagoClient(null) {
            @Override public String findTerminalId(String terminalName) { return terminalName; }

            @Override public List<BusSchedule> searchBuses(String depId, String arrId, String date) {
                return List.of(
                        schedule("ONLY_WITHIN_30", "202608250900", 5000),
                        schedule("TOO_FAR", "202608250730", 15000)
                );
            }
        };

        BusSearchService service = new BusSearchService(client);
        List<BusRecommendation> recs = service.recommend(new BusSearchRequest(
                "서울", "대전", "2026-08-25", "09:00", "ANY", "ANY", "ANY"));

        assertThat(recs).noneMatch(r -> r.label().equals("추천 시간"));
    }

    @Test
    void other_time_card_prefers_a_slightly_later_bus_over_an_equally_close_earlier_one() {
        // "+30분에 가중치를 조금 더 줘"라는 요청: 요청 시각으로부터 같은 거리(20분)만큼 떨어진
        // 이른 버스와 늦은 버스 중에서는, 늦게 출발하는 쪽을 더 선호해야 한다.
        TagoClient client = new TagoClient(null) {
            @Override public String findTerminalId(String terminalName) { return terminalName; }

            @Override public List<BusSchedule> searchBuses(String depId, String arrId, String date) {
                return List.of(
                        schedule("EXACT", "202608250900", 5000),   // 요청 시각과 일치, 최저가로 소진
                        schedule("EARLIER", "202608250840", 15000), // 20분 이르게
                        schedule("LATER", "202608250920", 15000)    // 20분 늦게 — 같은 거리, 같은 가격
                );
            }
        };

        BusSearchService service = new BusSearchService(client);
        List<BusRecommendation> recs = service.recommend(new BusSearchRequest(
                "서울", "대전", "2026-08-25", "09:00", "ANY", "ANY", "ANY"));

        assertThat(recs).filteredOn(r -> r.label().equals("추천 시간"))
                .extracting(r -> r.bus().routeId()).containsExactly("LATER");
    }

    @Test
    void first_card_is_labeled_as_closest_time_not_cheapest_when_both_cards_cost_the_same() {
        // 실제로 보고된 사고: "최저가"와 "추천 시간" 두 카드의 가격이 똑같은데도(같은 노선/등급)
        // 첫 번째 카드가 "최저가"라고 표시돼, 실제로는 없는 가격 차이가 있는 것처럼 보였다.
        // 30분 이내에 후보가 하나뿐이라 그게 "최저가"로 뽑힌 것뿐이므로, 가격이 같으면
        // "가까운 시간"이라고 정직하게 표시해야 한다.
        TagoClient client = new TagoClient(null) {
            @Override public String findTerminalId(String terminalName) { return terminalName; }

            @Override public List<BusSchedule> searchBuses(String depId, String arrId, String date) {
                return List.of(
                        schedule("CLOSE", "202608251930", 16_000), // 요청 시각 30분 전, 30분 이내 유일한 후보
                        schedule("FAR", "202608252100", 16_000)    // 1시간 후, 같은 가격
                );
            }
        };

        BusSearchService service = new BusSearchService(client);
        List<BusRecommendation> recs = service.recommend(new BusSearchRequest(
                "서울", "대전", "2026-08-25", "20:00", "ANY", "ANY", "ANY"));

        assertThat(recs).extracting(BusRecommendation::label).containsExactly("가까운 시간", "추천 시간");
        assertThat(recs).noneMatch(r -> r.label().equals("최저가"));
    }
}
