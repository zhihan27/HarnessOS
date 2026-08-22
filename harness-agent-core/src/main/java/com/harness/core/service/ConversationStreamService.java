package com.harness.core.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.harness.core.entity.AgentTodoTask;
import com.harness.core.entity.ChatMessage;
import com.harness.core.hook.ChatContext;
import com.harness.core.hook.ChatHookExecutor;
import com.harness.core.model.AiChatModel;
import com.harness.core.tool.DagTaskToolProvider;
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
import java.util.concurrent.CompletableFuture;

/**
 * 对话流式服务（统一版本）
 *
 * 使用 AiServiceFactory 实现流式响应 + 工具调用 + 会话记忆
 * 集成 Hook 机制和任务跟踪
 */
@Service
public class ConversationStreamService {

    private static final Logger logger = LoggerFactory.getLogger(ConversationStreamService.class);

    private final ChatSessionService chatSessionService;
    private final AiServiceFactory aiServiceFactory;
    private final ChatHookExecutor hookExecutor;
    private final AgentTodoTaskService todoTaskService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ConversationStreamService(ChatSessionService chatSessionService,
                                    AiServiceFactory aiServiceFactory,
                                    ChatHookExecutor hookExecutor,
                                    AgentTodoTaskService todoTaskService) {
        this.chatSessionService = chatSessionService;
        this.aiServiceFactory = aiServiceFactory;
        this.hookExecutor = hookExecutor;
        this.todoTaskService = todoTaskService;
    }

    /**
     * 流式对话核心流程（使用统一 AI 服务）
     *
     * @param sessionId 会话ID（可选）
     * @param message 用户消息
     * @param modelType 模型类型 (openai/anthropic)
     * @param emitter SSE发射器
     */
    public void streamConversation(String sessionId, String message, String modelType, SseEmitter emitter) {
        String tenantId = "default-tenant";
        String userId = "default-user";

        // 设置工具上下文（必须在 try-finally 中清理）
        TodoWriteToolProvider.setSessionContext(tenantId, userId, sessionId);
        SubAgentToolProvider.setSessionContext(tenantId, userId, sessionId);
        DagTaskToolProvider.setSessionContext(tenantId, userId, sessionId);

        AgentTodoTask trackingTask = null;

        try {
            logger.info("=== 开始流式对话 ===");
            logger.info("sessionId: {}, message: {}, modelType: {}", sessionId, message, modelType);

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

            // 更新工具上下文中的 sessionId
            TodoWriteToolProvider.setSessionContext(tenantId, userId, finalSessionId);
            SubAgentToolProvider.setSessionContext(tenantId, userId, finalSessionId);
            DagTaskToolProvider.setSessionContext(tenantId, userId, finalSessionId);

            // 2. 发送用户消息事件（立即显示）
            sendEvent(emitter, "user_message", new MessageEvent(
                    null,
                    "USER",
                    message,
                    LocalDateTime.now().toString()
            ));
            logger.info("已发送user_message事件");

            // 3. 创建跟踪任务（用于监控对话进度）
            trackingTask = todoTaskService.createTask(
                    tenantId, userId, finalSessionId, message, null
            );
            logger.info("创建跟踪任务: id={}", trackingTask.getId());

            // 4. 执行前置 Hook
            ChatContext context = new ChatContext(tenantId, userId, finalSessionId, message);
            hookExecutor.executeBefore(context);

            if (!context.isSuccess()) {
                // Hook 阻止了对话执行
                sendEvent(emitter, "error", new ErrorEvent(context.getResult()));
                emitter.complete();
                return;
            }

            // 5. 发送AI开始思考事件
            sendEvent(emitter, "ai_thinking", new ThinkingEvent("AI正在思考..."));
            logger.info("已发送ai_thinking事件");

            // 6. 调用统一 AI 服务（流式响应 + 工具调用）
            try {
                logger.info("开始调用统一 AI 服务（支持工具调用），modelType={}", modelType);

                // 获取 AI 模型（支持工具 + 流式 + 会话记忆）
                AiChatModel aiModel = aiServiceFactory.getModel(modelType);
                logger.info("已获取 AI 模型: modelType={}", modelType);

                // 创建异步 Future
                CompletableFuture<String> future = new CompletableFuture<>();
                StringBuilder responseBuilder = new StringBuilder();

                // 调用流式聊天
                aiModel.chatStream(finalSessionId, message)
                        .onPartialResponse(token -> {
                            // 处理每个 token
                            if (token != null && !token.isEmpty()) {
                                responseBuilder.append(token);
                                sendTokenEvent(emitter, token);
                            }
                        })
                        .onCompleteResponse(response -> {
                            // 流式响应完成
                            String aiResponse = responseBuilder.toString();
                            logger.info("AI流式响应完成，长度: {}", aiResponse.length());

                            // 更新上下文结果
                            context.setResult(aiResponse);

                            // 发送AI消息完成事件
                            sendEvent(emitter, "ai_message_complete", new MessageEvent(
                                    null,
                                    "AI",
                                    aiResponse,
                                    LocalDateTime.now().toString()
                            ));
                            logger.info("已发送ai_message_complete事件");

                            // 完成 Future
                            future.complete(aiResponse);
                        })
                        .onError(error -> {
                            // 错误处理
                            logger.error("AI流式响应错误: {}", error.getMessage(), error);
                            future.completeExceptionally(error);
                        })
                        .start(); // 启动流式处理

                // 等待流式响应完成
                String aiResponse = future.get();

                // 确保aiResponse不为空
                if (aiResponse == null || aiResponse.isEmpty()) {
                    aiResponse = "抱歉，AI没有返回有效响应";
                    logger.warn("AI响应为空，使用默认消息");
                    context.setResult(aiResponse);
                }

                logger.info("统一 AI 服务调用完成");

                // 7. 执行后置 Hook
                hookExecutor.executeAfter(context);

                // 8. 标记跟踪任务完成
                todoTaskService.markCompleted(trackingTask.getId());
                logger.info("跟踪任务已完成: id={}", trackingTask.getId());

            } catch (Exception aiError) {
                logger.error("AI调用失败: {}", aiError.getMessage(), aiError);

                // 标记跟踪任务失败
                if (trackingTask != null) {
                    todoTaskService.markFailed(trackingTask.getId(), aiError.getMessage());
                }

                // 发送错误消息作为AI回复
                sendEvent(emitter, "ai_message", new MessageEvent(
                        null,
                        "AI",
                        "抱歉，AI服务暂时不可用：" + aiError.getMessage(),
                        LocalDateTime.now().toString()
                ));
                logger.info("已发送AI错误消息");
            }

            // 9. 发送完成事件
            sendEvent(emitter, "chat_complete", new ChatCompleteEvent(
                    context.isSuccess(),
                    finalSessionId,
                    null
            ));
            logger.info("已发送chat_complete事件");

            // 完成 SSE 连接
            emitter.complete();
            logger.info("=== 流式对话完成 ===");

        } catch (Exception e) {
            logger.error("流式对话失败: {}", e.getMessage(), e);

            // 标记跟踪任务失败
            if (trackingTask != null) {
                try {
                    todoTaskService.markFailed(trackingTask.getId(), e.getMessage());
                } catch (Exception markError) {
                    logger.error("标记任务失败时出错: {}", markError.getMessage());
                }
            }

            try {
                String errorMessage = e.getMessage() != null ? e.getMessage() : "未知错误";
                sendEvent(emitter, "error", new ErrorEvent(errorMessage));
                emitter.complete();
                logger.info("已发送error事件");
            } catch (Exception sendError) {
                logger.error("发送error事件失败: {}", sendError.getMessage());
            }
        } finally {
            // 10. 清理工具上下文（重要！）
            TodoWriteToolProvider.clearSessionContext();
            SubAgentToolProvider.clearSessionContext();
            DagTaskToolProvider.clearSessionContext();
            logger.debug("已清理工具上下文");
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
     * 发送 token 事件
     */
    private void sendTokenEvent(SseEmitter emitter, String token) {
        try {
            String escapedToken = escapeJson(token);
            String json = String.format("{\"token\":\"%s\"}", escapedToken);
            emitter.send(SseEmitter.event()
                    .name("ai_token")
                    .data(json));
        } catch (IOException e) {
            logger.error("发送token事件失败: {}", e.getMessage());
        }
    }

    /**
     * JSON 字符串转义
     */
    private String escapeJson(String str) {
        if (str == null) return "";
        return str.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
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