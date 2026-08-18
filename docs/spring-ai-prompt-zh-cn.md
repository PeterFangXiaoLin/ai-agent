# Spring AI Prompts 中文整理

> 原文：[Prompts - Spring AI Reference](https://docs.spring.io/spring-ai/reference/api/prompt.html)  
> 整理日期：2026-08-17  
> 说明：本文使用中文忠实转述官方文档，保留编程、Spring AI 及 AI 领域的专业术语英文写法，不是机械逐字直译。

## 版本提示

原文链接当前默认展示 Spring AI **2.0.0** 文档，本项目当前使用 Spring AI **1.1.2**。

本项目 `1.1.2` 已经包含大部分核心 API：

- `Prompt`
- `Message`
- `MessageType`
- `UserMessage`
- `SystemMessage`
- `AssistantMessage`
- `ToolResponseMessage`
- `PromptTemplate`
- `SystemPromptTemplate`
- `TemplateRenderer`
- `StTemplateRenderer`
- `NoOpTemplateRenderer`
- `ChatOptions`

但根据本项目 `1.1.2` 源码对照，下列 `Prompt` convenience methods 与当前官网 `2.0.0` 存在差异：

- `getLastUserOrToolResponseMessage()` 在本项目 `1.1.2` 中不存在。
- `getSystemMessages()` 在本项目 `1.1.2` 中不存在。
- `getUserMessages()` 在本项目 `1.1.2` 中已经存在。
- `getUserMessage()` 和 `getSystemMessage()` 在本项目 `1.1.2` 中已经存在。

> 在当前项目中编写代码时，应以 Spring AI `1.1.2` 实际 API 为准。

## Prompt 概述

Prompt 是交给 AI Model 的输入，用来引导模型生成特定输出。

Prompt 的设计方式、结构和措辞都会显著影响 AI Model 的 response。即使用户意图相同，不同的 Prompt 也可能导致：

- 不同的准确度。
- 不同的输出格式。
- 不同的推理深度。
- 不同的语气和风格。
- 不同的 token usage。

## 与 Spring MVC View 的类比

在 Spring AI 的较低层 API 中，处理 Prompt 有点类似在 Spring MVC 中处理 View template：

1. 先编写一段包含 placeholder 的 template。
2. 在 runtime 根据 user request 或业务数据填入参数。
3. 将渲染后的完整内容交给 AI Model。

也可以类比为包含 placeholder 的 SQL：

```sql
SELECT * FROM user WHERE id = :id
```

在 PromptTemplate 中，类似的结构是：

```text
Explain the following topic: {topic}
```

然后在 runtime 传入：

```java
Map.of("topic", "Spring AI")
```

## 与 JDBC 的类比

官方文档将本章介绍的基础 Prompt 类比为 JDBC 层级的 API：

- `ChatModel` 类似于 JDK 中的核心 JDBC API。
- `ChatClient` 类似于构建在底层 API 之上的 `JdbcClient`。

`ChatClient` 在 `ChatModel` 之上增加了更高层能力，例如：

- fluent API
- Advisor Chain
- Chat Memory
- RAG
- Structured Output
- Tool Calling
- agentic behavior

## Prompt 结构的演变

AI 领域的 Prompt 结构经历了演变：

1. 最初只是简单 `String`。
2. 后来加入 placeholder 与 `USER:` 等文本标记。
3. 现在大部分 Chat Model API 使用带 role 的 Message 结构。

因此，现代 Prompt 通常不是一条字符串，而是由多个具有不同 role 的 Message 组成。

## API Overview

## Prompt

`ChatModel.call()` 通常接收一个 `Prompt` 实例，并返回 `ChatResponse`。

```java
ChatResponse response = chatModel.call(prompt);
```

`Prompt` 是一个 container，主要保存：

- 按顺序组织的 `List<Message>`
- 当前 request 的 `ChatOptions`

简化结构如下：

```java
public class Prompt implements ModelRequest<List<Message>> {

    private final List<Message> messages;

    private ChatOptions chatOptions;
}
```

每个 `Message` 在 Prompt 中承担不同的 role，表示不同的 content 与 intent。

一个 Prompt 可以同时包含：

- 对 AI Model 的 system instruction。
- 用户当前问题。
- 之前的 `UserMessage`。
- 之前的 `AssistantMessage`。
- Tool Call 与 Tool Response。
- RAG 检索到的 background context。
- model-specific request options。

## 创建 Prompt

### 使用文本

```java
Prompt prompt = new Prompt("Explain Spring AI.");
```

该方式通常会把文本作为 `UserMessage`。

### 使用一组 Message

```java
Message systemMessage =
        new SystemMessage("You are a Java expert.");

Message userMessage =
        new UserMessage("Explain Java records.");

Prompt prompt = new Prompt(
        List.of(systemMessage, userMessage));
```

### 使用 ChatOptions

```java
ChatOptions options = ChatOptions.builder()
        .temperature(0.2)
        .build();

Prompt prompt = new Prompt(
        List.of(userMessage),
        options);
```

> 实际可用 `ChatOptions` builder 与构造方式取决于 Spring AI 版本以及底层 provider。

## Prompt Convenience Methods

`Prompt` 提供了按 role 访问 Message 的 convenience methods。

### getUserMessage()

返回 Prompt 中最后一条 `UserMessage`。当前 `2.0.0` 文档表明，如果不存在 `UserMessage`，则返回空 `UserMessage`。

```java
UserMessage userMessage = prompt.getUserMessage();
```

### getSystemMessage()

返回 Prompt 中第一条 `SystemMessage`。当前 `2.0.0` 文档表明，如果不存在，则返回空 `SystemMessage`。

```java
SystemMessage systemMessage = prompt.getSystemMessage();
```

### getLastUserOrToolResponseMessage()（2.0.0）

返回 Prompt 中最后一条 `UserMessage` 或 `ToolResponseMessage`，对维持 conversation continuity 很有用。

```java
Message message = prompt.getLastUserOrToolResponseMessage();
```

> 本项目 Spring AI `1.1.2` 中没有该方法。

### getUserMessages()

按原始顺序返回 Prompt 中的全部 `UserMessage`。

```java
List<UserMessage> userMessages = prompt.getUserMessages();
```

### getSystemMessages()（2.0.0）

按原始顺序返回 Prompt 中的全部 `SystemMessage`。

```java
List<SystemMessage> systemMessages = prompt.getSystemMessages();
```

> 本项目 Spring AI `1.1.2` 中没有该方法，需要自己从 `prompt.getInstructions()` 或对应 Message 列表中过滤。

## Message

`Message` 表示 Prompt 中的一个有 role 的单元。

`Message` 封装：

- text content
- metadata
- `MessageType`

官方文档中的简化接口如下：

```java
public interface Content {

    String getContent();

    Map<String, Object> getMetadata();
}

public interface Message extends Content {

    MessageType getMessageType();
}
```

> Spring AI 的新旧版本可能同时存在 `getContent()` 与 `getText()` 等命名差异，应根据当前 Message implementation 的实际 API 调用。

## MediaContent

支持 multimodal 的 Message 还会实现 `MediaContent`，用来提供一组 `Media`。

```java
public interface MediaContent extends Content {

    Collection<Media> getMedia();
}
```

`Media` 可能表示：

- image
- audio
- video
- document
- provider 支持的其他 multimodal input

实际可用的 Media type 取决于 AI Model provider。

## Message Role

每条 Message 都会被分配一个 role。role 帮助 AI Model 区分不同 content 的上下文作用和意图。

## System Role

System Role 用于设置 AI Model 的：

- behavior
- response style
- rule
- identity
- security boundary
- output constraint

它类似于在对话开始前交给 AI Model 的 instruction。

```java
SystemMessage systemMessage = new SystemMessage(
        "You are a professional relationship consultant.");
```

## User Role

User Role 表示用户的直接输入，例如：

- question
- command
- statement
- code
- document content
- multimodal input

```java
UserMessage userMessage =
        new UserMessage("How can I improve communication?");
```

## Assistant Role

Assistant Role 表示 AI Model 对用户输入的 response。

`AssistantMessage` 不只是用于显示的答案，它还可以在后续 Prompt 中作为 conversation history，帮助 AI Model 保持：

- coherence
- context continuity
- previous decisions
- previous response style

`AssistantMessage` 也可以包含 Tool Call request。

## Tool / Function Role

Tool / Function Role 表示 Tool Call 执行后返回给 AI Model 的附加信息。

基本流程：

```text
UserMessage
    |
    v
AssistantMessage with Tool Call
    |
    v
Application executes Tool
    |
    v
ToolResponseMessage
    |
    v
AI Model generates final AssistantMessage
```

## MessageType

Spring AI 使用 `MessageType` enum 表示 role：

```java
public enum MessageType {

    USER("user"),

    ASSISTANT("assistant"),

    SYSTEM("system"),

    TOOL("tool");
}
```

| MessageType | 常见 implementation | 用途 |
| --- | --- | --- |
| `SYSTEM` | `SystemMessage` | 设置 instruction 与 behavior |
| `USER` | `UserMessage` | 用户输入 |
| `ASSISTANT` | `AssistantMessage` | AI Model response 或 Tool Call request |
| `TOOL` | `ToolResponseMessage` | Tool execution result |

## PromptTemplate

`PromptTemplate` 是 Spring AI 中用于创建结构化 Prompt 的核心组件。

它帮助开发者：

- 定义可复用 template。
- 声明 placeholder。
- 在 runtime 渲染参数。
- 生成 `String`。
- 生成 `Message`。
- 生成 `Prompt`。
- 添加 `Media`。
- 绑定 `ChatOptions`。

简化结构：

```java
public class PromptTemplate
        implements PromptTemplateActions,
                   PromptTemplateMessageActions {
}
```

## TemplateRenderer

`PromptTemplate` 使用 `TemplateRenderer` 执行参数替换。

```java
public interface TemplateRenderer
        extends BiFunction<String, Map<String, Object>, String> {

    @Override
    String apply(
            String template,
            Map<String, Object> variables);
}
```

`TemplateRenderer` 的输入是：

- template String
- placeholder 与值组成的 `Map<String, Object>`

输出是已经渲染的最终 `String`。

## StTemplateRenderer

Spring AI 默认使用 `StTemplateRenderer`，该 implementation 基于 StringTemplate engine。

默认 placeholder syntax：

```text
{variable}
```

示例：

```java
PromptTemplate template = PromptTemplate.builder()
        .template("Explain {topic} to a {level} developer.")
        .build();

String content = template.render(Map.of(
        "topic", "Spring AI Prompt",
        "level", "beginner"));
```

## NoOpTemplateRenderer

如果 template String 已经是完整内容，不需要任何 template rendering，可以使用 `NoOpTemplateRenderer`。

适用场景：

- Prompt 已经由其他 template engine 生成。
- content 包含大量 `{}` 且不应该被解析。
- 希望保留原始 String。

## 自定义 delimiter

默认 `{}` 在包含 JSON 的 Prompt 中容易与 JSON object syntax 冲突。

可以将 delimiter 改为 `<` 和 `>`：

```java
PromptTemplate promptTemplate = PromptTemplate.builder()
        .renderer(StTemplateRenderer.builder()
                .startDelimiterToken('<')
                .endDelimiterToken('>')
                .build())
        .template("""
                List five movies composed by <composer>.
                """)
        .build();

String prompt = promptTemplate.render(
        Map.of("composer", "John Williams"));
```

这样 Prompt 中的 JSON：

```json
{
  "name": "example"
}
```

不会被错误当成 template placeholder。

## 自定义 TemplateRenderer

如果需要自定义 rendering logic，可以实现 `TemplateRenderer`。

```java
public final class DollarTemplateRenderer
        implements TemplateRenderer {

    @Override
    public String apply(
            String template,
            Map<String, Object> variables) {

        String result = template;

        for (Map.Entry<String, Object> entry : variables.entrySet()) {
            result = result.replace(
                    "${" + entry.getKey() + "}",
                    String.valueOf(entry.getValue()));
        }

        return result;
    }
}
```

> 自定义 renderer 时需要考虑 missing variable、escaping、null value、Prompt Injection 以及性能。不应将上述简化示例直接用于生产环境。

## PromptTemplateStringActions

`PromptTemplateStringActions` 专注于将 template 渲染成最终 `String`。

```java
public interface PromptTemplateStringActions {

    String render();

    String render(Map<String, Object> model);
}
```

### render()

不传入额外变量，将 template 渲染为 String。适合：

- static template
- 变量已经在 Builder 中提供
- 不包含 placeholder 的 content

```java
String text = promptTemplate.render();
```

### render(Map<String, Object>)

使用 `Map` 中的值填入 placeholder。

```java
String text = promptTemplate.render(
        Map.of("topic", "Spring AI"));
```

## PromptTemplateMessageActions

`PromptTemplateMessageActions` 用来根据 template 创建 `Message`。

```java
public interface PromptTemplateMessageActions {

    Message createMessage();

    Message createMessage(List<Media> mediaList);

    Message createMessage(Map<String, Object> model);
}
```

### createMessage()

使用 static content 或 Builder 中已提供的 variable 创建 `Message`。

```java
Message message = promptTemplate.createMessage();
```

### createMessage(List<Media>)

创建包含 text 和 Media 的 multimodal Message。

```java
Message message = promptTemplate.createMessage(mediaList);
```

### createMessage(Map<String, Object>)

使用 runtime variable 渲染 template 并创建 Message。

```java
Message message = promptTemplate.createMessage(
        Map.of("topic", "Chat Memory"));
```

## PromptTemplateActions

`PromptTemplateActions` 专注于创建可以传给 `ChatModel` 的 `Prompt`。

```java
public interface PromptTemplateActions
        extends PromptTemplateStringActions {

    Prompt create();

    Prompt create(ChatOptions modelOptions);

    Prompt create(Map<String, Object> model);

    Prompt create(
            Map<String, Object> model,
            ChatOptions modelOptions);
}
```

### create()

根据 static template 生成 Prompt。

```java
Prompt prompt = promptTemplate.create();
```

### create(ChatOptions)

创建 Prompt，同时设置 request-specific `ChatOptions`。

```java
Prompt prompt = promptTemplate.create(chatOptions);
```

### create(Map<String, Object>)

使用 runtime variable 渲染 template 并创建 Prompt。

```java
Prompt prompt = promptTemplate.create(
        Map.of("topic", "Spring AI"));
```

### create(Map<String, Object>, ChatOptions)

同时传入 template variable 和 `ChatOptions`。

```java
Prompt prompt = promptTemplate.create(
        Map.of("topic", "Spring AI"),
        chatOptions);
```

## PromptTemplate API 对照

| 目标 | 方法 | 返回类型 |
| --- | --- | --- |
| 得到最终文本 | `render()` | `String` |
| 使用 runtime variable 得到文本 | `render(Map)` | `String` |
| 创建 Message | `createMessage()` | `Message` |
| 创建 multimodal Message | `createMessage(List<Media>)` | `Message` |
| 使用 runtime variable 创建 Message | `createMessage(Map)` | `Message` |
| 创建 Prompt | `create()` | `Prompt` |
| 创建带 ChatOptions 的 Prompt | `create(ChatOptions)` | `Prompt` |
| 使用 runtime variable 创建 Prompt | `create(Map)` | `Prompt` |
| 同时使用 variable 和 ChatOptions | `create(Map, ChatOptions)` | `Prompt` |

## Example Usage

## 基础 PromptTemplate 示例

```java
PromptTemplate promptTemplate = new PromptTemplate(
        "Tell me a {adjective} joke about {topic}");

Prompt prompt = promptTemplate.create(Map.of(
        "adjective", adjective,
        "topic", topic));

Generation result = chatModel.call(prompt).getResult();
```

执行过程：

```text
Template
    |
    | adjective + topic
    v
PromptTemplate.create(Map)
    |
    v
Prompt
    |
    v
ChatModel.call(Prompt)
    |
    v
ChatResponse / Generation
```

## 组合 SystemMessage 和 UserMessage

```java
String userText = """
        Explain three famous pirates.
        Write at least one sentence for each pirate.
        """;

Message userMessage = new UserMessage(userText);

String systemText = """
        You are a helpful AI assistant.
        Your name is {name}.
        Reply in the style of {voice}.
        """;

SystemPromptTemplate systemTemplate =
        new SystemPromptTemplate(systemText);

Message systemMessage = systemTemplate.createMessage(Map.of(
        "name", name,
        "voice", voice));

Prompt prompt = new Prompt(
        List.of(systemMessage, userMessage));

List<Generation> results =
        chatModel.call(prompt).getResults();
```

这个示例展示了：

1. `UserMessage` 保存用户任务。
2. `SystemPromptTemplate` 根据 runtime variable 创建 `SystemMessage`。
3. 两条不同 role 的 Message 组成 Prompt。
4. Prompt 交给 `ChatModel`。

## 使用 Resource 保存 Prompt

Spring AI 支持 `org.springframework.core.io.Resource`，因此可以将 Prompt template 放在独立文件中，不需硬编码在 Java source code。

例如创建：

```text
src/main/resources/prompts/system-message.st
```

通过 Spring 注入：

```java
@Value("classpath:/prompts/system-message.st")
private Resource systemResource;
```

创建 `SystemPromptTemplate`：

```java
SystemPromptTemplate systemPromptTemplate =
        new SystemPromptTemplate(systemResource);
```

这样做的优点：

- Prompt 与 Java code 分离。
- 更方便审查和修改 Prompt。
- 避免超长 Java String。
- 方便不同环境使用不同 Prompt。
- 方便编写 Prompt versioning 和 test。

> Prompt template 也是 source code 的一部分，应纳入 version control、code review 和 automated test。

## Prompt Engineering

Prompt Engineering 是 Generative AI 开发中非常关键的工作。Prompt 的质量和结构会直接影响 AI Model 输出的有效性。

一个高质量 Prompt 通常不只是一句问题，而是需要精心设计：

- instruction 是否清晰。
- context 是否充足。
- user input 与 system instruction 是否分离。
- output format 是否明确。
- 是否提供 example。
- 是否存在 Prompt Injection 风险。
- token 数量是否合理。

Prompt Engineering 不是一次完成的工作，而是需要根据 model version、evaluation result 和真实 user input 持续迭代。

## 高质量 Prompt 的核心组成

### Instructions

使用清晰、直接的 instruction 告诉 AI Model 要做什么。

不够明确：

```text
Tell me about Spring AI.
```

更明确：

```text
Explain Spring AI Prompt and PromptTemplate to a Java beginner.
Use three short sections and one code example.
Do not discuss unrelated Spring projects.
```

### External Context

必要时提供背景信息或参考数据，帮助 AI Model 理解当前场景。

External Context 可以来自：

- database
- RAG / VectorStore
- user profile
- Chat Memory
- Tool result
- application state
- document

> External Context 不应被默认当作可信 instruction。从用户或外部 Document 获得的内容可能包含 Prompt Injection。

### User Input

User Input 是用户直接提供的请求或问题，通常应放在 `UserMessage`。

需要避免将未验证 user input 直接拼接到 system instruction 的关键位置。

### Output Indicator

Output Indicator 用来指定期望的输出方式，例如：

- JSON
- Markdown
- table
- code block
- Java record fields
- fixed enum value
- concise text

例如：

```text
Return a JSON object with the fields title and suggestions.
Do not include Markdown code fences or additional explanations.
```

但 prompt-based format instruction 仍然是 best effort。AI Model 可能：

- 在 JSON 前加入解释文本。
- 返回 JSON-like text，但不是合法 JSON。
- 遗漏字段。
- 返回错误类型。

对 Structured Output 要求高的场景，应使用 `BeanOutputConverter`、JSON Schema、validation、retry，或 provider-native Structured Output。

### Examples

在 Prompt 中提供预期 question / answer 样例，能帮助 AI Model 理解输出的结构、风格和意图。

这是 Few-shot Learning 的基础形式之一。

```text
Example input:
The user is unable to communicate calmly after an argument.

Example output:
{"category":"communication","priority":"medium"}

Now classify the following input:
{user_input}
```

## Prompt Engineering 基础技巧

### Text Summarization

将大量文本压缩为简洁 summary，保留关键点和主要信息，省略不重要细节。

### Question Answering

根据已提供的文本和 user question，提取特定 answer。

### Text Classification

将文本分类到预定义 category。通常需要明确：

- 允许的 category 列表。
- 每个 category 的定义。
- 输出格式。
- 无法判断时的 fallback category。

### Conversation

通过将之前的 `UserMessage` 和 `AssistantMessage` 继续加入 Prompt，构建有 context 的多轮对话。

AI Model 本身是 stateless，conversation continuity 通常由 Chat Memory 或应用自己管理。

### Code Generation

根据需求描述生成 code。高质量 Code Generation Prompt 应指定：

- language 与 version
- framework 与 version
- input / output contract
- error handling
- style requirement
- test requirement
- 禁止使用的 dependency 或 API

## Prompt Engineering 高级技巧

### Zero-shot

不提供具体 example，只通过 instruction 要求 AI Model 完成任务。

### Few-shot Learning

在 Prompt 中提供少量 input / output example，帮助 AI Model 理解任务模式。

### Chain-of-Thought

Chain-of-Thought 通过中间 reasoning step 帮助处理复杂问题。

> 对于生产系统，应关注可验证的最终结果、Tool result 和输出结构，不要依赖模型暴露内部 hidden reasoning。

### ReAct（Reason + Act）

ReAct 将 reasoning 与 action 结合：

```text
Model analyzes current state
    |
    v
Model chooses an action / Tool
    |
    v
Application executes Tool
    |
    v
Tool result returns to Model
    |
    v
Model continues until final answer
```

Spring AI 中的 Tool Calling、Advisor 和 agent framework 可用来实现这类 workflow。

## Tokens

Token 是 AI Model 处理文本的基本单位。

AI Model 不是直接以人类看到的完整单词形式处理文本，而是：

```text
Input text
    |
    v
Tokenization
    |
    v
Tokens
    |
    v
AI Model processing
    |
    v
Output tokens
    |
    v
Output text
```

Token 可以是：

- 一个完整单词。
- 单词的一部分。
- punctuation。
- whitespace 的某种组合。
- CJK 文字的一个或多个单元。

官方文档使用了一个粗略估算：一个 token 约等于 3/4 个英文单词。但这只是英文场景下的粗略估算，不同 tokenizer、language 和 model 的结果会明显不同。

## Tokenization

Tokenization 是将文本分解成 token 的过程。不同 AI Model 可能使用不同 tokenizer，因此相同 Prompt 在不同 model 中可能产生不同 token count。

不应使用 Java `String.length()` 作为精确 token count。

## Token 与 Billing

大部分 hosted AI Model 会根据 token usage 计费。通常同时计算：

- input tokens：Prompt、Chat Memory、RAG context、Tool definition 等。
- output tokens：AI Model 生成的 response。

因此，Prompt 越长：

- 每次请求费用越高。
- latency 可能越大。
- context window 占用越多。
- 留给 response 的 token budget 越少。

## Context Window

Context Window 是 AI Model 一次请求能处理的最大 token 范围。通常包含：

```text
SystemMessage tokens
+ UserMessage tokens
+ Chat Memory tokens
+ RAG context tokens
+ Tool definition tokens
+ Tool result tokens
+ Generated output tokens
```

超出 context window 时，provider 可能：

- 直接拒绝请求。
- 自动截断部分内容。
- 根据 provider 行为返回错误。

应只向 AI Model 提供完成当前任务所需的最小有效 context。

例如，询问《Hamlet》时，没有必要把 Shakespeare 的所有作品都放入 Prompt。

## Response Metadata

AI Model response metadata 通常包含 token usage。使用 `ChatClient` 时可以返回 `ChatResponse` 查看更完整信息：

```java
ChatResponse response = chatClient.prompt()
        .user("Explain PromptTemplate")
        .call()
        .chatResponse();
```

实际 metadata 结构与 token usage 字段取决于底层 provider。

## 与本项目 LoveApp 的对照

当前 `LoveApp` 中的 Prompt 组装过程可以理解为：

```java
chatClient.prompt()
        .system(SYSTEM_PROMPT)
        .user(message)
        .advisors(spec -> spec.param(
                ChatMemory.CONVERSATION_ID,
                chatId))
        .call()
        .chatResponse();
```

其中：

| LoveApp 代码 | Prompt 概念 |
| --- | --- |
| `SYSTEM_PROMPT` | system instruction |
| `.system(SYSTEM_PROMPT)` | 生成 `SystemMessage` |
| `.user(message)` | 生成 `UserMessage` |
| `MessageChatMemoryAdvisor` | 将之前 Message 加入 Prompt |
| `MyLoggerAdvisor` | 观察或记录最终 Prompt |
| `.call()` | 选择 synchronous invocation |
| `.chatResponse()` | 触发 request 并返回完整 response |

实际发送给 AI Model 的 Prompt 可能不只包含 `.system()` 和 `.user()` 中直接写入的内容，还可能包含：

- Chat Memory 添加的历史 Message。
- Structured Output Converter 添加的 format instructions。
- RAG Advisor 添加的 Document context。
- Tool definition。
- 其他 Advisor 添加或修改的内容。

## Prompt 的实际组装流程

```text
default SystemMessage
        |
runtime SystemMessage / UserMessage
        |
Chat Memory history
        |
RAG context
        |
Structured Output format instructions
        |
Tool definitions
        |
Other Advisor modifications
        |
        v
Final Prompt + ChatOptions
        |
        v
ChatModel
```

这也说明了为什么 debugging AI request 时，不能只看业务代码中的 user String，还要检查 Advisor 处理后的最终 Prompt。

## 常见问题

### Prompt 就是一个 String 吗？

不是。在现代 Chat Model API 中，Prompt 通常是 `Message` 列表与 `ChatOptions` 组成的 request structure。

### SystemMessage 和 UserMessage 有什么区别？

- `SystemMessage` 设置 AI Model 的角色、行为、规则和输出风格。
- `UserMessage` 表示用户当前的直接请求。

### AssistantMessage 为什么会出现在下一次 Prompt 中？

因为 Chat Model 是 stateless 的。要保持 conversation continuity，应用程序需要将之前的 `AssistantMessage` 与 `UserMessage` 一起重新传入。

### PromptTemplate 和 ChatClient.user(lambda) 有什么关系？

`ChatClient.user(lambda)` 是更高层的 fluent API。它在内部也会使用 template rendering 机制处理 text 和 parameter。`PromptTemplate` 更适合直接使用 `ChatModel` 或需要独立复用 template 的场景。

### 为什么 JSON Prompt 容易和 placeholder 冲突？

因为默认 StringTemplate placeholder 使用 `{}`，而 JSON object 也使用 `{}`。可以改用 `<variable>` 等自定义 delimiter。

### Prompt 要求返回 JSON，为什么仍然得到非法 JSON？

prompt-based format instruction 只是 best effort。应使用 Structured Output Converter、Schema validation、retry 或 provider-native Structured Output 提高稳定性。

### Chat Memory 中的消息是 Prompt 的一部分吗？

是。Memory Advisor 会在当前 request 发送前，将选中的 conversation memory 加入最终 Prompt。

### Prompt 越长是否效果越好？

不一定。无关 context 会增加 token cost、latency 和 distraction，还可能挤压真正有用的 context 与 output token budget。

### Template parameter 能防止 Prompt Injection 吗？

不能。template rendering 只负责替换变量，不等于 security boundary。未信任的 user input 和 external content 仍可能包含攻击指令。

## 最佳实践

1. **明确分离 SystemMessage 和 UserMessage**  
   将稳定的 behavior 和 rule 放在 `SystemMessage`，将用户当前请求放在 `UserMessage`。

2. **使用 PromptTemplate 管理动态内容**  
   避免在 Java source code 中通过复杂字符串拼接构造 Prompt。

3. **复杂 Prompt 使用 Resource 文件**  
   将长 Prompt 放在 `src/main/resources/prompts/` 中，方便 versioning、review 和 test。

4. **JSON 场景考虑更换 delimiter**  
   避免 `{}` 同时被 JSON 和 StringTemplate 使用。

5. **为 Structured Output 增加 validation**  
   不要只依赖 Prompt 中的格式说明。

6. **只提供最小有效 context**  
   过长或无关 Prompt 会增加 cost、latency 和模型干扰。

7. **对 Prompt 做 version control**  
   Prompt 会影响应用行为，应像 code 一样被 review、test 和 versioning。

8. **建立 Prompt evaluation**  
   使用固定 test case 比较 Prompt version，不要只凭单次主观感受调整。

9. **观测 token usage**  
   监控 input / output token、latency 和 cost，并根据业务调整 Prompt。

10. **将 external content 当作 untrusted data**  
    RAG Document、user content 和 Tool result 可能包含 Prompt Injection，不应被当成高优先级 instruction。

11. **升级时检查 Prompt convenience methods**  
    Spring AI `1.1.2` 和 `2.0.0` 的 `Prompt` API 存在差异。

## 学习自测

1. `Prompt` 中主要包含哪两类数据？
2. `SystemMessage`、`UserMessage`、`AssistantMessage` 和 `ToolResponseMessage` 分别表示什么？
3. 为什么 Assistant Role 对 conversation continuity 很重要？
4. `PromptTemplate.render()`、`createMessage()` 和 `create()` 的返回类型有什么区别？
5. `TemplateRenderer` 的输入和输出分别是什么？
6. Prompt 中包含 JSON 时，为什么适合修改 delimiter？
7. `NoOpTemplateRenderer` 适合什么场景？
8. 为什么建议将复杂 Prompt 保存在 Resource 文件中？
9. 高质量 Prompt 通常包含哪几个核心组成部分？
10. prompt-based JSON format 为什么只是 best effort？
11. Token 与普通字符、单词有什么区别？
12. Context Window 中除 user text 外还可能包含哪些数据？
13. Prompt 为什么不是越长越好？
14. `getLastUserOrToolResponseMessage()` 为什么不能直接用在本项目 `1.1.2`？
15. template parameter substitution 为什么不等于 Prompt Injection protection？

## 参考资料

- [Spring AI Prompts](https://docs.spring.io/spring-ai/reference/api/prompt.html)
- [Spring AI ChatClient API](https://docs.spring.io/spring-ai/reference/api/chatclient.html)
- [Spring AI Advisors API](https://docs.spring.io/spring-ai/reference/api/advisors.html)
- [Spring AI Structured Output Converter](https://docs.spring.io/spring-ai/reference/api/structured-output-converter.html)
- [Spring AI Chat Memory](https://docs.spring.io/spring-ai/reference/api/chat-memory.html)
- [Spring AI Tool Calling](https://docs.spring.io/spring-ai/reference/api/tools.html)
