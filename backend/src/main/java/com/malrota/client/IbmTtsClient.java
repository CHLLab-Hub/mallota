package com.malrota.client;

import com.malrota.config.IbmSpeechProperties;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
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
@Component
public class IbmTtsClient {

    private final IbmSpeechProperties props;
    private final RestClient http = RestClient.create();

    public IbmTtsClient(IbmSpeechProperties props) {
        this.props = props;
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

        return http.post()
                .uri(url)
                .header(HttpHeaders.AUTHORIZATION, basicAuth())
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .header(HttpHeaders.ACCEPT, "audio/mp3")
                .body(Map.of("text", text))
                .retrieve()
                .body(byte[].class);
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
