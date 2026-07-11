package com.harness.core.controller;

import com.harness.core.entity.DagTask;
import com.harness.core.service.DagTaskService;
import com.harness.core.service.TaskDispatcher;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Task Team 控制器
 * 提供任务状态查询和管理的 API
 */
@RestController
@RequestMapping("/api/task")
public class TaskController {

    private final DagTaskService taskService;
    private final TaskDispatcher taskDispatcher;

    public TaskController(DagTaskService taskService, TaskDispatcher taskDispatcher) {
        this.taskService = taskService;
        this.taskDispatcher = taskDispatcher;
    }

    /**
     * 获取所有任务列表
     */
    @GetMapping("list")
    public List<TaskResponse> listTasks(
            @RequestParam(required = false) String sessionId,
            @RequestParam(required = false) String status) {

        List<DagTask> tasks;
        if (sessionId != null && !sessionId.isEmpty()) {
            tasks = taskService.getTasksBySession(sessionId);
        } else if (status != null && !status.isEmpty()) {
            tasks = taskService.getTasksByStatus(status);
        } else {
            tasks = taskService.getAllTasks();
        }

        return tasks.stream()
                .map(this::toTaskResponse)
                .toList();
    }

    /**
     * 获取任务详情
     */
    @GetMapping("{taskId}")
    public TaskResponse getTask(@PathVariable String taskId) {
        DagTask task = taskService.getTask(taskId);
        if (task == null) {
            return null;
        }
        return toTaskResponse(task);
    }

    /**
     * 获取任务进度统计
     */
    @GetMapping("progress")
    public TaskProgressResponse getProgress(
            @RequestParam(required = false) String sessionId) {

        List<DagTask> tasks = sessionId != null
                ? taskService.getTasksBySession(sessionId)
                : taskService.getAllTasks();

        long pending = tasks.stream().filter(t -> "pending".equals(t.getStatus())).count();
        long inProgress = tasks.stream().filter(t -> "in_progress".equals(t.getStatus())).count();
        long completed = tasks.stream().filter(t -> "completed".equals(t.getStatus())).count();
        long failed = tasks.stream().filter(t -> "failed".equals(t.getStatus())).count();

        return new TaskProgressResponse(
                tasks.size(), pending, inProgress, completed, failed,
                taskDispatcher.isRunning()
        );
    }

    /**
     * 获取就绪任务（可立即执行的）
     */
    @GetMapping("ready")
    public List<TaskResponse> getReadyTasks() {
        return taskService.getUnblockedTasks().stream()
                .map(this::toTaskResponse)
                .toList();
    }

    /**
     * 获取被阻塞的任务
     */
    @GetMapping("blocked")
    public List<TaskResponse> getBlockedTasks() {
        return taskService.getBlockedTasks().stream()
                .map(this::toTaskResponse)
                .toList();
    }

    /**
     * 启动 Task Team
     */
    @PostMapping("team/start")
    public TeamStatusResponse startTaskTeam(@RequestBody(required = false) TeamStartRequest request) {
        String sessionId = request != null ? request.sessionId() : null;
        taskDispatcher.startTaskTeam(sessionId);
        return new TeamStatusResponse(true, "Task Team 已启动", taskDispatcher.getStatus());
    }

    /**
     * 停止 Task Team
     */
    @PostMapping("team/stop")
    public TeamStatusResponse stopTaskTeam() {
        taskDispatcher.stopTaskTeam();
        return new TeamStatusResponse(false, "Task Team 已停止", taskDispatcher.getStatus());
    }

    /**
     * 获取 Task Team 状态
     */
    @GetMapping("team/status")
    public TeamStatusResponse getTeamStatus() {
        return new TeamStatusResponse(
                taskDispatcher.isRunning(),
                taskDispatcher.isRunning() ? "运行中" : "已停止",
                taskDispatcher.getStatus()
        );
    }

    // ========== Helper Methods ==========

    private TaskResponse toTaskResponse(DagTask task) {
        return new TaskResponse(
                task.getTaskId(),
                task.getSubject(),
                task.getDescription(),
                task.getStatus(),
                task.getOwner(),
                task.getSessionId(),
                task.getBlockedBy(),
                task.getBlocks(),
                task.getResult(),
                task.getError(),
                task.getAssignedAgentId(),  // 新增：分配的Agent ID
                task.getStartedAt(),
                task.getCompletedAt(),
                task.getCreatedAt()
        );
    }

    // ========== Request/Response Records ==========

    public record TaskResponse(
            String taskId,
            String subject,
            String description,
            String status,
            String owner,
            String sessionId,
            String blockedBy,
            String blocks,
            String result,
            String error,
            String assignedAgentId,  // 新增字段
            java.time.LocalDateTime startedAt,
            java.time.LocalDateTime completedAt,
            java.time.LocalDateTime createdAt
    ) {}

    public record TaskProgressResponse(
            long total,
            long pending,
            long inProgress,
            long completed,
            long failed,
            boolean isRunning
    ) {}

    public record TeamStartRequest(String sessionId) {}

    public record TeamStatusResponse(
            boolean running,
            String message,
            String details
    ) {}
}