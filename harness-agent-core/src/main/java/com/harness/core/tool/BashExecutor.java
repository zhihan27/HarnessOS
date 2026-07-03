package com.harness.core.tool;

/**
 * Bash 命令真实执行器
 * 只负责执行命令，不做任何安全检查
 */
public interface BashExecutor {

    /**
     * 执行 Bash 命令
     *
     * @param command 命令内容
     * @return 执行结果
     */
    String execute(String command);
}