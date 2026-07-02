package com.harness.core.interceptor;

import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 防 CC 攻击拦截器
 * 基于 IP 的请求频率限制
 */
@Component
public class RateLimitInterceptor implements HandlerInterceptor {

    // 每个 IP 每分钟最大请求次数
    private static final int MAX_REQUESTS_PER_MINUTE = 20;

    // 每个 IP 每秒最大请求次数
    private static final int MAX_REQUESTS_PER_SECOND = 2;

    // IP 请求计数器（分钟级）
    private final Map<String, AtomicInteger> minuteCounter = new ConcurrentHashMap<>();

    // IP 请求计数器（秒级）
    private final Map<String, AtomicInteger> secondCounter = new ConcurrentHashMap<>();

    // 上次清理时间
    private volatile long lastMinuteCleanup = System.currentTimeMillis();
    private volatile long lastSecondCleanup = System.currentTimeMillis();

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String ip = getClientIp(request);

        // 定期清理计数器
        cleanupCounters();

        // 检查秒级限制
        AtomicInteger secondCount = secondCounter.computeIfAbsent(ip, k -> new AtomicInteger(0));
        if (secondCount.incrementAndGet() > MAX_REQUESTS_PER_SECOND) {
            response.setStatus(429);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"success\":false,\"error\":\"请求过于频繁，请稍后再试\"}");
            return false;
        }

        // 检查分钟级限制
        AtomicInteger minuteCount = minuteCounter.computeIfAbsent(ip, k -> new AtomicInteger(0));
        if (minuteCount.incrementAndGet() > MAX_REQUESTS_PER_MINUTE) {
            response.setStatus(429);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"success\":false,\"error\":\"请求次数超限，请稍后再试\"}");
            return false;
        }

        return true;
    }

    /**
     * 获取客户端真实 IP
     */
    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("WL-Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }

        // 多级代理时取第一个 IP
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }

        return ip;
    }

    /**
     * 定期清理过期计数器
     */
    private void cleanupCounters() {
        long now = System.currentTimeMillis();

        // 每秒清理一次秒级计数器
        if (now - lastSecondCleanup >= 1000) {
            secondCounter.clear();
            lastSecondCleanup = now;
        }

        // 每分钟清理一次分钟级计数器
        if (now - lastMinuteCleanup >= 60000) {
            minuteCounter.clear();
            lastMinuteCleanup = now;
        }
    }
}