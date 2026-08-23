package com.malrota.client;

import com.malrota.config.IbmSpeechProperties;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;

/**
 * IBM Speech to Text 호출 담당 (음성 → 텍스트).
 * 별도 라이브러리 없이 Spring 기본 기능만 사용합니다.
 */
@Component
public class IbmSttClient {

    private final IbmSpeechProperties props;
    private final RestClient http = RestClient.create();

    public IbmSttClient(IbmSpeechProperties props) {
        this.props = props;
    }

    /**
     * 오디오 바이트 → 한국어 텍스트
     */
    @SuppressWarnings("unchecked")
    public String transcribe(byte[] audio, String contentType) {
        if (!props.isSttEnabled()) {
            throw new IllegalStateException(
                    "STT가 비활성화되어 있습니다. .env의 IBM_STT_ENABLED=true 로 바꾸세요.");
        }

        String url = props.getSttUrl()
                + "/v1/recognize?model=" + props.getSttModel()
                + "&smart_formatting=true";

        Map<String, Object> res = http.post()
                .uri(url)
                .header(HttpHeaders.AUTHORIZATION, basicAuth())
                .header(HttpHeaders.CONTENT_TYPE, contentType)
                .body(audio)
                .retrieve()
                .body(Map.class);

        if (res == null) return "";

        // 응답 구조: { "results": [ { "alternatives": [ { "transcript": "..." } ] } ] }
        Object resultsObj = res.get("results");
        if (!(resultsObj instanceof List<?> results)) return "";

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
        return sb.toString();
    }

    /** IBM Speech 는 사용자명 "apikey" + 비밀번호에 API Key 를 넣는 Basic 인증을 씁니다. */
    private String basicAuth() {
        String raw = "apikey:" + props.getSttApiKey();
        String encoded = Base64.getEncoder()
                .encodeToString(raw.getBytes(StandardCharsets.UTF_8));
        return "Basic " + encoded;
    }
}