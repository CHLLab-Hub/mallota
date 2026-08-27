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
}
