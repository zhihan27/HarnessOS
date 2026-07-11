package com.harness.core.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.harness.core.entity.AgentInstance;
import com.harness.core.entity.DagTask;
import com.harness.core.mapper.AgentInstanceMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * MainAgent 服务
 *
 * 负责：
 * 1. 复杂任务拆解
 * 2. 创建子任务并入库
 * 3. 设置任务依赖关系
 */
@Service
public class MainAgentService {

    private static final Logger logger = LoggerFactory.getLogger(MainAgentService.class);

    private final AgentRegistryService registryService;
    private final DagTaskService taskService;
    private final AiServiceFactory aiServiceFactory;
    private final AgentInstanceMapper agentMapper;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public MainAgentService(AgentRegistryService registryService,
                            DagTaskService taskService,
                            AiServiceFactory aiServiceFactory,
                            AgentInstanceMapper agentMapper) {
        this.registryService = registryService;
        this.taskService = taskService;
        this.aiServiceFactory = aiServiceFactory;
        this.agentMapper = agentMapper;
    }

    /**
     * 拆解复杂任务
     *
     * @param mainAgentId       Main Agent ID
     * @param taskDescription   任务描述
     * @param sessionId         会话 ID
     * @return 拆解结果（创建的子任务列表）
     */
    @Transactional
    public DecompositionResult decomposeComplexTask(String mainAgentId,
                                                     String taskDescription,
                                                     String sessionId) {
        AgentInstance agent = registryService.getAgent(mainAgentId);

        if (agent == null) {
            throw new IllegalArgumentException("Agent 不存在: " + mainAgentId);
        }

        if (!"MAIN".equals(agent.getAgentType())) {
            throw new IllegalStateException("只有 MainAgent 可以拆解任务");
        }

        // 1. 更新 Agent 状态为 WORKING
        agent.setStatus("WORKING");
        agent.setLastActiveAt(java.time.LocalDateTime.now());
        agentMapper.updateById(agent);

        logger.info("MainAgent 开始拆解任务: agentId={}, description={}", mainAgentId, taskDescription);

        try {
            // 2. 调用 AI 进行任务拆解
            String decompositionPrompt = buildDecompositionPrompt(taskDescription);
            AiChatService aiService = aiServiceFactory.getService("openai", sessionId);
            String aiResponse = aiService.chat(sessionId, decompositionPrompt);

            // 3. 解析 AI 返回的任务规格
            List<TaskCreationSpec> taskSpecs = parseTaskSpecs(aiResponse);

            // 4. 创建子任务
            List<DagTask> createdTasks = new ArrayList<>();
            String tenantId = agent.getTenantId();
            String userId = agent.getUserId();

            // 先创建所有任务（不带依赖）
            for (TaskCreationSpec spec : taskSpecs) {
                DagTask task = taskService.createTask(
                        spec.subject, spec.description,
                        tenantId, userId, sessionId
                );
                createdTasks.add(task);

                // 记录 Agent 创建的任务
                registryService.recordTaskCreated(mainAgentId, task.getTaskId(), sessionId);
            }

            // 5. 设置依赖关系
            for (int i = 0; i < taskSpecs.size(); i++) {
                TaskCreationSpec spec = taskSpecs.get(i);
                DagTask task = createdTasks.get(i);

                if (spec.blockedBy != null && !spec.blockedBy.isEmpty()) {
                    // 将依赖名称转换为任务 ID
                    List<String> blockedByTaskIds = resolveDependencies(spec.blockedBy, createdTasks, taskSpecs);
                    if (!blockedByTaskIds.isEmpty()) {
                        taskService.setBlockedBy(task.getTaskId(), blockedByTaskIds);
                    }
                }
            }

            // 6. 更新 Agent 状态为 IDLE
            agent.setStatus("IDLE");
            agent.setLastActiveAt(java.time.LocalDateTime.now());
            agentMapper.updateById(agent);

            logger.info("MainAgent 拆解完成: agentId={}, createdTasks={}", mainAgentId, createdTasks.size());

            return new DecompositionResult(createdTasks, aiResponse, null);

        } catch (Exception e) {
            // 出错时恢复 Agent 状态
            agent.setStatus("ERROR");
            agentMapper.updateById(agent);

            logger.error("MainAgent 拆解失败: agentId={}, error={}", mainAgentId, e.getMessage(), e);
            return new DecompositionResult(null, null, e.getMessage());
        }
    }

    /**
     * 构造拆解提示
     */
    private String buildDecompositionPrompt(String taskDescription) {
        return """
            请将以下复杂任务拆解为多个可独立执行的原子任务。

            复杂任务：%s

            拆解要求：
            1. 每个子任务应该是可以独立完成的最小单元
            2. 明确任务之间的依赖关系（哪些任务必须先完成）
            3. 按执行顺序排列任务
            4. 使用以下 JSON 格式返回：
               {
                 "tasks": [
                   {
                     "name": "task-name",
                     "subject": "简要标题",
                     "description": "详细描述",
                     "blockedBy": ["前置任务名称"]
                   }
                 ]
               }

            注意：
            - 第一个任务不应有依赖
            - blockedBy 中的任务名称应与前面任务的 name 对应
            - 请直接返回 JSON，不要添加其他内容
            """.formatted(taskDescription);
    }

    /**
     * 解析 AI 返回的任务规格
     */
    private List<TaskCreationSpec> parseTaskSpecs(String aiResponse) {
        try {
            // 尝试提取 JSON 部分
            String jsonContent = extractJson(aiResponse);

            Map<String, Object> responseMap = objectMapper.readValue(jsonContent, Map.class);
            List<Map<String, Object>> tasksList = (List<Map<String, Object>>) responseMap.get("tasks");

            List<TaskCreationSpec> specs = new ArrayList<>();
            for (Map<String, Object> taskMap : tasksList) {
                String name = (String) taskMap.get("name");
                String subject = (String) taskMap.get("subject");
                String description = (String) taskMap.get("description");
                List<String> blockedBy = (List<String>) taskMap.get("blockedBy");

                specs.add(new TaskCreationSpec(name, subject, description, blockedBy != null ? blockedBy : new ArrayList<>()));
            }

            return specs;

        } catch (Exception e) {
            logger.error("解析任务规格失败: {}", e.getMessage());
            // 返回默认的简单拆解
            return List.of(new TaskCreationSpec(
                    "default-task",
                    "执行任务",
                    aiResponse,
                    new ArrayList<>()
            ));
        }
    }

    /**
     * 从 AI 回复中提取 JSON
     */
    private String extractJson(String response) {
        int start = response.indexOf("{");
        int end = response.lastIndexOf("}");
        if (start >= 0 && end > start) {
            return response.substring(start, end + 1);
        }
        return response;
    }

    /**
     * 解析依赖关系（将任务名称转换为任务 ID）
     */
    private List<String> resolveDependencies(List<String> blockedByNames,
                                             List<DagTask> createdTasks,
                                             List<TaskCreationSpec> specs) {
        List<String> taskIds = new ArrayList<>();

        for (String blockedByName : blockedByNames) {
            for (int i = 0; i < specs.size(); i++) {
                if (specs.get(i).name.equals(blockedByName)) {
                    taskIds.add(createdTasks.get(i).getTaskId());
                    break;
                }
            }
        }

        return taskIds;
    }

    // ========== 内部数据结构 ==========

    /**
     * 任务创建规格
     */
    public record TaskCreationSpec(String name, String subject, String description, List<String> blockedBy) {}

    /**
     * 拆解结果
     */
    public record DecompositionResult(List<DagTask> createdTasks, String aiResponse, String error) {
        public boolean isSuccess() {
            return error == null && createdTasks != null;
        }
    }
}