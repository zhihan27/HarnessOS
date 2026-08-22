package com.harness.core.memory;

import com.harness.core.mapper.ChatMemorySummaryMapper;
import com.harness.core.mapper.ChatMessageMapper;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 聊天记忆压缩器
 * 使用 OpenAI 模型生成摘要
 */
@Service
public class ChatMemoryCompressor {

    private static final Logger logger = LoggerFactory.getLogger(ChatMemoryCompressor.class);

    private final OpenAiStreamingChatModel streamingChatModel;
    private final ChatMessageMapper messageMapper;
    private final ChatMemorySummaryMapper summaryMapper;
    private final TokenCounter tokenCounter;

    private static final int PRESERVED_MESSAGE_COUNT = 4;

    // 摘要生成接口
    private interface SummaryGenerator {
        @SystemMessage("""
            你是一个专业的对话摘要助手。请为以下对话生成简洁的摘要：

            1. 保留关键决策和结论
            2. 保留重要的技术细节
            3. 忽略寒暄和无关内容
            4. 使用中文输出
            """)
        String generateSummary(@UserMessage String conversation);
    }

    public ChatMemoryCompressor(
            OpenAiStreamingChatModel streamingChatModel,
            ChatMessageMapper messageMapper,
            ChatMemorySummaryMapper summaryMapper,
            TokenCounter tokenCounter) {
        this.streamingChatModel = streamingChatModel;
        this.messageMapper = messageMapper;
        this.summaryMapper = summaryMapper;
        this.tokenCounter = tokenCounter;
    }

    /**
     * 压缩聊天记忆
     */
    public List<ChatMessage> compress(String sessionId, List<ChatMessage> messages) {
        if (messages == null || messages.size() <= PRESERVED_MESSAGE_COUNT) {
            logger.info("消息数量不足，无需压缩: sessionId={}, count={}", sessionId, messages == null ? 0 : messages.size());
            return messages;
        }

        logger.info("开始压缩记忆: sessionId={}, 原始消息数={}", sessionId, messages.size());

        try {
            // 保留最近的消息
            List<ChatMessage> preservedMessages = new ArrayList<>(
                    messages.subList(messages.size() - PRESERVED_MESSAGE_COUNT, messages.size())
            );

            // 需要压缩的历史消息
            List<ChatMessage> toCompress = new ArrayList<>(
                    messages.subList(0, messages.size() - PRESERVED_MESSAGE_COUNT)
            );

            // 生成摘要
            String summary = generateSummary(toCompress);

            // TODO: 保存摘要到数据库

            logger.info("压缩完成: sessionId={}, 压缩前={}, 压缩后={}, 摘要长度={}",
                    sessionId, messages.size(), preservedMessages.size() + 1, summary.length());

            // 返回：摘要 + 保留的消息
            List<ChatMessage> compressed = new ArrayList<>();
            // 这里简化处理，实际应该将摘要作为 SystemMessage 添加
            compressed.addAll(preservedMessages);

            return compressed;

        } catch (Exception e) {
            logger.error("压缩记忆失败: sessionId=" + sessionId, e);
            return messages; // 失败时返回原消息
        }
    }

    /**
     * 使用 AI 生成摘要
     */
    private String generateSummary(List<ChatMessage> messages) {
        try {
            // 构建对话文本
            StringBuilder conversation = new StringBuilder();
            for (ChatMessage message : messages) {
                conversation.append(message.toString()).append("\n");
            }

            // 使用 OpenAI 模型生成摘要
            SummaryGenerator generator = AiServices.builder(SummaryGenerator.class)
                    .streamingChatModel(streamingChatModel)
                    .build();

            return generator.generateSummary(conversation.toString());

        } catch (Exception e) {
            logger.error("生成摘要失败", e);
            return "历史对话摘要生成失败";
        }
    }
}