package com.malrota.client;

import com.ibm.watsonx.ai.chat.ChatService;
import com.ibm.watsonx.ai.chat.model.AssistantMessage;
import com.malrota.config.WatsonxProperties;
import org.springframework.stereotype.Component;

@Component
public class WatsonxClient {

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