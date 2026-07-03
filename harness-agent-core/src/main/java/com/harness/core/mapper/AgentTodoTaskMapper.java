package com.harness.core.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.harness.core.entity.AgentTodoTask;
import org.apache.ibatis.annotations.Mapper;

/**
 * Agent 任务 Mapper 接口（秘书模式）
 */
@Mapper
public interface AgentTodoTaskMapper extends BaseMapper<AgentTodoTask> {
}