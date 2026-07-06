package com.harness.core.controller;

import com.harness.core.entity.ChatMessage;
import com.harness.core.entity.ChatSession;
import com.harness.core.service.AgentService;
import com.harness.core.service.ChatSessionService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Harness Agent 控制器
 * 仅负责请求接收和响应转换，业务逻辑在 Service 层
 */
@RestController
@RequestMapping("/api/agent")
public class HarnessController {

    private final AgentService agentService;
    private final ChatSessionService chatSessionService;

    public HarnessController(AgentService agentService,
                             ChatSessionService chatSessionService) {
        this.agentService = agentService;
        this.chatSessionService = chatSessionService;
    }

    /**
     * Chat 接口
     */
    @PostMapping("chat")
    public ChatResponse chat(@RequestBody ChatRequest request) {
        AgentService.ChatResult result = agentService.chat(
                "default-tenant", "default-user", request.sessionId(), request.message()
        );
        return new ChatResponse(result.success(), result.message(), result.sessionId());
    }

    /**
     * 创建新会话
     */
    @PostMapping("sessions")
    public SessionResponse createSession(@RequestBody SessionRequest request) {
        ChatSession session = chatSessionService.createSession(
                request.tenantId() != null ? request.tenantId() : "default-tenant",
                request.userId() != null ? request.userId() : "default-user"
        );
        return new SessionResponse(session.getSessionId(), session.getCreatedAt());
    }

    /**
     * 获取用户的所有会话列表
     */
    @GetMapping("sessions")
    public List<SessionListResponse> listSessions(
            @RequestParam(required = false) String tenantId,
            @RequestParam(required = false) String userId) {
        tenantId = tenantId != null ? tenantId : "default-tenant";
        userId = userId != null ? userId : "default-user";

        List<ChatSession> sessions = chatSessionService.listSessionsByUser(tenantId, userId);
        return sessions.stream()
                .map(s -> new SessionListResponse(
                        s.getSessionId(),
                        s.getTitle(),
                        s.getLastMessageAt(),
                        s.getTotalTokens(),
                        s.getTokenUsagePercent(),
                        s.getStatus()
                ))
                .toList();
    }

    /**
     * 获取会话详情
     */
    @GetMapping("sessions/{sessionId}")
    public SessionDetailResponse getSession(@PathVariable String sessionId) {
        ChatSession session = chatSessionService.getSession(sessionId);
        if (session == null) {
            return null;
        }
        return new SessionDetailResponse(
                session.getSessionId(),
                session.getTitle(),
                session.getTotalTokens(),
                session.getMaxTokens(),
                session.getTokenUsagePercent(),
                session.getIsCompressed(),
                session.getCompressionCount(),
                session.getStatus(),
                session.getLastMessageAt(),
                session.getCreatedAt()
        );
    }

    /**
     * 获取会话历史消息（支持分页）
     */
    @GetMapping("history/{sessionId}")
    public List<MessageResponse> getHistory(
            @PathVariable String sessionId,
            @RequestParam(required = false, defaultValue = "20") Integer limit,
            @RequestParam(required = false, defaultValue = "0") Integer offset,
            @RequestParam(required = false, defaultValue = "asc") String order) {
        List<ChatMessage> messages = chatSessionService.getHistory(sessionId, limit, offset, order);
        return messages.stream()
                .map(m -> new MessageResponse(
                        m.getId(),
                        m.getMessageType(),
                        m.getContent(),
                        m.getTokenCount(),
                        m.getMessageOrder(),
                        m.getCreatedAt()
                ))
                .toList();
    }

    /**
     * 归档会话
     */
    @PostMapping("sessions/{sessionId}/archive")
    public ArchiveResponse archiveSession(@PathVariable String sessionId) {
        boolean success = chatSessionService.archiveSession(sessionId);
        return new ArchiveResponse(success, sessionId);
    }

    /**
     * 删除会话
     */
    @DeleteMapping("sessions/{sessionId}")
    public DeleteResponse deleteSession(@PathVariable String sessionId) {
        boolean success = chatSessionService.deleteSession(sessionId);
        return new DeleteResponse(success, sessionId);
    }

    // ========== Request/Response Records ==========

    public record ChatRequest(String message, String sessionId) {}
    public record ChatResponse(boolean success, String message, String sessionId) {}

    public record SessionRequest(String tenantId, String userId) {}
    public record SessionResponse(String sessionId, java.time.LocalDateTime createdAt) {}

    public record SessionListResponse(String sessionId, String title,
                                       java.time.LocalDateTime lastMessageAt,
                                       Long totalTokens,
                                       java.math.BigDecimal tokenUsagePercent,
                                       String status) {}

    public record SessionDetailResponse(String sessionId, String title,
                                         Long totalTokens, Integer maxTokens,
                                         java.math.BigDecimal tokenUsagePercent,
                                         Boolean isCompressed, Integer compressionCount,
                                         String status,
                                         java.time.LocalDateTime lastMessageAt,
                                         java.time.LocalDateTime createdAt) {}

    public record MessageResponse(Long id, String messageType, String content,
                                  Integer tokenCount, Integer messageOrder,
                                  java.time.LocalDateTime createdAt) {}

    public record ArchiveResponse(boolean success, String sessionId) {}
    public record DeleteResponse(boolean success, String sessionId) {}
}