package com.harness.core.hook;

import com.harness.core.dto.SubTaskDefinition;
import com.harness.core.dto.TaskDecompositionContext;
import com.harness.core.entity.SubAgentTask;
import com.harness.core.service.TaskDecompositionService;
import com.harness.core.service.SubAgentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 任务拆解 Hook
 */
@Component
@Order(2)
public class DecompositionHook implements ChatHook {

    private static final Logger logger = LoggerFactory.getLogger(DecompositionHook.class);

    private final TaskDecompositionService decompositionService;
    private final SubAgentService subAgentService;

    public DecompositionHook(TaskDecompositionService decompositionService,
                              SubAgentService subAgentService) {
        this.decompositionService = decompositionService;
        this.subAgentService = subAgentService;
    }

    @Override
    public String getName() {
        return "DecompositionHook";
    }

    @Override
    public boolean execute(ChatContext context) {
        logger.info("任务拆解: message={}", context.getMessage());

        // 构建拆解上下文
        TaskDecompositionContext decompContext = TaskDecompositionContext.builder()
                .mainTaskDescription(context.getMessage())
                .tenantId(context.getTenantId())
                .userId(context.getUserId())
                .sessionId(context.getSessionId())
                .build();

        // 拆解任务
        List<SubTaskDefinition> subTaskDefs = decompositionService.decompose(decompContext);
        logger.info("拆解完成: {} 个子任务", subTaskDefs.size());

        // 创建子任务实体
        List<SubAgentTask> subTasks = new ArrayList<>();
        for (SubTaskDefinition def : subTaskDefs) {
            SubAgentTask task = subAgentService.createSubTask(
                    context.getTenantId(),
                    context.getUserId(),
                    context.getSessionId(),
                    def.getTaskType().getValue(),
                    def.getDescription(),
                    def.getInputTemplate()
            );
            subTasks.add(task);
        }

        context.setSubTasks(subTasks);
        return true;
    }
}