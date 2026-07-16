package com.harness.core.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 工具执行进度广播服务
 *
 * 通过 SSE 向前端实时推送任务执行进度：
 * - 任务创建
 * - 任务开始执行
 * - 任务执行进度
 * - 任务完成
 * - 任务失败
 */
@Service
public class ToolProgressBroadcaster {

    private static final Logger logger = LoggerFactory.getLogger(ToolProgressBroadcaster.class);

    // SSE 连接列表（按 sessionId 分组）
    private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 创建 SSE 连接
     */
    public SseEmitter createConnection() {
        SseEmitter emitter = new SseEmitter(Long.MAX_VALUE);

        emitter.onCompletion(() -> {
            emitters.remove(emitter);
            logger.info("工具进度 SSE 连接关闭");
        });

        emitter.onTimeout(() -> {
            emitters.remove(emitter);
            logger.warn("工具进度 SSE 连接超时");
        });

        emitter.onError(e -> {
            emitters.remove(emitter);
            logger.error("工具进度 SSE 连接错误: {}", e.getMessage());
        });

        emitters.add(emitter);
        logger.info("新建工具进度 SSE 连接，当前连接数: {}", emitters.size());

        return emitter;
    }

    // ==================== 任务进度事件 ====================

    /**
     * 广播任务创建
     */
    public void broadcastTaskCreated(String sessionId, String taskId, String subject) {
        TaskProgressEvent event = new TaskProgressEvent(
                "TASK_CREATED",
                sessionId,
                taskId,
                subject,
                null,
                null,
                null,
                0
        );
        broadcastEvent(event);
        logger.debug("任务创建: sessionId={}, taskId={}", sessionId, taskId);
    }

    /**
     * 广播任务开始执行
     */
    public void broadcastTaskStarted(String sessionId, String taskId, String subject, String agentId) {
        TaskProgressEvent event = new TaskProgressEvent(
                "TASK_STARTED",
                sessionId,
                taskId,
                subject,
                agentId,
                null,
                null,
                10
        );
        broadcastEvent(event);
        logger.debug("任务开始执行: sessionId={}, taskId={}, agentId={}", sessionId, taskId, agentId);
    }

    /**
     * 广播任务执行进度
     *
     * @param sessionId  会话 ID
     * @param taskId     任务 ID
     * @param subject    任务标题
     * @param progress   进度百分比 (0-100)
     * @param message    进度消息
     */
    public void broadcastTaskProgress(String sessionId, String taskId, String subject, int progress, String message) {
        TaskProgressEvent event = new TaskProgressEvent(
                "TASK_PROGRESS",
                sessionId,
                taskId,
                subject,
                null,
                message,
                null,
                Math.min(100, Math.max(0, progress))
        );
        broadcastEvent(event);
        logger.debug("任务进度: sessionId={}, taskId={}, progress={}%", sessionId, taskId, progress);
    }

    /**
     * 广播任务完成
     */
    public void broadcastTaskCompleted(String sessionId, String taskId, String subject, String result) {
        TaskProgressEvent event = new TaskProgressEvent(
                "TASK_COMPLETED",
                sessionId,
                taskId,
                subject,
                null,
                null,
                truncate(result, 500),
                100
        );
        broadcastEvent(event);
        logger.debug("任务完成: sessionId={}, taskId={}", sessionId, taskId);
    }

    /**
     * 广播任务失败
     */
    public void broadcastTaskFailed(String sessionId, String taskId, String subject, String error) {
        TaskProgressEvent event = new TaskProgressEvent(
                "TASK_FAILED",
                sessionId,
                taskId,
                subject,
                null,
                null,
                "错误: " + error,
                -1
        );
        broadcastEvent(event);
        logger.debug("任务失败: sessionId={}, taskId={}, error={}", sessionId, taskId, error);
    }

    /**
     * 广播所有任务完成
     */
    public void broadcastAllTasksCompleted(String sessionId, int successCount, int failedCount) {
        AllTasksEvent event = new AllTasksEvent(
                "ALL_TASKS_COMPLETED",
                sessionId,
                successCount,
                failedCount
        );
        broadcastEvent(event);
        logger.info("所有任务完成: sessionId={}, success={}, failed={}", sessionId, successCount, failedCount);
    }

    // ==================== 工具执行事件 ====================

    /**
     * 广播工具开始执行
     */
    public void broadcastToolStarted(String sessionId, String toolName, String args) {
        ToolProgressEvent event = new ToolProgressEvent(
                "TOOL_STARTED",
                sessionId,
                null,
                toolName,
                truncate(args, 200),
                null,
                null
        );
        broadcastEvent(event);
        logger.debug("工具开始执行: sessionId={}, tool={}", sessionId, toolName);
    }

    /**
     * 广播工具执行完成
     */
    public void broadcastToolCompleted(String sessionId, String toolName, String result) {
        ToolProgressEvent event = new ToolProgressEvent(
                "TOOL_COMPLETED",
                sessionId,
                null,
                toolName,
                null,
                truncate(result, 500),
                null
        );
        broadcastEvent(event);
        logger.debug("工具执行完成: sessionId={}, tool={}", sessionId, toolName);
    }

    /**
     * 广播工具执行出错
     */
    public void broadcastToolError(String sessionId, String toolName, String error) {
        ToolProgressEvent event = new ToolProgressEvent(
                "TOOL_ERROR",
                sessionId,
                null,
                toolName,
                null,
                null,
                error
        );
        broadcastEvent(event);
        logger.debug("工具执行出错: sessionId={}, tool={}, error={}", sessionId, toolName, error);
    }

    // ==================== 内部方法 ====================

    /**
     * 广播事件到所有连接
     */
    private void broadcastEvent(Object event) {
        if (emitters.isEmpty()) {
            return;
        }

        try {
            String json = objectMapper.writeValueAsString(event);

            for (SseEmitter emitter : emitters) {
                try {
                    emitter.send(SseEmitter.event()
                            .name("task-progress")
                            .data(json));
                } catch (IOException e) {
                    emitters.remove(emitter);
                    logger.warn("发送进度消息失败，移除连接: {}", e.getMessage());
                }
            }

        } catch (Exception e) {
            logger.error("序列化进度事件失败: {}", e.getMessage());
        }
    }

    /**
     * 获取当前连接数
     */
    public int getConnectionCount() {
        return emitters.size();
    }

    private String truncate(String str, int maxLength) {
        if (str == null) return null;
        return str.length() > maxLength ? str.substring(0, maxLength) + "..." : str;
    }

    // ==================== 事件数据结构 ====================

    /**
     * 任务进度事件
     */
    public record TaskProgressEvent(
            String eventType,    // TASK_CREATED, TASK_STARTED, TASK_PROGRESS, TASK_COMPLETED, TASK_FAILED
            String sessionId,    // 会话 ID
            String taskId,       // 任务 ID
            String subject,      // 任务标题
            String agentId,      // 执行的 Agent ID（TASK_STARTED 时有值）
            String message,      // 进度消息
            String result,       // 执行结果（TASK_COMPLETED 时有值）
            int progress         // 进度百分比 (0-100, -1 表示失败)
    ) {}

    /**
     * 工具进度事件
     */
    public record ToolProgressEvent(
            String eventType,    // TOOL_STARTED, TOOL_COMPLETED, TOOL_ERROR
            String sessionId,    // 会话 ID
            String taskId,       // 关联的任务 ID（可选）
            String toolName,     // 工具名称
            String args,         // 工具参数（TOOL_STARTED 时有值）
            String result,       // 执行结果（TOOL_COMPLETED 时有值）
            String error         // 错误信息（TOOL_ERROR 时有值）
    ) {}

    /**
     * 所有任务完成事件
     */
    public record AllTasksEvent(
            String eventType,    // ALL_TASKS_COMPLETED
            String sessionId,    // 会话 ID
            int successCount,    // 成功数量
            int failedCount      // 失败数量
    ) {}
}