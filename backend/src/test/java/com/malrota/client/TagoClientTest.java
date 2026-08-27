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
}
