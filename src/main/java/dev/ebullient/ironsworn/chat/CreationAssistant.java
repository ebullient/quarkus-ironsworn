package dev.ebullient.ironsworn.chat;

import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.guardrail.OutputGuardrails;
import io.quarkiverse.langchain4j.RegisterAiService;

@RegisterAiService(chatMemoryProviderSupplier = RegisterAiService.BeanChatMemoryProviderSupplier.class)
@OutputGuardrails(CreationResponseGuardrail.class)
public interface CreationAssistant {

    @SystemMessage(fromResource = "prompts/creation-guide-system.txt")
    @UserMessage(fromResource = "prompts/creation-guide-user.txt")
    CreationResponse guide(
            @MemoryId String sessionId,
            String name,
            int edge,
            int heart,
            int iron,
            int shadow,
            int wits,
            String journalContext,
            int exchangeCount,
            String playerInput,
            String vowInstruction);

    @SystemMessage(fromResource = "prompts/creation-inspire-system.txt")
    @UserMessage(fromResource = "prompts/creation-inspire-user.txt")
    CreationResponse inspire(@MemoryId String sessionId, String name);
}
