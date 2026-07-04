package com.harness.core.controller;

import com.harness.core.entity.SubAgentTask;
import com.harness.core.service.SubAgentService;
import org.springframework.web.bind.annotation.*;

import java.util.List;


/**
 * 子 Agent 控制器
 * 仅提供基础的查询接口，不提供手动执行/重试接口
 * 所有执行和重试逻辑由 AI 通过 Tool 自动完成
 */
@RestController
@RequestMapping("/api/subAgent")
public class SubAgentController {

    private final SubAgentService subAgentService;

    public SubAgentController(SubAgentService subAgentService) {
        this.subAgentService = subAgentService;
    }

    /**
     * 查询子任务状态（供前端展示）
     */
    @GetMapping("/status/{taskId}")
    public SubAgentStatusResult getStatus(@PathVariable Long taskId) {
       SubAgentTask task = subAgentService.getSubTaskStatus(taskId);

        if (task == null) {
            return new SubAgentStatusResult(false, "任务不存在", null);
        }

        return new SubAgentStatusResult(true, "查询成功", task);
    }

    /**
     * 列出父会话的所有子任务（供前端展示）
     */
    @GetMapping("/list/{parentSessionId}")
    public SubAgentListResult listSubTasks(@PathVariable String parentSessionId) {
        List<SubAgentTask> tasks = subAgentService.getSubTasksByParentSession(parentSessionId);
        return new SubAgentListResult(true, tasks.size() + " 个子任务", tasks);
    }

    // DTOs
    public record SubAgentStatusResult(
        boolean success,
        String message,
        SubAgentTask task
    ) {}

    public record SubAgentListResult(
        boolean success,
        String message,
        List<SubAgentTask> tasks
    ) {}
}