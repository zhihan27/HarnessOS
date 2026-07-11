package com.harness.core.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.harness.core.entity.AgentTaskAssignment;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * Agent-任务关联 Mapper
 */
@Mapper
public interface AgentTaskAssignmentMapper extends BaseMapper<AgentTaskAssignment> {

    /**
     * 查询 Agent 的活跃分配
     */
    @Select("SELECT * FROM agent_task_assignments WHERE agent_id = #{agentId} " +
            "AND status IN ('ASSIGNED', 'EXECUTING') ORDER BY assigned_at DESC")
    List<AgentTaskAssignment> findActiveAssignments(@Param("agentId") String agentId);

    /**
     * 查询任务的分配记录
     */
    @Select("SELECT * FROM agent_task_assignments WHERE task_id = #{taskId}")
    List<AgentTaskAssignment> findByTaskId(@Param("taskId") String taskId);

    /**
     * 更新分配状态为执行中
     */
    @Update("UPDATE agent_task_assignments SET status = 'EXECUTING' " +
            "WHERE agent_id = #{agentId} AND task_id = #{taskId}")
    int markExecuting(@Param("agentId") String agentId, @Param("taskId") String taskId);

    /**
     * 释放分配（完成任务）
     */
    @Update("UPDATE agent_task_assignments SET status = 'RELEASED', released_at = NOW() " +
            "WHERE agent_id = #{agentId} AND task_id = #{taskId}")
    int releaseAssignment(@Param("agentId") String agentId, @Param("taskId") String taskId);

    /**
     * 查询 Agent 的历史任务数
     */
    @Select("SELECT COUNT(*) FROM agent_task_assignments WHERE agent_id = #{agentId}")
    int countByAgent(@Param("agentId") String agentId);
}