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
    void recommends_the_cheapest_bus_only_within_two_hours_of_the_requested_time() {
        TagoClient client = new TagoClient(null) {
            @Override public String findTerminalId(String terminalName) { return terminalName; }

            @Override public List<BusSchedule> searchBuses(String depId, String arrId, String date) {
                return List.of(
                        schedule("VERY_CHEAP_BUT_FAR", "202608250600", 1_000),
                        schedule("CLOSEST", "202608252100", 16_000),
                        schedule("CHEAP_IN_WINDOW", "202608251950", 12_000),
                        schedule("LATER_IN_WINDOW", "202608252130", 15_000),
                        schedule("TOO_LATE", "202608252131", 9_000)
                );
            }
        };

        BusSearchService service = new BusSearchService(client);
        var result = service.recommend(new BusSearchRequest("서울", "대전", "2026-08-25", "21:00", "NIGHT", "ANY", "ANY"));

        assertThat(result).extracting(r -> r.bus().routeId()).doesNotContain("VERY_CHEAP_BUT_FAR", "TOO_LATE");
        assertThat(result.stream().filter(r -> r.bus().departureTime().compareTo("202608252100") > 0).count()).isLessThanOrEqualTo(1);
        assertThat(result).filteredOn(r -> r.label().equals("최저가"))
                .extracting(r -> r.bus().routeId()).containsExactly("CHEAP_IN_WINDOW");
    }

    private static BusSchedule schedule(String routeId, String departureTime) {
        return schedule(routeId, departureTime, 16_000);
    }

    private static BusSchedule schedule(String routeId, String departureTime, int charge) {
        return new BusSchedule(routeId, "우등", "서울", "대전", departureTime, departureTime, charge);
    }

    @Test
    void cheapest_recommendation_stays_within_two_hours_of_the_requested_time() {
        // 실제로 보고된 사고: "최저가"가 예매하려는 시간대와 완전히 동떨어진(예: 새벽) 가장 싼 버스를
        // 잡아버렸다. 아무리 싸도 "말씀하신 시간과 가장 가까운 버스"(추천 시간)에서 2시간을 넘게
        // 벗어나면 최저가 후보에서 제외해야 한다.
        TagoClient client = new TagoClient(null) {
            @Override
            public String findTerminalId(String terminalName) {
                return terminalName;
            }

            @Override
            public List<BusSchedule> searchBuses(String depId, String arrId, String date) {
                return List.of(
                        schedule("R01", "202608250900", 20000), // 요청 시각과 가장 가까움 (추천 시간)
                        schedule("R02", "202608250930", 18000), // 2시간 이내라 최저가 후보 가능
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
        // 섞여 나왔다. 그날 실제 가장 늦은 버스를 기준으로 근처(2시간 전까지)만 추천해야 한다.
        TagoClient client = new TagoClient(null) {
            @Override
            public String findTerminalId(String terminalName) {
                return terminalName;
            }

            @Override
            public List<BusSchedule> searchBuses(String depId, String arrId, String date) {
                return List.of(
                        schedule("CHEAP_BUT_EARLY", "202608251330", 5000), // 훨씬 싸지만 낮 시간대
                        schedule("NEAR_LAST", "202608251930", 16000),      // 실제 막차 2시간 이내
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
}
