package com.harness.core.context;

/**
 * Agent 上下文（ThreadLocal）
 *
 * 用于跟踪当前线程执行的 Agent 身份
 * 在 Worker 执行任务时设置，供 SubAgent 等组件使用
 */
public class AgentContext {

    private static final ThreadLocal<String> currentAgentId = new ThreadLocal<>();
    private static final ThreadLocal<String> currentSessionId = new ThreadLocal<>();
    private static final ThreadLocal<String> currentTaskId = new ThreadLocal<>();
    private static final ThreadLocal<String> currentAgentType = new ThreadLocal<>();

    /**
     * 设置当前 Agent ID
     */
    public static void setCurrentAgent(String agentId) {
        currentAgentId.set(agentId);
    }

    /**
     * 设置当前会话 ID
     */
    public static void setCurrentSession(String sessionId) {
        currentSessionId.set(sessionId);
    }

    /**
     * 设置当前任务 ID
     */
    public static void setCurrentTask(String taskId) {
        currentTaskId.set(taskId);
    }

    /**
     * 设置当前 Agent 类型（MAIN/WORKER）
     */
    public static void setCurrentAgentType(String agentType) {
        currentAgentType.set(agentType);
    }

    /**
     * 获取当前 Agent ID
     */
    public static String getCurrentAgent() {
        return currentAgentId.get();
    }

    /**
     * 获取当前会话 ID
     */
    public static String getCurrentSession() {
        return currentSessionId.get();
    }

    /**
     * 获取当前任务 ID
     */
    public static String getCurrentTask() {
        return currentTaskId.get();
    }

    /**
     * 获取当前 Agent 类型
     */
    public static String getCurrentAgentType() {
        return currentAgentType.get();
    }

    /**
     * 判断是否在 Agent 执行上下文中
     */
    public static boolean isInAgentContext() {
        return currentAgentId.get() != null;
    }

    /**
     * 判断当前是否是 Worker Agent
     */
    public static boolean isWorkerAgent() {
        String agentType = currentAgentType.get();
        return "WORKER".equals(agentType);
    }

    /**
     * 判断当前是否是 Main Agent
     */
    public static boolean isMainAgent() {
        String agentType = currentAgentType.get();
        return "MAIN".equals(agentType);
    }

    /**
     * 清除当前 Agent 上下文
     */
    public static void clearCurrentAgent() {
        currentAgentId.remove();
    }

    /**
     * 清除当前会话上下文
     */
    public static void clearCurrentSession() {
        currentSessionId.remove();
    }

    /**
     * 清除当前任务上下文
     */
    public static void clearCurrentTask() {
        currentTaskId.remove();
    }

    /**
     * 清除当前 Agent 类型
     */
    public static void clearCurrentAgentType() {
        currentAgentType.remove();
    }

    /**
     * 清除所有上下文
     */
    public static void clearAll() {
        clearCurrentAgent();
        clearCurrentSession();
        clearCurrentTask();
        clearCurrentAgentType();
    }

    /**
     * 设置完整上下文
     */
    public static void setContext(String agentId, String sessionId, String taskId) {
        setCurrentAgent(agentId);
        setCurrentSession(sessionId);
        setCurrentTask(taskId);
    }

    /**
     * 设置完整上下文（包含Agent类型）
     */
    public static void setContext(String agentId, String agentType, String sessionId, String taskId) {
        setCurrentAgent(agentId);
        setCurrentAgentType(agentType);
        setCurrentSession(sessionId);
        setCurrentTask(taskId);
    }

    /**
     * 获取上下文摘要（用于日志）
     */
    public static String getContextSummary() {
        return String.format("Agent[%s] Type[%s] Session[%s] Task[%s]",
                getCurrentAgent() != null ? getCurrentAgent() : "none",
                getCurrentAgentType() != null ? getCurrentAgentType() : "none",
                getCurrentSession() != null ? getCurrentSession() : "none",
                getCurrentTask() != null ? getCurrentTask() : "none");
    }
}