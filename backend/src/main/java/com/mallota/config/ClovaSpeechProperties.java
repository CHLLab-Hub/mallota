package com.mallota.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 네이버 CLOVA Speech 설정.
 * backend/.env 의 CLOVA_STT_* 값을 자바로 읽어옵니다.
 * (키 값 자체는 .env 에만 있고 코드에는 없습니다 — 팀 규칙)
 *
 * NCP 콘솔의 "CLOVA Speech" 앱에서 발급받은 Invoke URL과 Secret Key를 그대로 넣으면 됩니다.
 * Invoke URL 예: https://clovaspeech-gw.ncloud.com/external/v1/{appId}/{domainId}
 */
@Component
public class ClovaSpeechProperties {

    @Value("${CLOVA_STT_ENABLED:false}")
    private boolean sttEnabled;

    @Value("${CLOVA_STT_SECRET:}")
    private String secretKey;

    @Value("${CLOVA_STT_INVOKE_URL:}")
    private String invokeUrl;

    public boolean isSttEnabled() { return sttEnabled; }
    public String getSecretKey() { return secretKey; }
    public String getInvokeUrl() { return invokeUrl; }
}
