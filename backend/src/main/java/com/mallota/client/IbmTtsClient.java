package com.mallota.client;

import com.mallota.config.IbmSpeechProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

/**
 * IBM Text to Speech 호출 담당 (텍스트 → 음성).
 *
 * 안내 문장을 한국어 음성(mp3)으로 만들어 프론트에 전달합니다.
 * 고령층 대상이므로 또렷한 한국어 음성을 사용합니다.
 */
@Slf4j
@Component
public class IbmTtsClient {

    private final IbmSpeechProperties props;
    // 타임아웃이 없으면 IBM 서버 응답이 느릴 때 무한정 걸려서 안내 음성이 영영 안 나올 수 있다.
    private final RestClient http = RestClient.builder().requestFactory(timeoutRequestFactory()).build();

    public IbmTtsClient(IbmSpeechProperties props) {
        this.props = props;
    }

    private static SimpleClientHttpRequestFactory timeoutRequestFactory() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(10_000);
        factory.setReadTimeout(20_000);
        return factory;
    }

    /**
     * 텍스트 → 음성(mp3 바이트)
     *
     * @param text 읽어줄 한국어 문장
     * @return mp3 오디오 바이트
     */
    public byte[] synthesize(String text) {
        if (!props.isTtsEnabled()) {
            throw new IllegalStateException("TTS가 비활성화되어 있습니다. .env의 IBM_TTS_ENABLED=true 로 바꾸세요.");
        }

        String url = props.getTtsUrl()
                + "/v1/synthesize?voice=" + props.getTtsVoice();

        // .retrieve().body(byte[].class)로 바로 받으면 IBM이 오류를 낼 때(예: 401/400, 또는
        // audio/mp3가 아닌 text/html·application/json으로 응답할 때) 원인을 전혀 알 수 없는
        // 통짜 예외만 던지고 끝나버린다 — STT 쪽에서 겪었던 것과 똑같은 진단 사각지대다. 항상
        // 응답을 원문 바이트로 먼저 받아서, 실패 시 상태 코드와 본문을 로그로 남긴다.
        return http.post()
                .uri(url)
                .header(HttpHeaders.AUTHORIZATION, basicAuth())
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .header(HttpHeaders.ACCEPT, "audio/mp3")
                .body(Map.of("text", text))
                .exchange((request, response) -> {
                    byte[] body = response.getBody().readAllBytes();
                    if (!response.getStatusCode().is2xxSuccessful()) {
                        String bodyPreview = new String(body, StandardCharsets.UTF_8);
                        log.error("IBM TTS 오류 응답 (status={}): {}", response.getStatusCode(), bodyPreview);
                        throw new IllegalStateException("IBM TTS 호출 실패 (status=" + response.getStatusCode() + ")");
                    }
                    return body;
                });
    }

    /** 프론트가 바로 재생할 수 있도록 base64 문자열로 변환 */
    public String synthesizeBase64(String text) {
        return Base64.getEncoder().encodeToString(synthesize(text));
    }

    private String basicAuth() {
        String raw = "apikey:" + props.getTtsApiKey();
        String encoded = Base64.getEncoder()
                .encodeToString(raw.getBytes(StandardCharsets.UTF_8));
        return "Basic " + encoded;
    }
}
