package com.harness.core.service;

import com.harness.core.entity.AgentInstance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Agent 自动启动器
 *
 * 应用启动完成后自动注册默认的 Agent（如果不存在）：
 * - 1 个 MainAgent（负责任务拆解）
 * - N 个 WorkerAgent（负责任务执行，可配置数量）
 */
@Component
public class AgentAutoStarter {

    private static final Logger logger = LoggerFactory.getLogger(AgentAutoStarter.class);

    private final AgentRegistryService registryService;
    private final WorkerAgentService workerAgentService;

    // Worker Agent 数量（可通过配置文件设置）
    @Value("${agent.worker.count:3}")
    private int workerCount;

    // 是否启用自动启动
    @Value("${agent.auto-start:true}")
    private boolean autoStart;

    // 默认租户和用户
    @Value("${agent.default.tenant:default-tenant}")
    private String defaultTenant;

    @Value("${agent.default.user:default-user}")
    private String defaultUser;

    public AgentAutoStarter(@Lazy AgentRegistryService registryService,
                            @Lazy WorkerAgentService workerAgentService) {
        this.registryService = registryService;
        this.workerAgentService = workerAgentService;
    }

    /**
     * 应用启动完成后自动注册 Agent（如果不存在）
     */
    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        if (!autoStart) {
            logger.info("Agent 自动启动已禁用");
            return;
        }

        // 从数据库加载已存在的活跃 Agent
        List<AgentInstance> existingAgents = registryService.loadActiveAgentsFromDb();
        if (!existingAgents.isEmpty()) {
            logger.info("已存在 {} 个活跃 Agent，跳过自动注册", existingAgents.size());

            // 启动已存在的 Worker Agent 的执行循环
            for (AgentInstance agent : existingAgents) {
                if ("WORKER".equals(agent.getAgentType()) &&
                    !"STOPPED".equals(agent.getStatus()) &&
                    !workerAgentService.isWorkerRunning(agent.getAgentId())) {
                    workerAgentService.startWorkerLoop(agent.getAgentId());
                    logger.info("重启已存在的 WorkerAgent: {} [{}]", agent.getAgentId(), agent.getAgentName());
                }
            }
            return;
        }

        logger.info("开始自动注册 Agent，Worker 数量: {}", workerCount);

        // 1. 注册 MainAgent
        AgentInstance mainAgent = registryService.registerAgent(
                "MAIN",
                "MainAgent-01",
                defaultTenant,
                defaultUser,
                1,  // MainAgent 只需要 1 个并发
                List.of("DECOMPOSITION", "PLANNING")
        );
        logger.info("自动注册 MainAgent: {}", mainAgent.getAgentId());

        // 2. 注册 WorkerAgent
        String[] workerNames = {"Worker-Alpha", "Worker-Beta", "Worker-Gamma", "Worker-Delta", "Worker-Epsilon"};
        String[][] workerCapabilities = {
                {"CODING", "RESEARCH"},
                {"CODING", "TESTING"},
                {"ANALYSIS", "DOCUMENTATION"},
                {"CODING", "REFACTORING"},
                {"GENERAL"}
        };

        for (int i = 0; i < Math.min(workerCount, workerNames.length); i++) {
            AgentInstance worker = registryService.registerAgent(
                    "WORKER",
                    workerNames[i],
                    defaultTenant,
                    defaultUser,
                    2,  // 每个 Worker 最多 2 个并发
                    List.of(workerCapabilities[i])
            );

            // Worker 注册后自动启动执行循环
            workerAgentService.startWorkerLoop(worker.getAgentId());

            logger.info("自动注册 WorkerAgent: {} [{}]", worker.getAgentId(), workerNames[i]);
        }

        logger.info("Agent 自动启动完成: 1 MainAgent + {} WorkerAgent", workerCount);
    }

    /**
     * 手动触发注册（用于动态扩展）
     */
    public AgentInstance registerAdditionalWorker(String name, int maxConcurrency, List<String> capabilities) {
        AgentInstance worker = registryService.registerAgent(
                "WORKER",
                name,
                defaultTenant,
                defaultUser,
                maxConcurrency,
                capabilities
        );

        workerAgentService.startWorkerLoop(worker.getAgentId());

        logger.info("手动注册 WorkerAgent: {} [{}]", worker.getAgentId(), name);

        return worker;
    }
}