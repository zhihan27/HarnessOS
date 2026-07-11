package com.harness.core.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.harness.core.entity.AgentInstance;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * Agent 实例 Mapper
 */
@Mapper
public interface AgentInstanceMapper extends BaseMapper<AgentInstance> {

    /**
     * 查询活跃的 Agent（非 STOPPED 状态）
     */
    @Select("SELECT * FROM agent_instances WHERE status != 'STOPPED' ORDER BY started_at ASC")
    List<AgentInstance> findActiveAgents();

    /**
     * 按类型查询 Agent
     */
    @Select("SELECT * FROM agent_instances WHERE agent_type = #{agentType} AND status != 'STOPPED'")
    List<AgentInstance> findByType(@Param("agentType") String agentType);

    /**
     * 按状态查询 Agent
     */
    @Select("SELECT * FROM agent_instances WHERE status = #{status}")
    List<AgentInstance> findByStatus(@Param("status") String status);

    /**
     * 更新 Agent 状态
     */
    @Update("UPDATE agent_instances SET status = #{status}, current_task_id = #{currentTaskId}, " +
            "current_load = #{currentLoad}, last_active_at = NOW() WHERE agent_id = #{agentId}")
    int updateStatus(@Param("agentId") String agentId,
                     @Param("status") String status,
                     @Param("currentTaskId") String currentTaskId,
                     @Param("currentLoad") int currentLoad);

    /**
     * 查询空闲的 Worker Agent
     */
    @Select("SELECT * FROM agent_instances WHERE agent_type = 'WORKER' AND status = 'IDLE' " +
            "AND current_load < max_concurrency ORDER BY last_active_at ASC LIMIT 1")
    AgentInstance findIdleWorker();
}