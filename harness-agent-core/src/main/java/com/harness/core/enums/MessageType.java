package com.harness.core.enums;

/**
 * 消息类型枚举
 */
public enum MessageType {

    SYSTEM("SYSTEM", "系统消息"),
    USER("USER", "用户消息"),
    AI("AI", "AI回复"),
    TOOL("TOOL", "工具调用");

    private final String value;
    private final String description;

    MessageType(String value, String description) {
        this.value = value;
        this.description = description;
    }

    public String getValue() {
        return value;
    }

    public String getDescription() {
        return description;
    }

    public static MessageType fromValue(String value) {
        for (MessageType type : values()) {
            if (type.value.equals(value)) {
                return type;
            }
        }
        return USER;
    }
}