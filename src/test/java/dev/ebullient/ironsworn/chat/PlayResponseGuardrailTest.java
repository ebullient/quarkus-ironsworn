package dev.ebullient.ironsworn.chat;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.guardrail.OutputGuardrailResult;

class PlayResponseGuardrailTest {

    @Test
    void validate_missingNarrative_reprompts() {
        PlayResponseGuardrail guardrail = new PlayResponseGuardrail();
        guardrail.objectMapper = new ObjectMapper();

        OutputGuardrailResult result = guardrail.validate(AiMessage.from("{\"location\":\"turning_point\"}"));
        assertTrue(result.isReprompt());
        assertTrue(result.getReprompt().isPresent());
    }

    @Test
    void validate_validResponse_succeeds() {
        PlayResponseGuardrail guardrail = new PlayResponseGuardrail();
        guardrail.objectMapper = new ObjectMapper();

        OutputGuardrailResult result = guardrail
                .validate(AiMessage.from("{\"narrative\":\"Test\",\"npcs\":[],\"location\":\"The Deep Wood\"}"));
        assertFalse(result.isReprompt());
        assertInstanceOf(PlayResponse.class, result.successfulResult());
    }
}
