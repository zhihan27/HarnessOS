package com.harness.core.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.harness.core.entity.ChatMessage;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 聊天消息 Mapper 接口
 */
@Mapper
public interface ChatMessageMapper extends BaseMapper<ChatMessage> {

    /**
     * 查询会话的最大消息序号
     */
    @Select("SELECT MAX(message_order) FROM chat_messages WHERE session_id = #{sessionId}")
    Integer getMaxMessageOrder(@Param("sessionId") String sessionId);

    /**
     * 查询会话的消息列表（按序号排序）
     */
    @Select("SELECT * FROM chat_messages WHERE session_id = #{sessionId} ORDER BY message_order ASC")
    List<ChatMessage> findBySessionIdOrderByOrder(@Param("sessionId") String sessionId);

    /**
     * 统计会话的总Token数
     */
    @Select("SELECT COALESCE(SUM(token_count), 0) FROM chat_messages WHERE session_id = #{sessionId}")
    Long sumTokensBySessionId(@Param("sessionId") String sessionId);

    /**
     * 分页查询会话消息
     * @param sessionId 会话ID
     * @param limit 返回条数
     * @param offset 偏移量
     * @param order 排序方式（asc/desc）
     */
    @Select("""
        SELECT * FROM chat_messages
        WHERE session_id = #{sessionId}
        ORDER BY message_order ${order}
        LIMIT #{limit} OFFSET #{offset}
        """)
    List<ChatMessage> findBySessionIdWithPagination(
            @Param("sessionId") String sessionId,
            @Param("limit") Integer limit,
            @Param("offset") Integer offset,
            @Param("order") String order
    );
}