package com.harness.core.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.harness.core.entity.ChatMemorySummary;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 记忆摘要 Mapper 接口
 */
@Mapper
public interface ChatMemorySummaryMapper extends BaseMapper<ChatMemorySummary> {

    /**
     * 查询会话的摘要列表（按创建时间降序）
     */
    @Select("SELECT * FROM chat_memory_summaries WHERE session_id = #{sessionId} ORDER BY created_at DESC")
    List<ChatMemorySummary> findBySessionIdOrderByCreatedDesc(@Param("sessionId") String sessionId);

    /**
     * 查询会话的最新摘要
     */
    @Select("SELECT * FROM chat_memory_summaries WHERE session_id = #{sessionId} ORDER BY created_at DESC LIMIT 1")
    ChatMemorySummary findLatestBySessionId(@Param("sessionId") String sessionId);
}