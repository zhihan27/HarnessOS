package com.harness.core.memory;

import com.harness.core.entity.ChatMemorySummary;
import com.harness.core.mapper.ChatMemorySummaryMapper;
import com.harness.core.mapper.ChatMessageMapper;
import com.harness.core.model.AiChatModel;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.service.AiServices;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * 聊天记忆压缩器
 * 压缩策略：保留系统消息 + 最近2轮对话，中间历史生成摘要
 */
@Component
public class ChatMemoryCompressor {

    private static final Logger logger = LoggerFactory.getLogger(ChatMemoryCompressor.class);

    private final OpenAiChatModel chatModel;
    private final ChatMessageMapper messageMapper;
    private final ChatMemorySummaryMapper summaryMapper;
    private final TokenCounter tokenCounter;

    // 压缩时保留的消息数量（最近2轮对话 = 4条消息）
    private static final int PRESERVED_MESSAGE_COUNT = 4;

    public ChatMemoryCompressor(OpenAiChatModel chatModel,
                                ChatMessageMapper messageMapper,
                                ChatMemorySummaryMapper summaryMapper,
                                TokenCounter tokenCounter) {
        this.chatModel = chatModel;
        this.messageMapper = messageMapper;
        this.summaryMapper = summaryMapper;
        this.tokenCounter = tokenCounter;
    }

    /**
     * 执行压缩
     * @param sessionId 会话ID
     * @param messages 当前消息列表（LangChain4j消息）
     * @return 压缩后的消息列表
     */
    @Transactional
    public List<dev.langchain4j.data.message.ChatMessage> compress(String sessionId,
                                                                    List<dev.langchain4j.data.message.ChatMessage> messages) {
        if (messages == null || messages.size() <= PRESERVED_MESSAGE_COUNT) {
            logger.info("消息数量不足，无需压缩: sessionId={}, count={}", sessionId, messages.size());
            return messages;
        }

        logger.info("开始压缩会话记忆: sessionId={}, originalCount={}", sessionId, messages.size());

        // 1. 分离系统消息和对话消息
        List<dev.langchain4j.data.message.ChatMessage> systemMessages = new ArrayList<>();
        List<dev.langchain4j.data.message.ChatMessage> conversationMessages = new ArrayList<>();

        for (dev.langchain4j.data.message.ChatMessage message : messages) {
            if (message instanceof SystemMessage) {
                systemMessages.add(message);
            } else {
                conversationMessages.add(message);
            }
        }

        // 2. 确定需要压缩的消息范围（保留最近2轮对话）
        int preserveCount = Math.min(PRESERVED_MESSAGE_COUNT, conversationMessages.size());
        List<dev.langchain4j.data.message.ChatMessage> toCompress = conversationMessages.subList(0, conversationMessages.size() - preserveCount);
        List<dev.langchain4j.data.message.ChatMessage> preserved = conversationMessages.subList(conversationMessages.size() - preserveCount, conversationMessages.size());

        if (toCompress.isEmpty()) {
            logger.info("无消息需要压缩");
            return messages;
        }

        // 3. 生成摘要
        String summary = generateSummary(toCompress);
        int summaryTokens = tokenCounter.countTokens(summary);

        // 4. 构建压缩后的消息列表
        List<dev.langchain4j.data.message.ChatMessage> compressedMessages = new ArrayList<>();
        compressedMessages.addAll(systemMessages);  // 系统消息
        compressedMessages.add(SystemMessage.from("[历史摘要]\n" + summary));  // 摘要作为系统消息
        compressedMessages.addAll(preserved);  // 最近对话

        // 5. 记录压缩信息到数据库
        int originalTokens = tokenCounter.countTokens(toCompress);
        saveCompressionRecord(sessionId, toCompress.size(), summary, originalTokens, summaryTokens);

        logger.info("压缩完成: sessionId={}, original={}tokens, compressed={}tokens, saved={}tokens",
                sessionId, originalTokens, summaryTokens, originalTokens - summaryTokens);

        return compressedMessages;
    }

    /**
     * 使用AI生成历史摘要
     */
    private String generateSummary(List<dev.langchain4j.data.message.ChatMessage> messages) {
        StringBuilder historyText = new StringBuilder();
        for (dev.langchain4j.data.message.ChatMessage message : messages) {
            String role = getRoleName(message);
            String content = extractContent(message);
            historyText.append(role).append(": ").append(content).append("\n");
        }

        String prompt = String.format("""
            请对以下对话历史进行简洁摘要，保留关键信息、决策和结论：

            %s

            要求：
            1. 摘要简洁，不超过200字
            2. 保留重要的事实和决策
            3. 省略细节和重复内容
            """, historyText.toString());

        try {
            // 使用AiServices创建临时服务生成摘要
            interface SummaryGenerator {
                String generate(String prompt);
            }

            SummaryGenerator generator = AiServices.builder(SummaryGenerator.class)
                    .chatModel(chatModel)
                    .build();

            return generator.generate(prompt);
        } catch (Exception e) {
            logger.error("生成摘要失败", e);
            return "历史对话摘要生成失败，请查看原始记录。";
        }
    }

    /**
     * 获取消息角色名称
     */
    private String getRoleName(dev.langchain4j.data.message.ChatMessage message) {
        if (message instanceof UserMessage) return "用户";
        if (message instanceof AiMessage) return "AI";
        if (message instanceof SystemMessage) return "系统";
        return "其他";
    }

    /**
     * 从消息中提取内容
     */
    private String extractContent(dev.langchain4j.data.message.ChatMessage message) {
        if (message instanceof SystemMessage sysMsg) return sysMsg.text();
        if (message instanceof UserMessage userMsg) return userMsg.singleText();
        if (message instanceof AiMessage aiMsg) return aiMsg.text();
        if (message instanceof ToolExecutionResultMessage toolMsg) return toolMsg.text();
        return "";
    }

    /**
     * 保存压缩记录到数据库
     */
    private void saveCompressionRecord(String sessionId, int compressedCount,
                                        String summary, int originalTokens, int compressedTokens) {
        // 获取数据库消息记录的范围
        List<com.harness.core.entity.ChatMessage> dbMessages = messageMapper.findBySessionIdOrderByOrder(sessionId);
        if (dbMessages.isEmpty()) {
            return;
        }

        Long startId = dbMessages.get(0).getId();
        Long endId = dbMessages.get(Math.min(compressedCount, dbMessages.size()) - 1).getId();

        int tokenSaved = originalTokens - compressedTokens;
        BigDecimal ratio = BigDecimal.valueOf(compressedTokens)
                .divide(BigDecimal.valueOf(originalTokens), 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100));

        ChatMemorySummary summaryRecord = new ChatMemorySummary()
                .setSessionId(sessionId)
                .setSummaryType("PARTIAL")
                .setSummaryContent(summary)
                .setStartMessageId(startId)
                .setEndMessageId(endId)
                .setMessagesCount(compressedCount)
                .setOriginalTokens(originalTokens)
                .setCompressedTokens(compressedTokens)
                .setTokenSaved(tokenSaved)
                .setCompressionRatio(ratio);

        summaryMapper.insert(summaryRecord);
        logger.debug("压缩记录已保存: id={}", summaryRecord.getId());
    }
}