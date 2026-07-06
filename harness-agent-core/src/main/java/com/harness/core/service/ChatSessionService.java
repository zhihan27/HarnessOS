package com.harness.core.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.harness.core.entity.ChatSession;
import com.harness.core.entity.ChatMessage;
import com.harness.core.enums.SessionStatus;
import com.harness.core.mapper.ChatSessionMapper;
import com.harness.core.mapper.ChatMessageMapper;
import com.harness.core.memory.DatabaseChatMemoryStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * 会话管理服务
 * 提供会话创建、查询、历史获取等功能
 */
@Service
public class ChatSessionService {

    private static final Logger logger = LoggerFactory.getLogger(ChatSessionService.class);

    private final ChatSessionMapper sessionMapper;
    private final ChatMessageMapper messageMapper;
    private final DatabaseChatMemoryStore memoryStore;

    public ChatSessionService(ChatSessionMapper sessionMapper,
                             ChatMessageMapper messageMapper,
                             DatabaseChatMemoryStore memoryStore) {
        this.sessionMapper = sessionMapper;
        this.messageMapper = messageMapper;
        this.memoryStore = memoryStore;
    }

    /**
     * 创建新会话
     */
    @Transactional
    public ChatSession createSession(String tenantId, String userId) {
        String sessionId = generateSessionId();
        ChatSession session = memoryStore.createSession(sessionId, tenantId, userId);
        logger.info("创建新会话: sessionId={}, tenant={}, user={}", sessionId, tenantId, userId);
        return session;
    }

    /**
     * 使用指定ID创建会话（用于继续对话）
     */
    @Transactional
    public ChatSession createSessionWithId(String sessionId, String tenantId, String userId) {
        ChatSession session = memoryStore.createSession(sessionId, tenantId, userId);
        return session;
    }

    /**
     * 获取会话详情
     */
    public ChatSession getSession(String sessionId) {
        LambdaQueryWrapper<ChatSession> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ChatSession::getSessionId, sessionId);
        return sessionMapper.selectOne(wrapper);
    }

    /**
     * 查询用户的所有会话列表
     */
    public List<ChatSession> listSessionsByUser(String tenantId, String userId) {
        LambdaQueryWrapper<ChatSession> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ChatSession::getTenantId, tenantId)
               .eq(ChatSession::getUserId, userId)
               .eq(ChatSession::getStatus, SessionStatus.ACTIVE.getValue())
               .orderByDesc(ChatSession::getLastMessageAt);
        return sessionMapper.selectList(wrapper);
    }

    /**
     * 获取会话历史消息（支持分页）
     */
    public List<ChatMessage> getHistory(String sessionId) {
        return messageMapper.findBySessionIdOrderByOrder(sessionId);
    }

    /**
     * 获取会话历史消息（分页查询）
     * @param sessionId 会话ID
     * @param limit 返回条数
     * @param offset 偏移量
     * @param order 排序方式（asc/desc）
     */
    public List<ChatMessage> getHistory(String sessionId, Integer limit, Integer offset, String order) {
        return messageMapper.findBySessionIdWithPagination(sessionId, limit, offset, order);
    }

    /**
     * 归档会话
     */
    @Transactional
    public boolean archiveSession(String sessionId) {
        ChatSession session = getSession(sessionId);
        if (session == null) {
            logger.warn("会话不存在: sessionId={}", sessionId);
            return false;
        }

        session.setStatus(SessionStatus.ARCHIVED.getValue());
        int updated = sessionMapper.updateById(session);
        logger.info("会话已归档: sessionId={}", sessionId);
        return updated > 0;
    }

    /**
     * 删除会话
     */
    @Transactional
    public boolean deleteSession(String sessionId) {
        memoryStore.deleteMessages(sessionId);
        logger.info("会话已删除: sessionId={}", sessionId);
        return true;
    }

    /**
     * 检查会话是否存在
     */
    public boolean existsSession(String sessionId) {
        LambdaQueryWrapper<ChatSession> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ChatSession::getSessionId, sessionId)
               .eq(ChatSession::getStatus, SessionStatus.ACTIVE.getValue());
        return sessionMapper.selectCount(wrapper) > 0;
    }

    /**
     * 更新会话标题
     */
    @Transactional
    public boolean updateSessionTitle(String sessionId, String title) {
        ChatSession session = getSession(sessionId);
        if (session == null) {
            logger.warn("会话不存在，无法更新标题: sessionId={}", sessionId);
            return false;
        }
        session.setTitle(title);
        int updated = sessionMapper.updateById(session);
        logger.info("更新会话标题: sessionId={}, title={}", sessionId, title);
        return updated > 0;
    }

    /**
     * 生成会话ID
     */
    private String generateSessionId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }
}