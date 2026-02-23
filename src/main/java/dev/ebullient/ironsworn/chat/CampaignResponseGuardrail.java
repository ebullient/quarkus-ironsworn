package dev.ebullient.ironsworn.chat;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.guardrail.OutputGuardrail;
import dev.langchain4j.guardrail.OutputGuardrailResult;

@ApplicationScoped
public class CampaignResponseGuardrail implements OutputGuardrail {

    public static final String REPROMPT_MESSAGE = "Invalid JSON";
    public static final String MISSING_RESPONSE_MESSAGE = "Missing response field";

    public static final String REPROMPT_PROMPT = "Make sure you return a valid JSON object following the specified format";
    public static final String MISSING_RESPONSE_PROMPT = "Your JSON response must include a non-empty 'response' field containing your answer in markdown format. Do not put your answer in the thinking field.";

    @Inject
    ObjectMapper objectMapper;

    @Override
    public OutputGuardrailResult validate(AiMessage responseFromLLM) {
        try {
            CampaignResponse response = objectMapper.readValue(responseFromLLM.text(), CampaignResponse.class);
            if (response.response() == null || response.response().isBlank()) {
                return reprompt(MISSING_RESPONSE_MESSAGE, MISSING_RESPONSE_PROMPT);
            }
            return OutputGuardrailResult.successWith(responseFromLLM.text(), response);
        } catch (JsonProcessingException e) {
            return reprompt(REPROMPT_MESSAGE, e, REPROMPT_PROMPT);
        }
    }
}
