package com.harness.core.hook;

import com.harness.core.security.SecurityCheckResult;
import com.harness.core.security.SecurityInterceptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 安全检查 Hook
 */
@Component
@Order(1)
public class SecurityHook implements ChatHook {

    private static final Logger logger = LoggerFactory.getLogger(SecurityHook.class);

    private final SecurityInterceptor securityInterceptor;

    public SecurityHook(SecurityInterceptor securityInterceptor) {
        this.securityInterceptor = securityInterceptor;
    }

    @Override
    public String getName() {
        return "SecurityHook";
    }

    @Override
    public boolean execute(ChatContext context) {
        logger.info("安全检查: sessionId={}", context.getSessionId());

        var result = securityInterceptor.check(context.getMessage());

        if (!result.allowed() && result.level() == SecurityCheckResult.SecurityLevel.BLOCKED) {
            logger.warn("安全拦截: {}", result.reason());
            context.setSuccess(false);
            context.setResult("安全拦截: " + result.reason());
            return false;
        }

        return true;
    }
}