package com.harness.core.enums;

/**
 * 会话状态枚举
 */
public enum SessionStatus {

    ACTIVE("ACTIVE", "活跃"),
    ARCHIVED("ARCHIVED", "已归档"),
    DELETED("DELETED", "已删除");

    private final String value;
    private final String description;

    SessionStatus(String value, String description) {
        this.value = value;
        this.description = description;
    }

    public String getValue() {
        return value;
    }

    public String getDescription() {
        return description;
    }

    public static SessionStatus fromValue(String value) {
        for (SessionStatus status : values()) {
            if (status.value.equals(value)) {
                return status;
            }
        }
        return ACTIVE;
    }
}