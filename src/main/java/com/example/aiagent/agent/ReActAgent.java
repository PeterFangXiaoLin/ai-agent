package com.example.aiagent.agent;

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
        try {
            boolean shoutAct = thinking();
            if (!shoutAct) {
                return "思考完成 - 无需行动";
            }
            return act();
        } catch (Exception e){
            // 记录异常
            e.printStackTrace();
            return "步骤执行失败: " + e.getMessage();
        }
    }
}
