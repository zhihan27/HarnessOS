package com.harness.core.hook;

import com.harness.core.entity.SubAgentTask;
import lombok.Data;

import java.util.List;

/**
 * Chat Hook 上下文
 * 在Hook链中传递的状态
 */
@Data
public class ChatContext {

    private String tenantId;
    private String userId;
    private String sessionId;
    private String message;
    private String result;
    private boolean success;
    private List<SubAgentTask> subTasks;

    public ChatContext(String tenantId, String userId, String sessionId, String message) {
        this.tenantId = tenantId;
        this.userId = userId;
        this.sessionId = sessionId;
        this.message = message;
        this.success = true;
    }

}