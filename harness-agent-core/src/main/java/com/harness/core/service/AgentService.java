package com.harness.core.service;

import com.harness.core.entity.SubAgentTask;
import com.harness.core.hook.ChatContext;
import com.harness.core.hook.ChatHookExecutor;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.service.AiServices;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Agent 服务
 * chat方法显式调用AI，Hook无感执行
 */
@Service
public class AgentService {

    private static final Logger logger = LoggerFactory.getLogger(AgentService.class);

    private final ChatHookExecutor hookExecutor;
    private final OpenAiChatModel openAiChatModel;

    private final ExecutorService executor = Executors.newFixedThreadPool(4);

    public AgentService(ChatHookExecutor hookExecutor,
                        OpenAiChatModel openAiChatModel) {
        this.hookExecutor = hookExecutor;
        this.openAiChatModel = openAiChatModel;
    }

    /**
     * Chat接口
     * 显式调用AI，Hook无感拦截
     */
    public ChatResult chat(String tenantId, String userId, String sessionId, String message) {
        logger.info("Chat开始: sessionId={}", sessionId);

        ChatContext context = new ChatContext(tenantId, userId, sessionId, message);

        // 前置Hook：安全检查、任务拆解
        hookExecutor.executeBefore(context);

        if (!context.isSuccess()) {
            return new ChatResult(false, context.getResult(), sessionId);
        }

        // === 显式调用AI ===
        String aiResponse = callAI(context);
        // ==================

        context.setResult(aiResponse);

        // 后置Hook：完整性检查
        hookExecutor.executeAfter(context);

        logger.info("Chat完成: success={}", context.isSuccess());

        return new ChatResult(context.isSuccess(), context.getResult(), sessionId);
    }

    /**
     * 异步执行子任务，主线程等待后组装结果
     */
    private String callAI(ChatContext context) {
        List<SubAgentTask> subTasks = context.getSubTasks();
        StringBuilder summary = new StringBuilder();

        // 异步执行所有子任务
        List<CompletableFuture<String>> futures = subTasks.stream()
                .map(task -> CompletableFuture.supplyAsync(() -> executeTask(task), executor))
                .toList();

        // 主线程等待所有任务完成
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .orTimeout(60, TimeUnit.SECONDS)
                .join();

        // 组装结果
        for (int i = 0; i < futures.size(); i++) {
            SubAgentTask task = subTasks.get(i);
            String result = futures.get(i).join();

            summary.append("[").append(task.getTaskDescription()).append("]\n");
            summary.append(result).append("\n\n");
        }

        return summary.toString();
    }

    /**
     * 执行单个子任务
     */
    private String executeTask(SubAgentTask task) {
        logger.info("执行子任务: {}", task.getTaskDescription());

        AiChatService aiService = AiServices.builder(AiChatService.class)
                .chatModel(openAiChatModel)
                .chatMemory(MessageWindowChatMemory.withMaxMessages(10))
                .build();

        String prompt = buildPrompt(task);
        return aiService.chat(prompt);
    }

    private String buildPrompt(SubAgentTask task) {
        return String.format("任务: %s\n输入: %s\n要求: 独立完成并返回结果摘要。",
                task.getTaskDescription(),
                task.getTaskInput() != null ? task.getTaskInput() : "");
    }

    public record ChatResult(boolean success, String message, String sessionId) {}
}