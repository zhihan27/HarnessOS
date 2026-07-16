package com.harness.core.executor;

/**
 * Bash 命令执行器接口
 * 定义命令执行的标准行为
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