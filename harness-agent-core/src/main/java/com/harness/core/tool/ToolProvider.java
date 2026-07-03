package com.harness.core.tool;

import dev.langchain4j.agent.tool.Tool;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 工具提供者
 * 提供 AI 可调用的工具集合
 */
@Component
public class ToolProvider {

    private static final DateTimeFormatter DATE_TIME_FORMATTER =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * 获取当前时间
     *
     * @return 当前时间字符串
     */
    @Tool("获取当前系统时间，返回格式：yyyy-MM-dd HH:mm:ss")
    public String getCurrentTime() {
        return LocalDateTime.now().format(DATE_TIME_FORMATTER);
    }

    /**
     * 执行数学计算
     *
     * @param expression 数学表达式，如 "2 + 3 * 4"
     * @return 计算结果
     */
    @Tool("执行数学计算，支持加减乘除和括号，例如：'2 + 3 * 4' 或 '(10 + 5) / 3'")
    public String calculate(String expression) {
        try {
            double result = evaluateExpression(expression.replaceAll("\\s+", ""));
            return String.format("计算结果: %.2f", result);
        } catch (Exception e) {
            return "计算错误: " + e.getMessage();
        }
    }

    /**
     * 获取天气信息（模拟）
     *
     * @param city 城市名称
     * @return 天气信息
     */
    @Tool("查询指定城市的天气信息，返回温度和天气状况")
    public String getWeather(String city) {
        if (city == null || city.trim().isEmpty()) {
            return "请提供城市名称";
        }

        // 模拟天气数据
        int temperature = 15 + (int) (Math.random() * 20);
        String[] conditions = {"晴天", "多云", "小雨", "阴天"};
        String condition = conditions[(int) (Math.random() * conditions.length)];

        return String.format("%s的天气：%s，温度：%d°C", city, condition, temperature);
    }

    /**
     * 简单的表达式计算器
     * 支持 +, -, *, / 和括号
     */
    private double evaluateExpression(String expr) {
        return new Object() {
            int pos = -1, ch;

            void nextChar() {
                ch = (++pos < expr.length()) ? expr.charAt(pos) : -1;
            }

            boolean eat(int charToEat) {
                while (ch == ' ') nextChar();
                if (ch == charToEat) {
                    nextChar();
                    return true;
                }
                return false;
            }

            double parse() {
                nextChar();
                double x = parseExpression();
                if (pos < expr.length()) throw new RuntimeException("意外的字符: " + (char) ch);
                return x;
            }

            double parseExpression() {
                double x = parseTerm();
                for (; ; ) {
                    if (eat('+')) x += parseTerm();
                    else if (eat('-')) x -= parseTerm();
                    else return x;
                }
            }

            double parseTerm() {
                double x = parseFactor();
                for (; ; ) {
                    if (eat('*')) x *= parseFactor();
                    else if (eat('/')) x /= parseFactor();
                    else return x;
                }
            }

            double parseFactor() {
                if (eat('+')) return parseFactor();
                if (eat('-')) return -parseFactor();

                double x;
                int startPos = this.pos;
                if (eat('(')) {
                    x = parseExpression();
                    eat(')');
                } else if ((ch >= '0' && ch <= '9') || ch == '.') {
                    while ((ch >= '0' && ch <= '9') || ch == '.') nextChar();
                    x = Double.parseDouble(expr.substring(startPos, this.pos));
                } else {
                    throw new RuntimeException("意外的字符: " + (char) ch);
                }

                return x;
            }
        }.parse();
    }
}