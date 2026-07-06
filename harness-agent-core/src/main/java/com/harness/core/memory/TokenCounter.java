package com.harness.core.memory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Token计数器
 * 使用简单估算方法：英文约4字符=1token，中文约2字符=1token
 * DeepSeek与OpenAI Token计算方式相近，可使用此近似算法
 */
@Component
public class TokenCounter {

    private static final Logger logger = LoggerFactory.getLogger(TokenCounter.class);

    /**
     * 计算消息列表的总Token数
     */
    public int countTokens(List<dev.langchain4j.data.message.ChatMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            return 0;
        }

        int totalTokens = 0;
        for (dev.langchain4j.data.message.ChatMessage message : messages) {
            totalTokens += countTokens(message);
        }

        logger.debug("计算Token总数: {}条消息, {} tokens", messages.size(), totalTokens);
        return totalTokens;
    }

    /**
     * 计算单条消息的Token数
     */
    public int countTokens(dev.langchain4j.data.message.ChatMessage message) {
        if (message == null) {
            return 0;
        }

        String text = extractText(message);
        if (text == null || text.isEmpty()) {
            return 0;
        }

        return estimateTokenCount(text);
    }

    /**
     * 从消息中提取文本
     */
    private String extractText(dev.langchain4j.data.message.ChatMessage message) {
        if (message instanceof dev.langchain4j.data.message.SystemMessage sysMsg) {
            return sysMsg.text();
        } else if (message instanceof dev.langchain4j.data.message.UserMessage userMsg) {
            return userMsg.singleText();
        } else if (message instanceof dev.langchain4j.data.message.AiMessage aiMsg) {
            return aiMsg.text();
        } else if (message instanceof dev.langchain4j.data.message.ToolExecutionResultMessage toolMsg) {
            return toolMsg.text();
        }
        return "";
    }

    /**
     * 计算文本的Token数
     */
    public int countTokens(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        return estimateTokenCount(text);
    }

    /**
     * 估算Token数量
     * 规则：
     * - 英文/数字/符号：约4字符=1token
     * - 中文：约2字符=1token
     * - 加上消息结构开销（每条消息额外约4 tokens）
     */
    private int estimateTokenCount(String text) {
        int tokens = 0;
        int chineseChars = 0;
        int otherChars = 0;

        for (char c : text.toCharArray()) {
            if (isChinese(c)) {
                chineseChars++;
            } else {
                otherChars++;
            }
        }

        // 英文/数字/符号：4字符≈1token
        tokens += otherChars / 4 + (otherChars % 4 > 0 ? 1 : 0);

        // 中文：2字符≈1token
        tokens += chineseChars / 2 + (chineseChars % 2 > 0 ? 1 : 0);

        // 消息结构开销
        tokens += 4;

        return tokens;
    }

    /**
     * 判断是否为中文字符
     */
    private boolean isChinese(char c) {
        return c >= 0x4E00 && c <= 0x9FFF;
    }
}