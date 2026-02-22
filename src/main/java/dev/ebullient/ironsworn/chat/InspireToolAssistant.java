package dev.ebullient.ironsworn.chat;

import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.guardrail.OutputGuardrails;
import io.quarkiverse.langchain4j.RegisterAiService;

@RegisterAiService(chatMemoryProviderSupplier = RegisterAiService.BeanChatMemoryProviderSupplier.class, tools = OracleTool.class)
@OutputGuardrails(PlayResponseGuardrail.class)
public interface InspireToolAssistant {

    @SystemMessage(fromResource = "prompts/play-inspire-tool-system.txt")
    @UserMessage(fromResource = "prompts/play-inspire-tool-user.txt")
    PlayResponse inspire(
            @MemoryId String campaignId,
            String characterContext,
            String journalContext,
            String memoryContext);
}
