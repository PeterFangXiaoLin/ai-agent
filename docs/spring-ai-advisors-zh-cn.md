# Spring AI Advisors API 中文整理

> 原文：[Advisors API - Spring AI Reference](https://docs.spring.io/spring-ai/reference/api/advisors.html#_implementing_an_advisor)  
> 整理日期：2026-08-08  
> 说明：本文使用中文忠实转述官方文档，保留 Spring AI 专业术语和类名的英文写法，不是逐字直译。

## 版本提示

原文链接当前默认展示 Spring AI **2.0.0** 文档，本项目当前使用的是 Spring AI **1.1.2**。两个版本的 Advisor 核心模型基本一致，但某些类、方法或内置 Advisor 可能存在差异。

在本项目中实际编写代码时，应以项目中的 Spring AI `1.1.2` API 为准，不要直接复制其他版本的示例。

## Advisors API 概述

Spring AI Advisors API 提供了一种灵活的拦截机制，用来在 Spring 应用中检查、修改或增强 AI 交互。

Advisor 适合封装可重用的 Generative AI 模式，例如：

- 记录 AI 请求和响应。
- 管理 Chat Memory。
- 在 Prompt 发送前增加额外指令。
- 实现 RAG（Retrieval-Augmented Generation）。
- 在请求发送前进行安全检查。
- 在响应返回后进行观察、转换或异常处理。

Advisor 还可以参与 Spring AI 的 Observability 体系，因此可以观察其执行过程中的 metrics 和 traces。

### 注册内置 Advisor

官方建议在构建 `ChatClient` 时，通过 `defaultAdvisors()` 注册通用 Advisor：

```java
ChatMemory chatMemory = ...;
VectorStore vectorStore = ...;

ChatClient chatClient = ChatClient.builder(chatModel)
        .defaultAdvisors(
                MessageChatMemoryAdvisor.builder(chatMemory).build(),
                QuestionAnswerAdvisor.builder(vectorStore).build()
        )
        .build();
```

调用时可以动态传入 Advisor 参数：

```java
String answer = chatClient.prompt()
        .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, conversationId))
        .user(userText)
        .call()
        .content();
```

## 核心组件

Advisor API 根据调用方式分为两类：

| 场景 | Advisor | Advisor Chain |
| --- | --- | --- |
| Non-Streaming | `CallAdvisor` | `CallAdvisorChain` |
| Streaming | `StreamAdvisor` | `StreamAdvisorChain` |

同时还有两个重要的数据类型：

- `ChatClientRequest`：表示尚可修改的 Prompt 请求。
- `ChatClientResponse`：表示 Chat Completion 响应。

请求和响应中都携带 Advisor Context，它可以在整条 Advisor Chain 中共享状态。

### Advisor 能做什么

`adviseCall()` 和 `adviseStream()` 是自定义 Advisor 的核心入口。它们通常可以：

1. 检查当前 Prompt。
2. 修改或增强 Prompt。
3. 调用 Advisor Chain 中的下一个节点。
4. 不再向后调用，从而阻断请求；此时当前 Advisor 必须自己构造响应。
5. 检查或处理 Chat Completion 响应。
6. 在处理失败时抛出异常。

`getOrder()` 用于决定 Advisor 在 Chain 中的顺序，`getName()` 则返回唯一名称。

## Advisor Chain 执行流程

Spring AI 负责创建 Advisor Chain，并按 `getOrder()` 的值对多个 Advisor 排序。数值越小，请求阶段越早执行。

1. Spring AI 根据用户 Prompt 创建 `ChatClientRequest`，同时创建空的 Advisor Context。
2. 请求依次经过各个 Advisor，每个 Advisor 都可以修改请求或阻断请求。
3. Chain 末尾由 Spring AI 自动添加的 Advisor 将请求发送给 Chat Model。
4. Chat Model 的响应按相反方向通过 Advisor Chain 返回。
5. 每个 Advisor 可以观察或修改响应。
6. Spring AI 从最终的 `ChatClientResponse` 中提取 Chat Completion 并返回给调用方。

## Advisor 执行顺序

Advisor Chain 具有类似调用栈的结构：

- `order` 值越小，优先级越高。
- 优先级最高的 Advisor 最先处理请求，但最后处理响应。
- 优先级最低的 Advisor 最后处理请求，但最先处理响应。
- 多个 Advisor 的 `order` 值相同时，它们之间的先后顺序不受保证。

Spring `Ordered` 提供了两个边界常量：

```java
int HIGHEST_PRECEDENCE = Integer.MIN_VALUE;
int LOWEST_PRECEDENCE = Integer.MAX_VALUE;
```

### 顺序示例

假设有两个 Advisor：

- `AdvisorA.getOrder()` 返回 `0`。
- `AdvisorB.getOrder()` 返回 `100`。

执行顺序是：

```text
请求 -> AdvisorA.before -> AdvisorB.before -> Chat Model
响应 <- AdvisorA.after  <- AdvisorB.after  <- Chat Model
```

换句话说，实际时间顺序是：

```text
AdvisorA 请求前逻辑
AdvisorB 请求前逻辑
Chat Model
AdvisorB 响应后逻辑
AdvisorA 响应后逻辑
```

如果某个需求要在输入和输出两侧都第一个执行，官方建议：

1. 将输入处理和输出处理拆成两个 Advisor。
2. 为它们配置不同的 `order`。
3. 通过 Advisor Context 共享状态。

## API 概览

自定义 Advisor 常用接口位于：

```text
org.springframework.ai.chat.client.advisor.api
```

### Advisor

`Advisor` 继承 Spring 的 `Ordered`，所有 Advisor 都需要提供名称和执行顺序。

```java
public interface Advisor extends Ordered {
    String getName();
}
```

### CallAdvisor

`CallAdvisor` 用于 Non-Streaming 调用：

```java
public interface CallAdvisor extends Advisor {
    ChatClientResponse adviseCall(
            ChatClientRequest request,
            CallAdvisorChain chain);
}
```

自定义实现通常应在 `adviseCall()` 中调用：

```java
chain.nextCall(request)
```

这个调用会把请求交给 Chain 中的下一个 Advisor。如果不调用它，就相当于中断了 Chain。

### StreamAdvisor

`StreamAdvisor` 用于 Streaming 调用：

```java
public interface StreamAdvisor extends Advisor {
    Flux<ChatClientResponse> adviseStream(
            ChatClientRequest request,
            StreamAdvisorChain chain);
}
```

继续 Streaming Chain 时调用：

```java
chain.nextStream(request)
```

## 实现自定义 Advisor

要创建自定义 Advisor，可以实现：

- `CallAdvisor`：支持 Non-Streaming。
- `StreamAdvisor`：支持 Streaming。
- 同时实现两者：同时支持两种调用方式。
- `BaseAdvisor`：当请求前和响应后的处理可以共用时，用它减少重复代码。

> 注意：自定义 `CallAdvisor` 需要实现的方法是 `adviseCall()`，然后在其中调用 `chain.nextCall()`。`nextCall()` 是 Chain 的方法，不是 Advisor 本身的实现方法。

## 示例一：Logging Advisor

Logging Advisor 在调用下一个 Advisor 之前记录 `ChatClientRequest`，在下一个 Advisor 返回后记录 `ChatClientResponse`。

它只观察请求和响应，不修改数据。下面是简化后的 Non-Streaming 实现：

```java
public final class SimpleLoggerAdvisor implements CallAdvisor {

    private static final Logger log =
            LoggerFactory.getLogger(SimpleLoggerAdvisor.class);

    @Override
    public String getName() {
        return SimpleLoggerAdvisor.class.getSimpleName();
    }

    @Override
    public int getOrder() {
        return 0;
    }

    @Override
    public ChatClientResponse adviseCall(
            ChatClientRequest request,
            CallAdvisorChain chain) {

        log.debug("request: {}", request);

        ChatClientResponse response = chain.nextCall(request);

        log.debug("response: {}", response);
        return response;
    }
}
```

关键点：

1. `getName()` 返回 Advisor 的唯一名称。
2. `getOrder()` 控制 Chain 中的顺序，值越小，请求阶段越早执行。
3. `chain.nextCall(request)` 是调用栈的分界点：它之前是请求前逻辑，它之后是响应后逻辑。

### Streaming Logging

Streaming 响应是 `Flux<ChatClientResponse>`，不是一个完整响应。如果想在流结束后查看完整内容，可以使用 `ChatClientMessageAggregator`。

```java
@Override
public Flux<ChatClientResponse> adviseStream(
        ChatClientRequest request,
        StreamAdvisorChain chain) {

    log.debug("request: {}", request);
    Flux<ChatClientResponse> responses = chain.nextStream(request);

    return new ChatClientMessageAggregator()
            .aggregateChatClientResponse(
                    responses,
                    response -> log.debug("response: {}", response));
}
```

`ChatClientMessageAggregator` 适合做日志或对完整响应的只读观察，不能在聚合回调中修改响应。

## 示例二：Re-Reading（Re2）Advisor

Re-Reading（Re2）是一种增强 LLM 推理的 Prompt 技巧。核心做法是让模型重新阅读一次用户问题：

```text
{Input_Query}
Read the question again: {Input_Query}
```

这类需求可以通过 `BaseAdvisor` 实现。`before()` 负责改写用户消息，`after()` 直接返回原响应：

```java
public final class ReReadingAdvisor implements BaseAdvisor {

    private static final String TEMPLATE = """
            {query}
            Read the question again: {query}
            """;

    private int order;

    @Override
    public ChatClientRequest before(
            ChatClientRequest request,
            AdvisorChain chain) {

        String userText = request.prompt().getUserMessage().getText();
        String augmentedText = PromptTemplate.builder()
                .template(TEMPLATE)
                .variables(Map.of("query", userText))
                .build()
                .render();

        return request.mutate()
                .prompt(request.prompt().augmentUserMessage(augmentedText))
                .build();
    }

    @Override
    public ChatClientResponse after(
            ChatClientResponse response,
            AdvisorChain chain) {
        return response;
    }

    @Override
    public int getOrder() {
        return order;
    }

    public ReReadingAdvisor withOrder(int order) {
        this.order = order;
        return this;
    }
}
```

这个示例的重点是：

- `before()` 可以在请求发送给 Chat Model 前改写 Prompt。
- 修改 Spring AI 请求时应优先使用 `mutate()` 构建新对象。
- `after()` 如果不需要处理响应，可以直接返回原响应。
- `order` 仍然会影响它与 Chat Memory、RAG 等其他 Advisor 的组合顺序。

## Spring AI 内置 Advisors

### Chat Memory Advisors

#### MessageChatMemoryAdvisor

`MessageChatMemoryAdvisor` 从 Chat Memory 中获取历史消息，然后以消息列表的方式加入 Prompt。

这种方式可以保留对话历史的 `UserMessage` / `AssistantMessage` 结构，但不是所有 AI Model 都支持这种消息结构。

#### VectorStoreChatMemoryAdvisor

`VectorStoreChatMemoryAdvisor` 从 VectorStore 中搜索相关记忆，并将结果添加到 Prompt 的 system text 中。它适合从大量历史数据中检索相关信息。

### Question Answering Advisors

#### QuestionAnswerAdvisor

`QuestionAnswerAdvisor` 使用 VectorStore 实现基础的问答能力，对应 Naive RAG 模式。

#### RetrievalAugmentationAdvisor

`RetrievalAugmentationAdvisor` 使用 `org.springframework.ai.rag` 包中的组件实现常见 RAG 流程，遵循 Modular RAG Architecture。

### Reasoning Advisor

#### ReReadingAdvisor

`ReReadingAdvisor` 实现 Re2 重读策略，通过在输入阶段让 LLM 再次阅读问题，提高对问题的理解和推理能力。

### Tool Calling Advisor

#### ToolCallingAdvisor

`ToolCallingAdvisor` 在 Advisor Chain 中处理 Tool Calling 循环。它会执行模型请求的 Tool Call，把 Tool 结果再发回模型，并持续迭代，直到模型不再请求调用 Tool。

在当前官方文档中，除非显式禁用，否则 `ChatClient` 会自动注册它。它实现了 `ToolAdvisor` marker interface，用来避免自动重复注册第二个 `ToolCallingAdvisor`。

### Content Safety Advisor

#### SafeGuardAdvisor

`SafeGuardAdvisor` 是一个基础安全 Advisor，用来防止模型生成有害或不合适的内容。

## Streaming 与 Non-Streaming

### Non-Streaming

Non-Streaming Advisor 一次处理完整的请求和响应，理解起来更接近普通的同步方法调用：

```text
ChatClientRequest -> adviseCall() -> ChatClientResponse
```

### Streaming

Streaming Advisor 处理持续的数据流，使用 Reactor 的 `Flux` 等概念：

```java
@Override
public Flux<ChatClientResponse> adviseStream(
        ChatClientRequest request,
        StreamAdvisorChain chain) {

    return Mono.just(request)
            .publishOn(Schedulers.boundedElastic())
            .map(currentRequest -> {
                // 调用下一个 Advisor 前的处理
                return currentRequest;
            })
            .flatMapMany(chain::nextStream)
            .map(response -> {
                // 调用下一个 Advisor 后的处理
                return response;
            });
}
```

对 Streaming Advisor 要特别注意：

- 响应可能被分成多个 chunk。
- 不要在 Reactor 线程上直接执行长时间 blocking 操作。
- 如果需要完整响应才能执行逻辑，需要先明确是否应该聚合响应。
- 聚合会影响“边生成边显示”的 Streaming 体验，因此要谨慎使用。

## 最佳实践

1. **一个 Advisor 只聚焦一类职责**  
   例如将日志、Chat Memory、RAG、安全校验分别放在不同 Advisor 中，以保持模块化。

2. **必要时通过 Advisor Context 共享状态**  
   不要依赖不安全的全局可变状态在 Advisor 之间传递数据。

3. **需要时同时支持 Streaming 和 Non-Streaming**  
   如果 Advisor 会被多种类型的 `ChatClient` 调用复用，应考虑同时实现 `CallAdvisor` 和 `StreamAdvisor`。

4. **认真设计 `order`**  
   Prompt 增强、Chat Memory、RAG 和 Logging 的顺序会影响最终请求以及日志中能看到的数据。

5. **注意 Chain 必须显式继续**  
   对于普通的拦截逻辑，不要忘记调用 `chain.nextCall(request)` 或 `chain.nextStream(request)`。只有在确实需要阻断请求并自行构造响应时，才应该停止 Chain。

6. **优先使用不可变对象的构建方式**  
   修改 `ChatClientRequest` 或 Prompt 时，使用 Spring AI 提供的 `mutate()` / builder API，不要假设对象内部可以直接修改。

## API 变更摘要

Advisor API 在 Spring AI 早期版本中发生过多次命名和结构变化：

### Advisor 接口变化

- Spring AI `1.0 M2` 曾分别提供 `RequestAdvisor` 和 `ResponseAdvisor`。
- Spring AI `1.0 M3` 将它们替换为 `CallAroundAdvisor` 和 `StreamAroundAdvisor`。
- Spring AI `1.0.0` 又将这些类型统一调整为现在常见的命名：
  - `CallAroundAdvisor` -> `CallAdvisor`
  - `StreamAroundAdvisor` -> `StreamAdvisor`
  - `CallAroundAdvisorChain` -> `CallAdvisorChain`
  - `StreamAroundAdvisorChain` -> `StreamAdvisorChain`
  - `AdvisedRequest` -> `ChatClientRequest`
  - `AdivsedResponse` -> `ChatClientResponse`

因此，搜索网上教程时，如果看到 `CallAroundAdvisor` 或 `AdvisedRequest`，通常说明示例基于早期 milestone 版本，不应直接复制到当前项目。

### Context Map 变化

- `1.0 M2` 中，Context Map 是独立的可变方法参数。
- `1.0 M3` 之后，Context Map 成为 Request / Response 对象的一部分，并转为不可变结构。
- 更新 Context 时应通过对应的 Context 更新 API 创建新对象，而不是原地修改 Map。

## 学习自测

读完后，如果能回答下面几个问题，就已经掌握了自定义 Advisor 的核心：

1. `adviseCall()` 中，`chain.nextCall(request)` 前后分别对应什么阶段？
2. 为什么 `order` 值最小的 Advisor 最先处理请求，却最后处理响应？
3. 如果忘记调用 `chain.nextCall()`，会发生什么？
4. 什么情况适合直接实现 `CallAdvisor`，什么情况适合使用 `BaseAdvisor`？
5. Streaming 响应为什么不能完全照搬 Non-Streaming 的处理方式？

如果这些问题仍然不好理解，下一步可以直接在本项目中实现一个 `SimpleLoggerAdvisor`，再通过断点观察 Request 进入和 Response 返回的顺序。

## 参考资料

- [Spring AI Advisors API](https://docs.spring.io/spring-ai/reference/api/advisors.html)
- [Spring AI Chat Client API](https://docs.spring.io/spring-ai/reference/api/chatclient.html)
- [Spring AI Chat Memory](https://docs.spring.io/spring-ai/reference/api/chat-memory.html)
