package com.harness.core.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Pattern;

/**
 * 命令语义安全分析器
 * 在静态规则检查通过后，进行二次动态语义分析
 * 检测命令的真实意图是否具有破坏性
 */
@Component
public class CommandSemanticAnalyzer {

    private static final Logger logger = LoggerFactory.getLogger(CommandSemanticAnalyzer.class);

    // 命令组合符号（可能隐藏危险意图）
    private static final Set<String> COMPOSITION_OPERATORS = Set.of(
        "&&", "||", "|", ";", "&", "$()", "`", ">", ">>", "2>", "&>"
    );

    // 危险意图关键词（语义层面）
    private static final Set<String> DESTRUCTIVE_INTENT_KEYWORDS = Set.of(
        "删除", "remove", "delete", "erase", "wipe",
        "格式化", "format", "clear",
        "清空", "truncate", "drop",
        "销毁", "destroy", "kill", "terminate",
        "覆盖", "overwrite", "replace",
        "卸载", "uninstall", "remove-all",
        "关闭系统", "shutdown", "reboot", "restart"
    );

    // 危险命令组合模式
    private static final List<Pattern> DANGEROUS_COMPOSITION_PATTERNS = Arrays.asList(
        // 管道组合危险操作
        Pattern.compile(".*\\|.*\\b(rm|del|rmdir|format|shutdown|kill)\\b.*"),
        // 命令链组合危险操作
        Pattern.compile(".*(&&|\\|\\||;).*\\b(rm|del|rmdir|format|shutdown)\\b.*"),
        // 重定向到系统文件
        Pattern.compile(".*>.*(\\/dev\\/|\\\\dev\\\\|\\/etc\\/|\\\\etc\\\\).*"),
        // Fork 炸弹
        Pattern.compile(".*:\\(\\).*"),
        // 递归强制删除
        Pattern.compile(".*\\b(rm|del|rmdir)\\b.*(-rf|--recursive|--force|\\/s|\\/q).*"),
        // 权限全开
        Pattern.compile(".*\\b(chmod|chown)\\b.*(-R|--recursive).*777.*"),
        // 远程代码执行
        Pattern.compile(".*\\b(wget|curl)\\b.*\\|.*(sh|bash|python|perl).*"),
        // 环境变量破坏
        Pattern.compile(".*\\b(unset|export)\\b.*(PATH|HOME|USER).*"),
        // 进程终止
        Pattern.compile(".*\\b(kill|pkill|taskkill)\\b.*(-9|--force|\\/F).*")
    );

    /**
     * 分析命令是否具有破坏性意图
     *
     * @param command 待执行的命令
     * @return true 表示具有破坏性，应拒绝执行
     */
    public boolean isDestructiveCommand(String command) {
        if (command == null || command.trim().isEmpty()) {
            return false;
        }

        String normalizedCommand = command.toLowerCase().trim();

        logger.info("开始语义分析: {}", command);

        // 1. 检查命令组合符号
        if (containsCompositionOperators(normalizedCommand)) {
            logger.warn("检测到命令组合符号，需深度分析");
            return analyzeComposition(normalizedCommand);
        }

        // 2. 检查危险意图关键词
        if (containsDestructiveIntent(normalizedCommand)) {
            logger.warn("检测到危险意图关键词");
            return true;
        }

        // 3. 检查危险命令组合模式
        for (Pattern pattern : DANGEROUS_COMPOSITION_PATTERNS) {
            if (pattern.matcher(normalizedCommand).matches()) {
                logger.warn("匹配危险组合模式: {}", pattern.pattern());
                return true;
            }
        }

        // 4. 检查递归/强制操作
        if (isRecursiveForceOperation(normalizedCommand)) {
            logger.warn("检测到递归强制操作");
            return true;
        }

        logger.info("语义分析通过，命令安全");
        return false;
    }

    /**
     * 检查是否包含命令组合符号
     */
    private boolean containsCompositionOperators(String command) {
        for (String operator : COMPOSITION_OPERATORS) {
            if (command.contains(operator.toLowerCase())) {
                return true;
            }
        }
        return false;
    }

    /**
     * 分析命令组合的危险性
     */
    private boolean analyzeComposition(String command) {
        // 分解命令链
        String[] subCommands = command.split("&&|\\|\\||;|\\|");

        for (String subCmd : subCommands) {
            String trimmed = subCmd.trim();

            // 检查每个子命令的危险性
            if (isSingleCommandDestructive(trimmed)) {
                return true;
            }
        }

        // 检查管道危险组合
        if (command.contains("|")) {
            return analyzePipeline(command);
        }

        return false;
    }

    /**
     * 分析单个命令的危险性
     */
    private boolean isSingleCommandDestructive(String command) {
        // 危险命令基础词
        Set<String> dangerousCommands = Set.of(
            "rm", "del", "rmdir", "format", "shutdown",
            "reboot", "halt", "kill", "pkill", "mkfs",
            "fdisk", "dd", "chmod", "chown"
        );

        for (String dangerous : dangerousCommands) {
            if (command.startsWith(dangerous) || command.contains(" " + dangerous + " ")) {
                // 进一步检查是否有危险参数
                if (command.contains("-rf") || command.contains("-r") ||
                    command.contains("/s") || command.contains("/q") ||
                    command.contains("-R") || command.contains("--recursive")) {
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * 分析管道组合的危险性
     */
    private boolean analyzePipeline(String command) {
        // 检查是否是远程代码执行
        if (command.contains("wget") || command.contains("curl")) {
            if (command.contains("|") && (command.contains("sh") || command.contains("bash"))) {
                logger.warn("检测到远程代码执行模式");
                return true;
            }
        }

        // 检查是否是数据破坏管道
        String[] parts = command.split("\\|");
        for (String part : parts) {
            if (part.trim().startsWith("rm") || part.trim().startsWith("del")) {
                return true;
            }
        }

        return false;
    }

    /**
     * 检查是否包含危险意图关键词
     */
    private boolean containsDestructiveIntent(String command) {
        for (String keyword : DESTRUCTIVE_INTENT_KEYWORDS) {
            if (command.contains(keyword.toLowerCase())) {
                return true;
            }
        }
        return false;
    }

    /**
     * 检查是否是递归强制操作
     */
    private boolean isRecursiveForceOperation(String command) {
        // rm -rf, rmdir /s, del /s /q 等
        boolean hasDeleteCommand = command.contains("rm") || command.contains("del") ||
                                    command.contains("rmdir") || command.contains("erase");
        boolean hasRecursiveFlag = command.contains("-rf") || command.contains("-r") ||
                                    command.contains("-R") || command.contains("/s") ||
                                    command.contains("--recursive");
        boolean hasForceFlag = command.contains("-f") || command.contains("--force") ||
                               command.contains("/q") || command.contains("/y");

        return hasDeleteCommand && (hasRecursiveFlag || hasForceFlag);
    }

    /**
     * 获取分析报告
     */
    public String getAnalysisReport(String command) {
        StringBuilder report = new StringBuilder();
        report.append("命令语义分析报告:\n");
        report.append("原始命令: ").append(command).append("\n");

        if (containsCompositionOperators(command)) {
            report.append("⚠ 包含命令组合符号\n");
        }

        if (containsDestructiveIntent(command)) {
            report.append("⚠ 包含危险意图关键词\n");
        }

        if (isRecursiveForceOperation(command)) {
            report.append("⚠ 检测到递归强制操作\n");
        }

        boolean destructive = isDestructiveCommand(command);
        report.append("最终判断: ").append(destructive ? "❌ 拒绝执行" : "✅ 安全").append("\n");

        return report.toString();
    }
}