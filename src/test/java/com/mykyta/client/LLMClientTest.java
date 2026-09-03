package com.mykyta.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mykyta.model.Confidence;
import com.mykyta.model.LLMMessage;
import com.mykyta.response.AssistantResponse;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LLMClientTest {

    private final LLMClient client = new LLMClient(
            "http://localhost:11434",
            "test-model",
            new ObjectMapper(),
            Map.of()
    );

    @Test
    void parsesStructuredAgentAnswer() throws Exception {
        AssistantResponse response = client.parseAssistantResponse(
                new LLMMessage("assistant", "{\"answer\":\"Done\",\"confidence\":\"HIGH\"}")
        );

        assertEquals("Done", response.answer());
        assertEquals(Confidence.HIGH, response.confidence());
    }

    @Test
    void preservesPlainTextWhenModelIgnoresJsonInstruction() throws Exception {
        AssistantResponse response = client.parseAssistantResponse(
                new LLMMessage("assistant", "A useful plain-text answer")
        );

        assertEquals("A useful plain-text answer", response.answer());
        assertEquals(Confidence.MEDIUM, response.confidence());
    }

    @Test
    void rejectsEmptyFinalAgentMessage() {
        assertThrows(IOException.class, () -> client.parseAssistantResponse(new LLMMessage("assistant", " ")));
    }
}
