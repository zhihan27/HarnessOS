package com.harness.core.enums;

/**
 * Agent 任务状态枚举（秘书模式）
 * 极简架构：只有待完成和已完成两种状态
 */
public enum TaskStatus {

    /**
     * 待完成状态
     */
    PENDING("待完成"),

    /**
     * 已完成状态
     */
    COMPLETED("已完成");

    private final String description;

    TaskStatus(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }

    public String getValue() {
        return name();
    }

    public static TaskStatus fromValue(String value) {
        for (TaskStatus status : TaskStatus.values()) {
            if (status.name().equals(value)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown task status: " + value);
    }
}