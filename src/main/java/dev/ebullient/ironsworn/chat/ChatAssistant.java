package dev.ebullient.ironsworn.chat;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.guardrail.OutputGuardrails;
import io.quarkiverse.langchain4j.RegisterAiService;

@RegisterAiService(chatMemoryProviderSupplier = RegisterAiService.NoChatMemoryProviderSupplier.class)
@OutputGuardrails(JsonChatResponseGuardrail.class)
public interface ChatAssistant {

    @SystemMessage(fromResource = "prompts/chat-chat-system.txt")
    JsonChatResponse chat(@UserMessage String userMessage);

    @SystemMessage(fromResource = "prompts/chat-rules-system.txt")
    JsonChatResponse rules(@UserMessage String userMessage);
}
