package com.malrota.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.malrota.config.ClovaSpeechProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * 네이버 CLOVA Speech(장문 인식) 호출 담당 (음성 → 텍스트).
 *
 * IBM Watson STT가 사투리/어눌한 발화를 잘 못 알아듣는다는 문제 때문에, 같은 {@link SttClient}
 * 계약으로 교체 투입할 수 있게 만들었다. VoiceController가 STT_PROVIDER 값으로 IBM/CLOVA 중
 * 어느 쪽을 쓸지 고른다.
 *
 * ⚠ 아직 실제 NCP 발급 키로 검증 전이다. 여기 쓰인 업로드 경로("/recognizer/upload")와 응답의
 * "text" 필드는 CLOVA Speech(장문 인식, Long Sentence Recognition) 공식 문서 기준으로 작성했다.
 * 실제 호출에서 404/400이 나면 NCP 콘솔의 "CLOVA Speech" 앱 예제 코드와 이 두 부분부터 맞춰봐야
 * 한다. 브라우저 녹음 포맷(webm/opus)을 CLOVA가 그대로 받아주는지도 라이브로 확인이 필요 —
 * 거부되면 서버에서 wav 등으로 변환하는 단계가 추가로 필요할 수 있다.
 */
@Slf4j
@Component
public class ClovaSttClient implements SttClient {

    private final ClovaSpeechProperties props;
    // 타임아웃을 안 걸어두면 응답이 느릴 때 요청이 무한정 걸려서 프론트가 "인식 중..."에서
    // 영영 멈춰버린다 (IBM 클라이언트에서 겪었던 것과 같은 문제).
    private final RestClient http = RestClient.builder().requestFactory(timeoutRequestFactory()).build();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ClovaSttClient(ClovaSpeechProperties props) {
        this.props = props;
    }

    private static SimpleClientHttpRequestFactory timeoutRequestFactory() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(10_000);
        factory.setReadTimeout(20_000);
        return factory;
    }

    @Override
    @SuppressWarnings("unchecked")
    public String transcribe(byte[] audio, String contentType) {
        if (!props.isSttEnabled()) {
            log.warn("CLOVA STT가 비활성화되어 있습니다.");
            return "내일 오전 서울에서 대전 가는 버스 찾아줘"; // 데모 안전용 Mock Fallback
        }

        String url = props.getInvokeUrl().replaceAll("/+$", "") + "/recognizer/upload";

        MultipartBodyBuilder builder = new MultipartBodyBuilder();
        // completion=sync: 콜백 URL 없이 이 응답에 결과를 바로 담아 돌려받는다.
        builder.part("params", "{\"language\":\"ko-KR\",\"completion\":\"sync\"}", MediaType.APPLICATION_JSON);
        builder.part("media", new ByteArrayResource(audio))
                .filename("audio" + fileExtension(contentType))
                .contentType(MediaType.parseMediaType(normalize(contentType)));

        log.info("CLOVA STT 호출 시작: URL={}, Content-Type={}", url, contentType);

        // IBM 클라이언트와 같은 이유로, 상태 코드/본문을 먼저 원문으로 받아서 실패 원인을 남긴다.
        String rawBody = http.post()
                .uri(url)
                .header("X-CLOVASPEECH-API-KEY", props.getSecretKey())
                .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .body(builder.build())
                .exchange((request, response) -> {
                    byte[] bytes = response.getBody().readAllBytes();
                    String body = new String(bytes, StandardCharsets.UTF_8);
                    if (!response.getStatusCode().is2xxSuccessful()) {
                        log.error("CLOVA STT 오류 응답 (status={}): {}", response.getStatusCode(), body);
                        throw new IllegalStateException("CLOVA STT 호출 실패 (status=" + response.getStatusCode() + ")");
                    }
                    return body;
                });

        if (rawBody == null || rawBody.isBlank()) {
            log.warn("CLOVA STT 응답 본문이 비어 있습니다.");
            return "";
        }

        Map<String, Object> res;
        try {
            res = objectMapper.readValue(rawBody, Map.class);
        } catch (Exception e) {
            log.error("CLOVA STT 응답을 JSON으로 해석하지 못했습니다. 원본 응답: {}", rawBody, e);
            throw new IllegalStateException("CLOVA STT 응답 형식을 해석할 수 없습니다.", e);
        }

        Object text = res.get("text");
        String recognizedText = text == null ? "" : text.toString().trim();
        log.info("CLOVA STT 변환 결과: '{}'", recognizedText);
        return recognizedText;
    }

    /** 브라우저가 보낸 MIME 타입에서 세미콜론 뒤 코덱 정보를 제거 (예: "audio/webm;codecs=opus" -> "audio/webm") */
    private String normalize(String contentType) {
        return (contentType != null && !contentType.isBlank())
                ? contentType.split(";")[0].trim()
                : "audio/webm";
    }

    private String fileExtension(String contentType) {
        String base = normalize(contentType);
        if (base.contains("wav")) return ".wav";
        if (base.contains("mp3") || base.contains("mpeg")) return ".mp3";
        if (base.contains("ogg")) return ".ogg";
        return ".webm";
    }
}
