package com.mallota.client;

import com.ibm.watsonx.ai.chat.ChatService;
import com.ibm.watsonx.ai.chat.model.AssistantMessage;
import com.ibm.watsonx.ai.chat.model.ChatParameters;
import com.ibm.watsonx.ai.chat.model.UserMessage;
import com.mallota.config.WatsonxProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

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

    // STT 오인식 교정/조건 추출은 "그럴듯하게 창작"하면 안 되고 최대한 결정적이어야 한다 — 실제로
    // 보고된 사고: 기본 temperature로는 "경부"를 "경기"/"경주"/"명일"처럼 실제 존재하는 다른
    // 지명으로 "창의적으로" 잘못 교정하는 경우가 있었다. temperature를 0으로 낮춰 매번 가장
    // 확률 높은(=보수적인) 답을 고르게 한다.
    private static final ChatParameters DETERMINISTIC_PARAMETERS = ChatParameters.builder()
            .temperature(0.0)
            .build();

    /** 메인 질의 메서드 */
    public String ask(String prompt) {
        AssistantMessage response = chatService()
                .chat(List.of(UserMessage.text(prompt)), DETERMINISTIC_PARAMETERS)
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