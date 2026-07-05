package com.harness.core.service;

import com.harness.core.dto.SubTaskDefinition;
import com.harness.core.dto.TaskDecompositionContext;
import com.harness.core.enums.TaskType;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.service.AiServices;
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
    private final AiChatService decompositionService;

    public TaskDecompositionService(OpenAiChatModel openAiChatModel) {
        this.openAiChatModel = openAiChatModel;
        this.decompositionService = AiServices.builder(AiChatService.class)
                .chatModel(openAiChatModel)
                .build();
    }

    /**
     * AI拆解任务为真实的子任务列表
     */
    public List<SubTaskDefinition> decompose(TaskDecompositionContext context) {
        logger.info("AI拆解任务: {}", context.getMainTaskDescription());

        // 调用AI生成拆解计划
        String decompositionPrompt = buildDecompositionPrompt(context.getMainTaskDescription());
        String aiResponse = decompositionService.chat(decompositionPrompt);

        // 解析AI返回的子任务列表
        List<SubTaskDefinition> subTasks = parseSubTasks(aiResponse);

        logger.info("拆解完成: {} 个子任务", subTasks.size());
        return subTasks;
    }

    /**
     * 构建拆解prompt
     */
    private String buildDecompositionPrompt(String task) {
        return String.format("""
            分析以下任务，将其拆解为具体的子任务步骤。

            任务：%s

            要求：
            1. 每个子任务必须是一个具体的、可独立执行的操作
            2. 子任务之间尽量独立，可以并行执行
            3. 根据任务实际情况拆解，不要固定模板

            输出格式（每行一个子任务）：
            序号|子任务描述

            例如格式：
            1|第一步具体操作
            2|第二步具体操作
            3|第三步具体操作

            请根据任务内容输出拆解结果：
            """, task);
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