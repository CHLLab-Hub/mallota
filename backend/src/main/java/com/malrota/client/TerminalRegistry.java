package com.malrota.client;

import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class TerminalRegistry {

    public record TerminalInfo(String terminalId, String canonicalName, String cityName, boolean isPrimary) {}

    // 전국 주요 터미널 마스터 데이터 (TAGO 고속버스 터미널 ID 매핑)
    private final Map<String, TerminalInfo> terminalMap = new LinkedHashMap<>();
    private final Map<String, List<TerminalInfo>> cityTerminalsMap = new LinkedHashMap<>();

    public TerminalRegistry() {
        initTerminals();
    }

    private void initTerminals() {
        // -------------------------------------------------------------
        // 1. 서울권 (복수 터미널)
        // -------------------------------------------------------------
        register("NAEK010", "서울경부", "서울", true, "서울", "강남", "고터", "강남고속", "서울고속");
        register("NAEK020", "센트럴시티", "서울", false, "센트럴", "강남호남", "호남선");
        register("NAEK030", "동서울", "서울", false, "강변", "동서울터미널");
        register("NAEK040", "서울남부", "서울", false, "남부터미널");

        // -------------------------------------------------------------
        // 2. 대구권 (동대구, 서대구 등)
        // -------------------------------------------------------------
        register("NAEK801", "동대구", "대구", true, "대구", "동대구복합", "동대구환승센터", "대구고속");
        register("NAEK803", "서대구", "대구", false, "서대구고속", "만평");
        register("NAEK805", "대구북부", "대구", false, "북부정류장");
        register("NAEK807", "대구서부", "대구", false, "서부정류장", "성당못");

        // -------------------------------------------------------------
        // 3. 부산권 (노포, 사상, 해운대)
        // -------------------------------------------------------------
        register("NAEK700", "부산종합", "부산", true, "부산", "노포", "노포동", "부산고속");
        register("NAEK703", "부산서부", "부산", false, "사상", "서부산", "사상터미널");
        register("NAEK705", "해운대", "부산", false, "해운대터미널");

        // -------------------------------------------------------------
        // 4. 대전권 (대전복합, 유성, 청사)
        // -------------------------------------------------------------
        register("NAEK300", "대전복합", "대전", true, "대전", "동대전", "대전터미널");
        register("NAEK310", "유성고속", "대전", false, "유성", "유성터미널", "충남대");
        register("NAEK305", "대전청사", "대전", false, "정부청사", "둔산");

        // -------------------------------------------------------------
        // 5. 광주권 (유스퀘어, 송정)
        // -------------------------------------------------------------
        register("NAEK500", "광주종합", "광주", true, "광주", "유스퀘어", "광주고속", "광천동");
        register("NAEK505", "광주송정", "광주", false, "송정");

        // -------------------------------------------------------------
        // 6. 인천 / 경기권
        // -------------------------------------------------------------
        register("NAEK100", "인천종합", "인천", true, "인천", "인천터미널", "관교동");
        register("NAEK110", "수원종합", "수원", true, "수원", "수원터미널");
        register("NAEK115", "서수원", "수원", false);
        register("NAEK120", "성남종합", "성남", true, "성남", "야탑", "분당");

        // -------------------------------------------------------------
        // 7. 충청 / 전라 / 강원 / 경상권
        // -------------------------------------------------------------
        register("NAEK320", "청주고속", "청주", true, "청주", "가경동");
        register("NAEK325", "북청주", "청주", false, "청주시외");
        register("NAEK340", "천안고속", "천안", true, "천안", "천안터미널");
        register("NAEK602", "전주고속", "전주", true, "전주", "전주터미널");
        register("NAEK200", "강릉고속", "강릉", true, "강릉");
        register("NAEK210", "원주고속", "원주", true, "원주");
        register("NAEK230", "속초고속", "속초", true, "속초");
        register("NAEK820", "포항고속", "포항", true, "포항");
        register("NAEK710", "창원고속", "창원", true, "창원");
        register("NAEK715", "마산고속", "마산", true, "마산");
        register("NAEK560", "완도", "완도", true, "완도터미널");
    }

    private void register(String id, String canonicalName, String cityName, boolean isPrimary, String... aliases) {
        TerminalInfo info = new TerminalInfo(id, canonicalName, cityName, isPrimary);
        terminalMap.put(canonicalName, info);
        cityTerminalsMap.computeIfAbsent(cityName, k -> new ArrayList<>()).add(info);

        // 별칭(Alias) 등록
        for (String alias : aliases) {
            terminalMap.put(alias, info);
        }
    }

    /** 입력된 지명/터미널명으로 TAGO 터미널 ID 조회 (1순위 완전일치 -> 2순위 포함일치) */
    public String findTerminalId(String rawName) {
        if (rawName == null || rawName.isBlank()) return null;
        String clean = rawName.trim().replaceAll("\\s+", "");

        // 1. 완전 일치 (예: "대구" -> 동대구 NAEK801, "서대구" -> NAEK803, "서울" -> 서울경부 NAEK010)
        if (terminalMap.containsKey(clean)) {
            return terminalMap.get(clean).terminalId();
        }

        // 2. 포함 일치 (예: "동대구역" -> 동대구)
        for (Map.Entry<String, TerminalInfo> entry : terminalMap.entrySet()) {
            if (clean.contains(entry.getKey()) || entry.getKey().contains(clean)) {
                return entry.getValue().terminalId();
            }
        }
        return null;
    }

    /** 표준 터미널 명칭 반환 (예: "강남" -> "서울경부", "대구" -> "동대구", "사상" -> "부산서부") */
    public String getCanonicalName(String rawName) {
        if (rawName == null || rawName.isBlank()) return rawName;
        String clean = rawName.trim().replaceAll("\\s+", "");
        if (terminalMap.containsKey(clean)) {
            return terminalMap.get(clean).canonicalName();
        }
        return rawName;
    }

    /** 해당 도시가 복수 터미널을 가지고 있는지 확인 */
    public boolean isMultiTerminalCity(String cityName) {
        return cityTerminalsMap.containsKey(cityName) && cityTerminalsMap.get(cityName).size() > 1;
    }

    /** 해당 도시의 전체 세부 터미널 목록 이름 반환 (예: 대구 -> ["동대구", "서대구", "대구서부", "대구북부"]) */
    public List<String> getTerminalNames(String cityName) {
        if (!cityTerminalsMap.containsKey(cityName)) return List.of(cityName);
        return cityTerminalsMap.get(cityName).stream().map(TerminalInfo::canonicalName).toList();
    }
}