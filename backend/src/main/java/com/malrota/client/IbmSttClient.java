package com.malrota.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.malrota.config.IbmSpeechProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
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
    // 타임아웃을 안 걸어두면 IBM 서버가 응답이 느릴 때 요청이 무한정 걸려서, 프론트 화면이
    // "인식 중..."에서 영영 멈춰버린다. 연결/응답 각각 상한을 둬서 실패라도 빨리 나게 한다.
    private final RestClient http = RestClient.builder().requestFactory(timeoutRequestFactory()).build();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public IbmSttClient(IbmSpeechProperties props) {
        this.props = props;
    }

    private static SimpleClientHttpRequestFactory timeoutRequestFactory() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(10_000);
        factory.setReadTimeout(20_000);
        return factory;
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
        //    ⚠ keywords/keywords_threshold(터미널명 부스팅)와 word_alternatives_threshold를
        //    한때 추가했었는데, ko-KR_Multimedia 모델이 keywords 계열을 지원하지 않아 요청 자체가
        //    400으로 거부되어 STT가 완전히 먹통이 되는 걸 실사용으로 확인했다. 지금 모델에서
        //    확실히 지원되는 기본 파라미터만 남긴다. 도메인 단어 부스팅은 이 기능을 지원하는
        //    모델을 실제로 검증한 뒤에 다시 추가해야 한다.
        String baseUrl = props.getSttUrl().replaceAll("/+$", "");
        String url = String.format(
                "%s/v1/recognize?model=%s&smart_formatting=true&background_audio_suppression=0.5" +
                        "&end_of_phrase_silence_time=0.8",
                baseUrl, model
        );

        // 3. Content-Type 정규화 (e.g. "audio/webm;codecs=opus" -> "audio/webm")
        String normalizedContentType = (contentType != null && !contentType.isBlank())
                ? contentType.split(";")[0].trim()
                : "audio/webm";

        log.info("IBM STT 호출 시작: URL={}, Content-Type={}", url, normalizedContentType);

        // 예외를 여기서 삼키지 않는다 — 인증 실패·네트워크 오류 같은 "진짜 오류"를 "그냥 무음"과
        // 똑같이 빈 문자열로 뭉개버리면, 사용자 입장에서는 분명히 말을 했는데 왜 아무 반응이 없는지
        // 전혀 알 길이 없다. 여기서 던져진 예외는 GlobalExceptionHandler가 받아서 프론트에 정상적인
        // 오류 응답으로 내려주고, 프론트는 이미 그걸 "음성 인식에 실패했습니다. 다시 시도해 주세요."로
        // 보여주도록 되어 있다. 아래에서 빈 문자열을 돌려주는 곳은 오직 "정상 응답인데 인식된 말이
        // 없는" 진짜 무음 상황뿐이다.
        //
        // 응답을 항상 원문 텍스트로 먼저 받는다 — Content-Type이 우리 예상(JSON)과 다르면
        // (예: IBM이 오류를 text/html로 내려줄 때) .body(Map.class)로 바로 받으면 무슨 내용인지
        // 알 수도 없이 UnknownContentTypeException만 던지고 끝나버려서 원인을 알 수가 없다.
        String rawBody = http.post()
                .uri(url)
                .header(HttpHeaders.AUTHORIZATION, basicAuth())
                .header(HttpHeaders.CONTENT_TYPE, normalizedContentType)
                .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .body(audio)
                .exchange((request, response) -> {
                    byte[] bytes = response.getBody().readAllBytes();
                    String body = new String(bytes, StandardCharsets.UTF_8);
                    if (!response.getStatusCode().is2xxSuccessful()) {
                        log.error("IBM STT 오류 응답 (status={}): {}", response.getStatusCode(), body);
                        throw new IllegalStateException("IBM STT 호출 실패 (status=" + response.getStatusCode() + ")");
                    }
                    return body;
                });

        if (rawBody == null || rawBody.isBlank()) {
            log.warn("IBM STT 응답 본문이 비어 있습니다.");
            return "";
        }

        Map<String, Object> res;
        try {
            res = objectMapper.readValue(rawBody, Map.class);
        } catch (Exception e) {
            log.error("IBM STT 응답을 JSON으로 해석하지 못했습니다. 원본 응답: {}", rawBody, e);
            throw new IllegalStateException("IBM STT 응답 형식을 해석할 수 없습니다.", e);
        }

        // 응답 구조 파싱: { "results": [ { "alternatives": [ { "transcript": "..." } ] } ] }
        Object resultsObj = res.get("results");
        if (!(resultsObj instanceof List<?> results) || results.isEmpty()) {
            // 원본 응답을 그대로 남겨서, 진짜 무음인지 IBM이 다른 형태의 오류를 200으로 내려준
            // 것인지 나중에 로그로 구분할 수 있게 한다.
            log.warn("IBM STT가 인식된 말을 찾지 못했습니다. 원본 응답: {}", res);
            return "";
        }

        StringBuilder sb = new StringBuilder();
        for (Object r : results) {
            if (!(r instanceof Map<?, ?> resultMap)) continue;
            String text = firstAlternativeText(resultMap);
            if (text != null && !text.isEmpty()) {
                if (sb.length() > 0) sb.append(' ');
                sb.append(text);
            }
        }

        String recognizedText = sb.toString();
        log.info("IBM STT 변환 결과: '{}'", recognizedText);
        return recognizedText;
    }

    /** 한 발화 구간(result)의 1등 인식 후보 텍스트 */
    private String firstAlternativeText(Map<?, ?> resultMap) {
        Object altsObj = resultMap.get("alternatives");
        if (!(altsObj instanceof List<?> alts) || alts.isEmpty()) return null;
        if (!(alts.get(0) instanceof Map<?, ?> firstAlt)) return null;

        Object t = firstAlt.get("transcript");
        return t == null ? null : t.toString().trim();
    }

    /** IBM Speech Basic 인증 생성 */
    private String basicAuth() {
        String raw = "apikey:" + props.getSttApiKey();
        String encoded = Base64.getEncoder()
                .encodeToString(raw.getBytes(StandardCharsets.UTF_8));
        return "Basic " + encoded;
    }
}
