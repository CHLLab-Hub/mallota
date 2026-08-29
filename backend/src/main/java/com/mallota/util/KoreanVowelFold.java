package com.mallota.util;

import java.util.Map;

/**
 * 현대 한국어 발음에서 사실상 하나로 합쳐져 들리는 모음들을 접어서 비교하는 유틸리티.
 * STT가 "센트럴시티"를 "샌트럴시티"로, "모레"를 "모래"로 잘못 받아쓰는 것처럼, ㅐ/ㅔ 같은 근접
 * 모음은 원어민 발음 자체가 구별되지 않아 음성 인식이 흔히 혼동한다. 터미널명뿐 아니라 날짜/
 * 시간 키워드 등 문자열 매칭이 필요한 곳이면 어디서든 재사용한다.
 *
 * 자음의 평음/경음/격음(ㄱ/ㄲ/ㅋ 등)은 실제 발음에서도 뚜렷이 구별되므로 여기서는 섞지 않는다 —
 * 섞으면 서로 다른 실제 단어끼리 잘못 겹칠 위험이 있다.
 */
public final class KoreanVowelFold {

    private KoreanVowelFold() {}

    // 모음 index 순서(유니코드 한글 분해 공식 기준): ㅏㅐㅑㅒㅓㅔㅕㅖㅗㅘㅙㅚㅛㅜㅝㅞㅟㅠㅡㅢㅣ (0~20)
    private static final Map<Integer, Integer> VOWEL_FOLD_MAP = Map.of(
            1, 5,   // ㅐ -> ㅔ (예: 센트럴시티/샌트럴시티, 모레/모래)
            3, 7,   // ㅒ -> ㅖ (예: 얘/예)
            10, 11, // ㅙ -> ㅚ
            15, 11  // ㅞ -> ㅚ (예: 왜/외/웨가 모두 비슷하게 들림)
    );

    /** 완성형 한글 음절의 근접 혼동 모음을 대표 모음으로 통일해 비교용 키를 만든다 */
    public static String fold(String text) {
        if (text == null) return null;
        StringBuilder sb = new StringBuilder(text.length());
        for (char c : text.toCharArray()) {
            if (c >= 0xAC00 && c <= 0xD7A3) {
                int idx = c - 0xAC00;
                int medial = (idx / 28) % 21;
                Integer folded = VOWEL_FOLD_MAP.get(medial);
                if (folded != null) {
                    int lead = idx / (21 * 28);
                    int trail = idx % 28;
                    c = (char) (0xAC00 + (lead * 21 + folded) * 28 + trail);
                }
            }
            sb.append(c);
        }
        return sb.toString();
    }

    /** text 안에 keyword가 모음 혼동을 감안하고도 포함되어 있는지 (keyword는 상수이므로 매 호출마다 fold) */
    public static boolean contains(String text, String keyword) {
        if (text == null || keyword == null) return false;
        return fold(text).contains(fold(keyword));
    }
}
