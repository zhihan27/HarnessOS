package com.harness.core.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.harness.core.entity.ChatSession;
import org.apache.ibatis.annotations.Mapper;

/**
 * 会话 Mapper 接口
 */
@Mapper
public interface ChatSessionMapper extends BaseMapper<ChatSession> {
}