package com.harness.core.service;

import com.harness.core.entity.ModelConfig;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 负责调用 OpenAI 兼容 Embedding 服务生成语义向量，并提供本地降级实现。
 */
@Service
public class RagEmbeddingService {

    private static final Logger logger = LoggerFactory.getLogger(RagEmbeddingService.class);

    /** 向量维度，必须与数据库 rag_chunks.embedding 的 vector(1024) 保持一致。 */
    private static final int DIMENSION = 1024;
    private final ModelConfigService modelConfigService;

    public RagEmbeddingService(ModelConfigService modelConfigService) {
        this.modelConfigService = modelConfigService;
    }

    /**
     * 将文本转换为归一化的语义向量；未配置向量模型时使用本地特征哈希。
     *
     * @param text 待向量化文本
     * @return 固定维度向量
     */
    public double[] embed(String text) {
        ModelConfig config = modelConfigService.activeEmbeddingOrNull();
        if (config != null) {
            try {
                EmbeddingModel model = OpenAiEmbeddingModel.builder()
                        .apiKey(modelConfigService.decryptToken(config))
                        .baseUrl(config.getBaseUrl())
                        .modelName(config.getModelName())
                        .dimensions(DIMENSION)
                        .build();
                double[] vector = model.embed(TextSegment.from(text == null ? "" : text))
                        .content()
                        .vectorAsDoubleArray();
                if (vector.length != DIMENSION) {
                    throw new IllegalStateException("向量模型返回维度异常：期望 " + DIMENSION + "，实际 " + vector.length);
                }
                return vector;
            } catch (RuntimeException exception) {
                logger.error("调用向量模型失败，无法完成 RAG 向量化。配置 id={}，model={}", config.getId(), config.getModelName(), exception);
                throw new IllegalStateException("调用向量模型失败，请检查 Embedding 配置和服务可用性", exception);
            }
        }
        logger.debug("未配置 Embedding 模型，使用本地特征向量降级");
        double[] vector = new double[DIMENSION];
        List<String> features = features(text);
        for (String feature : features) {
            byte[] digest = sha256(feature);
            int index = ((digest[0] & 0xff) << 8 | (digest[1] & 0xff)) % DIMENSION;
            double sign = (digest[2] & 1) == 0 ? 1.0 : -1.0;
            vector[index] += sign;
        }

        double norm = 0.0;
        for (double value : vector) {
            norm += value * value;
        }
        if (norm == 0.0) {
            return vector;
        }

        norm = Math.sqrt(norm);
        for (int index = 0; index < vector.length; index++) {
            vector[index] /= norm;
        }
        return vector;
    }

    /**
     * 计算两个归一化向量的余弦相似度。
     *
     * @param left 左向量
     * @param right 右向量
     * @return 0 到 1 之间的相似度
     */
    public double cosine(double[] left, double[] right) {
        int length = Math.min(left.length, right.length);
        double dot = 0.0;
        for (int index = 0; index < length; index++) {
            dot += left[index] * right[index];
        }
        return Math.max(0.0, Math.min(1.0, dot));
    }

    private List<String> features(String source) {
        String text = source == null ? "" : source.toLowerCase(Locale.ROOT).trim();
        List<String> result = new ArrayList<>();
        // 同时保留空格分词和中文字符 n-gram，兼顾英文标识符与连续中文查询。
        String[] words = text.split("[^\\p{L}\\p{N}]+");
        for (String word : words) {
            if (!word.isBlank()) {
                result.add("w:" + word);
            }
        }

        // 中文等连续文本缺少天然空格，补充字符二元组和三元组特征。
        int[] codePoints = text.replaceAll("\\s+", "").codePoints().toArray();
        for (int index = 0; index < codePoints.length; index++) {
            result.add("c:" + new String(codePoints, index, 1));
            if (index + 1 < codePoints.length) {
                result.add("b:" + new String(codePoints, index, 2));
            }
            if (index + 2 < codePoints.length) {
                result.add("t:" + new String(codePoints, index, 3));
            }
        }
        return result;
    }

    private byte[] sha256(String text) {
        try {
            return MessageDigest.getInstance("SHA-256")
                    .digest(text.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("当前 JDK 不支持 SHA-256", exception);
        }
    }
}
