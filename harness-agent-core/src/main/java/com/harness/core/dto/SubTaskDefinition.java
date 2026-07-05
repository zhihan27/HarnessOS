package com.harness.core.dto;

import com.harness.core.enums.TaskType;
import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 子任务定义
 */
@Data
@Builder
public class SubTaskDefinition {

    private int order;
    private TaskType taskType;
    private String description;
    private String inputTemplate;
    @Builder.Default
    private boolean required = true;
    private Integer maxRetries;
    private List<Integer> dependsOn;
}