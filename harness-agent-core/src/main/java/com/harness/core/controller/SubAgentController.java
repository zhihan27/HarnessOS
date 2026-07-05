package com.harness.core.controller;

import com.harness.core.entity.SubAgentTask;
import com.harness.core.service.SubAgentService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 子任务查询接口
 */
@RestController
@RequestMapping("/api/subAgent")
public class SubAgentController {

    private final SubAgentService subAgentService;

    public SubAgentController(SubAgentService subAgentService) {
        this.subAgentService = subAgentService;
    }

    /**
     * 查询子任务状态
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
     * 列出会话的所有子任务
     */
    @GetMapping("/list/{parentSessionId}")
    public SubAgentListResult listSubTasks(@PathVariable String parentSessionId) {
        List<SubAgentTask> tasks = subAgentService.getSubTasksByParentSession(parentSessionId);
        return new SubAgentListResult(true, tasks.size() + " 个子任务", tasks);
    }

    // DTOs
    public record SubAgentStatusResult(boolean success, String message, SubAgentTask task) {}
    public record SubAgentListResult(boolean success, String message, List<SubAgentTask> tasks) {}
}