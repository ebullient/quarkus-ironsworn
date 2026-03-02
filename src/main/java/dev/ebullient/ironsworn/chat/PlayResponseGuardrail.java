package dev.ebullient.ironsworn.chat;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.guardrail.OutputGuardrail;
import dev.langchain4j.guardrail.OutputGuardrailResult;
import io.quarkus.logging.Log;

@ApplicationScoped
public class PlayResponseGuardrail implements OutputGuardrail {

    @Inject
    ObjectMapper objectMapper;

    @ConfigProperty(name = "ironsworn.narrate.choice-prompt", defaultValue = "false")
    boolean choicePrompt;

    @Override
    public OutputGuardrailResult validate(AiMessage responseFromLLM) {
        Log.debugf("Validate play response");
        // Tool call responses have no text — let them pass through for tool execution
        if (responseFromLLM.hasToolExecutionRequests()) {
            return OutputGuardrailResult.successWith(responseFromLLM);
        }
        try {
            PlayResponse response = objectMapper.readValue(responseFromLLM.text(), PlayResponse.class);
            if (response.narrative() == null || response.narrative().isBlank()) {
                return reprompt("Missing narrative", new IllegalArgumentException("narrative is blank"),
                        """
                                Return a valid JSON object with fields: narrative (string), npcs (array), location (string), choices (array).
                                The narrative field MUST be non-empty markdown text (2-4 paragraphs).
                                """
                                .trim());
            }
            if (choicePrompt && (response.choices() == null || response.choices().isEmpty())) {
                return reprompt("Missing choices", new IllegalArgumentException("choices is empty"),
                        """
                                Return a valid JSON object with fields: narrative (string), npcs (array), location (string), choices (array).
                                The choices field MUST contain exactly 3 short suggestions for what the player could do next.
                                Each should be a brief player action (e.g. "Search the ruins for supplies").
                                """
                                .trim());
            }
            return OutputGuardrailResult.successWith(responseFromLLM.text(), response);
        } catch (JsonProcessingException e) {
            return reprompt("Invalid JSON", e,
                    "Make sure you return a valid JSON object following the specified format");
        }
    }
}
