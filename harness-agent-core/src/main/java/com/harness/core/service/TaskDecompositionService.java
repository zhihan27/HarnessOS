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
            你是任务拆解专家。分析用户任务，识别是否需要文件操作，并拆解为具体子任务步骤。

            # 核心原则

            ## 主Agent职责（仅问答）
            - 回答问题、分析需求、提供方案
            - **不执行任何文件操作、代码执行、系统操作**

            ## 子Agent职责（必须拆解）
            - **文件操作**：创建/读取/修改/删除文件
            - **代码执行**：运行程序、编译、测试
            - **系统操作**：安装依赖、配置环境、bash命令
            - **数据处理**：数据库操作、API调用

            # 拆解规则

            1. **识别操作类型**：
               - 涉及文件/代码/系统操作 → 必须拆解为子任务
               - 仅问答/分析 → 主Agent直接回答

            2. **拆解粒度**：
               - 每个子任务是可独立执行的最小单元
               - 明确依赖关系和执行顺序

            3. **任务类型标记**：
               - FILE_OPERATION: 文件操作
               - CODE_EXECUTION: 代码执行
               - SYSTEM_OPERATION: 系统操作
               - DATA_PROCESSING: 数据处理
               - GENERAL: 通用任务

            输出格式：每行一个子任务，格式为"序号|任务类型|子任务描述"
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
                try {
                    int order = Integer.parseInt(parts[0].trim().replaceAll("[^0-9]", ""));

                    // 解析任务类型（如果有）
                    TaskType taskType = TaskType.GENERAL;
                    String description;

                    if (parts.length >= 3) {
                        // 格式: 序号|任务类型|描述
                        String typeStr = parts[1].trim().toUpperCase();
                        try {
                            taskType = TaskType.valueOf(typeStr);
                        } catch (IllegalArgumentException e) {
                            // 如果类型不匹配，保持 GENERAL
                        }
                        description = parts[2].trim();
                    } else {
                        // 格式: 序号|描述
                        description = parts[1].trim();
                    }

                    subTasks.add(SubTaskDefinition.builder()
                            .order(order)
                            .taskType(taskType)
                            .description(description)
                            .inputTemplate(description)
                            .build());
                } catch (Exception e) {
                    logger.warn("解析子任务失败: {}", line);
                }
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