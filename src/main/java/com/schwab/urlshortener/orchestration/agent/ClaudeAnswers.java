package com.schwab.urlshortener.orchestration.agent;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.converter.BeanOutputConverter;

/**
 * Calls the model and maps its answer to a record — robust to thinking blocks.
 *
 * <p>The model may return a thinking part before its answer, so a response can have several
 * generations. {@code .entity()} read only the first one (the thinking part, no text) and failed
 * with "No content to map". Here we take the last generation that actually contains text and
 * convert that with the same {@link BeanOutputConverter} {@code .entity()} uses internally.
 */
public final class ClaudeAnswers {

    private ClaudeAnswers() {}

    public static <T> T ask(ChatClient chatClient, String userText, Class<T> type) {
        BeanOutputConverter<T> converter = new BeanOutputConverter<>(type);
        ChatResponse response = chatClient.prompt()
                .user(userText + "\n\n" + converter.getFormat())
                .call()
                .chatResponse();
        String text = answerText(response);
        if (text == null) {
            String finish = response == null || response.getResults() == null || response.getResults().isEmpty()
                    ? "no results"
                    : String.valueOf(response.getResults().getLast().getMetadata().getFinishReason());
            throw new IllegalStateException("model returned no answer text (finish reason: " + finish + ")");
        }
        return converter.convert(text);
    }

    /** The last generation with non-blank text, or null if there is none. */
    public static String answerText(ChatResponse response) {
        if (response == null || response.getResults() == null) {
            return null;
        }
        return response.getResults().stream()
                .map(g -> g.getOutput().getText())
                .filter(t -> t != null && !t.isBlank())
                .reduce((first, second) -> second)
                .orElse(null);
    }
}
