package com.harness.core.enums;

/**
 * 任务类型枚举（简化版）
 * 仅保留通用类型，所有任务统一拆解
 */
public enum TaskType {

    GENERAL("通用任务");

    private final String description;

    TaskType(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }

    public String getValue() {
        return name();
    }

    public static TaskType fromValue(String value) {
        return GENERAL;
    }
}