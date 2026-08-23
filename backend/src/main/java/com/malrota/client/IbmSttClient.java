package com.malrota.client;

import com.malrota.config.IbmSpeechProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;

/**
 * IBM Speech to Text 호출 담당 (음성 → 텍스트).
 * 별도 라이브러리 없이 Spring RestClient로 호출합니다.
 */
@Slf4j
@Component
public class IbmSttClient {

    private final IbmSpeechProperties props;
    private final RestClient http = RestClient.create();

    public IbmSttClient(IbmSpeechProperties props) {
        this.props = props;
    }

    /**
     * 오디오 바이트 → 한국어 텍스트 변환
     */
    @SuppressWarnings("unchecked")
    public String transcribe(byte[] audio, String contentType) {
        if (!props.isSttEnabled()) {
            log.warn("STT가 비활성화되어 있습니다.");
            return "내일 오전 서울에서 대전 가는 버스 찾아줘"; // 데모 안전용 Mock Fallback
        }

        // 1. 모델명 확인 (기본값 ko-KR_Multimedia)
        String model = (props.getSttModel() != null && !props.getSttModel().isBlank()) 
                ? props.getSttModel() 
                : "ko-KR_Multimedia";

        // 2. URL 끝의 슬래시 제거 및 정확도 향상 파라미터 조합
        String baseUrl = props.getSttUrl().replaceAll("/+$", "");
        String url = String.format(
                "%s/v1/recognize?model=%s&smart_formatting=true&background_audio_suppression=0.5&end_of_phrase_silence_time=0.8",
                baseUrl, model
        );

        // 3. Content-Type 정규화 (e.g. "audio/webm;codecs=opus" -> "audio/webm")
        String normalizedContentType = (contentType != null && !contentType.isBlank()) 
                ? contentType.split(";")[0].trim() 
                : "audio/webm";

        log.info("IBM STT 호출 시작: URL={}, Content-Type={}", url, normalizedContentType);

        try {
            Map<String, Object> res = http.post()
                    .uri(url)
                    .header(HttpHeaders.AUTHORIZATION, basicAuth())
                    .header(HttpHeaders.CONTENT_TYPE, normalizedContentType)
                    .body(audio)
                    .retrieve()
                    .body(Map.class);

            if (res == null) return "";

            // 응답 구조 파싱: { "results": [ { "alternatives": [ { "transcript": "..." } ] } ] }
            Object resultsObj = res.get("results");
            if (!(resultsObj instanceof List<?> results) || results.isEmpty()) return "";

            StringBuilder sb = new StringBuilder();
            for (Object r : results) {
                if (!(r instanceof Map<?, ?> resultMap)) continue;
                Object altsObj = resultMap.get("alternatives");
                if (!(altsObj instanceof List<?> alts) || alts.isEmpty()) continue;
                Object first = alts.get(0);
                if (!(first instanceof Map<?, ?> altMap)) continue;
                Object t = altMap.get("transcript");
                if (t == null) continue;
                String text = t.toString().trim();
                if (!text.isEmpty()) {
                    if (sb.length() > 0) sb.append(' ');
                    sb.append(text);
                }
            }

            String recognizedText = sb.toString();
            log.info("IBM STT 변환 결과: '{}'", recognizedText);
            return recognizedText;

        } catch (Exception e) {
            log.error("IBM STT 호출 중 오류 발생: {}", e.getMessage(), e);
            // 에러 발생 시에도 빈 문자열 또는 기본 대체 문장을 반환하여 서비스 중단 방지
            return "";
        }
    }

    /** IBM Speech Basic 인증 생성 */
    private String basicAuth() {
        String raw = "apikey:" + props.getSttApiKey();
        String encoded = Base64.getEncoder()
                .encodeToString(raw.getBytes(StandardCharsets.UTF_8));
        return "Basic " + encoded;
    }
}