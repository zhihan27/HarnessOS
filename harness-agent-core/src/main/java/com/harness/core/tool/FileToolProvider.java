package com.harness.core.tool;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import dev.langchain4j.agent.tool.Tool;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;

/**
 * 文件操作工具
 *
 * 提供安全的文件读写功能，避免bash命令的引号转义问题
 */
@Component
public class FileToolProvider {

    private static final Logger logger = LoggerFactory.getLogger(FileToolProvider.class);

    /**
     * 写入文件（覆盖模式）
     *
     * @param filePath 文件路径（相对或绝对）
     * @param content 文件内容
     * @return 执行结果
     */
    @Tool("写入文件内容。如果文件不存在会自动创建，如果存在会覆盖。支持多行文本，无需担心引号转义。")
    public String writeFile(String filePath, String content) {
        logger.info("写入文件: path={}, contentLength={}", filePath, content != null ? content.length() : 0);

        try {
            Path path = Paths.get(filePath);

            // 创建父目录（如果不存在）
            Path parentDir = path.getParent();
            if (parentDir != null && !Files.exists(parentDir)) {
                Files.createDirectories(parentDir);
                logger.info("创建父目录: {}", parentDir);
            }

            // 写入文件
            Files.writeString(path, content != null ? content : "", StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

            String result = String.format("文件写入成功: %s (大小: %d 字符)", filePath, content != null ? content.length() : 0);
            logger.info(result);
            return result;

        } catch (IOException e) {
            String error = String.format("文件写入失败: %s, 错误: %s", filePath, e.getMessage());
            logger.error(error, e);
            return error;
        }
    }

    /**
     * 追加内容到文件
     *
     * @param filePath 文件路径
     * @param content 要追加的内容
     * @return 执行结果
     */
    @Tool("追加内容到文件末尾。如果文件不存在会自动创建。")
    public String appendFile(String filePath, String content) {
        logger.info("追加文件: path={}, contentLength={}", filePath, content != null ? content.length() : 0);

        try {
            Path path = Paths.get(filePath);

            // 创建父目录（如果不存在）
            Path parentDir = path.getParent();
            if (parentDir != null && !Files.exists(parentDir)) {
                Files.createDirectories(parentDir);
                logger.info("创建父目录: {}", parentDir);
            }

            // 追加内容
            Files.writeString(path, content != null ? content : "", StandardOpenOption.CREATE, StandardOpenOption.APPEND);

            String result = String.format("内容追加成功: %s (追加: %d 字符)", filePath, content != null ? content.length() : 0);
            logger.info(result);
            return result;

        } catch (IOException e) {
            String error = String.format("文件追加失败: %s, 错误: %s", filePath, e.getMessage());
            logger.error(error, e);
            return error;
        }
    }

    /**
     * 读取文件内容
     *
     * @param filePath 文件路径
     * @return 文件内容或错误信息
     */
    @Tool("读取文件内容。如果文件不存在会返回错误信息。")
    public String readFile(String filePath) {
        logger.info("读取文件: path={}", filePath);

        try {
            Path path = Paths.get(filePath);

            if (!Files.exists(path)) {
                return String.format("文件不存在: %s", filePath);
            }

            String content = Files.readString(path);
            logger.info("文件读取成功: path={}, length={}", filePath, content.length());
            return content;

        } catch (IOException e) {
            String error = String.format("文件读取失败: %s, 错误: %s", filePath, e.getMessage());
            logger.error(error, e);
            return error;
        }
    }

    /**
     * 检查文件是否存在
     *
     * @param filePath 文件路径
     * @return 是否存在
     */
    @Tool("检查文件或目录是否存在。")
    public String fileExists(String filePath) {
        logger.info("检查文件: path={}", filePath);

        Path path = Paths.get(filePath);
        boolean exists = Files.exists(path);

        if (exists) {
            boolean isDirectory = Files.isDirectory(path);
            return String.format("存在: %s (%s)", filePath, isDirectory ? "目录" : "文件");
        } else {
            return String.format("不存在: %s", filePath);
        }
    }

    /**
     * 删除文件
     *
     * @param filePath 文件路径
     * @return 执行结果
     */
    @Tool("删除文件。如果文件不存在会返回错误信息。")
    public String deleteFile(String filePath) {
        logger.info("删除文件: path={}", filePath);

        try {
            Path path = Paths.get(filePath);

            if (!Files.exists(path)) {
                return String.format("文件不存在: %s", filePath);
            }

            Files.delete(path);
            logger.info("文件删除成功: {}", filePath);
            return String.format("文件删除成功: %s", filePath);

        } catch (IOException e) {
            String error = String.format("文件删除失败: %s, 错误: %s", filePath, e.getMessage());
            logger.error(error, e);
            return error;
        }
    }

    /**
     * 创建目录
     *
     * @param dirPath 目录路径
     * @return 执行结果
     */
    @Tool("创建目录（包括所有必需的父目录）。")
    public String createDirectory(String dirPath) {
        logger.info("创建目录: path={}", dirPath);

        try {
            Path path = Paths.get(dirPath);

            if (Files.exists(path)) {
                return String.format("目录已存在: %s", dirPath);
            }

            Files.createDirectories(path);
            logger.info("目录创建成功: {}", dirPath);
            return String.format("目录创建成功: %s", dirPath);

        } catch (IOException e) {
            String error = String.format("目录创建失败: %s, 错误: %s", dirPath, e.getMessage());
            logger.error(error, e);
            return error;
        }
    }

    /**
     * 列出目录内容
     *
     * @param dirPath 目录路径
     * @return 目录内容列表
     */
    @Tool("列出目录下的所有文件和子目录。")
    public String listDirectory(String dirPath) {
        logger.info("列出目录: path={}", dirPath);

        try {
            Path path = Paths.get(dirPath);

            if (!Files.exists(path)) {
                return String.format("目录不存在: %s", dirPath);
            }

            if (!Files.isDirectory(path)) {
                return String.format("不是目录: %s", dirPath);
            }

            StringBuilder result = new StringBuilder();
            result.append(String.format("目录内容: %s\n\n", dirPath));

            Files.list(path).forEach(p -> {
                String type = Files.isDirectory(p) ? "[目录]" : "[文件]";
                result.append(String.format("%s %s\n", type, p.getFileName()));
            });

            return result.toString();

        } catch (IOException e) {
            String error = String.format("列出目录失败: %s, 错误: %s", dirPath, e.getMessage());
            logger.error(error, e);
            return error;
        }
    }

    /**
     * 清理会话上下文（FileTool不需要会话上下文，提供空实现）
     */
    public static void clearSessionContext() {
        // FileTool不依赖会话上下文，无需清理
    }
}