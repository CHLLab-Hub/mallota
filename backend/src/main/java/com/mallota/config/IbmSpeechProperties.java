package com.mallota.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * IBM Speech 설정.
 * backend/.env 의 IBM_STT_*, IBM_TTS_* 값을 자바로 읽어옵니다.
 * (키 값 자체는 .env 에만 있고 코드에는 없습니다 — 팀 규칙)
 */
@Component
public class IbmSpeechProperties {

    @Value("${IBM_STT_ENABLED:false}")
    private boolean sttEnabled;

    @Value("${IBM_STT_API_KEY:}")
    private String sttApiKey;

    @Value("${IBM_STT_URL:}")
    private String sttUrl;

    @Value("${IBM_TTS_ENABLED:false}")
    private boolean ttsEnabled;

    @Value("${IBM_TTS_API_KEY:}")
    private String ttsApiKey;

    @Value("${IBM_TTS_URL:}")
    private String ttsUrl;

    /** 한국어 STT 모델 (마이크 녹음은 Multimedia 권장) */
    @Value("${IBM_STT_MODEL:ko-KR_Multimedia}")
    private String sttModel;

    /** 한국어 TTS 음성 */
    @Value("${IBM_TTS_VOICE:ko-KR_JinV3Voice}")
    private String ttsVoice;

    public boolean isSttEnabled() { return sttEnabled; }
    public String getSttApiKey() { return sttApiKey; }
    public String getSttUrl() { return sttUrl; }
    public String getSttModel() { return sttModel; }

    public boolean isTtsEnabled() { return ttsEnabled; }
    public String getTtsApiKey() { return ttsApiKey; }
    public String getTtsUrl() { return ttsUrl; }
    public String getTtsVoice() { return ttsVoice; }
}