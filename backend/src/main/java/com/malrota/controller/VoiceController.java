package com.malrota.controller;

import com.malrota.client.IbmSttClient;
import com.malrota.client.IbmTtsClient;
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
 * IBM 키는 백엔드에만 있으므로 프론트에 키가 노출되지 않습니다.
 */
@RestController
@RequestMapping("/api/voice")
public class VoiceController {

    private final IbmSttClient stt;
    private final IbmTtsClient tts;

    public VoiceController(IbmSttClient stt, IbmTtsClient tts) {
        this.stt = stt;
        this.tts = tts;
    }

    /** 음성 → 텍스트 */
    @PostMapping(value = "/stt", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, String> speechToText(@RequestParam("audio") MultipartFile audio) throws IOException {
        String contentType = audio.getContentType() == null ? "audio/webm" : audio.getContentType();
        String transcript = stt.transcribe(audio.getBytes(), contentType);
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