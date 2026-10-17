package com.example.aiagent.agent;

import com.example.aiagent.agent.model.AgentState;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;

/**
 * ReAct 抽象类
 */
@EqualsAndHashCode(callSuper = true)
@Slf4j
@Data
public abstract class ReActAgent extends BaseAgent {

    /**
     * 当前状态并决定下一步行动
     *
     * @return 是否需要执行行动，true 需要，false 不需要
     */
    public abstract boolean thinking();

    /**
     * 执行行动
     *
     * @return 行动结果
     */
    public abstract String act();

    /**
     * 执行单个步骤：思考 + 行动
     *
     * @return 执行结果
     */
    @Override
    public String step() {
        boolean shouldAct = thinking();
        // 模型不再调用工具时，其文本就是最终答复；取消则交由统一出口结束。
        checkCancellation();
        if (!shouldAct || getState() == AgentState.FINISHED) {
            return getFinalAnswer();
        }
        return act();
    }
}
