package com.harness.core.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.harness.core.entity.ChatMessage;
import com.harness.core.model.AiChatModel;
import com.harness.core.tool.DagTaskToolProvider;
import com.harness.core.tool.FileToolProvider;
import com.harness.core.tool.SubAgentToolProvider;
import com.harness.core.tool.TodoWriteToolProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 对话流式服务
 *
 * 实现SSE实时推送对话过程中的所有事件
 */
@Service
public class ConversationStreamService {

    private static final Logger logger = LoggerFactory.getLogger(ConversationStreamService.class);

    private final ChatSessionService chatSessionService;
    private final AiServiceFactory aiServiceFactory;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ConversationStreamService(ChatSessionService chatSessionService,
                                    AiServiceFactory aiServiceFactory) {
        this.chatSessionService = chatSessionService;
        this.aiServiceFactory = aiServiceFactory;
    }

    /**
     * 流式对话核心流程（简化版）
     *
     * @param sessionId 会话ID（可选）
     * @param message 用户消息
     * @param emitter SSE发射器
     */
    public void streamConversation(String sessionId, String message, SseEmitter emitter) {
        String tenantId = "default-tenant";
        String userId = "default-user";

        try {
            logger.info("=== 开始流式对话 ===");
            logger.info("sessionId: {}, message: {}", sessionId, message);

            // 1. 准备会话
            if (sessionId == null || sessionId.isEmpty()) {
                sessionId = generateSessionId();
                chatSessionService.createSessionWithId(sessionId, tenantId, userId);

                // 发送会话创建事件
                sendEvent(emitter, "session_created", new SessionCreatedEvent(sessionId));
                logger.debug("已发送session_created事件");

                // 设置会话标题
                String title = generateTitle(message);
                chatSessionService.updateSessionTitle(sessionId, title);
            }

            String finalSessionId = sessionId;
            logger.info("会话ID: {}", finalSessionId);

            // 2. 发送用户消息事件（立即显示）
            sendEvent(emitter, "user_message", new MessageEvent(
                    null,
                    "USER",
                    message,
                    LocalDateTime.now().toString()
            ));
            logger.info("已发送user_message事件");

            // 3. 发送AI开始思考事件
            sendEvent(emitter, "ai_thinking", new ThinkingEvent("AI正在思考..."));
            logger.info("已发送ai_thinking事件");

            // 4. 调用AI（简化：不设置工具上下文）
            try {
                logger.info("开始调用AI服务...");
                AiChatModel aiModel = aiServiceFactory.getModel("openai", finalSessionId);

                logger.info("AI模型获取成功，开始chat...");
                String aiResponse = aiModel.chat(finalSessionId, message);

                logger.info("AI响应完成，长度: {}", aiResponse != null ? aiResponse.length() : 0);

                // 确保aiResponse不为空
                if (aiResponse == null || aiResponse.isEmpty()) {
                    aiResponse = "抱歉，AI没有返回有效响应";
                    logger.warn("AI响应为空，使用默认消息");
                }

                // 发送AI回复事件
                sendEvent(emitter, "ai_message", new MessageEvent(
                        null,
                        "AI",
                        aiResponse,
                        LocalDateTime.now().toString()
                ));
                logger.info("已发送ai_message事件");

            } catch (Exception aiError) {
                logger.error("AI调用失败: {}", aiError.getMessage(), aiError);

                // 发送错误消息作为AI回复
                sendEvent(emitter, "ai_message", new MessageEvent(
                        null,
                        "AI",
                        "抱歉，AI服务暂时不可用：" + aiError.getMessage(),
                        LocalDateTime.now().toString()
                ));
                logger.info("已发送AI错误消息");
            }

            // 5. 发送完成事件
            sendEvent(emitter, "chat_complete", new ChatCompleteEvent(
                    true,
                    finalSessionId,
                    null
            ));
            logger.info("已发送chat_complete事件");

            // 完成 SSE 连接
            emitter.complete();
            logger.info("=== 流式对话完成 ===");

        } catch (Exception e) {
            logger.error("流式对话失败: {}", e.getMessage(), e);

            try {
                String errorMessage = e.getMessage() != null ? e.getMessage() : "未知错误";
                sendEvent(emitter, "error", new ErrorEvent(errorMessage));
                emitter.complete();
                logger.info("已发送error事件");
            } catch (Exception sendError) {
                logger.error("发送error事件失败: {}", sendError.getMessage());
            }
        }
    }

    /**
     * 获取历史消息（过滤TOOL类型）
     */
    public List<MessageResponse> getHistory(String sessionId, Integer limit, Integer offset, String order) {
        List<ChatMessage> messages = chatSessionService.getHistory(sessionId, limit, offset, order);

        return messages.stream()
                .filter(m -> !"TOOL".equals(m.getMessageType()))
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

    // ========== Helper Methods ==========

    /**
     * 发送SSE事件
     */
    private void sendEvent(SseEmitter emitter, String eventName, Object data) {
        try {
            String json = objectMapper.writeValueAsString(data);
            emitter.send(SseEmitter.event()
                    .name(eventName)
                    .data(json));
            logger.debug("发送SSE事件: {} -> {}", eventName, json);
        } catch (IOException e) {
            logger.error("发送SSE事件失败: {}", e.getMessage());
        }
    }

    /**
     * 生成会话ID
     */
    private String generateSessionId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }

    /**
     * 生成会话标题
     */
    private String generateTitle(String message) {
        if (message == null || message.isEmpty()) {
            return "新对话";
        }
        String title = message.replaceAll("[\\r\\n\\t]", " ").trim();
        if (title.length() > 20) {
            title = title.substring(0, 20) + "...";
        }
        return title;
    }

    // ========== Event Data Structures ==========

    public record SessionCreatedEvent(String sessionId) {}

    public record MessageEvent(Long id, String messageType, String content, String createdAt) {}

    public record ThinkingEvent(String message) {}

    public record ToolCallEvent(String toolName, String arguments) {}

    public record ToolResultEvent(String toolName, String result) {}

    public record ChatCompleteEvent(boolean success, String sessionId, String error) {}

    public record ErrorEvent(String message) {}

    public record MessageResponse(Long id, String messageType, String content,
                                  Integer tokenCount, Integer messageOrder,
                                  LocalDateTime createdAt) {}
}