package com.harness.core.hook;

/**
 * Chat Hook 接口
 * 每个Hook处理一个阶段
 */
public interface ChatHook {

    /**
     * Hook名称
     */
    String getName();

    /**
     * 执行Hook逻辑
     *
     * @param context 上下文
     * @return 是否继续执行下一个Hook
     */
    boolean execute(ChatContext context);
}