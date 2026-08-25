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

    public String ask(String prompt) {
        AssistantMessage response = chatService()
                .chat(prompt)
                .toAssistantMessage();
        return response.content();
    }

    public boolean isConfigured() {
        return properties.enabled()
                && properties.apiKey() != null && !properties.apiKey().isBlank()
                && properties.projectId() != null && !properties.projectId().isBlank();
    }
}
