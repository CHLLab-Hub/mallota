package com.mallota.service;

import com.mallota.client.TagoClient;
import com.mallota.dto.request.BusSearchRequest;
import com.mallota.dto.response.BusRecommendation;
import com.mallota.dto.response.BusSchedule;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class BusSearchServiceTest {

    @Test
    void reports_no_route_when_the_two_terminals_have_zero_schedules() {
        // 우리는 직행 노선만 다룬다. 이 두 터미널 사이에 그날 배차가 아예 없으면(조건이 안 맞는 게
        // 아니라 노선 자체가 없는 경우) hasAnyScheduleBetween이 false를 반환해서, 호출한 쪽이
        // "노선이 없습니다"와 "조건에 맞는 버스가 없습니다"를 구분해서 안내할 수 있어야 한다.
        TagoClient client = new TagoClient(null) {
            @Override public String findTerminalId(String terminalName) { return terminalName; }

            @Override public List<BusSchedule> searchBuses(String depId, String arrId, String date) {
                return List.of();
            }
        };

        BusSearchService service = new BusSearchService(client);
        BusSearchRequest request = new BusSearchRequest("서울", "완도", "2026-08-28", "09:00", "ANY", "ANY", "ANY");

        assertThat(service.recommend(request)).isEmpty();
        assertThat(service.hasAnyScheduleBetween(request)).isFalse();
    }

    @Test
    void distinguishes_no_route_from_no_bus_matching_the_requested_time() {
        // 노선 자체는 있는데(그날 배차가 존재) 요청한 시각 근처에는 버스가 없는 경우 —
        // 이건 "노선이 없다"가 아니라 "그 시간엔 버스가 없다"로 구분해야 한다.
        TagoClient client = new TagoClient(null) {
            @Override public String findTerminalId(String terminalName) { return terminalName; }

            @Override public List<BusSchedule> searchBuses(String depId, String arrId, String date) {
                return List.of(schedule("R01", "202608280600", 16_000)); // 요청 시각(21:00)과 15시간 차이
            }
        };

        BusSearchService service = new BusSearchService(client);
        BusSearchRequest request = new BusSearchRequest("서울", "대전", "2026-08-28", "21:00", "ANY", "ANY", "ANY");

        assertThat(service.recommend(request)).isEmpty();
        assertThat(service.hasAnyScheduleBetween(request)).isTrue();
    }

    @Test
    void finds_a_route_through_any_terminal_of_an_undisambiguated_multi_terminal_city() {
        // 실제로 보고된 사고: "서울"처럼 세부 터미널이 여러 개인 도시는, 사용자가 "서울경부/센트럴
        // 시티/동서울 중 어디로?"에 답해야만 노선 존재를 확인했다 — 그 사이 한 턴이 낭비됐다.
        // 세부 터미널이 안 정해졌어도, 그 도시 터미널 중 하나(여기선 센트럴시티)라도 노선이 있으면
        // 바로 "노선 있음"으로 판단해야 한다.
        TagoClient client = new TagoClient(null) {
            @Override public String findTerminalId(String terminalName) { return terminalName; }

            @Override public List<BusSchedule> searchBuses(String depId, String arrId, String date) {
                // 서울경부/동서울 -> 전주는 노선이 없고, 센트럴시티 -> 전주만 노선이 있다.
                if ("센트럴시티".equals(depId) && "전주".equals(arrId)) {
                    return List.of(schedule("R01", "202608280800", 20_000));
                }
                return List.of();
            }
        };

        BusSearchService service = new BusSearchService(client);
        BusSearchRequest request = new BusSearchRequest("서울", "전주", "2026-08-28");

        assertThat(service.hasAnyScheduleBetween(request)).isTrue();
    }

    @Test
    void reports_no_route_when_none_of_a_multi_terminal_citys_terminals_has_service() {
        // 위 테스트의 대칭 — 세부 터미널이 안 정해졌어도, 그 도시의 모든 터미널을 확인한 뒤에야
        // "노선 없음"으로 판단해야 한다(임의로 하나만 확인하고 없다고 단정하면 안 된다).
        TagoClient client = new TagoClient(null) {
            @Override public String findTerminalId(String terminalName) { return terminalName; }

            @Override public List<BusSchedule> searchBuses(String depId, String arrId, String date) {
                return List.of();
            }
        };

        BusSearchService service = new BusSearchService(client);
        BusSearchRequest request = new BusSearchRequest("서울", "전주", "2026-08-28");

        assertThat(service.hasAnyScheduleBetween(request)).isFalse();
    }

    @Test
    void premium_grade_filter_excludes_the_pricier_late_night_premium_variant() {
        // 실제로 보고된 사고: 앱에서 보이는 "프리미엄" 요금이 실제(TAGO)와 다른 것 같다는 문의.
        // 원인은 TAGO가 "심야프리미엄"(52,600원)을 "프리미엄"(43,900원)과는 별도의, 더 비싼 등급으로
        // 취급하는데 grade.contains("프리미엄")만 쓰면 "심야프리미엄"도 같이 걸려서, 같은 "프리미엄"
        // 요청인데 카드마다 요금이 다르게 나오는 사고로 이어졌다. "우등"/"심야우등"도 동일한 문제였다.
        TagoClient client = new TagoClient(null) {
            @Override public String findTerminalId(String terminalName) { return terminalName; }

            @Override public List<BusSchedule> searchBuses(String depId, String arrId, String date) {
                return List.of(
                        new BusSchedule("R01", "프리미엄", "서울경부", "부산", "202608280900", "202608281300", 43_900),
                        new BusSchedule("R02", "심야프리미엄", "서울경부", "부산", "202608280910", "202608281310", 52_600),
                        new BusSchedule("R03", "우등", "서울경부", "부산", "202608280920", "202608281320", 39_700),
                        new BusSchedule("R04", "심야우등", "서울경부", "부산", "202608280930", "202608281330", 47_600)
                );
            }
        };

        BusSearchService service = new BusSearchService(client);

        var premiumOnly = service.search(new BusSearchRequest(
                "서울", "부산", "2026-08-28", "09:00", "ANY", "ANY", "PREMIUM"));
        assertThat(premiumOnly).extracting(BusSchedule::grade).containsOnly("프리미엄");
        assertThat(premiumOnly).extracting(BusSchedule::charge).containsOnly(43_900);

        var excellentOnly = service.search(new BusSearchRequest(
                "서울", "부산", "2026-08-28", "09:00", "ANY", "ANY", "EXCELLENT"));
        assertThat(excellentOnly).extracting(BusSchedule::grade).containsOnly("우등");
        assertThat(excellentOnly).extracting(BusSchedule::charge).containsOnly(39_700);
    }

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
        assertThat(result).filteredOn(r -> r.labels().contains("최저가"))
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

        BusRecommendation cheapest = recs.stream().filter(r -> r.labels().contains("최저가")).findFirst().orElseThrow();
        assertThat(cheapest.bus().departureTime()).isEqualTo("202608250930");
        // R03(새벽 5,000원)은 훨씬 싸지만 요청 시각과 너무 동떨어져 있어 "최저가"로는 추천되지 않는다.
        assertThat(recs).noneMatch(r -> r.labels().contains("최저가") && r.bus().departureTime().equals("202608252300"));
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
    void last_bus_itself_is_labeled_closest_time_even_when_a_cheaper_alternative_gets_the_cheapest_label() {
        // 실제로 보고된 사고: "막차"를 요청했는데, 그날 진짜 막차(22시, 심야우등이라 비쌈)가
        // "최저가"로 소진되고, 60분 이내로 범위를 넓혀 찾은 더 저렴한 다른 버스(21시, 우등)가
        // "가까운 시간"으로 나왔다 — "가까운 시간"이라는 버스가 실제로는 요청 시각(=막차 시각)과
        // 더 멀리 떨어져 있는 라벨 역전 현상이었다. 막차 자신은 정의상 거리 0이므로 항상 "가까운
        // 시간"이어야 하고, 그보다 싼 대안이 있다면 그게 "최저가"여야 한다.
        TagoClient client = new TagoClient(null) {
            @Override public String findTerminalId(String terminalName) { return terminalName; }

            @Override public List<BusSchedule> searchBuses(String depId, String arrId, String date) {
                return List.of(
                        schedule("CHEAPER_ALTERNATIVE", "202608252100", 11_000), // 막차 1시간 전, 더 저렴
                        schedule("ACTUAL_LAST_BUS", "202608252200", 13_200)      // 그날 진짜 막차, 더 비쌈
                );
            }
        };

        BusSearchService service = new BusSearchService(client);
        List<BusRecommendation> recs = service.recommend(new BusSearchRequest(
                "동대구", "부산", "2026-08-25", null, "ANY", "LAST", "ANY"));

        assertThat(recs).filteredOn(r -> r.labels().contains("가까운 시간"))
                .extracting(r -> r.bus().routeId()).containsExactly("ACTUAL_LAST_BUS");
        assertThat(recs).filteredOn(r -> r.labels().contains("최저가"))
                .extracting(r -> r.bus().routeId()).containsExactly("CHEAPER_ALTERNATIVE");
    }

    @Test
    void closest_time_card_widens_from_thirty_minutes_to_one_hour_when_nothing_closer_exists() {
        // "최저가랑 조건에 맞는 가장 가까운 시간 +-30분으로, 없으면 1시간까지"라는 요청에 따라,
        // 30분 이내에 "최저가"와 구분되는 다른 후보가 없으면 1시간까지 범위를 넓혀서 "가까운 시간"을
        // 찾아야 한다. ONLY_WITHIN_30(요청 시각과 정확히 일치)은 유일한 30분 이내 후보라 "최저가"로
        // 소진되고, WITHIN_HOUR(45분 후)가 그다음으로 넓은 범위에서 "가까운 시간"으로 나와야 한다.
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

        assertThat(recs).filteredOn(r -> r.labels().contains("최저가"))
                .extracting(r -> r.bus().routeId()).containsExactly("ONLY_WITHIN_30");
        assertThat(recs).filteredOn(r -> r.labels().contains("가까운 시간"))
                .extracting(r -> r.bus().routeId()).containsExactly("WITHIN_HOUR");
    }

    @Test
    void closest_time_card_duplicates_the_cheapest_bus_when_nothing_else_exists_within_one_hour() {
        // "가장 가까운 시간"/"최저가" 두 카테고리는 항상 둘 다 떠야 한다 — 중복돼도 괜찮다. TOO_FAR는
        // 요청 시각보다 90분 이르다(1시간 확장 범위 밖)라서, 이 노선엔 진짜로 다른 버스가 없다.
        // 이 경우 카드를 생략하는 대신 최저가와 같은 버스를 "가까운 시간"에도 중복으로 보여준다.
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

        // 두 카테고리가 결국 같은 버스로 겹치므로, 카드를 둘로 쪼개지 않고 뱃지 2개를 붙인
        // 카드 하나로 합쳐서 보여준다.
        assertThat(recs).hasSize(1);
        assertThat(recs.get(0).bus().routeId()).isEqualTo("ONLY_WITHIN_30");
        assertThat(recs.get(0).labels()).containsExactlyInAnyOrder("최저가", "가까운 시간");
    }

    @Test
    void closest_time_card_prefers_a_slightly_later_bus_over_an_equally_close_earlier_one() {
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

        assertThat(recs).filteredOn(r -> r.labels().contains("가까운 시간"))
                .extracting(r -> r.bus().routeId()).containsExactly("LATER");
    }

    @Test
    void always_shows_both_categories_even_when_both_cards_cost_the_same() {
        // 실제로 보고된 사고: "최저가"와 "가까운 시간" 두 카드의 가격이 똑같으니까(같은 노선/등급)
        // 둘 중 하나의 라벨이 통째로 사라지거나 다른 이름으로 바뀌었다. 가격이 같아도 두 카테고리는
        // 각자 정직한 이름 그대로, 항상 둘 다 떠야 한다 — 서로 다른 버스라면 카드도 둘이어야 한다.
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

        assertThat(recs).hasSize(2);
        assertThat(recs).filteredOn(r -> r.labels().contains("최저가"))
                .extracting(r -> r.bus().routeId()).containsExactly("CLOSE");
        assertThat(recs).filteredOn(r -> r.labels().contains("가까운 시간"))
                .extracting(r -> r.bus().routeId()).containsExactly("FAR");
    }
}
