package com.harness.core.service;

import com.harness.core.dto.SubTaskDefinition;
import com.harness.core.dto.TaskDecompositionContext;
import com.harness.core.enums.TaskType;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.SystemMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 任务拆解服务
 * AI真实拆解任务，而非固定模板
 */
@Service
public class TaskDecompositionService {

    private static final Logger logger = LoggerFactory.getLogger(TaskDecompositionService.class);

    private final OpenAiChatModel openAiChatModel;

    // 简单的拆解接口（无记忆，单参数）
    private interface DecompositionAI {
        @SystemMessage("""
            你是任务分析专家。分析用户任务，拆解为具体子任务步骤。

            要求：
            1. 每个子任务必须是具体的、可独立执行的操作
            2. 子任务之间尽量独立，可以并行执行
            3. 根据任务实际情况拆解

            输出格式：每行一个子任务，格式为"序号|子任务描述"
            """)
        String decompose(String taskDescription);
    }

    private final DecompositionAI decompositionAI;

    public TaskDecompositionService(OpenAiChatModel openAiChatModel) {
        this.openAiChatModel = openAiChatModel;
        this.decompositionAI = AiServices.builder(DecompositionAI.class)
                .chatModel(openAiChatModel)
                .build();
    }

    /**
     * AI拆解任务为真实的子任务列表
     */
    public List<SubTaskDefinition> decompose(TaskDecompositionContext context) {
        logger.info("AI拆解任务: {}", context.getMainTaskDescription());

        // 调用AI生成拆解计划
        String taskPrompt = context.getMainTaskDescription();
        String aiResponse = decompositionAI.decompose(taskPrompt);

        // 解析AI返回的子任务列表
        List<SubTaskDefinition> subTasks = parseSubTasks(aiResponse);

        logger.info("拆解完成: {} 个子任务", subTasks.size());
        return subTasks;
    }

    /**
     * 解析AI返回的子任务列表
     */
    private List<SubTaskDefinition> parseSubTasks(String aiResponse) {
        List<SubTaskDefinition> subTasks = new ArrayList<>();

        String[] lines = aiResponse.split("\n");
        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty() || !line.contains("|")) {
                continue;
            }

            String[] parts = line.split("\\|");
            if (parts.length >= 2) {
                int order = Integer.parseInt(parts[0].trim().replaceAll("[^0-9]", ""));
                String description = parts[1].trim();

                subTasks.add(SubTaskDefinition.builder()
                        .order(order)
                        .taskType(TaskType.GENERAL)  // 设置默认类型
                        .description(description)
                        .inputTemplate(description)
                        .build());
            }
        }

        // 如果解析失败，至少返回原任务
        if (subTasks.isEmpty()) {
            subTasks.add(SubTaskDefinition.builder()
                    .order(1)
                    .taskType(TaskType.GENERAL)
                    .description("执行主任务")
                    .inputTemplate(aiResponse)
                    .build());
        }

        return subTasks;
    }
}