# Harness RAG 实现说明

本文档描述 Harness Agent Core 当前 RAG（Retrieval-Augmented Generation，检索增强生成）模块的实际实现，包括数据结构、文档处理、Embedding 调用、检索评分、HTTP 接口和前端操作流程。

## 1. 模块定位

当前 RAG 模块是一个独立的知识库检索模块，负责：

- 管理知识库及其检索参数；
- 接收 UTF-8 纯文本、Markdown、CSV、JSON、HTML 等文本文件；
- 按配置切分文档并计算 token 估算值；
- 调用 OpenAI 兼容的 Embedding API 生成语义向量；
- 将分段、向量和 PostgreSQL 全文检索字段写入数据库；
- 使用 pgvector 向量召回与 PostgreSQL 关键词召回进行混合排序；
- 在前端提供文档管理、分段查看、参数配置和命中测试。

RAG 命中测试不会调用聊天模型或 LLM，只返回召回的知识分段及各项得分。聊天链路是否使用这些结果，需要由上层业务自行编排。

## 2. 代码结构

| 文件 | 职责 |
| --- | --- |
| `src/main/java/com/harness/core/service/RagService.java` | 知识库、文档、分段和检索的核心业务逻辑 |
| `src/main/java/com/harness/core/service/RagEmbeddingService.java` | 调用 Embedding 模型并提供本地降级向量 |
| `src/main/java/com/harness/core/controller/RagController.java` | RAG HTTP API |
| `src/main/java/com/harness/core/mapper/RagChunkMapper.java` | pgvector 和 PostgreSQL 全文检索 SQL |
| `src/main/java/com/harness/core/entity/RagKnowledgeBase.java` | 知识库实体 |
| `src/main/java/com/harness/core/entity/RagDocument.java` | 原始文档实体 |
| `src/main/java/com/harness/core/entity/RagChunk.java` | 文档分段及向量实体 |
| `src/main/java/com/harness/core/dto/RagSearchRow.java` | 检索 SQL 结果 DTO |
| `src/main/resources/db/init_rag.sql` | pgvector、知识库、文档、分段和全文索引初始化脚本 |
| `harness-agent-ui/src/views/RagView.vue` | RAG 页面和交互状态管理 |
| `harness-agent-ui/src/api/rag.js` | 前端 RAG API 封装 |

## 3. 数据模型

### 3.1 `rag_knowledge_bases`

知识库是检索参数和文档的隔离边界。

| 字段 | 说明 |
| --- | --- |
| `id` | UUID 主键 |
| `name` | 名称，最长 120 字符 |
| `description` | 描述，最长 500 字符 |
| `retrieval_mode` | `embedding`、`keywords` 或 `blend` |
| `top_k` | 最多返回的分段数量，范围 1-20 |
| `similarity_threshold` | 最低综合分数，范围 0-1 |
| `segment_size` | 分段目标长度，范围 100-4000 字符 |
| `overlap_size` | 相邻分段重叠长度，必须小于 `segment_size` |
| `created_at`、`updated_at` | 创建和更新时间 |

默认配置为：混合检索、Top K 5、相似度阈值 0.10、分段长度 600、重叠长度 80。

### 3.2 `rag_documents`

保存原始文档，文档删除时通过外键级联删除其分段。

| 字段 | 说明 |
| --- | --- |
| `id` | UUID 主键 |
| `knowledge_base_id` | 所属知识库 |
| `name` | 文档名称，最长 255 字符 |
| `content` | 原始正文 |
| `character_count` | 正文字符数，按 Unicode code point 统计 |
| `chunk_count` | 生成的分段数 |
| `status` | 当前写入流程使用 `ready` |
| `created_at`、`updated_at` | 创建和更新时间 |

单个文档正文上限为 10,000,000 个 Java 字符；上传文件大小上限为 10 MB。

### 3.3 `rag_chunks`

每条记录是一个可独立召回的分段。

| 字段 | 说明 |
| --- | --- |
| `id` | UUID 主键 |
| `knowledge_base_id`、`document_id` | 所属知识库和文档 |
| `position` | 文档内分段序号，从 1 开始 |
| `title` | 分段标题，格式为“文档名 · 分段 N” |
| `content` | 分段正文 |
| `token_count` | 应用层估算 token 数 |
| `embedding` | `vector(1024)`，存储 Embedding 向量 |
| `active` | 是否参与检索 |
| `search_vector` | 由标题和正文生成的 `tsvector`，用于关键词检索 |
| `created_at`、`updated_at` | 创建和更新时间 |

数据库使用 `vector(1024)` 是因为当前配置的 `qwen3.7-text-embedding` 默认返回 1024 维向量。向量维度必须与写入文档和查询时使用的模型保持一致。

## 4. 文档导入流程

入口是 `RagService.createDocument`，前端有“新建文档”和“上传文件”两种方式。

处理顺序如下：

```text
校验知识库
    -> 校验名称、正文和大小
    -> 规范化换行并切分正文
    -> 创建 rag_documents 记录
    -> 对每个分段调用 Embedding
    -> 将向量转为 pgvector 字面量
    -> 写入 rag_chunks
    -> 更新时间库
```

### 4.1 文档校验

- 文档名称不能为空，长度不能超过 255；
- 正文不能为空；
- 正文不能超过 10,000,000 个字符；
- 上传接口只接受 UTF-8 解码的文本内容。

### 4.2 分段算法

`splitContent` 使用知识库的 `segment_size` 和 `overlap_size`：

1. 将 `CRLF` 统一为 `LF` 并去除首尾空白；
2. 以 `segment_size` 作为目标终点；
3. 在目标点前最多 120 个字符内，优先寻找换行、中文句号、问号、感叹号或英文句末；
4. 找不到边界时直接按目标位置切分；
5. 下一段从上一段结束位置减去 `overlap_size` 开始，以保留上下文；
6. 最后一段处理到正文末尾。

分段并不是按 token 计数，而是按 Java 字符长度。`estimateTokens` 仅用于展示和容量参考：中文字符按 1 token 估算，其他非空白字符按每 4 个字符估算。

### 4.3 Embedding 生成

`RagEmbeddingService.embed` 每次只处理一个文本分段或一个查询字符串：

- 查询 `model_configs` 中 `model_type='embedding' AND active=true` 的配置；
- 使用配置中的 `base_url`、`model_name` 和解密后的 API Token；
- 通过 LangChain4j `OpenAiEmbeddingModel` 调用 OpenAI 兼容 `/embeddings` 接口；
- 显式请求 `dimensions(1024)`；
- 校验接口实际返回维度必须为 1024；
- 外部模型调用失败时抛出业务异常，不会静默写入错误向量。

如果没有启用向量模型，则使用本地 SHA-256 特征哈希向量作为降级方案。该降级向量可用于基本检索，但不具备远端 Embedding 模型的语义理解能力。

## 5. 检索流程

入口是 `RagService.search(knowledgeBaseId, queryText, requestedMode)`。

```text
校验知识库和查询文本
    -> 确定检索模式
    -> 为查询文本生成 1024 维向量
    -> SQL 召回向量候选
    -> SQL 召回关键词候选
    -> 两路候选 UNION 去重
    -> Service 层计算关键词兜底分数
    -> 按模式计算综合分数
    -> 应用相似度阈值
    -> 倒序排序并截取 Top K
```

### 5.1 向量召回

`RagChunkMapper.searchCandidates` 使用 pgvector 余弦距离：

```sql
ORDER BY c.embedding <=> CAST(:vector AS vector)
LIMIT 500
```

返回分数为：

```text
vectorScore = max(0, 1 - cosine_distance)
```

当前 SQL 会先召回最多 500 个向量候选，再在 Service 层进行阈值过滤和 Top K 截取。

### 5.2 关键词召回

关键词候选同样最多 500 条，排序优先级如下：

1. 标题或正文包含完整查询短语；
2. PostgreSQL `ts_rank_cd(search_vector, plainto_tsquery('simple', query), 32)` 分数。

由于 PostgreSQL `simple` 配置不提供中文分词，中文长句和中英文连续字符串的效果有限。Service 层还会对标题和正文执行简单的大小写不敏感包含匹配，作为关键词分数兜底。

### 5.3 检索模式和评分

知识库默认模式可以被单次请求的 `mode` 覆盖。支持三种模式：

| 模式 | 综合分数 |
| --- | --- |
| `embedding` | `vectorScore` |
| `keywords` | `keywordScore` |
| `blend` | `max(keywordScore, vectorScore * 0.7 + keywordScore * 0.3)` |

最终只保留 `score >= similarity_threshold` 的结果，然后按分数倒序排列，最多返回 `top_k` 条。

返回结果包含：分段 ID、文档 ID、文档名称、标题、正文、综合分数、向量分数和关键词分数。

## 6. HTTP API

所有接口前缀为 `/api/rag`。

### 6.1 知识库

| 方法 | 路径 | 用途 |
| --- | --- | --- |
| `GET` | `/knowledge-bases` | 查询全部知识库及文档、分段统计 |
| `GET` | `/knowledge-bases/{id}` | 查询知识库详情 |
| `POST` | `/knowledge-bases` | 创建知识库 |
| `PUT` | `/knowledge-bases/{id}` | 更新知识库配置 |
| `DELETE` | `/knowledge-bases/{id}` | 删除知识库及关联文档、分段 |

创建或更新请求示例：

```json
{
  "name": "产品使用手册",
  "description": "产品相关文档",
  "retrievalMode": "blend",
  "topK": 5,
  "similarityThreshold": 0.1,
  "segmentSize": 600,
  "overlapSize": 80
}
```

### 6.2 文档和分段

| 方法 | 路径 | 用途 |
| --- | --- | --- |
| `GET` | `/knowledge-bases/{id}/documents` | 查询知识库文档 |
| `POST` | `/knowledge-bases/{id}/documents` | 写入 JSON 纯文本文档 |
| `POST` | `/knowledge-bases/{id}/documents/upload` | 上传 UTF-8 文本文件 |
| `DELETE` | `/knowledge-bases/{knowledgeBaseId}/documents/{documentId}` | 删除文档及其分段 |
| `GET` | `/knowledge-bases/{knowledgeBaseId}/documents/{documentId}/chunks` | 查看文档分段 |

JSON 文档请求示例：

```json
{
  "name": "枚举说明.md",
  "content": "这里是需要进入知识库的正文。"
}
```

### 6.3 命中测试

```http
POST /api/rag/knowledge-bases/{knowledgeBaseId}/search
Content-Type: application/json
```

请求示例：

```json
{
  "query": "系统 unit 枚举",
  "mode": "blend"
}
```

`mode` 可以为空；为空时使用知识库的 `retrieval_mode`。

## 7. 前端使用流程

RAG 页面位于左侧导航的“RAG”入口，对应 `RagView.vue`。

1. 创建或选择知识库；
2. 在“分段与检索”中调整检索模式、Top K、阈值、分段长度和重叠长度；
3. 在“文档”页粘贴正文或上传 UTF-8 文本文件；
4. 保存后等待“完成分段和向量化”提示；
5. 点击文档可查看实际分段；
6. 切换到“命中测试”，输入查询并选择检索模式；
7. 查看召回正文以及向量、关键词和综合得分。

模型配置位于左侧“模型配置”页面：

- `聊天模型` 供对话链路使用；
- `向量模型` 供 RAG 文档和查询向量化使用；
- 两类模型分别维护启用状态；
- API Token 在服务端以 AES-GCM 密文保存，前端只显示掩码。

## 8. 数据库和模型迁移注意事项

### 8.1 向量维度必须一致

文档向量和查询向量必须来自同一个 Embedding 模型及相同维度。切换模型或修改维度后，旧分段向量不能继续混用。

当前 Qwen 配置使用 `vector(1024)`。如果以后改为其他模型，应确认其原生维度，并同步修改：

- `RagEmbeddingService.DIMENSION`；
- `OpenAiEmbeddingModel.builder().dimensions(...)`；
- `rag_chunks.embedding` 的 pgvector 类型；
- 前端设置页展示文本；
- 已有文档的重新向量化流程。

### 8.2 重新向量化

当前实现是在文档导入时同步生成向量，没有独立的批量重建接口。切换 Embedding 模型后，建议删除旧文档并重新导入，确保所有分段使用同一模型。

直接把数据库列从 `vector(384)` 改成 `vector(1024)` 不能自动转换已有向量。迁移前应备份 `rag_chunks`，清理旧向量，并重新导入文档。

### 8.3 检索规模

当前检索 SQL 每路最多召回 500 个候选，之后在 Java 内存中排序和截取 Top K。表结构暂未创建 HNSW 或 IVFFlat 向量索引。数据量较大时，应增加向量索引并评估候选数量、过滤条件和数据库执行计划。

### 8.4 事务和失败处理

文档写入使用 Spring `@Transactional`。文档记录、分段写入或 Embedding 调用任一步骤失败时，事务会回滚，避免留下半成品数据。外部 API 失败会记录配置 ID 和模型名，不记录 API Token。

## 9. 常见问题排查

### 没有任何命中

按以下顺序检查：

1. 知识库是否存在可检索分段；
2. 分段 `active` 是否为 `true`；
3. `model_configs` 是否有启用的 `embedding` 配置；
4. 文档是否在切换模型后重新导入；
5. 查询模式是否为 `embedding`，以及阈值是否过高；
6. 混合中英文查询是否用空格分隔，例如 `系统 unit 枚举`；
7. API 返回维度是否与数据库列一致。

### 关键词能命中但语义查询不能命中

通常是旧文档使用了另一套向量模型，或文档和查询向量维度不一致。重新导入文档并确认 Embedding 配置处于启用状态。

### API 调用失败

检查 Base URL 是否为 OpenAI 兼容地址并包含正确路径前缀、模型名是否可用、Token 是否有效，以及运行环境是否能访问外部服务。服务端会将模型调用异常转换为 HTTP 400 业务错误。

## 10. 当前实现边界

- 输入以纯文本为主，没有 PDF、Word 等格式解析器；
- 文档向量化是同步流程，大文档会占用请求线程较长时间；
- 没有批量 Embedding 请求，按分段逐条调用模型；
- 没有独立的向量重建、模型版本标记和异步任务队列；
- 中文全文检索依赖 `simple` 配置和应用层包含匹配，复杂中文分词能力有限；
- RAG 结果目前不会自动注入聊天 Prompt，需要上层对话流程显式消费 `SearchHit`。

