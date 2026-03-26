package dev.ebullient.ironsworn.chat.oracle;

import dev.ebullient.ironsworn.memory.StoryMemoryTool;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import io.quarkiverse.langchain4j.RegisterAiService;

@RegisterAiService(chatMemoryProviderSupplier = RegisterAiService.BeanChatMemoryProviderSupplier.class, tools = {
        OracleTool.class, StoryMemoryTool.class })
public interface InspireToolAssistant {

    @SystemMessage(fromResource = "prompts/play-narrator-system.txt")
    @UserMessage(fromResource = "prompts/play-inspire-tool-user.txt")
    String inspire(
            @MemoryId String campaignId,
            String characterContext,
            String journalContext,
            String memoryContext);
}
