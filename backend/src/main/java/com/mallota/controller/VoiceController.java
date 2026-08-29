package com.mallota.controller;

import com.mallota.client.ClovaSttClient;
import com.mallota.client.IbmSttClient;
import com.mallota.client.IbmTtsClient;
import com.mallota.client.SttClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;

/**
 * 음성 입출력 API (프론트 ↔ 백엔드).
 *
 *  POST /api/voice/stt   오디오 → 텍스트   (프론트가 녹음 파일 전송)
 *  POST /api/voice/tts   텍스트 → 음성     (프론트가 재생할 mp3 base64 반환)
 *
 * IBM/CLOVA 키는 백엔드에만 있으므로 프론트에 키가 노출되지 않습니다.
 */
@RestController
@RequestMapping("/api/voice")
public class VoiceController {

    private final IbmSttClient ibmStt;
    private final ClovaSttClient clovaStt;
    private final IbmTtsClient tts;

    /** STT_PROVIDER=CLOVA 로 바꾸면 코드 변경 없이 네이버 CLOVA Speech로 전환된다 (기본값 IBM). */
    @Value("${STT_PROVIDER:IBM}")
    private String sttProvider;

    public VoiceController(IbmSttClient ibmStt, ClovaSttClient clovaStt, IbmTtsClient tts) {
        this.ibmStt = ibmStt;
        this.clovaStt = clovaStt;
        this.tts = tts;
    }

    private SttClient activeStt() {
        return "CLOVA".equalsIgnoreCase(sttProvider) ? clovaStt : ibmStt;
    }

    /** 음성 → 텍스트 */
    @PostMapping(value = "/stt", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, String> speechToText(@RequestParam("audio") MultipartFile audio) throws IOException {
        String contentType = audio.getContentType() == null ? "audio/webm" : audio.getContentType();
        String transcript = activeStt().transcribe(audio.getBytes(), contentType);
        return Map.of("transcript", transcript);
    }

    /** 텍스트 → 음성 (base64 mp3) */
    @PostMapping("/tts")
    public Map<String, String> textToSpeech(@RequestBody Map<String, String> body) {
        String text = body.getOrDefault("text", "").trim();
        if (text.isEmpty()) {
            return Map.of("error", "읽어줄 문장이 없습니다.");
        }
        return Map.of("audio", tts.synthesizeBase64(text));
    }
}