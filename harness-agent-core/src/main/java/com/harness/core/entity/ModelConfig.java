package com.harness.core.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 页面维护的聊天模型或向量模型连接配置。
 */
@Data
@TableName("model_configs")
public class ModelConfig {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private String provider;
    /** 模型类型：chat 聊天模型，embedding 向量模型。 */
    private String modelType;
    private String baseUrl;
    private String modelName;
    private String apiTokenCiphertext;
    private Boolean active;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
