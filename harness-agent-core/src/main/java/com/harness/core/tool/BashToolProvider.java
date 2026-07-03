package com.harness.core.tool;

import dev.langchain4j.agent.tool.Tool;
import org.springframework.stereotype.Component;

/**
 * Bash 命令执行工具
 * 双层拦截机制：
 * - 高危：硬拦截，绝对拒绝
 * - 中危：软询问，用户确认后可执行
 */
@Component
public class BashToolProvider {

    private final SecureBashExecutor secureExecutor;

    public BashToolProvider(SecureBashExecutor secureExecutor) {
        this.secureExecutor = secureExecutor;
    }

    /**
     * 执行 Bash 命令（带安全检查）
     *
     * @param command 命令内容
     * @return 执行结果（可能包含软询问提示）
     */
    @Tool("执行 Shell/Bash 命令。会进行安全检查，中危操作需要用户确认。")
    public String executeBash(String command) {
        return secureExecutor.execute(command);
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
        return secureExecutor.executeConfirmed(confirmToken, command);
    }
}