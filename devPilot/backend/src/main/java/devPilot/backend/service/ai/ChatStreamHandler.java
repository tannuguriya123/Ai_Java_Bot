package devPilot.backend.service.ai;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import devPilot.backend.dto.ChatMessageResponse;
import devPilot.backend.dto.CitationDto;
import devPilot.backend.entity.ChatMessage;
import devPilot.backend.entity.MessageRole;
import devPilot.backend.repository.ChatMessageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Generation step: call Gemini via Spring AI and stream tokens to the browser over SSE.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ChatStreamHandler {

    private final ChatModel chatModel;
    private final ChatMessageRepository chatMessageRepository;
    private final CitationMapper citationMapper;

    public SseEmitter stream(
            UUID sessionId,
            ChatMessageResponse savedUserMessage,
            List<CitationDto> citations,
            String systemPrompt,
            String userPrompt) {

        SseEmitter emitter = new SseEmitter(RagSettings.STREAM_TIMEOUT_MS);
        StringBuilder fullReply = new StringBuilder();

        try {
            emitter.send(SseEmitter.event()
                    .name("user_message")
                    .data(savedUserMessage));

            ChatClient.builder(chatModel)
                    .build()
                    .prompt()
                    .system(systemPrompt)
                    .user(userPrompt)
                    .stream()
                    .content()
                    .subscribe(
                            token -> appendToken(emitter, fullReply, token),
                            error -> sendError(emitter, error),
                            () -> completeStream(emitter, sessionId, fullReply, citations));
        } catch (Exception ex) {
            sendError(emitter, ex);
        }

        return emitter;
    }

    private void appendToken(SseEmitter emitter, StringBuilder fullReply, String token) {
        fullReply.append(token);
        try {
            emitter.send(SseEmitter.event()
                    .name("token")
                    .data(token, MediaType.APPLICATION_JSON));
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    private void completeStream(
            SseEmitter emitter,
            UUID sessionId,
            StringBuilder fullReply,
            List<CitationDto> citations) {
        try {
            ChatMessage assistant = chatMessageRepository.save(ChatMessage.builder()
                    .sessionId(sessionId)
                    .role(MessageRole.ASSISTANT)
                    .content(fullReply.toString())
                    .citations(citationMapper.toJson(citations))
                    .build());

            emitter.send(SseEmitter.event()
                    .name("assistant_message")
                    .data(toMessageResponse(assistant)));
            emitter.send(SseEmitter.event().name("done").data("[DONE]"));
            emitter.complete();
        } catch (Exception ex) {
            sendError(emitter, ex);
        }
    }

    private void sendError(SseEmitter emitter, Throwable error) {
        log.error("Chat response generation failed", error);
        try {
            emitter.send(SseEmitter.event()
                    .name("error")
                    .data(Map.of("message", userFacingError(error)), MediaType.APPLICATION_JSON));
            emitter.complete();
        } catch (Exception sendError) {
            log.debug("Could not deliver chat error event because the client disconnected", sendError);
            emitter.completeWithError(sendError);
        }
    }

    static String userFacingError(Throwable error) {
        StringBuilder details = new StringBuilder();
        for (Throwable current = error; current != null; current = current.getCause()) {
            if (current.getMessage() != null) {
                details.append(' ').append(current.getMessage());
            }
        }
        String message = details.toString().toLowerCase(java.util.Locale.ROOT);

        if (message.contains("quota exceeded")
                || message.contains("resource_exhausted")
                || message.contains("quota")) {
            return "Gemini's request quota is exhausted for this API project. Wait for the quota to reset or enable billing/increase the Gemini API quota, then retry.";
        }
        if (message.contains("429") || message.contains("rate limit")) {
            return "Gemini is receiving too many requests. Wait a little, then retry.";
        }
        if (message.contains("503")
                || message.contains("high demand")
                || message.contains("service unavailable")) {
            return "Gemini is temporarily unavailable. Please try again shortly.";
        }
        return "The AI could not generate a reply. Please try again shortly.";
    }

    private ChatMessageResponse toMessageResponse(ChatMessage message) {
        return new ChatMessageResponse(
                message.getId(),
                message.getRole(),
                message.getContent(),
                citationMapper.fromJson(message.getCitations()),
                message.getCreatedAt());
    }
}
