package com.mallota.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class KoreanVowelFoldTest {

    @Test
    @DisplayName("ㅐ/ㅔ 혼동 모음을 하나로 접는다")
    void folds_ae_and_e() {
        // "내일"(ㅐ) vs "네일"(ㅔ)처럼 초성·종성이 같고 모음만 다른 실제 단어 쌍으로 검증한다.
        assertThat(KoreanVowelFold.fold("내일")).isEqualTo(KoreanVowelFold.fold("네일"));
        assertThat(KoreanVowelFold.fold("샌트럴시티")).isEqualTo(KoreanVowelFold.fold("센트럴시티"));
        assertThat(KoreanVowelFold.fold("모래")).isEqualTo(KoreanVowelFold.fold("모레"));
    }

    @Test
    @DisplayName("ㅒ/ㅖ 혼동 모음을 하나로 접는다")
    void folds_yae_and_ye() {
        assertThat(KoreanVowelFold.fold("얘")).isEqualTo(KoreanVowelFold.fold("예"));
    }

    @Test
    @DisplayName("ㅙ/ㅚ/ㅞ 세 모음을 모두 하나로 접는다")
    void folds_wae_oe_we_together() {
        String folded = KoreanVowelFold.fold("왜");
        assertThat(KoreanVowelFold.fold("외")).isEqualTo(folded);
        assertThat(KoreanVowelFold.fold("웨")).isEqualTo(folded);
    }

    @Test
    @DisplayName("자음(평음/경음/격음)은 절대 섞지 않는다 — 서로 다른 단어끼리 겹치면 안 된다")
    void never_folds_consonant_tension_pairs() {
        // "달"과 "탈"은 초성만 다른 완전히 다른 단어라 절대 같아지면 안 된다.
        assertThat(KoreanVowelFold.fold("달")).isNotEqualTo(KoreanVowelFold.fold("탈"));
        assertThat(KoreanVowelFold.fold("불")).isNotEqualTo(KoreanVowelFold.fold("풀")).isNotEqualTo(KoreanVowelFold.fold("뿔"));
    }

    @Test
    @DisplayName("ㅢ는 의도적으로 접지 않는다 (문맥 의존적인 변화라 섞으면 위험)")
    void does_not_fold_ui_vowel() {
        // "의사"(doctor)와 "이사"(moving)는 실제로 다른 단어이므로 서로 같아지면 안 된다.
        assertThat(KoreanVowelFold.fold("의사")).isNotEqualTo(KoreanVowelFold.fold("이사"));
        assertThat(KoreanVowelFold.fold("의사")).isEqualTo("의사");
    }

    @Test
    @DisplayName("한글이 아닌 문자(숫자·영문·공백·기호)는 그대로 통과시킨다")
    void passes_through_non_hangul_characters_unchanged() {
        assertThat(KoreanVowelFold.fold("2026-08-27 Bus #1, 부산!")).isEqualTo("2026-08-27 Bus #1, 부산!");
        assertThat(KoreanVowelFold.fold("")).isEqualTo("");
    }

    @Test
    @DisplayName("null은 예외 없이 null을 돌려준다")
    void handles_null_safely() {
        assertThat(KoreanVowelFold.fold(null)).isNull();
        assertThat(KoreanVowelFold.contains(null, "센트럴시티")).isFalse();
        assertThat(KoreanVowelFold.contains("센트럴시티", null)).isFalse();
    }

    @Test
    @DisplayName("contains()는 문장 속에서 모음 혼동을 감안하고도 부분 일치를 찾는다")
    void contains_finds_substring_despite_vowel_confusion() {
        assertThat(KoreanVowelFold.contains("강릉에서 샌트럴시티 가는 버스", "센트럴시티")).isTrue();
        assertThat(KoreanVowelFold.contains("음 모래 아침에 갈게요", "모레")).isTrue();
        assertThat(KoreanVowelFold.contains("서울에서 대전 가는 버스", "센트럴시티")).isFalse();
    }
}
