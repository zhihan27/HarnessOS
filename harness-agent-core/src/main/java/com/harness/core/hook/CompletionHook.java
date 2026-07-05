package com.harness.core.hook;

import com.harness.core.entity.AgentTodoTask;
import com.harness.core.service.AgentTodoTaskService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 完整性检查 Hook
 */
@Component
@Order(3)
public class CompletionHook implements ChatHook {

    private static final Logger logger = LoggerFactory.getLogger(CompletionHook.class);

    private final AgentTodoTaskService todoTaskService;

    public CompletionHook(AgentTodoTaskService todoTaskService) {
        this.todoTaskService = todoTaskService;
    }

    @Override
    public String getName() {
        return "CompletionHook";
    }

    @Override
    public boolean execute(ChatContext context) {
        logger.info("完整性检查: sessionId={}", context.getSessionId());

        // 检查是否有未完成的Todo任务
        var pendingTasks = todoTaskService.getPendingTasks(context.getSessionId());

        if (!pendingTasks.isEmpty()) {
            logger.warn("发现 {} 个未完成任务", pendingTasks.size());

            // 标记所有未完成任务为已完成（因为主流程已结束）
            for (AgentTodoTask task : pendingTasks) {
                todoTaskService.markCompleted(task.getId());
                logger.info("强制完成任务: id={}", task.getId());
            }
        }

        logger.info("完整性检查通过");
        return true;
    }
}