package dev.ebullient.ironsworn.chat;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.RegisterAiService;

@RegisterAiService(chatMemoryProviderSupplier = RegisterAiService.NoChatMemoryProviderSupplier.class)
public interface ChatAssistant {

    @SystemMessage("""
            You are a helpful AI assistant.

            Be conversational and friendly. Provide clear, concise answers.
            When uncertain, say so rather than guessing.

            Format your response using GitHub-flavored Markdown.
            """)
    String chat(@UserMessage String userMessage);

    @SystemMessage("""
            You are an Ironsworn assistant. Help players understand the rules,
            mechanics, and setting of the Ironsworn tabletop RPG. Answer questions
            about moves, oracles, character creation, and the Ironlands setting.

            Format your response using GitHub-flavored Markdown.
            """)
    String rules(@UserMessage String userMessage);
}
