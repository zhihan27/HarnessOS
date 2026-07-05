package com.harness.core.dto;

import lombok.Builder;
import lombok.Data;

/**
 * 任务拆解上下文
 */
@Data
@Builder
public class TaskDecompositionContext {

    private String mainTaskDescription;
    private String tenantId;
    private String userId;
    private String sessionId;
}