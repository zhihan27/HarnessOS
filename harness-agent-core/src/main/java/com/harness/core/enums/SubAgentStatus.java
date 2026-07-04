package com.harness.core.enums;

/**
 * 子任务状态枚举
 *
 * 注意：重试是自动进行的，不需要 RETRYING 中间状态
 * AI 调用 executeSubAgent 时，内部自动重试直到成功或失败
 */
public enum SubAgentStatus {

    PENDING("待执行"),
    RUNNING("执行中（含自动重试）"),
    COMPLETED("已完成"),
    FAILED("终态失败（已耗尽重试次数）");

    private final String description;

    SubAgentStatus(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }

    public String getValue() {
        return name();
    }

    public static SubAgentStatus fromValue(String value) {
        for (SubAgentStatus status : SubAgentStatus.values()) {
            if (status.name().equals(value)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown subAgent status: " + value);
    }
}