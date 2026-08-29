package com.mallota.client;

/** 음성 인식(STT) 공급자 공통 계약 — IBM/Clova 등 어떤 벤더를 쓰든 VoiceController는 이 형태만 알면 된다. */
public interface SttClient {

    /**
     * 오디오 바이트 → 한국어 텍스트 변환.
     *
     * @param audio       녹음된 오디오 바이트
     * @param contentType 브라우저가 보낸 오디오 MIME 타입 (예: "audio/webm;codecs=opus")
     * @return 인식된 텍스트. 진짜 무음이면 빈 문자열.
     */
    String transcribe(byte[] audio, String contentType);
}
