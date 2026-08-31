package com.harness.core.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.harness.core.entity.RagKnowledgeBase;
import org.apache.ibatis.annotations.Mapper;

/**
 * RAG 知识库数据访问接口。
 */
@Mapper
public interface RagKnowledgeBaseMapper extends BaseMapper<RagKnowledgeBase> {
}
