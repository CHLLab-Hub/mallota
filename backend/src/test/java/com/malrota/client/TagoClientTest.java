package com.malrota.client;

import com.malrota.config.TagoProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TagoClientTest {

    private final TagoClient client = new TagoClient(new TagoProperties(null, "https://example.invalid"));

    @Test
    @DisplayName("STT가 ㅐ/ㅔ를 혼동해 \"센트럴시티\"를 \"샌트럴시티\"로 받아써도 같은 터미널로 인식한다")
    void resolves_ae_e_vowel_confusion() {
        assertThat(TagoClient.resolveCanonicalName("샌트럴시티")).isEqualTo("센트럴시티");
        assertThat(client.findTerminalId("샌트럴시티")).isEqualTo(client.findTerminalId("센트럴시티"));
    }

    @Test
    @DisplayName("정확한 표기(\"센트럴시티\")는 그대로 정상 인식된다")
    void still_resolves_exact_spelling() {
        assertThat(TagoClient.resolveCanonicalName("센트럴시티")).isEqualTo("센트럴시티");
    }

    @Test
    @DisplayName("서로 다른 도시명끼리는 모음 혼동 보정을 적용해도 잘못 겹치지 않는다")
    void does_not_collide_different_cities_after_vowel_folding() {
        // "대구"와 "대전"처럼 애초에 다른 음절 개수/자음 구성이면 모음 보정과 무관하게 여전히 다르다
        assertThat(TagoClient.resolveCanonicalName("대구")).isNotEqualTo(TagoClient.resolveCanonicalName("대전"));
    }

    @Test
    @DisplayName("\"동서울\"은 실제 배차 데이터가 있는 NAEK032로 매핑된다 (NAEK030/031/035는 늘 0건)")
    void resolves_dongseoul_to_the_terminal_id_with_real_schedule_data() {
        // 실제로 보고된 사고: "동서울"이 NAEK030으로 등록돼 있었는데, TAGO API에서 NAEK030은
        // 항상 0건이라 Mock의 가짜 1시간30분/16,000원 시간표로 조용히 대체됐다. TAGO 터미널
        // 검색에서 "동서울"이란 이름의 ID가 NAEK030~032, 035로 4개나 나오는데, 그중 실제 배차
        // 데이터가 있는 건 NAEK032뿐이다.
        assertThat(client.findTerminalId("동서울")).isEqualTo("NAEK032");
    }

    @Test
    @DisplayName("\"해운대\"는 TAGO에 없는 터미널명이라, 가장 가까운 실제 터미널(부산종합)로 안내한다")
    void resolves_haeundae_to_the_real_busan_terminal() {
        // 실제로 보고된 사고: "해운대"가 존재하지 않는 가짜 터미널ID(NAEK705)로 등록돼 있어서
        // 조회할 때마다 항상 실패 → Mock으로 대체됐다. TAGO 터미널 목록 자체에 "해운대"라는
        // 이름이 없으므로(고속/시외버스가 해운대에 직접 정차하지 않음), 실제 데이터가 있는
        // 부산종합(NAEK700)으로 안내해야 한다.
        assertThat(client.findTerminalId("해운대")).isEqualTo("NAEK700");
    }

    @Test
    @DisplayName("\"청주고속\"은 실제로는 \"공주\"였던 NAEK320이 아니라 진짜 청주(고속)인 NAEK400으로 매핑된다")
    void resolves_cheongju_to_the_real_terminal_id_not_the_gongju_mixup() {
        // 실제로 보고된 사고: "청주고속"이 NAEK320으로 등록돼 있었는데, 실제 배차 데이터를
        // 확인해보니 NAEK320은 전부 "공주"행 노선이었다(청주와 무관한 도시). 진짜 "청주(고속)"는
        // NAEK400이고 서울행 95건으로 데이터도 훨씬 많다.
        assertThat(client.findTerminalId("청주고속")).isEqualTo("NAEK400");
        assertThat(client.findTerminalId("청주")).isEqualTo("NAEK400");
    }

    @Test
    @DisplayName("\"북청주\"는 청주와 무관한 다른 지역(인삼랜드)의 가짜 매핑이었으므로 더 이상 등록되어 있지 않다")
    void no_longer_maps_bukcheongju_to_an_unrelated_city() {
        // 실제로 보고된 사고: "북청주"가 NAEK325로 등록돼 있었는데, 실제 배차 데이터를 확인해보니
        // NAEK325의 진짜 이름은 "인삼랜드"(금산)였다 — 청주와는 전혀 무관한 지역이다. "북청주"라는
        // 이름 자체도 TAGO 터미널 목록에 없어서, 이 매핑이 없으면 라이브 검색 폴백으로 넘어가야
        // 한다(하드코딩된 registry에는 없어야 한다).
        assertThat(client.findTerminalId("북청주")).isNotEqualTo("NAEK325");
    }

    @Test
    @DisplayName("\"센트럴시티\"는 실제 배차 데이터가 있는 NAEK021로 매핑된다 (이름은 같은 NAEK020은 늘 0건)")
    void resolves_central_city_to_the_terminal_id_with_real_schedule_data() {
        // 실제로 보고된 사고: "센트럴시티"가 NAEK020으로 등록돼 있었는데, 이름은 정확히 일치해도
        // 실제 배차 데이터가 전혀 없었다(광주행 0건). 진짜 데이터가 있는 건 NAEK021(광주행 99건)이다.
        assertThat(client.findTerminalId("센트럴시티")).isEqualTo("NAEK021");
    }

    @Test
    @DisplayName("\"포항\"은 이름조차 안 맞던 기존 가짜 ID 대신 실제 TAGO 터미널명과 일치하는 ID로 매핑된다")
    void resolves_pohang_to_its_real_id() {
        // 기존 NAEK820은 "포항"으로 검색해도 안 잡히는 가짜 ID였다. 실제 "포항"은 NAEK830이고
        // 서울행 29건으로 배차 데이터도 확인됐다.
        assertThat(client.findTerminalId("포항고속")).isEqualTo("NAEK830");
    }

    @Test
    @DisplayName("우리 API가 커버하지 않는 시외버스 전용 노선(서울남부/서수원/완도)은 등록에서 제거되어 있다")
    void no_longer_registers_intercity_only_terminals_with_no_express_bus_coverage() {
        // 우리가 쓰는 TAGO API는 고속버스(ExpBusInfo) 전용이라 시외버스로만 운행되는 노선은 애초에
        // 조회가 안 된다. "서울남부"/"서수원"/"완도"는 TAGO 터미널 검색으로는 이름이 맞는 ID를
        // 찾았지만, 주요 거점 여러 곳과 짝지어도 실배차가 전부 0건이라(=시외버스 전용으로 추정)
        // 등록해봐야 항상 Mock으로 샐 뿐이었다. 하드코딩 registry에서 제거해, 라이브 검색 폴백으로
        // 넘어가게 한다.
        assertThat(client.findTerminalId("서울남부")).isNotEqualTo("NAEK050");
        assertThat(client.findTerminalId("서수원")).isNotEqualTo("NAEK109");
        assertThat(client.findTerminalId("완도")).isNotEqualTo("NAEK575");
    }

    @Test
    @DisplayName("\"서대구\"/\"대구북부\"는 같은 실제 터미널(NAEK805)로 합쳐서 매핑된다 — \"대구북부\"라는 이름은 TAGO에 없다")
    void merges_seodaegu_and_the_nonexistent_daegu_bukbu_into_the_same_real_terminal() {
        assertThat(client.findTerminalId("서대구")).isEqualTo("NAEK805");
        assertThat(client.findTerminalId("대구북부")).isEqualTo("NAEK805");
    }

    @Test
    @DisplayName("\"대구서부\"(NAEK807)는 ID 자체는 실제 데이터가 있는 정상 ID지만, TAGO상 진짜 이름은 \"대구용계\"다")
    void keeps_the_working_id_for_daegu_seobu_but_its_real_name_is_daegu_yonggye() {
        // NAEK807은 실제로 부산행 24건이 확인된 정상 ID다 — 다만 TAGO 터미널 검색에서 이 ID의
        // 진짜 이름은 "대구서부"가 아니라 "대구용계"였다. ID는 그대로 두고 "대구서부"는 별칭으로만
        // 남긴다.
        assertThat(client.findTerminalId("대구용계")).isEqualTo("NAEK807");
        assertThat(client.findTerminalId("대구서부")).isEqualTo("NAEK807");
    }

    @Test
    @DisplayName("\"천안\"/\"원주\"는 지명 대조 재검증에서 발견된 실제 ID로 매핑되고, \"유성\"은 실배차가 없어 제거되었다")
    void resolves_cheonan_and_wonju_after_re_verifying_place_names_and_drops_unconfirmed_yuseong() {
        // 재검증 중 발견한 사고 2건: 기존 "천안고속"(NAEK340)은 실제로는 "아산온양"행 노선이었고
        // (진짜 천안은 NAEK310, 서울행 109건), 기존 "원주고속"(NAEK210)은 실제로는 "동해"행 노선
        // 이었다(진짜 원주는 NAEK240, 서울행 100건). 둘 다 서울행 배차 건수는 있었지만 실제 지명을
        // 대조하지 않아서 놓쳤던 것. 그리고 NAEK310이 원래 "유성고속"으로 등록돼 있었는데, 실제
        // "유성"(NAEK360, 유성복합)은 서울/대전 어디와도 실배차가 0건이라 등록에서 제거했다.
        assertThat(client.findTerminalId("천안")).isEqualTo("NAEK310");
        assertThat(client.findTerminalId("원주")).isEqualTo("NAEK240");
        assertThat(client.findTerminalId("유성")).isNotEqualTo("NAEK310");
    }

    @Test
    @DisplayName("\"마산\"은 실제로는 \"울산\"이었던 NAEK715가 아니라 진짜 마산인 NAEK705로 매핑된다")
    void resolves_masan_to_the_real_terminal_id_not_the_ulsan_mixup() {
        // 새로 광역시급 도시를 추가하려고 전체 터미널 목록을 훑다가 발견한 사고: 기존 "마산고속"이
        // NAEK715로 등록돼 있었는데, 실제 배차 데이터를 확인해보니 NAEK715는 전부 "울산"행 노선이었다
        // (진짜 마산은 NAEK705). 서울행 배차 건수가 있다는 것만 확인하고 실제 지명(depPlaceNm/
        // arrPlaceNm)까지는 대조하지 않아서 놓쳤던 것 — 이번엔 지명까지 대조해서 확인했다.
        assertThat(client.findTerminalId("마산")).isEqualTo("NAEK705");
    }

    @Test
    @DisplayName("새로 추가한 광역시/도청소재지급 도시들이 실제 배차가 확인된 ID로 매핑된다")
    void resolves_newly_added_major_cities_to_verified_real_ids() {
        assertThat(client.findTerminalId("울산")).isEqualTo("NAEK715");
        assertThat(client.findTerminalId("춘천")).isEqualTo("NAEK250");
        assertThat(client.findTerminalId("세종")).isEqualTo("NAEK352");
        assertThat(client.findTerminalId("진주")).isEqualTo("NAEK722");
        assertThat(client.findTerminalId("여수")).isEqualTo("NAEK510");
        assertThat(client.findTerminalId("순천")).isEqualTo("NAEK515");
        assertThat(client.findTerminalId("목포")).isEqualTo("NAEK505");
        assertThat(client.findTerminalId("경주")).isEqualTo("NAEK815");
        assertThat(client.findTerminalId("안동")).isEqualTo("NAEK840");
        assertThat(client.findTerminalId("김해")).isEqualTo("NAEK735");
        assertThat(client.findTerminalId("구미")).isEqualTo("NAEK810");
        assertThat(client.findTerminalId("통영")).isEqualTo("NAEK730");
    }

    @Test
    @DisplayName("전체 453개 터미널 전수조사에서 실배차가 확인된 32개 도시가 실제 ID로 매핑된다")
    void resolves_terminals_found_in_the_full_nationwide_audit_to_verified_real_ids() {
        // 티머니고에는 있는데 우리 앱엔 없는 터미널이 많다는 지적을 받고, TAGO 전체 453개 터미널을
        // 서울/부산/대전 3개 거점으로 전수조사했다. 실배차가 확인된 143개 중 이미 등록된 33개를 뺀
        // 111개 후보에서, 이미 등록된 도시의 하위 정류소/대학교/휴게소를 제외하고 독립된 시/군만
        // 골라 배차 건수 순으로 32개를 추가했다.
        assertThat(client.findTerminalId("평택")).isEqualTo("NAEK180");
        assertThat(client.findTerminalId("아산")).isEqualTo("NAEK340");
        assertThat(client.findTerminalId("양양")).isEqualTo("NAEK270");
        assertThat(client.findTerminalId("공주")).isEqualTo("NAEK320");
        assertThat(client.findTerminalId("안성")).isEqualTo("NAEK130");
        assertThat(client.findTerminalId("제천")).isEqualTo("NAEK450");
        assertThat(client.findTerminalId("여주")).isEqualTo("NAEK140");
        assertThat(client.findTerminalId("횡성")).isEqualTo("NAEK238");
        assertThat(client.findTerminalId("용인")).isEqualTo("NAEK150");
        assertThat(client.findTerminalId("이천")).isEqualTo("NAEK160");
        assertThat(client.findTerminalId("삼척")).isEqualTo("NAEK220");
        // "동해"와 "공주"는 예전에 각각 "원주고속"/"청주고속"으로 잘못 등록됐던 ID(NAEK210/NAEK320)의
        // 진짜 정체였다 — 이미 확인된 데이터라 그대로 재사용했다.
        assertThat(client.findTerminalId("동해")).isEqualTo("NAEK210");
        assertThat(client.findTerminalId("상주")).isEqualTo("NAEK825");
        assertThat(client.findTerminalId("영주")).isEqualTo("NAEK835");
        assertThat(client.findTerminalId("문경")).isEqualTo("NAEK850");
        assertThat(client.findTerminalId("금산")).isEqualTo("NAEK330");
        assertThat(client.findTerminalId("태안")).isEqualTo("NAEK394");
        assertThat(client.findTerminalId("예천")).isEqualTo("NAEK851");
        assertThat(client.findTerminalId("광양")).isEqualTo("NAEK520");
        assertThat(client.findTerminalId("포천")).isEqualTo("NAEK146");
        assertThat(client.findTerminalId("철원")).isEqualTo("NAEK148");
        assertThat(client.findTerminalId("영월")).isEqualTo("NAEK272");
        assertThat(client.findTerminalId("부여")).isEqualTo("NAEK372");
        assertThat(client.findTerminalId("태백")).isEqualTo("NAEK274");
        assertThat(client.findTerminalId("정선")).isEqualTo("NAEK222");
        assertThat(client.findTerminalId("영천")).isEqualTo("NAEK845");
        assertThat(client.findTerminalId("영덕")).isEqualTo("NAEK843");
        assertThat(client.findTerminalId("홍성")).isEqualTo("NAEK389");
        assertThat(client.findTerminalId("울진")).isEqualTo("NAEK853");
        assertThat(client.findTerminalId("봉화")).isEqualTo("NAEK858");
        assertThat(client.findTerminalId("서산")).isEqualTo("NAEK393");
        assertThat(client.findTerminalId("당진")).isEqualTo("NAEK312");
        assertThat(client.findTerminalId("논산")).isEqualTo("NAEK370");
    }
}
