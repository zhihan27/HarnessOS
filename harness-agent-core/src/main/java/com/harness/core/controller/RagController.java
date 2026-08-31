package com.harness.core.controller;

import com.harness.core.entity.RagChunk;
import com.harness.core.entity.RagDocument;
import com.harness.core.entity.RagKnowledgeBase;
import com.harness.core.service.RagService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;

/**
 * 提供知识库管理、文档分段与 RAG 命中测试接口。
 */
@RestController
@RequestMapping("/api/rag")
@RequiredArgsConstructor
public class RagController {

    private static final long MAX_UPLOAD_BYTES = 10L * 1024L * 1024L;

    private final RagService ragService;

    /**
     * 查询全部知识库。
     *
     * @return 知识库摘要列表
     */
    @GetMapping("/knowledge-bases")
    public List<RagService.KnowledgeBaseSummary> listKnowledgeBases() {
        return ragService.listKnowledgeBases();
    }

    /**
     * 查询知识库详情。
     *
     * @param id 知识库 ID
     * @return 知识库详情
     */
    @GetMapping("/knowledge-bases/{id}")
    public RagKnowledgeBase getKnowledgeBase(@PathVariable String id) {
        return ragService.getKnowledgeBase(id);
    }

    /**
     * 新建知识库。
     *
     * @param request 知识库配置
     * @return 新建的知识库
     */
    @PostMapping("/knowledge-bases")
    public RagKnowledgeBase createKnowledgeBase(@RequestBody KnowledgeBaseRequest request) {
        return ragService.createKnowledgeBase(request.toCommand());
    }

    /**
     * 更新知识库。
     *
     * @param id 知识库 ID
     * @param request 知识库配置
     * @return 更新后的知识库
     */
    @PutMapping("/knowledge-bases/{id}")
    public RagKnowledgeBase updateKnowledgeBase(
            @PathVariable String id,
            @RequestBody KnowledgeBaseRequest request) {
        return ragService.updateKnowledgeBase(id, request.toCommand());
    }

    /**
     * 删除知识库。
     *
     * @param id 知识库 ID
     */
    @DeleteMapping("/knowledge-bases/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteKnowledgeBase(@PathVariable String id) {
        ragService.deleteKnowledgeBase(id);
    }

    /**
     * 查询知识库文档。
     *
     * @param knowledgeBaseId 知识库 ID
     * @return 文档列表
     */
    @GetMapping("/knowledge-bases/{knowledgeBaseId}/documents")
    public List<RagDocument> listDocuments(@PathVariable String knowledgeBaseId) {
        return ragService.listDocuments(knowledgeBaseId);
    }

    /**
     * 通过 JSON 文本导入文档。
     *
     * @param knowledgeBaseId 知识库 ID
     * @param request 文档内容
     * @return 新建的文档
     */
    @PostMapping("/knowledge-bases/{knowledgeBaseId}/documents")
    public RagDocument createDocument(
            @PathVariable String knowledgeBaseId,
            @RequestBody DocumentRequest request) {
        return ragService.createDocument(
                knowledgeBaseId,
                request.name(),
                request.content()
        );
    }

    /**
     * 上传 UTF-8 文本文档并完成分段向量化。
     *
     * @param knowledgeBaseId 知识库 ID
     * @param file 文本文件
     * @return 新建的文档
     * @throws IOException 文件读取失败时抛出
     */
    @PostMapping("/knowledge-bases/{knowledgeBaseId}/documents/upload")
    public RagDocument uploadDocument(
            @PathVariable String knowledgeBaseId,
            @RequestParam("file") MultipartFile file) throws IOException {
        // 先限制上传大小和空文件，避免无效内容进入分段及向量化流程。
        if (file.isEmpty()) {
            throw new IllegalArgumentException("上传文件不能为空");
        }
        if (file.getSize() > MAX_UPLOAD_BYTES) {
            throw new IllegalArgumentException("上传文件不能超过 10 MB");
        }
        String fileName = file.getOriginalFilename() == null
                ? "未命名文档.txt"
                : file.getOriginalFilename();
        // 上传接口统一按 UTF-8 解码，保证中文文档与命中测试使用相同字符内容。
        String content = new String(file.getBytes(), StandardCharsets.UTF_8);
        return ragService.createDocument(knowledgeBaseId, fileName, content);
    }

    /**
     * 删除知识库文档。
     *
     * @param knowledgeBaseId 知识库 ID
     * @param documentId 文档 ID
     */
    @DeleteMapping("/knowledge-bases/{knowledgeBaseId}/documents/{documentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteDocument(
            @PathVariable String knowledgeBaseId,
            @PathVariable String documentId) {
        ragService.deleteDocument(knowledgeBaseId, documentId);
    }

    /**
     * 查询文档分段。
     *
     * @param knowledgeBaseId 知识库 ID
     * @param documentId 文档 ID
     * @return 分段列表
     */
    @GetMapping("/knowledge-bases/{knowledgeBaseId}/documents/{documentId}/chunks")
    public List<RagChunk> listChunks(
            @PathVariable String knowledgeBaseId,
            @PathVariable String documentId) {
        return ragService.listChunks(knowledgeBaseId, documentId);
    }

    /**
     * 执行 RAG 命中测试。
     *
     * @param knowledgeBaseId 知识库 ID
     * @param request 检索参数
     * @return 命中分段列表
     */
    @PostMapping("/knowledge-bases/{knowledgeBaseId}/search")
    public List<RagService.SearchHit> search(
            @PathVariable String knowledgeBaseId,
            @RequestBody SearchRequest request) {
        // 检索模式为空时由 Service 使用知识库默认配置，接口只透传本次请求参数。
        return ragService.search(
                knowledgeBaseId,
                request.query(),
                request.mode()
        );
    }

    /**
     * 将业务校验异常转换为明确的 400 响应。
     *
     * @param exception 业务异常
     * @return 错误信息
     */
    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, String> handleBusinessException(RuntimeException exception) {
        return Map.of("error", exception.getMessage());
    }

    /**
     * 知识库写入请求。
     *
     * @param name 名称
     * @param description 描述
     * @param retrievalMode 检索模式
     * @param topK 返回数量
     * @param similarityThreshold 相似度阈值
     * @param segmentSize 分段长度
     * @param overlapSize 重叠长度
     */
    public record KnowledgeBaseRequest(
            String name,
            String description,
            String retrievalMode,
            Integer topK,
            Double similarityThreshold,
            Integer segmentSize,
            Integer overlapSize
    ) {

        private RagService.KnowledgeBaseCommand toCommand() {
            // 请求 DTO 与 Service 命令对象隔离，避免控制器参数结构泄漏到业务层。
            return new RagService.KnowledgeBaseCommand(
                    name,
                    description,
                    retrievalMode,
                    topK,
                    similarityThreshold,
                    segmentSize,
                    overlapSize
            );
        }
    }

    /**
     * 文档写入请求。
     *
     * @param name 文档名称
     * @param content 文档正文
     */
    public record DocumentRequest(String name, String content) {
    }

    /**
     * 命中测试请求。
     *
     * @param query 查询文本
     * @param mode 检索模式
     */
    public record SearchRequest(String query, String mode) {
    }
}
