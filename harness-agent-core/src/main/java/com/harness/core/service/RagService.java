package com.harness.core.service;

import lombok.RequiredArgsConstructor;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.harness.core.dto.RagSearchRow;
import com.harness.core.entity.RagChunk;
import com.harness.core.entity.RagDocument;
import com.harness.core.entity.RagKnowledgeBase;
import com.harness.core.mapper.RagChunkMapper;
import com.harness.core.mapper.RagDocumentMapper;
import com.harness.core.mapper.RagKnowledgeBaseMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * 管理知识库、文档分段以及基于 pgvector 的 RAG 检索。
 */
@Service
@RequiredArgsConstructor
public class RagService {

    private static final int MIN_SEGMENT_SIZE = 100;
    private static final int MAX_SEGMENT_SIZE = 4000;
    private static final int MAX_DOCUMENT_CHARACTERS = 10_000_000;

    private final RagKnowledgeBaseMapper knowledgeBaseMapper;
    private final RagDocumentMapper documentMapper;
    private final RagChunkMapper chunkMapper;
    private final RagEmbeddingService embeddingService;

    /**
     * 查询全部知识库及其文档、分段统计。
     *
     * @return 知识库摘要列表
     */
    public List<KnowledgeBaseSummary> listKnowledgeBases() {
        return knowledgeBaseMapper.selectList(
                        new LambdaQueryWrapper<RagKnowledgeBase>()
                                .orderByDesc(RagKnowledgeBase::getUpdatedAt)
                )
                .stream()
                .map(this::toSummary)
                .toList();
    }

    /**
     * 查询单个知识库。
     *
     * @param id 知识库 ID
     * @return 知识库实体
     */
    public RagKnowledgeBase getKnowledgeBase(String id) {
        return requireKnowledgeBase(id);
    }

    /**
     * 新建知识库。
     *
     * @param command 知识库配置
     * @return 新建的知识库
     */
    @Transactional
    public RagKnowledgeBase createKnowledgeBase(KnowledgeBaseCommand command) {
        validateKnowledgeBase(command);
        RagKnowledgeBase knowledgeBase = new RagKnowledgeBase();
        knowledgeBase.setId(UUID.randomUUID().toString());
        applyKnowledgeBaseCommand(knowledgeBase, command);
        knowledgeBase.setCreatedAt(LocalDateTime.now());
        knowledgeBase.setUpdatedAt(LocalDateTime.now());
        knowledgeBaseMapper.insert(knowledgeBase);
        return knowledgeBase;
    }

    /**
     * 更新知识库配置。
     *
     * @param id 知识库 ID
     * @param command 知识库配置
     * @return 更新后的知识库
     */
    @Transactional
    public RagKnowledgeBase updateKnowledgeBase(String id, KnowledgeBaseCommand command) {
        validateKnowledgeBase(command);
        RagKnowledgeBase knowledgeBase = requireKnowledgeBase(id);
        applyKnowledgeBaseCommand(knowledgeBase, command);
        knowledgeBase.setUpdatedAt(LocalDateTime.now());
        knowledgeBaseMapper.updateById(knowledgeBase);
        return knowledgeBaseMapper.selectById(id);
    }

    /**
     * 删除知识库及其文档和分段。
     *
     * @param id 知识库 ID
     */
    @Transactional
    public void deleteKnowledgeBase(String id) {
        requireKnowledgeBase(id);
        knowledgeBaseMapper.deleteById(id);
    }

    /**
     * 查询知识库内的文档。
     *
     * @param knowledgeBaseId 知识库 ID
     * @return 文档列表
     */
    public List<RagDocument> listDocuments(String knowledgeBaseId) {
        requireKnowledgeBase(knowledgeBaseId);
        return documentMapper.selectList(
                new LambdaQueryWrapper<RagDocument>()
                        .eq(RagDocument::getKnowledgeBaseId, knowledgeBaseId)
                        .orderByDesc(RagDocument::getCreatedAt)
        );
    }

    /**
     * 导入纯文本内容，并按知识库配置完成分段和向量化。
     *
     * @param knowledgeBaseId 知识库 ID
     * @param name 文档名称
     * @param content 文档正文
     * @return 新建的文档
     */
    @Transactional
    public RagDocument createDocument(String knowledgeBaseId, String name, String content) {
        RagKnowledgeBase knowledgeBase = requireKnowledgeBase(knowledgeBaseId);
        if (name == null || name.isBlank() || name.length() > 255) {
            throw new IllegalArgumentException("文档名称不能为空且不能超过 255 个字符");
        }
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("文档内容不能为空");
        }
        if (content.length() > MAX_DOCUMENT_CHARACTERS) {
            throw new IllegalArgumentException("文档内容不能超过 1000 万个字符");
        }

        // 先按知识库配置切分原文，再为每个分段生成独立向量，保证检索结果可定位到原文片段。
        List<String> segments = splitContent(
                content,
                knowledgeBase.getSegmentSize(),
                knowledgeBase.getOverlapSize()
        );
        RagDocument document = new RagDocument();
        document.setId(UUID.randomUUID().toString());
        document.setKnowledgeBaseId(knowledgeBaseId);
        document.setName(name.trim());
        document.setContent(content);
        document.setCharacterCount(content.codePointCount(0, content.length()));
        document.setChunkCount(segments.size());
        document.setStatus("ready");
        document.setCreatedAt(LocalDateTime.now());
        document.setUpdatedAt(LocalDateTime.now());
        documentMapper.insert(document);

        for (int index = 0; index < segments.size(); index++) {
            String segment = segments.get(index);
            RagChunk chunk = new RagChunk();
            chunk.setId(UUID.randomUUID().toString());
            chunk.setKnowledgeBaseId(knowledgeBaseId);
            chunk.setDocumentId(document.getId());
            chunk.setPosition(index + 1);
            chunk.setTitle(name.trim() + " · 分段 " + (index + 1));
            chunk.setContent(segment);
            chunk.setTokenCount(estimateTokens(segment));
            chunk.setEmbedding(toVectorLiteral(embeddingService.embed(segment)));
            chunk.setActive(true);
            // 使用 Mapper 的专用写入方法显式转换 pgvector，避免 MyBatis 按字符串类型写入。
            chunkMapper.insertVectorChunk(chunk);
        }
        touchKnowledgeBase(knowledgeBase);
        return document;
    }

    /**
     * 删除指定文档。
     *
     * @param knowledgeBaseId 知识库 ID
     * @param documentId 文档 ID
     */
    @Transactional
    public void deleteDocument(String knowledgeBaseId, String documentId) {
        RagKnowledgeBase knowledgeBase = requireKnowledgeBase(knowledgeBaseId);
        RagDocument document = requireDocument(knowledgeBaseId, documentId);
        documentMapper.deleteById(document.getId());
        touchKnowledgeBase(knowledgeBase);
    }

    /**
     * 查询文档的全部分段。
     *
     * @param knowledgeBaseId 知识库 ID
     * @param documentId 文档 ID
     * @return 分段列表
     */
    public List<RagChunk> listChunks(String knowledgeBaseId, String documentId) {
        requireDocument(knowledgeBaseId, documentId);
        return chunkMapper.selectList(
                new LambdaQueryWrapper<RagChunk>()
                        .select(
                                RagChunk::getId,
                                RagChunk::getKnowledgeBaseId,
                                RagChunk::getDocumentId,
                                RagChunk::getPosition,
                                RagChunk::getTitle,
                                RagChunk::getContent,
                                RagChunk::getTokenCount,
                                RagChunk::getActive,
                                RagChunk::getCreatedAt,
                                RagChunk::getUpdatedAt
                        )
                        .eq(RagChunk::getKnowledgeBaseId, knowledgeBaseId)
                        .eq(RagChunk::getDocumentId, documentId)
                        .orderByAsc(RagChunk::getPosition)
        );
    }

    /**
     * 使用 pgvector 与 PostgreSQL 全文索引执行命中测试。
     *
     * @param knowledgeBaseId 知识库 ID
     * @param queryText 查询文本
     * @param requestedMode 临时检索模式，为空时使用知识库配置
     * @return 按综合得分倒序排列的命中分段
     */
    public List<SearchHit> search(String knowledgeBaseId,
                                  String queryText,
                                  String requestedMode) {
        RagKnowledgeBase knowledgeBase = requireKnowledgeBase(knowledgeBaseId);
        if (queryText == null || queryText.isBlank()) {
            throw new IllegalArgumentException("检索内容不能为空");
        }
        String mode = requestedMode == null || requestedMode.isBlank()
                ? knowledgeBase.getRetrievalMode()
                : normalizeMode(requestedMode);
        String vector = toVectorLiteral(embeddingService.embed(queryText));

        // 向量候选和关键词候选在数据库侧分别召回后合并，避免任一路径的候选集限制另一条路径。
        List<SearchHit> candidates = chunkMapper.searchCandidates(
                knowledgeBaseId,
                vector,
                queryText.trim()
        ).stream()
                .map(row -> toSearchHit(row, queryText, mode))
                .toList();

        // 统一在 Service 层应用阈值、排序和 Top K，确保不同检索模式返回规则一致。
        return candidates.stream()
                .filter(hit -> hit.score() >= knowledgeBase.getSimilarityThreshold())
                .sorted(Comparator.comparingDouble(SearchHit::score).reversed())
                .limit(knowledgeBase.getTopK())
                .toList();
    }

    private SearchHit toSearchHit(RagSearchRow row, String queryText, String mode) {
        double vectorScore = row.getVectorScore() == null ? 0.0 : row.getVectorScore();
        double databaseKeywordScore = row.getKeywordScore() == null ? 0.0 : row.getKeywordScore();
        // 中文全文索引可能无法按词切分，因此使用应用层词面匹配作为兜底分数。
        double keywordScore = Math.max(
                databaseKeywordScore,
                lexicalScore(queryText, row.getTitle(), row.getContent())
        );
        return new SearchHit(
                row.getId(),
                row.getDocumentId(),
                row.getDocumentName(),
                row.getTitle(),
                row.getContent(),
                round(score(mode, vectorScore, keywordScore)),
                round(vectorScore),
                round(keywordScore)
        );
    }

    private KnowledgeBaseSummary toSummary(RagKnowledgeBase knowledgeBase) {
        Long documentCount = documentMapper.selectCount(
                new LambdaQueryWrapper<RagDocument>()
                        .eq(RagDocument::getKnowledgeBaseId, knowledgeBase.getId())
        );
        Long chunkCount = chunkMapper.selectCount(
                new LambdaQueryWrapper<RagChunk>()
                        .eq(RagChunk::getKnowledgeBaseId, knowledgeBase.getId())
        );
        return new KnowledgeBaseSummary(
                knowledgeBase,
                documentCount,
                chunkCount
        );
    }

    private void applyKnowledgeBaseCommand(RagKnowledgeBase knowledgeBase,
                                           KnowledgeBaseCommand command) {
        knowledgeBase.setName(command.name().trim());
        knowledgeBase.setDescription(command.description() == null
                ? ""
                : command.description().trim());
        knowledgeBase.setRetrievalMode(normalizeMode(command.retrievalMode()));
        knowledgeBase.setTopK(command.topK());
        knowledgeBase.setSimilarityThreshold(command.similarityThreshold());
        knowledgeBase.setSegmentSize(command.segmentSize());
        knowledgeBase.setOverlapSize(command.overlapSize());
    }

    private void validateKnowledgeBase(KnowledgeBaseCommand command) {
        // 创建和更新共用同一套边界校验，避免非法配置影响后续分段或检索。
        if (command == null || command.name() == null || command.name().isBlank()) {
            throw new IllegalArgumentException("知识库名称不能为空");
        }
        if (command.name().length() > 120) {
            throw new IllegalArgumentException("知识库名称不能超过 120 个字符");
        }
        if (command.description() != null && command.description().length() > 500) {
            throw new IllegalArgumentException("知识库描述不能超过 500 个字符");
        }
        normalizeMode(command.retrievalMode());
        if (command.topK() == null || command.topK() < 1 || command.topK() > 20) {
            throw new IllegalArgumentException("Top K 必须在 1 到 20 之间");
        }
        if (command.similarityThreshold() == null
                || command.similarityThreshold() < 0.0
                || command.similarityThreshold() > 1.0) {
            throw new IllegalArgumentException("相似度阈值必须在 0 到 1 之间");
        }
        if (command.segmentSize() == null
                || command.segmentSize() < MIN_SEGMENT_SIZE
                || command.segmentSize() > MAX_SEGMENT_SIZE) {
            throw new IllegalArgumentException("分段长度必须在 100 到 4000 之间");
        }
        if (command.overlapSize() == null
                || command.overlapSize() < 0
                || command.overlapSize() >= command.segmentSize()) {
            throw new IllegalArgumentException("重叠长度必须大于等于 0 且小于分段长度");
        }
    }

    private String normalizeMode(String mode) {
        // 统一模式大小写和空白，确保 API 临时模式与数据库默认模式行为一致。
        String normalized = mode == null ? "blend" : mode.trim().toLowerCase(Locale.ROOT);
        if (!List.of("embedding", "keywords", "blend").contains(normalized)) {
            throw new IllegalArgumentException("检索模式仅支持 embedding、keywords 或 blend");
        }
        return normalized;
    }

    private RagKnowledgeBase requireKnowledgeBase(String id) {
        // 所有下游操作先校验知识库归属，避免跨库读取或写入分段。
        RagKnowledgeBase knowledgeBase = knowledgeBaseMapper.selectById(id);
        if (knowledgeBase == null) {
            throw new IllegalArgumentException("知识库不存在：" + id);
        }
        return knowledgeBase;
    }

    private RagDocument requireDocument(String knowledgeBaseId, String documentId) {
        RagDocument document = documentMapper.selectOne(
                new LambdaQueryWrapper<RagDocument>()
                        .eq(RagDocument::getId, documentId)
                        .eq(RagDocument::getKnowledgeBaseId, knowledgeBaseId)
        );
        if (document == null) {
            throw new IllegalArgumentException("文档不存在：" + documentId);
        }
        return document;
    }

    private void touchKnowledgeBase(RagKnowledgeBase knowledgeBase) {
        knowledgeBase.setUpdatedAt(LocalDateTime.now());
        knowledgeBaseMapper.updateById(knowledgeBase);
    }

    private List<String> splitContent(String content, int segmentSize, int overlapSize) {
        String normalized = content.replace("\r\n", "\n").trim();
        List<String> segments = new ArrayList<>();
        int start = 0;
        while (start < normalized.length()) {
            int targetEnd = Math.min(start + segmentSize, normalized.length());
            int end = findBoundary(normalized, start, targetEnd);
            String segment = normalized.substring(start, end).trim();
            if (!segment.isBlank()) {
                segments.add(segment);
            }
            if (end >= normalized.length()) {
                break;
            }
            // 保留相邻分段的尾部上下文，降低代码或句子被切断后造成的语义损失。
            start = Math.max(start + 1, end - overlapSize);
        }
        return segments;
    }

    private int findBoundary(String content, int start, int targetEnd) {
        if (targetEnd >= content.length()) {
            return content.length();
        }
        int searchStart = Math.max(start + 1, targetEnd - 120);
        // 优先在目标位置附近的换行或句末切分，避免任意截断完整语句。
        for (int index = targetEnd; index >= searchStart; index--) {
            char current = content.charAt(index - 1);
            if (current == '\n'
                    || current == '。'
                    || current == '！'
                    || current == '？'
                    || current == '.'
                    || current == '!'
                    || current == '?') {
                return index;
            }
        }
        return targetEnd;
    }

    private int estimateTokens(String content) {
        int chineseCharacters = 0;
        int otherCharacters = 0;
        // 中文按字符估算，其他字符按每四个字符估算，用于前端展示和容量控制。
        for (int codePoint : content.codePoints().toArray()) {
            if (Character.UnicodeScript.of(codePoint) == Character.UnicodeScript.HAN) {
                chineseCharacters++;
            } else if (!Character.isWhitespace(codePoint)) {
                otherCharacters++;
            }
        }
        return chineseCharacters + (int) Math.ceil(otherCharacters / 4.0);
    }

    private String toVectorLiteral(double[] vector) {
        StringBuilder builder = new StringBuilder("[");
        // pgvector 接收标准数组字面量；这里保持与入库时完全相同的格式。
        for (int index = 0; index < vector.length; index++) {
            if (index > 0) {
                builder.append(',');
            }
            builder.append(vector[index]);
        }
        return builder.append(']').toString();
    }

    private double lexicalScore(String query, String title, String content) {
        String normalizedQuery = query.toLowerCase(Locale.ROOT).trim();
        String searchableText = (title == null ? "" : title)
                + "\n"
                + (content == null ? "" : content);
        String normalizedContent = searchableText.toLowerCase(Locale.ROOT);
        if (normalizedContent.contains(normalizedQuery)) {
            return 1.0;
        }
        // 对空格分隔语言按词匹配；中文连续文本则依赖字符包含关系作为降级判断。
        List<String> terms = Arrays.stream(normalizedQuery.split("[^\\p{L}\\p{N}]+"))
                .filter(term -> !term.isBlank())
                .toList();
        if (terms.isEmpty()) {
            return 0.0;
        }
        long matched = terms.stream()
                .filter(normalizedContent::contains)
                .count();
        return (double) matched / terms.size();
    }

    private double score(String mode, double vectorScore, double keywordScore) {
        // 混合模式偏重语义相似度，同时保留明确关键词命中的加成。
        double result = switch (mode) {
            case "embedding" -> vectorScore;
            case "keywords" -> keywordScore;
            default -> Math.max(
                    keywordScore,
                    vectorScore * 0.7 + keywordScore * 0.3
            );
        };
        return Math.max(0.0, Math.min(1.0, result));
    }

    private double round(double value) {
        return Math.round(value * 10000.0) / 10000.0;
    }

    /**
     * 知识库配置写入参数。
     *
     * @param name 名称
     * @param description 描述
     * @param retrievalMode 检索模式
     * @param topK 返回数量
     * @param similarityThreshold 相似度阈值
     * @param segmentSize 分段长度
     * @param overlapSize 重叠长度
     */
    public record KnowledgeBaseCommand(
            String name,
            String description,
            String retrievalMode,
            Integer topK,
            Double similarityThreshold,
            Integer segmentSize,
            Integer overlapSize
    ) {
    }

    /**
     * 知识库及其内容统计。
     *
     * @param knowledgeBase 知识库配置
     * @param documentCount 文档数
     * @param chunkCount 分段数
     */
    public record KnowledgeBaseSummary(
            RagKnowledgeBase knowledgeBase,
            long documentCount,
            long chunkCount
    ) {
    }

    /**
     * RAG 检索命中结果。
     *
     * @param chunkId 分段 ID
     * @param documentId 文档 ID
     * @param documentName 文档名称
     * @param title 分段标题
     * @param content 分段内容
     * @param score 综合得分
     * @param vectorScore 向量得分
     * @param keywordScore 关键词得分
     */
    public record SearchHit(
            String chunkId,
            String documentId,
            String documentName,
            String title,
            String content,
            double score,
            double vectorScore,
            double keywordScore
    ) {
    }
}
