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
     * 注意：工具调用消息简化处理，避免格式错误
     */
    private dev.langchain4j.data.message.ChatMessage convertToLangChainMessage(com.harness.core.entity.ChatMessage entity) {
        String content = entity.getContent();

        // 跳过空内容或工具调用摘要消息（避免发送给API时格式错误）
        if (content == null || content.isEmpty() || content.startsWith("Tool Calls:")) {
            // 工具调用请求不恢复为消息，LangChain4j会自动处理
            return null;
        }

        return switch (entity.getMessageType()) {
            case "SYSTEM" -> SystemMessage.from(content);
            case "USER" -> UserMessage.from(content);
            case "AI" -> AiMessage.from(content);
            case "TOOL" -> {
                // 工具结果消息：转换为用户消息格式（包含工具执行结果）
                // 避免发送 function role 给 OpenAI
                yield UserMessage.from("工具执行结果: " + content);
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
            if (aiMsg.text() != null) {
                entity.setContent(aiMsg.text());
            } else if (aiMsg.hasToolExecutionRequests()) {
                // 当AI消息只有工具调用请求时，记录工具调用信息
                StringBuilder toolInfo = new StringBuilder("Tool Calls: ");
                for (ToolExecutionRequest req : aiMsg.toolExecutionRequests()) {
                    toolInfo.append(req.name()).append("(").append(req.arguments()).append(") ");
                }
                entity.setContent(toolInfo.toString());
                // 同时保存工具调用详情
                entity.setToolName(aiMsg.toolExecutionRequests().get(0).name());
                entity.setToolArgs(aiMsg.toolExecutionRequests().get(0).arguments());
            } else {
                // 空的AI消息，设置空字符串避免NULL约束
                entity.setContent("");
            }
        } else if (message instanceof ToolExecutionResultMessage toolMsg) {
            entity.setMessageType(MessageType.TOOL.getValue());
            // 工具结果可能为空，确保content不为null
            String toolResultText = toolMsg.text();
            entity.setContent(toolResultText != null ? toolResultText : "");
            entity.setToolResult(toolResultText);
        } else {
            // 其他类型的消息作为用户消息处理
            entity.setMessageType(MessageType.USER.getValue());
            entity.setContent("");
        }

        return entity;
    }

    /**
     * 清除缓存
     */
    public void clearCache(String sessionId) {
        messageCache.remove(sessionId);
    }
}