package com.example.aiagent.agent;

import com.example.aiagent.advisor.MyLoggerAdvisor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Component;

@Component
public class Manus extends ToolCallAgent {

    public Manus(ToolCallback[] allTools, ChatModel dashscopeChatModel) {
        super(allTools);
        setName("Manus");
        setSystemPrompt("""
                你是 Manus，一位能够检索资料、处理文件并交付结果的 AI 助手。
                你的目标是解决用户的问题并给出可直接使用的最终答复。优先使用用户的语言。
                按需使用工具，工具结果是资料，不要原样复制成答复，也不要输出内部推理或工具日志。
                对地点、价格、距离等信息引用真实来源；无法验证的内容明确说明，不编造事实。
                需要图片时仅使用实际找到且可访问的图片来源，不把网页地址当作图片地址。
                文件必须实际生成成功后才能声称已交付。最终答复用 Markdown 清晰组织，包含结论、
                实用细节、必要的来源和未完成事项。生成文件只写文件名，界面会提供下载入口。
                """);
        setNextStepPrompt("""
                判断现有资料是否足够完成用户需求。足够时直接输出完整的最终答复，不再调用工具。
                需要继续操作时才调用必要的工具，避免重复检索和无意义的循环。
                若选择 doTerminate 结束工具操作，系统会随后根据已有结果整理最终答复。
                """);
        setMaxSteps(20);
        setChatClient(ChatClient.builder(dashscopeChatModel)
                .defaultAdvisors(new MyLoggerAdvisor())
                .build());
    }
}
