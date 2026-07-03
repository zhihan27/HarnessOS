package com.harness.core.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 安全拦截服务
 * 统一的安全检查入口
 */
@Service
public class SecurityInterceptor {

    private static final Logger logger = LoggerFactory.getLogger(SecurityInterceptor.class);

    private final SecurityRules securityRules;

    public SecurityInterceptor(SecurityRules securityRules) {
        this.securityRules = securityRules;
    }

    /**
     * 检查输入内容是否安全
     *
     * @param input    用户输入内容
     * @param toolName 工具名称（可选）
     * @return 安全检查结果
     */
    public SecurityCheckResult check(String input, String toolName) {
        if (input == null || input.trim().isEmpty()) {
            return SecurityCheckResult.safe();
        }

        // 1. 高危检查（硬拦截）
        if (securityRules.isHighRisk(input)) {
            logger.warn("[高危-硬拦截] 命令: {}, 工具: {}", input, toolName);
            return SecurityCheckResult.blocked(
                "该操作属于高危操作，系统拒绝执行。即使确认也无法执行。"
            );
        }

        // 2. 工具特定规则检查
        if (toolName != null) {
            for (String pattern : securityRules.getToolSpecificRules(toolName)) {
                if (input.toLowerCase().contains(pattern.toLowerCase())) {
                    logger.info("[工具规则-软询问] 模式: '{}', 工具: {}", pattern, toolName);
                    return SecurityCheckResult.needConfirm(
                        String.format("该操作涉及敏感内容 '%s'，请确认是否继续执行。", pattern),
                        input
                    );
                }
            }
        }

        // 3. 中危检查（软询问）
        if (securityRules.isMediumRisk(input)) {
            logger.info("[中危-软询问] 命令: {}, 工具: {}", input, toolName);
            return SecurityCheckResult.needConfirm(
                "该操作属于敏感操作，建议确认后再执行。",
                input
            );
        }

        // 4. 安全通过
        logger.debug("[安全通过] 工具: {}, 输入长度: {}", toolName, input.length());
        return SecurityCheckResult.safe();
    }

    /**
     * 快速检查（不指定工具名）
     */
    public SecurityCheckResult check(String input) {
        return check(input, null);
    }

    /**
     * 判断是否是高危命令（供外部调用）
     */
    public boolean isHighRisk(String command) {
        return securityRules.isHighRisk(command);
    }
}