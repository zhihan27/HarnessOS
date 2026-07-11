package com.harness.core.memory;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.harness.core.entity.ChatSession;
import com.harness.core.enums.MessageType;
import com.harness.core.enums.SessionStatus;
import com.harness.core.mapper.ChatMessageMapper;
import com.harness.core.mapper.ChatSessionMapper;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.*;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 数据库持久化的聊天记忆存储
 * 实现 LangChain4j 的 ChatMemoryStore 接口
 */
@Component
public class DatabaseChatMemoryStore implements ChatMemoryStore {

    private static final Logger logger = LoggerFactory.getLogger(DatabaseChatMemoryStore.class);

    private final ChatSessionMapper sessionMapper;
    private final ChatMessageMapper messageMapper;
    private final ChatMemoryCompressor compressor;
    private final TokenCounter tokenCounter;

    // 内存缓存（LangChain4j消息，提高性能）
    private final Map<String, List<dev.langchain4j.data.message.ChatMessage>> messageCache = new ConcurrentHashMap<>();

    // Token阈值（80%）
    private static final double COMPRESSION_THRESHOLD = 0.80;

    // 默认最大Token数（DeepSeek模型）
    private static final int DEFAULT_MAX_TOKENS = 128000;

    public DatabaseChatMemoryStore(ChatSessionMapper sessionMapper,
                                   ChatMessageMapper messageMapper,
                                   ChatMemoryCompressor compressor,
                                   TokenCounter tokenCounter) {
        this.sessionMapper = sessionMapper;
        this.messageMapper = messageMapper;
        this.compressor = compressor;
        this.tokenCounter = tokenCounter;
    }

    @Override
    public List<dev.langchain4j.data.message.ChatMessage> getMessages(Object memoryId) {
        String sessionId = memoryId.toString();
        logger.debug("获取会话消息: sessionId={}", sessionId);

        // 先检查缓存
        if (messageCache.containsKey(sessionId)) {
            return new ArrayList<>(messageCache.get(sessionId));
        }

        // 从数据库加载
        List<com.harness.core.entity.ChatMessage> entities = messageMapper.findBySessionIdOrderByOrder(sessionId);
        List<dev.langchain4j.data.message.ChatMessage> messages = new ArrayList<>();

        for (com.harness.core.entity.ChatMessage entity : entities) {
            dev.langchain4j.data.message.ChatMessage message = convertToLangChainMessage(entity);
            if (message != null) {
                messages.add(message);
            }
        }

        // 更新缓存
        messageCache.put(sessionId, new ArrayList<>(messages));

        logger.debug("加载消息完成: sessionId={}, count={}", sessionId, messages.size());
        return messages;
    }

    @Override
    @Transactional
    public void updateMessages(Object memoryId, List<dev.langchain4j.data.message.ChatMessage> messages) {
        String sessionId = memoryId.toString();
        logger.info("更新会话消息: sessionId={}, messageCount={}", sessionId, messages.size());

        // 获取或创建会话（子任务会话返回null）
        ChatSession session = getOrCreateSession(sessionId);

        // 更新缓存
        messageCache.put(sessionId, new ArrayList<>(messages));

        // 持久化消息到数据库（子任务会话也需要保存）
        saveMessages(sessionId, messages);

        // 子任务会话不更新session统计
        if (session == null) {
            logger.info("子任务会话消息保存完成: sessionId={}", sessionId);
            return;
        }

        // 计算Token总数
        int totalTokens = tokenCounter.countTokens(messages);
        session.setTotalTokens((long) totalTokens);
        session.setLastMessageAt(LocalDateTime.now());

        // 计算使用率
        BigDecimal usagePercent = BigDecimal.valueOf(totalTokens)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(session.getMaxTokens()), 2, RoundingMode.HALF_UP);
        session.setTokenUsagePercent(usagePercent);

        // 检查是否需要压缩
        if (usagePercent.compareTo(BigDecimal.valueOf(COMPRESSION_THRESHOLD * 100)) >= 0) {
            logger.info("Token使用率达到{}%，触发压缩: sessionId={}",
                    usagePercent, sessionId);
            messages = compressor.compress(sessionId, messages);
            session.setIsCompressed(true);
            session.setCompressedAt(LocalDateTime.now());
            session.setCompressionCount(session.getCompressionCount() + 1);

            // 重新计算压缩后的Token数
            totalTokens = tokenCounter.countTokens(messages);
            session.setTotalTokens((long) totalTokens);
            usagePercent = BigDecimal.valueOf(totalTokens)
                    .multiply(BigDecimal.valueOf(100))
                    .divide(BigDecimal.valueOf(session.getMaxTokens()), 2, RoundingMode.HALF_UP);
            session.setTokenUsagePercent(usagePercent);

            // 更新缓存和重新保存压缩后的消息
            messageCache.put(sessionId, new ArrayList<>(messages));
            saveMessages(sessionId, messages);
        }

        // 更新会话统计
        sessionMapper.updateById(session);

        logger.info("会话消息更新完成: sessionId={}, totalTokens={}, usagePercent={}%",
                sessionId, totalTokens, usagePercent);
    }

    @Override
    @Transactional
    public void deleteMessages(Object memoryId) {
        String sessionId = memoryId.toString();
        logger.info("删除会话消息: sessionId={}", sessionId);

        // 删除消息记录
        LambdaQueryWrapper<com.harness.core.entity.ChatMessage> messageWrapper = new LambdaQueryWrapper<>();
        messageWrapper.eq(com.harness.core.entity.ChatMessage::getSessionId, sessionId);
        messageMapper.delete(messageWrapper);

        // 更新会话状态
        LambdaQueryWrapper<ChatSession> sessionWrapper = new LambdaQueryWrapper<>();
        sessionWrapper.eq(ChatSession::getSessionId, sessionId);
        ChatSession session = sessionMapper.selectOne(sessionWrapper);
        if (session != null) {
            session.setStatus(SessionStatus.DELETED.getValue());
            sessionMapper.updateById(session);
        }

        // 清除缓存
        messageCache.remove(sessionId);

        logger.info("会话消息删除完成: sessionId={}", sessionId);
    }

    /**
     * 获取或创建会话
     * 子任务会话（SUB-开头）不在chat_sessions表创建记录，仅用于内部记忆存储
     */
    public ChatSession getOrCreateSession(String sessionId) {
        // 子任务会话不创建chat_sessions记录
        if (sessionId.startsWith("SUB-")) {
            return null;
        }

        LambdaQueryWrapper<ChatSession> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ChatSession::getSessionId, sessionId);
        ChatSession session = sessionMapper.selectOne(wrapper);

        if (session == null) {
            session = new ChatSession()
                    .setSessionId(sessionId)
                    .setTenantId("default-tenant")
                    .setUserId("default-user")
                    .setModelType("openai")
                    .setTotalTokens(0L)
                    .setMaxTokens(DEFAULT_MAX_TOKENS)
                    .setTokenUsagePercent(BigDecimal.ZERO)
                    .setIsCompressed(false)
                    .setCompressionCount(0)
                    .setStatus(SessionStatus.ACTIVE.getValue());
            sessionMapper.insert(session);
            logger.info("创建新会话: sessionId={}", sessionId);
        }

        return session;
    }

    /**
     * 创建会话（带租户和用户信息）
     * 子任务会话（SUB-开头）不在chat_sessions表创建记录
     */
    public ChatSession createSession(String sessionId, String tenantId, String userId) {
        // 子任务会话不创建chat_sessions记录
        if (sessionId.startsWith("SUB-")) {
            return null;
        }

        LambdaQueryWrapper<ChatSession> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ChatSession::getSessionId, sessionId);
        ChatSession session = sessionMapper.selectOne(wrapper);

        if (session == null) {
            session = new ChatSession()
                    .setSessionId(sessionId)
                    .setTenantId(tenantId)
                    .setUserId(userId)
                    .setModelType("openai")
                    .setTotalTokens(0L)
                    .setMaxTokens(DEFAULT_MAX_TOKENS)
                    .setTokenUsagePercent(BigDecimal.ZERO)
                    .setIsCompressed(false)
                    .setCompressionCount(0)
                    .setStatus(SessionStatus.ACTIVE.getValue());
            sessionMapper.insert(session);
            logger.info("创建新会话: sessionId={}, tenant={}, user={}", sessionId, tenantId, userId);
        }

        return session;
    }

    /**
     * 持久化消息到数据库
     */
    private void saveMessages(String sessionId, List<dev.langchain4j.data.message.ChatMessage> messages) {
        // 先删除旧消息
        LambdaQueryWrapper<com.harness.core.entity.ChatMessage> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(com.harness.core.entity.ChatMessage::getSessionId, sessionId);
        messageMapper.delete(wrapper);

        // 获取当前最大序号
        Integer maxOrder = messageMapper.getMaxMessageOrder(sessionId);
        int order = maxOrder != null ? maxOrder + 1 : 1;

        // 保存新消息
        for (dev.langchain4j.data.message.ChatMessage message : messages) {
            com.harness.core.entity.ChatMessage entity = convertToEntity(sessionId, message, order++);
            messageMapper.insert(entity);
        }
    }

    /**
     * 将数据库实体转换为LangChain4j消息
     * 正确处理工具调用请求和结果消息
     */
    private dev.langchain4j.data.message.ChatMessage convertToLangChainMessage(com.harness.core.entity.ChatMessage entity) {
        String messageType = entity.getMessageType();
        String content = entity.getContent();

        return switch (messageType) {
            case "SYSTEM" -> SystemMessage.from(content != null ? content : "");
            case "USER" -> UserMessage.from(content != null ? content : "");
            case "AI" -> {
                // 检查是否有工具执行请求
                String requestsJson = entity.getToolExecutionRequests();
                if (requestsJson != null && !requestsJson.isEmpty()) {
                    // 从JSON反序列化工具调用请求
                    List<ToolExecutionRequest> requests = deserializeToolExecutionRequests(requestsJson);
                    if (!requests.isEmpty()) {
                        // 创建带工具调用请求的AiMessage
                        String text = (content != null && !content.isEmpty()) ? content : null;
                        yield AiMessage.aiMessage(text, requests);
                    }
                }
                // 普通AI消息
                yield AiMessage.from(content != null ? content : "");
            }
            case "TOOL" -> {
                // 转换为 ToolExecutionResultMessage
                // 需要创建一个 ToolExecutionRequest 来匹配
                String toolCallId = entity.getToolCallId();
                String resultContent = content != null ? content : "";

                // 从保存的信息重建 ToolExecutionRequest
                String reqId = toolCallId != null ? toolCallId : "unknown";
                String reqName = entity.getToolName() != null ? entity.getToolName() : "unknown";
                String reqArgs = entity.getToolArgs() != null ? entity.getToolArgs() : "{}";

                ToolExecutionRequest dummyRequest = ToolExecutionRequest.builder()
                        .id(reqId)
                        .name(reqName)
                        .arguments(reqArgs)
                        .build();

                yield ToolExecutionResultMessage.toolExecutionResultMessage(dummyRequest, resultContent);
            }
            default -> null;
        };
    }

    /**
     * 将LangChain4j消息转换为数据库实体
     */
    private com.harness.core.entity.ChatMessage convertToEntity(String sessionId,
                                                                 dev.langchain4j.data.message.ChatMessage message,
                                                                 int order) {
        com.harness.core.entity.ChatMessage entity = new com.harness.core.entity.ChatMessage()
                .setSessionId(sessionId)
                .setMessageOrder(order)
                .setIsCompressed(false)
                .setIsImportant(false)
                .setTokenCount(tokenCounter.countTokens(message));

        if (message instanceof SystemMessage sysMsg) {
            entity.setMessageType(MessageType.SYSTEM.getValue());
            entity.setContent(sysMsg.text());
            entity.setIsImportant(true);
        } else if (message instanceof UserMessage userMsg) {
            entity.setMessageType(MessageType.USER.getValue());
            entity.setContent(userMsg.singleText());
        } else if (message instanceof AiMessage aiMsg) {
            entity.setMessageType(MessageType.AI.getValue());
            // 处理AI消息：可能只有文本、只有工具调用、或两者都有
            if (aiMsg.text() != null && !aiMsg.text().isEmpty()) {
                entity.setContent(aiMsg.text());
            }
            if (aiMsg.hasToolExecutionRequests()) {
                // 序列化所有工具调用请求为JSON
                String requestsJson = serializeToolExecutionRequests(aiMsg.toolExecutionRequests());
                entity.setToolExecutionRequests(requestsJson);

                // 兼容性：保存第一个工具调用的信息
                ToolExecutionRequest firstReq = aiMsg.toolExecutionRequests().get(0);
                entity.setToolName(firstReq.name());
                entity.setToolArgs(firstReq.arguments());

                // 如果没有文本内容，设置空字符串避免NULL约束
                if (entity.getContent() == null) {
                    entity.setContent("");
                }
            }
            // 空的AI消息设置空字符串
            if (entity.getContent() == null) {
                entity.setContent("");
            }
        } else if (message instanceof ToolExecutionResultMessage toolMsg) {
            entity.setMessageType(MessageType.TOOL.getValue());
            String toolResultText = toolMsg.text();
            entity.setContent(toolResultText != null ? toolResultText : "");
            entity.setToolResult(toolResultText);
            // 保存 tool_call_id 用于匹配
            entity.setToolCallId(toolMsg.id());
        } else {
            // 其他类型的消息作为用户消息处理
            entity.setMessageType(MessageType.USER.getValue());
            entity.setContent("");
        }

        return entity;
    }

    /**
     * 序列化工具执行请求列表为JSON
     */
    private String serializeToolExecutionRequests(List<ToolExecutionRequest> requests) {
        if (requests == null || requests.isEmpty()) {
            return null;
        }
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < requests.size(); i++) {
            ToolExecutionRequest req = requests.get(i);
            if (i > 0) sb.append(",");
            sb.append("{\"id\":\"").append(escapeJson(req.id())).append("\"");
            sb.append(",\"name\":\"").append(escapeJson(req.name())).append("\"");
            sb.append(",\"arguments\":\"").append(escapeJson(req.arguments())).append("\"}");
        }
        sb.append("]");
        return sb.toString();
    }

    /**
     * 反序列化JSON为工具执行请求列表
     */
    private List<ToolExecutionRequest> deserializeToolExecutionRequests(String json) {
        if (json == null || json.isEmpty()) {
            return new ArrayList<>();
        }
        List<ToolExecutionRequest> requests = new ArrayList<>();
        try {
            // 简单解析JSON数组
            String content = json.trim();
            if (!content.startsWith("[") || !content.endsWith("]")) {
                return requests;
            }
            content = content.substring(1, content.length() - 1); // 去掉 []

            // 分割每个对象
            List<String> objects = parseJsonObjects(content);
            for (String obj : objects) {
                String id = extractJsonValue(obj, "id");
                String name = extractJsonValue(obj, "name");
                String arguments = extractJsonValue(obj, "arguments");
                if (id != null && name != null) {
                    // 使用 builder 创建 ToolExecutionRequest
                    ToolExecutionRequest request = ToolExecutionRequest.builder()
                            .id(id)
                            .name(name)
                            .arguments(arguments != null ? arguments : "{}")
                            .build();
                    requests.add(request);
                }
            }
        } catch (Exception e) {
            logger.error("反序列化工具执行请求失败: {}", e.getMessage());
        }
        return requests;
    }

    /**
     * 解析JSON对象列表
     */
    private List<String> parseJsonObjects(String content) {
        List<String> objects = new ArrayList<>();
        int depth = 0;
        int start = -1;
        for (int i = 0; i < content.length(); i++) {
            char c = content.charAt(i);
            if (c == '{') {
                if (depth == 0) start = i;
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0 && start >= 0) {
                    objects.add(content.substring(start, i + 1));
                    start = -1;
                }
            }
        }
        return objects;
    }

    /**
     * 提取JSON字段值
     */
    private String extractJsonValue(String json, String key) {
        String searchKey = "\"" + key + "\":\"";
        int start = json.indexOf(searchKey);
        if (start < 0) return null;
        start += searchKey.length();
        int end = start;
        while (end < json.length()) {
            char c = json.charAt(end);
            if (c == '"' && (end == 0 || json.charAt(end - 1) != '\\')) {
                break;
            }
            end++;
        }
        return unescapeJson(json.substring(start, end));
    }

    /**
     * JSON字符串转义
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
     * JSON字符串反转义
     */
    private String unescapeJson(String str) {
        if (str == null) return "";
        return str.replace("\\\"", "\"")
                  .replace("\\\\", "\\")
                  .replace("\\n", "\n")
                  .replace("\\r", "\r")
                  .replace("\\t", "\t");
    }

    /**
     * 清除缓存
     */
    public void clearCache(String sessionId) {
        messageCache.remove(sessionId);
    }
}