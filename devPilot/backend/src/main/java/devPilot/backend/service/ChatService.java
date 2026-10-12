package devPilot.backend.service;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import devPilot.backend.dto.ChatMessageResponse;
import devPilot.backend.dto.ChatSessionResponse;
import devPilot.backend.dto.CreateChatSessionRequest;
import devPilot.backend.dto.CreateCourseChatSessionRequest;
import devPilot.backend.entity.ChatMessage;
import devPilot.backend.entity.ChatSession;
import devPilot.backend.entity.IndexStatus;
import devPilot.backend.entity.LearningCourse;
import devPilot.backend.entity.LearningUnit;
import devPilot.backend.entity.MessageRole;
import devPilot.backend.entity.Repository;
import devPilot.backend.exception.BadRequestException;
import devPilot.backend.exception.NotFoundException;
import devPilot.backend.repository.ChatMessageRepository;
import devPilot.backend.repository.ChatSessionRepository;
import devPilot.backend.service.ai.ChatPromptBuilder;
import devPilot.backend.service.ai.ChatStreamHandler;
import devPilot.backend.service.ai.CitationMapper;
import devPilot.backend.service.ai.CodeContextRetriever;
import devPilot.backend.repository.LearningUnitRepository;
import lombok.RequiredArgsConstructor;

/**
 * Chat sessions and the RAG chat pipeline entry point.
 *
 * <p>{@link #streamReply} orchestrates the full flow:
 * validate → save user message → retrieve code context → build prompts → stream AI reply.
 * Each step is implemented in a dedicated class under {@code service.ai}.
 */
@Service
@RequiredArgsConstructor
public class ChatService {

    private final ChatSessionRepository chatSessionRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final RepoService repoService;
    private final CodeContextRetriever codeContextRetriever;
    private final ChatPromptBuilder chatPromptBuilder;
    private final ChatStreamHandler chatStreamHandler;
    private final CitationMapper citationMapper;
    private final LearningService learningService;
    private final LearningUnitRepository learningUnitRepository;

    @Transactional
    public ChatSessionResponse createSession(UUID userId, CreateChatSessionRequest request) {
        Repository repo = repoService.requireOwned(request.repositoryId(), userId);
        if (repo.getIndexStatus() != IndexStatus.READY) {
            throw new BadRequestException("Repository must be indexed before chatting");
        }

        String title = request.title() != null && !request.title().isBlank()
                ? request.title()
                : "Chat with " + repo.getFullName();

        ChatSession session = ChatSession.builder()
                .userId(userId)
                .repositoryId(repo.getId())
                .title(title)
                .build();
        session = chatSessionRepository.save(session);
        return toSessionResponse(session);
    }

    @Transactional(readOnly = true)
    public List<ChatSessionResponse> listSessions(UUID userId, UUID repositoryId) {
        repoService.requireOwned(repositoryId, userId);
        return chatSessionRepository
                .findByUserIdAndRepositoryIdOrderByCreatedAtDesc(userId, repositoryId)
                .stream()
                .map(this::toSessionResponse)
                .toList();
    }

    @Transactional
    public ChatSessionResponse createCourseSession(
            UUID userId, CreateCourseChatSessionRequest request) {
        LearningCourse course = learningService.requireCourse(userId, request.courseId());
        LearningUnit unit = request.unitId() == null
                ? null
                : learningService.requireUnit(userId, course.getId(), request.unitId());
        if (unit != null && unit.getIndexStatus() != IndexStatus.READY) {
            throw new BadRequestException("Unit must finish indexing before chatting");
        }
        if (unit == null && learningUnitRepository
                .countByCourseIdAndUserIdAndIndexStatus(
                        course.getId(), userId, IndexStatus.READY) == 0) {
            throw new BadRequestException("At least one unit must be indexed before chatting");
        }

        String title = request.title() == null || request.title().isBlank()
                ? unit == null ? "Chat with " + course.getName() : "Chat about " + unit.getName()
                : request.title().trim();
        ChatSession session = ChatSession.builder()
                .userId(userId)
                .courseId(course.getId())
                .unitId(unit == null ? null : unit.getId())
                .title(title)
                .build();
        return toSessionResponse(chatSessionRepository.save(session));
    }

    @Transactional(readOnly = true)
    public List<ChatSessionResponse> listCourseSessions(
            UUID userId, UUID courseId, UUID unitId) {
        learningService.requireCourse(userId, courseId);
        if (unitId != null) {
            learningService.requireUnit(userId, courseId, unitId);
        }
        return chatSessionRepository
                .findByUserIdAndCourseIdAndUnitIdOrderByCreatedAtDesc(userId, courseId, unitId)
                .stream()
                .map(this::toSessionResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ChatMessageResponse> getMessages(UUID userId, UUID sessionId) {
        ChatSession session = requireSession(userId, sessionId);
        return chatMessageRepository.findBySessionIdOrderByCreatedAtAsc(session.getId()).stream()
                .map(this::toMessageResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public ChatSession requireSession(UUID userId, UUID sessionId) {
        return chatSessionRepository.findByIdAndUserId(sessionId, userId)
                .orElseThrow(() -> new NotFoundException("Chat session not found"));
    }

    public SseEmitter streamReply(UUID userId, UUID sessionId, String userContent) {
        // 1. Ensure the session exists and the repo is indexed
        ChatSession session = requireSession(userId, sessionId);
        Repository repo = session.getRepositoryId() == null
                ? null
                : repoService.requireOwned(session.getRepositoryId(), userId);
        LearningCourse course = session.getCourseId() == null
                ? null
                : learningService.requireCourse(userId, session.getCourseId());
        LearningUnit unit = session.getUnitId() == null
                ? null
                : learningService.requireUnit(userId, session.getCourseId(), session.getUnitId());
        if (repo != null && repo.getIndexStatus() != IndexStatus.READY) {
            throw new BadRequestException("Repository is not ready for chat");
        }
        if (unit != null && unit.getIndexStatus() != IndexStatus.READY) {
            throw new BadRequestException("Unit is not ready for chat");
        }
        if (repo == null && course == null) {
            throw new BadRequestException("Chat session has no valid source");
        }

        // 2. Persist the user's message
        ChatMessage userMessage = chatMessageRepository.save(ChatMessage.builder()
                .sessionId(session.getId())
                .role(MessageRole.USER)
                .content(userContent)
                .build());

        // 3. RAG retrieval — find code chunks similar to the question
        var retrievedContext = repo != null
                ? codeContextRetriever.retrieve(repo.getId(), userContent)
                : codeContextRetriever.retrieveLearningMaterial(
                        course.getId(), unit == null ? null : unit.getId(), userContent);

        // 4. Build LLM prompts from retrieved context + question
        String systemPrompt = repo != null
                ? chatPromptBuilder.systemPrompt(repo.getFullName())
                : chatPromptBuilder.learningPrompt(
                        course.getName(), unit == null ? null : unit.getName());
        String userPrompt = chatPromptBuilder.userPrompt(retrievedContext.contextText(), userContent);

        // 5. Stream Gemini response to the client (SSE)
        return chatStreamHandler.stream(
                session.getId(),
                toMessageResponse(userMessage),
                retrievedContext.citations(),
                systemPrompt,
                userPrompt);
    }

    private ChatSessionResponse toSessionResponse(ChatSession session) {
        return new ChatSessionResponse(
                session.getId(),
                session.getRepositoryId(),
                session.getCourseId(),
                session.getUnitId(),
                session.getTitle(),
                session.getCreatedAt());
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
