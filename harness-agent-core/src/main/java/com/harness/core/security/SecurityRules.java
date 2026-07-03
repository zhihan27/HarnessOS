package com.harness.core.security;

import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 安全拦截规则配置
 * 统一管理所有安全规则，支持动态添加
 */
@Component
public class SecurityRules {

    // 硬拦截黑名单：高危操作，绝对禁止（即使确认也无法执行）
    private final Set<String> highRiskPatterns = new HashSet<>();

    // 软询问名单：中危操作，用户确认后可执行
    private final Set<String> mediumRiskPatterns = new HashSet<>();

    // 工具级别规则映射（工具名 -> 规则）
    private final Map<String, List<String>> toolSpecificRules = new HashMap<>();

    public SecurityRules() {
        initDefaultRules();
    }

    /**
     * 初始化默认安全规则
     */
    private void initDefaultRules() {
        // 高危规则（硬拦截，绝对禁止）
        highRiskPatterns.addAll(Arrays.asList(
            // 系统破坏
            "rm -rf /", "rm -rf /*", "rmdir /s /q", "rmdir /s",
            "del /s /q", "format", "shutdown", "reboot", "halt",
            "init 0", "init 6",

            // 磁盘/分区操作
            "mkfs", "fdisk", "dd if=", "> /dev/sda",

            // Fork 炸弹
            ":(){ :|:& };:",

            // 远程代码执行
            "wget | bash", "wget | sh", "curl | bash", "curl | sh",

            // 权限破坏
            "chmod -R 777 /", "chown -R",

            // 数据库高危
            "DROP TABLE", "TRUNCATE TABLE"
        ));

        // 中危规则（软询问，用户确认后可执行）
        mediumRiskPatterns.addAll(Arrays.asList(
            // 文件操作
            "rm", "del", "rmdir", "mv", "cp",

            // 权限修改
            "chmod", "chown",

            // 进程管理
            "kill", "pkill", "taskkill",

            // 系统配置
            "netsh", "iptables", "firewall-cmd",
            "systemctl stop", "systemctl disable", "service stop",

            // 用户管理
            "useradd", "userdel", "passwd",

            // 数据库敏感
            "INSERT", "UPDATE", "DELETE", "DELETE FROM"
        ));

        // Bash 工具特定规则
        toolSpecificRules.put("executeBash", Arrays.asList(
            "rm", "del", "format", "shutdown"
        ));
    }

    /**
     * 判断是否是高危命令
     * 支持多种命令格式：直接命令、cmd.exe /c 包装、带引号等
     */
    public boolean isHighRisk(String command) {
        if (command == null) return false;
        String normalized = command.toLowerCase().trim();

        for (String pattern : highRiskPatterns) {
            String patternLower = pattern.toLowerCase();

            // 直接包含高危模式
            if (normalized.contains(patternLower)) {
                return true;
            }

            // cmd.exe /c "高危命令" 格式
            if (normalized.contains("cmd.exe") || normalized.contains("cmd")) {
                if (normalized.contains(patternLower)) {
                    return true;
                }
            }

            // bash -c "高危命令" 格式
            if (normalized.contains("bash") && normalized.contains("-c")) {
                if (normalized.contains(patternLower)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * 判断是否是中危命令
     * 支持多种命令格式：直接命令、cmd.exe /c 包装、带引号等
     */
    public boolean isMediumRisk(String command) {
        if (command == null) return false;
        String normalized = command.toLowerCase().trim();

        for (String pattern : mediumRiskPatterns) {
            String patternLower = pattern.toLowerCase();

            // 1. 直接以危险命令开头
            if (normalized.startsWith(patternLower)) {
                return true;
            }

            // 2. 命令中间包含（前后有空格）
            if (normalized.contains(" " + patternLower + " ")) {
                return true;
            }

            // 3. cmd.exe /c "危险命令" 格式
            if (normalized.contains("cmd.exe") || normalized.contains("cmd")) {
                if (normalized.contains(patternLower)) {
                    return true;
                }
            }

            // 4. bash -c "危险命令" 格式
            if (normalized.contains("bash") && normalized.contains("-c")) {
                if (normalized.contains(patternLower)) {
                    return true;
                }
            }

            // 5. 引号包裹的危险命令
            if (normalized.contains("\"" + patternLower) || normalized.contains(patternLower + "\"")) {
                return true;
            }
        }
        return false;
    }

    /**
     * 添加高危规则
     */
    public void addHighRiskPattern(String pattern) {
        highRiskPatterns.add(pattern.toLowerCase());
    }

    /**
     * 添加中危规则
     */
    public void addMediumRiskPattern(String pattern) {
        mediumRiskPatterns.add(pattern.toLowerCase());
    }

    /**
     * 获取高危规则（用于 SecurityInterceptor）
     */
    public Set<String> getHighRiskPatterns() {
        return Collections.unmodifiableSet(highRiskPatterns);
    }

    /**
     * 获取中危规则（用于 SecurityInterceptor）
     */
    public Set<String> getMediumRiskPatterns() {
        return Collections.unmodifiableSet(mediumRiskPatterns);
    }

    /**
     * 获取工具特定规则
     */
    public List<String> getToolSpecificRules(String toolName) {
        return toolSpecificRules.getOrDefault(toolName, Collections.emptyList());
    }
}