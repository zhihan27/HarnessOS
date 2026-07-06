package com.harness.core.hook;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 任务拆解 Hook
 * 不做拆解判断，让AI自己决定是否需要调用工具
 */
@Component
@Order(2)
public class DecompositionHook implements ChatHook {

    private static final Logger logger = LoggerFactory.getLogger(DecompositionHook.class);

    @Override
    public String getName() {
        return "DecompositionHook";
    }

    @Override
    public boolean execute(ChatContext context) {
        // 不判断复杂度，直接跳过
        // AI会根据问题内容自己决定是否需要调用Todo/SubAgent等工具
        logger.info("跳过任务拆解判断，让AI自主决定");
        context.setSubTasks(null);
        context.setNeedLoop(false);
        return true;
    }
}