package com.harness.core.security;

/**
 * 安全检查结果
 */
public record SecurityCheckResult(
    boolean allowed,
    String reason,
    SecurityLevel level,
    String confirmToken    // 确认令牌，用于已确认的执行
) {

    public enum SecurityLevel {
        SAFE,           // 安全，允许执行
        NEED_CONFIRM,   // 需要用户确认（中危）
        BLOCKED         // 禁止执行（高危）
    }

    public static SecurityCheckResult safe() {
        return new SecurityCheckResult(true, null, SecurityLevel.SAFE, null);
    }

    public static SecurityCheckResult needConfirm(String reason, String command) {
        // 生成确认令牌
        String token = "CONFIRM:" + command.hashCode();
        return new SecurityCheckResult(false, reason, SecurityLevel.NEED_CONFIRM, token);
    }

    public static SecurityCheckResult blocked(String reason) {
        return new SecurityCheckResult(false, reason, SecurityLevel.BLOCKED, null);
    }
}