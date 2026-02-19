package dev.ebullient.ironsworn.chat;

import java.util.concurrent.ConcurrentHashMap;

import jakarta.inject.Singleton;

import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;

@Singleton
public class PlayMemoryProvider implements ChatMemoryProvider {

    private final ConcurrentHashMap<Object, ChatMemory> memories = new ConcurrentHashMap<>();

    @Override
    public ChatMemory get(Object memoryId) {
        return memories.computeIfAbsent(memoryId,
                id -> MessageWindowChatMemory.withMaxMessages(20));
    }

    public void clear(Object memoryId) {
        memories.remove(memoryId);
    }
}
