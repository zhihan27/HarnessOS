package com.harness.core.enums;

/**
 * 子任务类型枚举
 */
public enum TaskType {

    RESEARCH("研究调查"),
    CODING("编码开发"),
    ANALYSIS("数据分析"),
    TESTING("测试验证"),
    DOCUMENTATION("文档编写"),
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
        for (TaskType type : TaskType.values()) {
            if (type.name().equals(value)) {
                return type;
            }
        }
        return GENERAL;
    }
}