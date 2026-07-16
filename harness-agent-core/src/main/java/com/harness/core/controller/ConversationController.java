package com.harness.core.controller;

import com.harness.core.entity.ChatSession;
import com.harness.core.service.AgentService;
import com.harness.core.service.ChatSessionService;
import com.harness.core.service.ConversationStreamService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 对话流式控制器
 *
 * 使用 SSE (Server-Sent Events) 实现实时对话流式输出
 */
@RestController
@RequestMapping("/api/chat")
public class ConversationController {

    private static final Logger logger = LoggerFactory.getLogger(ConversationController.class);

    private final AgentService agentService;
    private final ChatSessionService chatSessionService;
    private final ConversationStreamService streamService;
    private final ExecutorService executor = Executors.newCachedThreadPool();

    public ConversationController(AgentService agentService,
                                 ChatSessionService chatSessionService,
                                 ConversationStreamService streamService) {
        this.agentService = agentService;
        this.chatSessionService = chatSessionService;
        this.streamService = streamService;
    }

    /**
     * 流式对话接口（SSE）
     *
     * 实时推送：
     * 1. 用户消息
     * 2. AI回复片段
     * 3. 工具调用事件
     * 4. 最终完成事件
     *
     * @param sessionId 会话ID（可选，不传则创建新会话）
     * @param message 用户消息
     */
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamChat(
            @RequestParam(required = false) String sessionId,
            @RequestParam String message) {

        logger.info("收到流式对话请求: sessionId={}, message={}", sessionId, message);

        // 创建 SSE 连接
        SseEmitter emitter = new SseEmitter(Long.MAX_VALUE);

        // 异步执行对话流程
        executor.submit(() -> {
            try {
                streamService.streamConversation(sessionId, message, emitter);
            } catch (Exception e) {
                logger.error("流式对话异常: {}", e.getMessage(), e);
                try {
                    emitter.send(SseEmitter.event()
                            .name("error")
                            .data("对话处理失败: " + e.getMessage()));
                    emitter.complete();
                } catch (Exception ignored) {
                }
            }
        });

        return emitter;
    }

    /**
     * 创建新会话
     */
    @PostMapping("/sessions")
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
    @GetMapping("/sessions")
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
     * 获取会话历史消息
     */
    @GetMapping("/history/{sessionId}")
    public List<ConversationStreamService.MessageResponse> getHistory(
            @PathVariable String sessionId,
            @RequestParam(required = false, defaultValue = "20") Integer limit,
            @RequestParam(required = false, defaultValue = "0") Integer offset,
            @RequestParam(required = false, defaultValue = "asc") String order) {

        return streamService.getHistory(sessionId, limit, offset, order);
    }

    // ========== Request/Response Records ==========

    public record SessionRequest(String tenantId, String userId) {}
    public record SessionResponse(String sessionId, java.time.LocalDateTime createdAt) {}

    public record SessionListResponse(String sessionId, String title,
                                       java.time.LocalDateTime lastMessageAt,
                                       Long totalTokens,
                                       java.math.BigDecimal tokenUsagePercent,
                                       String status) {}
}