package com.harness.core.tool;

import com.harness.core.security.CommandSemanticAnalyzer;
import com.harness.core.security.SecurityCheckResult;
import com.harness.core.security.SecurityInterceptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 安全 Bash 执行器代理
 * 双层拦截机制：
 * - 高危操作：硬拦截，绝对拒绝（使用 SecurityRules 统一规则）
 * - 中危操作：软询问，用户确认后可执行
 */
@Component
public class SecureBashExecutor implements BashExecutor {

    private static final Logger logger = LoggerFactory.getLogger(SecureBashExecutor.class);

    private final SecurityInterceptor interceptor;
    private final CommandSemanticAnalyzer semanticAnalyzer;
    private final RealBashExecutor realExecutor;

    public SecureBashExecutor(SecurityInterceptor interceptor,
                             CommandSemanticAnalyzer semanticAnalyzer,
                             RealBashExecutor realExecutor) {
        this.interceptor = interceptor;
        this.semanticAnalyzer = semanticAnalyzer;
        this.realExecutor = realExecutor;
    }

    @Override
    public String execute(String command) {
        logger.info("安全代理拦截命令: {}", command);

        // 检查是否是已确认的命令
        if (command.startsWith("CONFIRMED:")) {
            String actualCommand = command.substring("CONFIRMED:".length()).trim();
            logger.info("检测到确认令牌，验证后执行: {}", actualCommand);
            return executeWithHighRiskCheck(actualCommand);
        }

        // 第一层：静态规则检查（使用 SecurityInterceptor）
        SecurityCheckResult result = interceptor.check(command, "bash");
        return handleCheckResult(result, command);
    }

    /**
     * 已确认后执行（跳过软询问，但仍检查高危）
     */
    public String executeConfirmed(String confirmToken, String command) {
        logger.info("用户已确认，验证命令: {}", command);

        // 验证确认令牌
        String expectedToken = "CONFIRM:" + command.hashCode();
        if (!expectedToken.equals(confirmToken)) {
            logger.warn("确认令牌不匹配，拒绝执行");
            return "[令牌错误] 确认令牌不匹配，请重新确认";
        }

        // 已确认，跳过软询问，但仍检查高危硬拦截
        return executeWithHighRiskCheck(command);
    }

    /**
     * 执行前的高危检查（统一使用 SecurityInterceptor）
     */
    private String executeWithHighRiskCheck(String command) {
        // 高危操作即使确认也拒绝
        if (interceptor.isHighRisk(command)) {
            logger.warn("[高危-硬拦截] 即使确认也拒绝: {}", command);
            return "[硬拦截-高危] 此操作属于高危操作，即使确认也无法执行。";
        }

        // 语义分析（第二层）
        if (semanticAnalyzer.isDestructiveCommand(command)) {
            if (interceptor.isHighRisk(command)) {
                return "[语义分析-高危] 发现高危意图，拒绝执行。";
            }
            // 中危语义，已确认，可执行
            logger.info("[语义分析-中危] 用户已确认，允许执行");
        }

        logger.info("检查通过，执行命令: {}", command);
        return realExecutor.execute(command);
    }

    /**
     * 处理检查结果
     */
    private String handleCheckResult(SecurityCheckResult result, String command) {
        switch (result.level()) {
            case BLOCKED:
                // 高危：硬拦截，绝对拒绝
                logger.warn("[高危-硬拦截] 拒绝: {}", command);
                return formatBlockedResult(result);

            case NEED_CONFIRM:
                // 中危：软询问，返回确认提示
                logger.info("[中危-软询问] 需用户确认: {}", command);
                return formatNeedConfirmResult(result, command);

            case SAFE:
                // 第一层通过，进入第二层语义分析
                return performSemanticAnalysis(command);

            default:
                return performSemanticAnalysis(command);
        }
    }

    /**
     * 第二层：语义分析
     */
    private String performSemanticAnalysis(String command) {
        if (semanticAnalyzer.isDestructiveCommand(command)) {
            logger.warn("[语义分析] 发现危险意图: {}", command);

            // 统一使用 SecurityInterceptor 判断高危/中危
            if (interceptor.isHighRisk(command)) {
                return formatBlockedResult(SecurityCheckResult.blocked(
                    "语义分析发现高危意图，系统拒绝执行"
                ));
            } else {
                return formatNeedConfirmResult(
                    SecurityCheckResult.needConfirm("语义分析发现敏感操作", command),
                    command
                );
            }
        }

        logger.info("双层检查通过，执行命令: {}", command);
        return realExecutor.execute(command);
    }

    /**
     * 格式化硬拦截结果
     */
    private String formatBlockedResult(SecurityCheckResult result) {
        return String.format("[硬拦截-高危] %s\n此操作属于高危操作，即使确认也无法执行。", result.reason());
    }

    /**
     * 格式化软询问结果
     */
    private String formatNeedConfirmResult(SecurityCheckResult result, String command) {
        return String.format(
            "[软询问-待确认] %s\n\n命令: %s\n\n如果您确认要执行，请回复:\n\"确认执行命令: %s\"\n\n确认令牌: %s",
            result.reason(),
            command,
            command,
            result.confirmToken()
        );
    }
}