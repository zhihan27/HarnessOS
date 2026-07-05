package com.harness.core.hook;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Hook执行器
 * 无感执行前置和后置Hook
 */
@Component
public class ChatHookExecutor {

    private static final Logger logger = LoggerFactory.getLogger(ChatHookExecutor.class);

    private final List<ChatHook> beforeHooks;  // 前置Hook
    private final List<ChatHook> afterHooks;   // 后置Hook

    public ChatHookExecutor(List<ChatHook> hooks) {
        // 按Order分组：1-2为前置，3-4为后置
        this.beforeHooks = hooks.stream()
                .filter(h -> getOrder(h) <= 2)
                .sorted((a, b) -> getOrder(a) - getOrder(b))
                .toList();

        this.afterHooks = hooks.stream()
                .filter(h -> getOrder(h) > 2)
                .sorted((a, b) -> getOrder(a) - getOrder(b))
                .toList();

        logger.info("HookExecutor初始化: 前置{}个, 后置{}个", beforeHooks.size(), afterHooks.size());
    }

    /**
     * 执行前置Hook（AI调用前）
     */
    public void executeBefore(ChatContext context) {
        for (ChatHook hook : beforeHooks) {
            logger.debug("前置Hook: {}", hook.getName());
            if (!hook.execute(context)) {
                break;
            }
        }
    }

    /**
     * 执行后置Hook（AI调用后）
     */
    public void executeAfter(ChatContext context) {
        for (ChatHook hook : afterHooks) {
            logger.debug("后置Hook: {}", hook.getName());
            hook.execute(context);
        }
    }

    private int getOrder(ChatHook hook) {
        // 通过反射获取@Order注解值
        try {
            org.springframework.core.annotation.Order order =
                hook.getClass().getAnnotation(org.springframework.core.annotation.Order.class);
            return order != null ? order.value() : 999;
        } catch (Exception e) {
            return 999;
        }
    }
}