package com.harness.core.tool;

import com.harness.core.executor.SecureBashExecutor;
import com.harness.core.service.ToolProgressBroadcaster;
import dev.langchain4j.agent.tool.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Bash 命令执行工具
 * 双层拦截机制：
 * - 高危：硬拦截，绝对拒绝
 * - 中危：软询问，用户确认后可执行
 *
 * 执行时会通过 SSE 推送工具进度
 */
@Component
public class BashToolProvider {

    private static final Logger logger = LoggerFactory.getLogger(BashToolProvider.class);

    private final SecureBashExecutor secureExecutor;
    private final ToolProgressBroadcaster progressBroadcaster;

    public BashToolProvider(SecureBashExecutor secureExecutor,
                            ToolProgressBroadcaster progressBroadcaster) {
        this.secureExecutor = secureExecutor;
        this.progressBroadcaster = progressBroadcaster;
    }

    /**
     * 执行 Bash 命令（带安全检查）
     *
     * @param command 命令内容
     * @return 执行结果（可能包含软询问提示）
     */
    @Tool("执行 Shell/Bash 命令。会进行安全检查，中危操作需要用户确认。")
    public String executeBash(String command) {
        String sessionId = getCurrentSessionId();
        String toolName = "executeBash";

        // 推送工具开始执行
        if (sessionId != null) {
            progressBroadcaster.broadcastToolStarted(sessionId, toolName, truncateCommand(command));
        }

        try {
            String result = secureExecutor.execute(command);

            // 推送工具执行完成
            if (sessionId != null) {
                progressBroadcaster.broadcastToolCompleted(sessionId, toolName, result);
            }

            return result;

        } catch (Exception e) {
            // 推送工具执行出错
            if (sessionId != null) {
                progressBroadcaster.broadcastToolError(sessionId, toolName, e.getMessage());
            }
            throw e;
        }
    }

    /**
     * 已确认后执行命令（跳过软询问）
     *
     * @param confirmToken 确认令牌
     * @param command      命令内容
     * @return 执行结果
     */
    @Tool("用户已确认后执行命令。需要提供正确的确认令牌。")
    public String executeBashConfirmed(String confirmToken, String command) {
        String sessionId = getCurrentSessionId();
        String toolName = "executeBashConfirmed";

        // 推送工具开始执行
        if (sessionId != null) {
            progressBroadcaster.broadcastToolStarted(sessionId, toolName, "已确认: " + truncateCommand(command));
        }

        try {
            String result = secureExecutor.executeConfirmed(confirmToken, command);

            // 推送工具执行完成
            if (sessionId != null) {
                progressBroadcaster.broadcastToolCompleted(sessionId, toolName, result);
            }

            return result;

        } catch (Exception e) {
            // 推送工具执行出错
            if (sessionId != null) {
                progressBroadcaster.broadcastToolError(sessionId, toolName, e.getMessage());
            }
            throw e;
        }
    }

    /**
     * 获取当前会话 ID（从 TodoWriteToolProvider 的上下文获取）
     */
    private String getCurrentSessionId() {
        return TodoWriteToolProvider.getCurrentSessionId();
    }

    /**
     * 截断命令用于显示
     */
    private String truncateCommand(String command) {
        if (command == null) return null;
        return command.length() > 100 ? command.substring(0, 100) + "..." : command;
    }
}