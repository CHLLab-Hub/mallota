package com.malrota.service;

import com.malrota.client.TagoClient;
import com.malrota.dto.request.BusSearchRequest;
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
}
