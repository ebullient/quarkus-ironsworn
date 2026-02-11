package dev.ebullient.ironsworn.chat;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.guardrail.OutputGuardrails;
import io.quarkiverse.langchain4j.RegisterAiService;

@RegisterAiService(chatMemoryProviderSupplier = RegisterAiService.NoChatMemoryProviderSupplier.class)
@OutputGuardrails(JsonChatResponseGuardrail.class)
public interface ChatAssistant {

    @SystemMessage("""
            You are a helpful AI assistant.

            Be conversational and friendly. Provide clear, concise answers.
            When uncertain, say so rather than guessing.
            """)
    JsonChatResponse chat(@UserMessage String userMessage);

    @SystemMessage("""
            You are an Ironsworn assistant. Help players understand the rules,
            mechanics, and setting of the Ironsworn tabletop RPG. Answer questions
            about moves, oracles, character creation, and the Ironlands setting.
            """)
    JsonChatResponse rules(@UserMessage String userMessage);
}
