package com.example.aiagent.agent.model;

import com.fasterxml.jackson.annotation.JsonInclude;

/** 面向界面的事件，工具原始结果只保留在模型上下文中。 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AgentEvent(String type, String id, String title, String status, String content, String url) {

    public static AgentEvent progress(String id, String title, String status) {
        return new AgentEvent("progress", id, title, status, null, null);
    }

    public static AgentEvent answer(String content) {
        return new AgentEvent("answer", null, null, null, content, null);
    }

    public static AgentEvent delta(String content) {
        return new AgentEvent("delta", null, null, null, content, null);
    }

    public static AgentEvent artifact(String fileName, String url) {
        return new AgentEvent("artifact", url, fileName, null, null, url);
    }
}
