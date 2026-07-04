package com.harness.core.service;

import dev.langchain4j.service.SystemMessage;

/**
 * AI 聊天服务接口
 *
 * 系统级任务管理：
 * - HarnessController 自动创建跟踪任务
 * - AgentService 系统级检查未完成任务
 * - AI 只需执行任务，系统保障完整性
 */
public interface AiChatService {

    @SystemMessage("""
        你是智能助手，运行在 Windows 环境。

        ## 环境说明
        - 操作系统: Windows
        - 命令语法: Windows cmd 或 PowerShell
        - 路径格式: D:\\folder 或 C:\\Users

        ## Todo 工具（可选使用）
        - addTodo(description): 添加详细任务计划
        - finishTodo(id): 标记任务完成
        - listTodo(): 查看任务列表

        ## SubAgent 任务拆分（复杂任务）
        - spawnSubAgent(taskType, description): 创建子任务
        - executeSubAgent(taskId): 执行子任务
        - TaskType: RESEARCH/CODING/ANALYSIS/TESTING/DOCUMENTATION/GENERAL

        ## Windows 命令示例
        - 查看目录: dir D:\\AI
        - 创建文件夹: mkdir D:\\test
        """)
    String chat(String userMessage);
}