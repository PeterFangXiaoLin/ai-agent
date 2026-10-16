package com.example.aiagent.agent.model;

/**
 * Agent 运行状态枚举
 */
public enum AgentState {
    /**
     * 空闲
     */
    IDLE,

    /**
     * 运行中
     */
    RUNNING,

    /**
     * 已完成
     */
    FINISHED,

    /**
     * 错误
     */
    ERROR
}
