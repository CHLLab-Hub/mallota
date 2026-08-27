package com.malrota.client;

import com.ibm.watsonx.ai.chat.ChatService;
import com.ibm.watsonx.ai.chat.model.AssistantMessage;
import com.malrota.config.WatsonxProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class WatsonxClient {

    /**
     * SDK 기본 타임아웃(60초)이면 watsonx가 느려질 때 대화 한 턴이 통째로 그만큼 멈춰버린다.
     * 대부분의 입력은 룰베이스 추출기만으로도 충분하므로(예: "없어"), LLM이 이 시간 안에 응답하지
     * 않으면 빨리 실패시켜 룰베이스 결과로 대체되게 한다(ConversationParseService의 폴백 참고).
     */
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);

    private final WatsonxProperties properties;
    private ChatService chatService;

    public WatsonxClient(WatsonxProperties properties) {
        this.properties = properties;
    }

    private ChatService chatService() {
        if (chatService == null) {
            chatService = ChatService.builder()
                    .apiKey(properties.apiKey())
                    .projectId(properties.projectId())
                    .baseUrl(properties.url())
                    .modelId(properties.modelId())
                    .timeout(REQUEST_TIMEOUT)
                    .build();
        }
        return chatService;
    }

    /** 메인 질의 메서드 */
    public String ask(String prompt) {
        AssistantMessage response = chatService()
                .chat(prompt)
                .toAssistantMessage();
        return response.content();
    }

    /** generate() 호출 호환 메서드 */
    public String generate(String prompt) {
        return ask(prompt);
    }

    public boolean isConfigured() {
        return properties != null
                && properties.enabled()
                && properties.apiKey() != null && !properties.apiKey().isBlank()
                && properties.projectId() != null && !properties.projectId().isBlank();
    }
}