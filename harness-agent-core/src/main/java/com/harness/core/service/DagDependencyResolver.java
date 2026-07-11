package com.harness.core.service;

import com.harness.core.entity.DagTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * DAG 依赖图解析器
 *
 * 核心算法：
 * 1. 环检测（DFS-based cycle detection）- 防止循环依赖
 * 2. 拓扑排序（Kahn's algorithm）- 计算有效执行顺序
 * 3. 就绪任务检测 - 找出所有依赖已完成的 pending 任务
 */
@Component
public class DagDependencyResolver {

    private static final Logger logger = LoggerFactory.getLogger(DagDependencyResolver.class);

    /**
     * 检测添加依赖是否会产生循环
     *
     * @param tasks 当前所有任务
     * @param taskId 要添加依赖的任务
     * @param blockedByTaskId 要依赖的任务
     * @return true 表示会产生循环
     */
    public boolean wouldCreateCycle(List<DagTask> tasks, String taskId, String blockedByTaskId) {
        // 构建依赖图：taskId -> 它依赖的任务列表
        Map<String, Set<String>> graph = buildDependencyGraph(tasks);

        // 添加新的依赖边
        graph.computeIfAbsent(taskId, k -> new HashSet<>()).add(blockedByTaskId);

        // 检测是否存在从 blockedByTaskId 出发能到达 taskId 的路径
        // 如果存在，说明新依赖会形成闭环
        Set<String> visited = new HashSet<>();
        Set<String> recursionStack = new HashSet<>();

        return hasCycle(graph, blockedByTaskId, visited, recursionStack, taskId);
    }

    /**
     * 获取就绪任务（无未完成依赖的 pending 任务）
     */
    public List<DagTask> getReadyTasks(List<DagTask> tasks) {
        return tasks.stream()
                .filter(task -> "pending".equals(task.getStatus()))
                .filter(task -> areDependenciesCompleted(tasks, task))
                .collect(Collectors.toList());
    }

    /**
     * 获取被阻塞的任务（有未完成依赖）
     */
    public List<DagTask> getBlockedTasks(List<DagTask> tasks) {
        return tasks.stream()
                .filter(task -> "pending".equals(task.getStatus()))
                .filter(task -> !areDependenciesCompleted(tasks, task))
                .collect(Collectors.toList());
    }

    /**
     * 检查任务的所有依赖是否已完成
     */
    public boolean areDependenciesCompleted(List<DagTask> tasks, DagTask task) {
        List<String> blockedByList = parseJsonArray(task.getBlockedBy());

        if (blockedByList == null || blockedByList.isEmpty()) {
            return true;
        }

        // 构建任务 ID -> 任务映射
        Map<String, DagTask> taskMap = tasks.stream()
                .collect(Collectors.toMap(DagTask::getTaskId, t -> t, (a, b) -> a));

        return blockedByList.stream()
                .allMatch(depId -> {
                    DagTask depTask = taskMap.get(depId);
                    return depTask != null && "completed".equals(depTask.getStatus());
                });
    }

    /**
     * 获取依赖某个任务的后续任务（反向依赖）
     */
    public List<DagTask> getDependents(List<DagTask> tasks, String taskId) {
        return tasks.stream()
                .filter(task -> {
                    List<String> blockedByList = parseJsonArray(task.getBlockedBy());
                    return blockedByList != null && blockedByList.contains(taskId);
                })
                .collect(Collectors.toList());
    }

    // ========== 私有方法 ==========

    /**
     * 构建依赖图
     * 返回：taskId -> 它依赖的任务 ID 列表
     */
    private Map<String, Set<String>> buildDependencyGraph(List<DagTask> tasks) {
        Map<String, Set<String>> graph = new HashMap<>();

        for (DagTask task : tasks) {
            List<String> blockedByList = parseJsonArray(task.getBlockedBy());
            if (blockedByList != null && !blockedByList.isEmpty()) {
                graph.put(task.getTaskId(), new HashSet<>(blockedByList));
            } else {
                graph.put(task.getTaskId(), new HashSet<>());
            }
        }

        return graph;
    }

    /**
     * DFS 检测环
     */
    private boolean hasCycle(Map<String, Set<String>> graph,
                             String node,
                             Set<String> visited,
                             Set<String> recursionStack,
                             String targetId) {
        if (recursionStack.contains(node)) {
            return true;
        }

        if (visited.contains(node)) {
            return false;
        }

        visited.add(node);
        recursionStack.add(node);

        Set<String> neighbors = graph.getOrDefault(node, new HashSet<>());
        for (String neighbor : neighbors) {
            // 如果能到达目标任务，说明会形成循环
            if (neighbor.equals(targetId)) {
                return true;
            }
            if (hasCycle(graph, neighbor, visited, recursionStack, targetId)) {
                return true;
            }
        }

        recursionStack.remove(node);
        return false;
    }

    /**
     * 解析 JSON 数组字符串为 List
     * 格式：["task-id-1", "task-id-2"]
     */
    public List<String> parseJsonArray(String jsonArray) {
        if (jsonArray == null || jsonArray.isEmpty() || jsonArray.equals("[]")) {
            return new ArrayList<>();
        }

        try {
            // 简单解析：去掉 [ ] 和引号，按逗号分割
            String content = jsonArray.trim();
            if (content.startsWith("[") && content.endsWith("]")) {
                content = content.substring(1, content.length() - 1);
            }

            if (content.isEmpty()) {
                return new ArrayList<>();
            }

            return Arrays.stream(content.split(","))
                    .map(s -> s.trim().replace("\"", ""))
                    .filter(s -> !s.isEmpty())
                    .collect(Collectors.toList());
        } catch (Exception e) {
            logger.warn("解析 JSON 数组失败: {}", jsonArray);
            return new ArrayList<>();
        }
    }

    /**
     * 将 List 转换为 JSON 数组字符串
     */
    public String toJsonArray(List<String> list) {
        if (list == null || list.isEmpty()) {
            return "[]";
        }

        return "[" + list.stream()
                .map(s -> "\"" + s + "\"")
                .collect(Collectors.joining(", ")) + "]";
    }
}