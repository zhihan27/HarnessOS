package com.harness.core.tool;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.concurrent.TimeUnit;

/**
 * Bash 命令真实执行器实现
 * 不包含任何安全检查，只负责命令执行
 */
@Component
public class RealBashExecutor implements BashExecutor {

    private static final Logger logger = LoggerFactory.getLogger(RealBashExecutor.class);
    private static final int TIMEOUT_SECONDS = 5;

    @Override
    public String execute(String command) {
        logger.debug("真实执行器执行命令: {}", command);
        return executeCommandWithTimeout(command);
    }

    private String executeCommandWithTimeout(String command) {
        try {
            String[] cmd;
            String osName = System.getProperty("os.name").toLowerCase();

            if (osName.contains("win")) {
                cmd = new String[]{"cmd", "/c", command};
            } else {
                cmd = new String[]{"bash", "-c", command};
            }

            ProcessBuilder processBuilder = new ProcessBuilder(cmd);
            processBuilder.redirectErrorStream(true);

            Process process = processBuilder.start();

            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }

            boolean finished = process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS);

            if (!finished) {
                process.destroyForcibly();
                logger.warn("命令执行超时: {}", command);
                return String.format("[超时] 命令执行超过 %d 秒，已强制终止。", TIMEOUT_SECONDS);
            }

            int exitCode = process.exitValue();

            if (output.length() == 0) {
                return String.format("命令执行成功（退出码: %d），无输出内容。", exitCode);
            }

            return String.format("命令执行结果:\n%s", output.toString().trim());

        } catch (Exception e) {
            logger.error("命令执行异常: {}", e.getMessage(), e);
            return String.format("命令执行失败: %s", e.getMessage());
        }
    }
}