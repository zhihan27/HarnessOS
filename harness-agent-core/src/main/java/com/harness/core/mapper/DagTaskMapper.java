package com.harness.core.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.harness.core.entity.DagTask;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * DAG 任务 Mapper 接口（任务看板系统）
 */
@Mapper
public interface DagTaskMapper extends BaseMapper<DagTask> {

    /**
     * 查询指定状态的任务
     */
    @Select("SELECT * FROM dag_tasks WHERE status = #{status} ORDER BY created_at ASC")
    List<DagTask> findByStatus(String status);

    /**
     * 查询指定责任人的任务
     */
    @Select("SELECT * FROM dag_tasks WHERE owner = #{owner} ORDER BY created_at ASC")
    List<DagTask> findByOwner(String owner);

    /**
     * 查询未完成的任务（pending + in_progress）
     */
    @Select("SELECT * FROM dag_tasks WHERE status IN ('pending', 'in_progress') ORDER BY created_at ASC")
    List<DagTask> findActiveTasks();

    /**
     * 查询指定会话创建的任务
     */
    @Select("SELECT * FROM dag_tasks WHERE session_id = #{sessionId} ORDER BY created_at ASC")
    List<DagTask> findBySessionId(String sessionId);

    /**
     * 原子领取任务（乐观锁）
     * 只有当任务状态为 pending 且 version 匹配时才能领取成功
     *
     * @param taskId        任务 ID
     * @param agentId       要分配的 Agent ID
     * @param currentVersion 当前版本号（乐观锁）
     * @return 更新的行数，>0 表示成功
     */
    @Update("UPDATE dag_tasks SET status = 'in_progress', assigned_agent_id = #{agentId}, " +
            "assignment_version = assignment_version + 1, started_at = NOW() " +
            "WHERE task_id = #{taskId} AND status = 'pending' AND assignment_version = #{currentVersion}")
    int updateTaskAssignment(@Param("taskId") String taskId,
                             @Param("agentId") String agentId,
                             @Param("currentVersion") int currentVersion);

    /**
     * 查询就绪且未被分配的任务（用于 Worker 领取）
     */
    @Select("SELECT * FROM dag_tasks WHERE status = 'pending' " +
            "AND blocked_by = '[]' AND assigned_agent_id IS NULL " +
            "ORDER BY created_at ASC LIMIT 10")
    List<DagTask> findReadyUnassignedTasks();

    /**
     * 查询指定 Agent 分配的任务
     */
    @Select("SELECT * FROM dag_tasks WHERE assigned_agent_id = #{agentId} ORDER BY started_at DESC")
    List<DagTask> findByAssignedAgent(@Param("agentId") String agentId);
}