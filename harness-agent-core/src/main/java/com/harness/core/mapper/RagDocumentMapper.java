package com.harness.core.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.harness.core.entity.RagDocument;
import org.apache.ibatis.annotations.Mapper;

/**
 * RAG 文档数据访问接口。
 */
@Mapper
public interface RagDocumentMapper extends BaseMapper<RagDocument> {
}
