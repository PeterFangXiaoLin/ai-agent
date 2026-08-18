# Spring AI ChatClient API 中文整理

> 原文：[Chat Client API - Spring AI Reference](https://docs.spring.io/spring-ai/reference/api/chatclient.html)  
> 整理日期：2026-08-17  
> 说明：本文使用中文忠实转述官方文档，保留编程、Spring AI 及 AI 领域的专业术语英文写法，不是机械逐字直译。

## 版本提示

原文链接当前默认展示 Spring AI **2.0.0** 文档，本项目当前使用 Spring AI **1.1.2**。

大部分核心 API 在本项目中已经存在，例如：

- `ChatClient.Builder`
- `prompt()`
- `user()` / `system()`
- `call()` / `stream()`
- `content()` / `chatResponse()` / `chatClientResponse()`
- `entity()` / `responseEntity()`
- `metadata()`
- `mutate()`
- `defaultAdvisors()`
- `defaultTools()`

但以下内容主要来自当前 `2.0.0` 官方文档，在本项目 `1.1.2` 中可能不存在或方法签名不同：

- `EntityParamSpec`
- `entity(..., spec -> ...)` 的配置重载
- `useProviderStructuredOutput()`
- `validateSchema()`
- `StructuredOutputValidationAdvisor`
- `ToolCallingAdvisor` 的自动注册配置
- `AdvisorParams.toolCallingAdvisorAutoRegister(false)`
- `ToolAdvisor` / `MemoryAdvisor` marker interface
- 部分多 `ChatModel` 的 auto-configuration 辅助 API

> 在本项目中编写代码时，应以 Spring AI `1.1.2` 的实际 API 为准。文档中标注为 `2.0.0` 的示例不应直接复制。

## ChatClient 概述

`ChatClient` 提供了一套与 AI Model 通信的 **fluent API**，同时支持：

- synchronous programming model
- streaming programming model

`ChatClient` 的核心目标是帮助开发者组装 `Prompt`、调用 AI Model，并将响应转换成所需的类型。

一次典型调用如下：

```java
String content = chatClient.prompt()
        .system("You are a helpful assistant.")
        .user("Explain Spring AI ChatClient.")
        .call()
        .content();
```

这段代码可以拆成：

```text
prompt()  -> 开始构建 Prompt
system()  -> 设置 SystemMessage
user()    -> 设置 UserMessage
call()    -> 选择 synchronous 调用模式
content() -> 触发调用并返回 String
```

## Prompt 与 Message

`ChatClient` fluent API 帮助构建将要传给 AI Model 的 `Prompt`。

从 API 角度看，`Prompt` 主要包含一组 `Message`。常见的两类 Message 是：

### UserMessage

`UserMessage` 表示用户直接输入的内容。

```java
chatClient.prompt()
        .user("What is Spring AI?");
```

### SystemMessage

`SystemMessage` 用来指导 AI Model 的角色、行为、输出风格和限制。

```java
chatClient.prompt()
        .system("You are a senior Java engineer.")
        .user("Explain dependency injection.");
```

Message 中可以包含 placeholder，并在 runtime 使用参数替换。

## ChatOptions

`Prompt` 还可以包含 `ChatOptions`，用于配置 AI Model 调用行为，例如：

- model name
- temperature
- max tokens
- top-p
- provider-specific options

`temperature` 通常用来控制输出的随机性或创造性。具体可用选项取决于当前 `ChatModel` provider。

## 创建 ChatClient

`ChatClient` 通过 `ChatClient.Builder` 创建。Builder 可以来自 Spring Boot auto-configuration，也可以手动创建。

## 使用 auto-configured ChatClient.Builder

Spring AI Spring Boot starter 通常会自动创建 prototype scope 的 `ChatClient.Builder` bean。

```java
@RestController
class AiController {

    private final ChatClient chatClient;

    AiController(ChatClient.Builder builder) {
        this.chatClient = builder.build();
    }

    @GetMapping("/ai")
    String generate(String userInput) {
        return chatClient.prompt()
                .user(userInput)
                .call()
                .content();
    }
}
```

其中：

- `builder.build()` 创建 `ChatClient`。
- `user(userInput)` 创建 `UserMessage`。
- `call()` 选择 synchronous 调用。
- `content()` 返回 AI Model 生成的文本。

### 为什么优先使用 auto-configured Builder

auto-configured `ChatClient.Builder` 可以携带 Spring Boot 已经配置的：

- Observability
- `ChatClientBuilderCustomizer`
- provider configuration
- default Advisor configuration
- Tool Calling 相关配置

如果直接调用 `ChatClient.create(chatModel)` 或 `ChatClient.builder(chatModel)`，可能绕过部分 auto-configuration。

## 同一 ChatModel 创建多个 ChatClient

auto-configured `ChatClient.Builder` 是 prototype scope，因此每个 injection point 可以获得独立 Builder。

```java
@Configuration
class ChatClientConfig {

    @Bean
    ChatClient defaultChatClient(ChatClient.Builder builder) {
        return builder.build();
    }

    @Bean
    ChatClient loveChatClient(ChatClient.Builder builder) {
        return builder
                .defaultSystem("You are a relationship consultant.")
                .build();
    }
}
```

这两个 `ChatClient` 使用同一类 `ChatModel`，但拥有不同的 default configuration。

## 使用多个 ChatModel

一个应用中可能需要多个 AI Model，例如：

- 复杂 reasoning 使用高能力模型，简单任务使用更快更便宜的模型。
- 主模型不可用时进行 fallback。
- 对不同模型进行 A/B testing。
- 允许用户选择模型。
- 使用不同 specialized model 处理 code generation、creative writing 等任务。

当 Spring Context 中存在多个 `ChatModel` bean 时，通常需要：

- 使用 `@Primary` 指定默认 bean。
- 使用 `@Qualifier` 选择具体 `ChatClient`。
- 必要时自定义 `ChatClient.Builder` bean。

```java
@Service
class MultiModelService {

    private final ChatClient fastClient;
    private final ChatClient reasoningClient;

    MultiModelService(
            @Qualifier("fastChatClient") ChatClient fastClient,
            @Qualifier("reasoningChatClient") ChatClient reasoningClient) {
        this.fastClient = fastClient;
        this.reasoningClient = reasoningClient;
    }
}
```

### ChatClientBuilderConfigurer（2.0.0）

当前官方 `2.0.0` 文档建议，为不同 `ChatModel` 创建 Builder 时使用 `ChatClientBuilderConfigurer`，以便保留已注册的 `ChatClientBuilderCustomizer` 和 Observability。

该配置方式在不同 Spring AI 版本中差异较大，本项目使用前应对照 `1.1.2` 实际 API。

## 多个 OpenAI-compatible Endpoint

对于 OpenAI-compatible API，可以通过不同 `baseUrl`、`apiKey`、model 和 options 创建多个 `OpenAiChatModel`，再为每个 model 创建独立 `ChatClient`。

```java
OpenAiChatModel firstModel = OpenAiChatModel.builder()
        .options(OpenAiChatOptions.builder()
                .baseUrl("https://provider-a.example.com")
                .apiKey(providerAKey)
                .model("model-a")
                .temperature(0.5)
                .build())
        .build();

OpenAiChatModel secondModel = OpenAiChatModel.builder()
        .options(OpenAiChatOptions.builder()
                .baseUrl("https://provider-b.example.com")
                .apiKey(providerBKey)
                .model("model-b")
                .temperature(0.7)
                .build())
        .build();

ChatClient firstClient = ChatClient.builder(firstModel).build();
ChatClient secondClient = ChatClient.builder(secondModel).build();
```

> API key 应从 environment variable 或 Secret Manager 获取，不要硬编码到 source code。

## ChatClient Fluent API

`ChatClient` 提供三种常用的 `prompt` overload。

### prompt()

无参方法用于从空的 request spec 开始组装 user、system、options、Advisor 等内容。

```java
chatClient.prompt()
        .system("You are a helpful assistant.")
        .user("Hello")
        .call()
        .content();
```

### prompt(Prompt prompt)

接收已经构建好的 `Prompt`。适用于业务代码已经使用非 fluent API 构造 `Prompt` 的情况。

```java
Prompt prompt = new Prompt(List.of(
        new SystemMessage("You are a Java expert."),
        new UserMessage("Explain records.")));

String content = chatClient.prompt(prompt)
        .call()
        .content();
```

### prompt(String content)

便捷方法，将传入的 `String` 作为 user text。

```java
String content = chatClient
        .prompt("Tell me a joke")
        .call()
        .content();
```

## ChatClient Response

`ChatClient` 可以根据业务需要返回简单文本、完整 response、执行 context 或 Java entity。

## 返回 String

```java
String content = chatClient.prompt()
        .user("Tell me a joke")
        .call()
        .content();
```

`content()` 适用于只关心 AI 输出文本的场景。

## 返回 ChatResponse

`ChatResponse` 是更完整的 response 结构，可以包含：

- 一个或多个 `Generation`
- finish reason
- model metadata
- token usage
- provider-specific metadata

```java
ChatResponse response = chatClient.prompt()
        .user("Tell me a joke")
        .call()
        .chatResponse();
```

token usage 对于观察请求成本非常重要，因为大部分 hosted AI Model 会根据 token 计费。

## 返回 ChatClientResponse

`ChatClientResponse` 包含：

- `ChatResponse`
- ChatClient execution context

```java
ChatClientResponse response = chatClient.prompt()
        .user("Answer using the knowledge base")
        .call()
        .chatClientResponse();
```

它适合需要访问 Advisor 执行期间产生的附加数据的场景，例如：

- RAG 检索到的 Document
- Advisor Context 数据
- 自定义 Advisor 附加的中间结果

## 返回 Java Entity

`entity()` 使用 Structured Output 能力，将 AI Model 返回的文本转换成 Java type。

假设定义：

```java
record ActorFilms(String actor, List<String> movies) {
}
```

可以直接返回该 record：

```java
ActorFilms actorFilms = chatClient.prompt()
        .user("Generate a filmography for a random actor.")
        .call()
        .entity(ActorFilms.class);
```

### Generic Type

当目标类型是 `List<T>` 等 generic type 时，应使用 `ParameterizedTypeReference<T>` 保留完整类型信息。

```java
List<ActorFilms> films = chatClient.prompt()
        .user("Generate filmographies for two actors.")
        .call()
        .entity(new ParameterizedTypeReference<List<ActorFilms>>() {});
```

### 使用 StructuredOutputConverter

```java
BeanOutputConverter<ActorFilms> converter =
        new BeanOutputConverter<>(ActorFilms.class);

ActorFilms result = chatClient.prompt()
        .user("Generate a filmography.")
        .call()
        .entity(converter);
```

> prompt-based Structured Output 属于 best effort。AI Model 不一定每次都生成完全正确的 JSON，生产环境应处理 parsing 失败、validation 失败和 retry。

## EntityParamSpec（2.0.0）

当前官方 `2.0.0` 文档中，`entity()` 的多个 overload 支持传入 `Consumer<EntityParamSpec>`，用于独立配置两种行为。

### Provider-Native Structured Output

默认情况下，Spring AI 将 JSON Schema 作为文本 format instructions 附加到 user message。

`useProviderStructuredOutput()` 会改为通过 provider API 直接传递 Schema constraint：

```java
ActorFilms result = chatClient.prompt()
        .user("Generate a filmography.")
        .call()
        .entity(ActorFilms.class,
                spec -> spec.useProviderStructuredOutput());
```

这要求底层模型支持 `StructuredOutputChatOptions`。不同 provider 和模型的支持程度差异很大，因此该功能不会默认启用。

也可以通过 Advisor parameter 启用：

```java
ActorFilms result = chatClient.prompt()
        .advisors(AdvisorParams.ENABLE_NATIVE_STRUCTURED_OUTPUT)
        .user("Generate a filmography.")
        .call()
        .entity(ActorFilms.class);
```

### Schema Validation with Retry

`validateSchema()` 会在返回 entity 前，根据 generated Schema 验证 AI Model 返回的 JSON。

如果 validation 失败：

1. 将错误信息追加到 user Prompt。
2. 重新调用 AI Model。
3. 最多重试 `maxRepeatAttempts` 次，官方文档默认为 3。

```java
ActorFilms result = chatClient.prompt()
        .user("Generate a filmography.")
        .call()
        .entity(ActorFilms.class,
                spec -> spec.validateSchema());
```

两个选项可以组合：

```java
ActorFilms result = chatClient.prompt()
        .user("Generate a filmography.")
        .call()
        .entity(ActorFilms.class, spec -> spec
                .useProviderStructuredOutput()
                .validateSchema());
```

> 启用 `validateSchema()` 时不支持 Streaming。上述 `EntityParamSpec` API 主要来自 `2.0.0`，本项目 `1.1.2` 中不应直接使用这些 overload。

## responseEntity()

`responseEntity()` 适合同时需要完整 `ChatResponse` 和转换后 Java entity 的场景。

```java
ResponseEntity<ChatResponse, ActorFilms> result =
        chatClient.prompt()
                .user("Generate a filmography.")
                .call()
                .responseEntity(ActorFilms.class);
```

它同时保留：

- model metadata
- token usage
- `Generation`
- Structured Output entity

本项目 Spring AI `1.1.2` 已经提供基础 `responseEntity()` overload，但 `2.0.0` 中带 `EntityParamSpec` 的 overload 可能不存在于 `1.1.2`。

## Streaming Response

`stream()` 返回 asynchronous response，底层基于 Reactor `Flux`。

```java
Flux<String> output = chatClient.prompt()
        .user("Tell me a story")
        .stream()
        .content();
```

返回完整 response chunk：

```java
Flux<ChatResponse> responses = chatClient.prompt()
        .user("Tell me a story")
        .stream()
        .chatResponse();
```

返回带 ChatClient execution context 的 chunk：

```java
Flux<ChatClientResponse> responses = chatClient.prompt()
        .user("Answer using RAG")
        .stream()
        .chatClientResponse();
```

### Streaming Structured Output

当前 `stream()` 没有直接将每个 chunk 转换成完整 Java entity 的便捷方法。通常需要：

1. 使用 `BeanOutputConverter.getFormat()` 生成 format instructions。
2. 调用 `stream().content()`。
3. 聚合所有 chunk。
4. 使用 `converter.convert()` 转换完整文本。

```java
BeanOutputConverter<List<ActorFilms>> converter =
        new BeanOutputConverter<>(
                new ParameterizedTypeReference<List<ActorFilms>>() {});

Flux<String> flux = chatClient.prompt()
        .user(user -> user
                .text("""
                        Generate filmographies.
                        {format}
                        """)
                .param("format", converter.getFormat()))
        .stream()
        .content();

String json = flux.collectList()
        .map(parts -> String.join("", parts))
        .block();

List<ActorFilms> result = converter.convert(json);
```

> `.block()` 会将 reactive flow 转成 blocking 调用。在纯 reactive application 中应尽量使用 `map()` / `flatMap()` 继续处理，而不是直接 `.block()`。

## Prompt Template

`ChatClient` 允许 user text 和 system text 使用 template placeholder，在 runtime 进行参数替换。

```java
String answer = chatClient.prompt()
        .user(user -> user
                .text("List five movies composed by {composer}.")
                .param("composer", "John Williams"))
        .call()
        .content();
```

`ChatClient` 内部使用 `PromptTemplate` 处理 template 参数，并通过 `TemplateRenderer` 执行渲染。

### StTemplateRenderer

Spring AI 默认使用 `StTemplateRenderer`，它基于 StringTemplate engine。

默认 delimiter 是：

```text
{variable}
```

### NoOpTemplateRenderer

如果不希望进行任何 template processing，可以使用 `NoOpTemplateRenderer`。

### 自定义 delimiter

如果 Prompt 中包含 JSON，`{}` 可能同时是 JSON syntax 和 template placeholder syntax，容易冲突。

可以将 delimiter 改为 `<` 和 `>`：

```java
String answer = chatClient.prompt()
        .user(user -> user
                .text("List five movies composed by <composer>.")
                .param("composer", "John Williams"))
        .templateRenderer(StTemplateRenderer.builder()
                .startDelimiterToken('<')
                .endDelimiterToken('>')
                .build())
        .call()
        .content();
```

### ChatClient Template 与 Advisor Template

`ChatClient.templateRenderer()` 只影响直接在 ChatClient request chain 中声明的：

- `.user()`
- `.system()`

它不会自动影响 Advisor 内部使用的 template，例如 `QuestionAnswerAdvisor` 有自己的 template customization 机制。

## call() 的返回类型

| Terminal Method | 返回类型 | 适用场景 |
| --- | --- | --- |
| `content()` | `String` | 只需要文本 |
| `chatResponse()` | `ChatResponse` | 需要 `Generation`、token usage 和 model metadata |
| `chatClientResponse()` | `ChatClientResponse` | 还需要 Advisor execution context |
| `entity(Class<T>)` | `T` | 转换为具体 Java type |
| `entity(ParameterizedTypeReference<T>)` | `T` | 转换为 generic type |
| `entity(StructuredOutputConverter<T>)` | `T` | 使用自定义 Converter |
| `responseEntity(...)` | `ResponseEntity<ChatResponse, T>` | 同时需要完整 response 和 entity |

### call() 不是真正的 Terminal Operation

官方文档特别指出：`call()` 本身并不一定立即调用 AI Model。它主要用来选择 synchronous 调用模式。

真正触发 model invocation 的是后面的 terminal method，例如：

- `content()`
- `chatResponse()`
- `chatClientResponse()`
- `entity()`
- `responseEntity()`

```text
prompt().user(...).call()
    尚未必真正执行请求

prompt().user(...).call().content()
    触发 model invocation
```

## stream() 的返回类型

| Terminal Method | 返回类型 | 说明 |
| --- | --- | --- |
| `content()` | `Flux<String>` | 持续返回生成的文本 chunk |
| `chatResponse()` | `Flux<ChatResponse>` | 持续返回 response chunk 与 metadata |
| `chatClientResponse()` | `Flux<ChatClientResponse>` | 返回 response chunk 和 ChatClient context |

## Message Metadata

`ChatClient` 支持为 `UserMessage` 和 `SystemMessage` 添加 metadata。metadata 可以为 Advisor、Memory 或其他 downstream processing 提供附加 context。

### UserMessage Metadata

逐个添加 key-value：

```java
String response = chatClient.prompt()
        .user(user -> user
                .text("What is the weather?")
                .metadata("messageId", "msg-123")
                .metadata("userId", "user-456")
                .metadata("priority", "high"))
        .call()
        .content();
```

一次添加 `Map`：

```java
Map<String, Object> metadata = Map.of(
        "messageId", "msg-123",
        "userId", "user-456",
        "timestamp", System.currentTimeMillis());

String response = chatClient.prompt()
        .user(user -> user
                .text("What is the weather?")
                .metadata(metadata))
        .call()
        .content();
```

### SystemMessage Metadata

```java
String response = chatClient.prompt()
        .system(system -> system
                .text("You are a helpful assistant.")
                .metadata("version", "1.0")
                .metadata("model", "dashscope"))
        .user("Tell me a joke")
        .call()
        .content();
```

### Default Metadata

```java
@Bean
ChatClient chatClient(ChatClient.Builder builder) {
    return builder
            .defaultSystem(system -> system
                    .text("You are a helpful assistant.")
                    .metadata("assistantType", "general")
                    .metadata("version", "1.0"))
            .defaultUser(user -> user
                    .text("Default user context")
                    .metadata("sessionId", "default-session"))
            .build();
}
```

### Metadata Validation

`ChatClient` 会验证 metadata：

- key 不能是 `null` 或空字符串。
- value 不能是 `null`。
- 传入 `Map` 时，key 和 value 都不能包含 `null`。

下列代码会抛出 `IllegalArgumentException`：

```java
chatClient.prompt()
        .user(user -> user
                .text("Hello")
                .metadata(null, "value"))
        .call()
        .content();
```

metadata 会进入最终构建的 `UserMessage` 或 `SystemMessage`，可以通过：

```java
message.getMetadata()
```

进行读取。这对自定义 Advisor 和检查 conversation history 尤其有用。

## 使用 Default Configuration

在创建 `ChatClient` 时设置 default configuration，可以避免 runtime 代码重复声明相同内容。

## Default System Text

```java
@Configuration
class ChatConfig {

    @Bean
    ChatClient chatClient(ChatClient.Builder builder) {
        return builder
                .defaultSystem(
                        "You are a friendly relationship consultant.")
                .build();
    }
}
```

runtime 只需传入 user text：

```java
String answer = chatClient.prompt()
        .user(message)
        .call()
        .content();
```

## Default System Text with Parameters

default system text 可以声明 placeholder，在每次调用时填入不同值。

```java
@Bean
ChatClient chatClient(ChatClient.Builder builder) {
    return builder
            .defaultSystem(
                    "You are a helpful assistant speaking as {role}.")
            .build();
}
```

```java
String answer = chatClient.prompt()
        .system(system -> system.param("role", "a Java mentor"))
        .user("Explain ChatClient")
        .call()
        .content();
```

## ChatClient.Builder Default Methods

Builder 级别可以配置：

### defaultOptions()

```java
.defaultOptions(chatOptions)
```

可以传入 portable `ChatOptions`，也可以传入 provider-specific options。

### defaultTools()

```java
.defaultTools(new DateTimeTools(), new WeatherTools())
```

为每次请求注册 default Tool。可以包含：

- `ToolCallback`
- `ToolCallbackProvider`
- 包含 `@Tool` method 的 POJO

### defaultToolContext()

```java
.defaultToolContext(Map.of("tenantId", tenantId))
```

为 Tool execution 设置 default context。

### defaultSystem()

支持：

```text
defaultSystem(String text)
defaultSystem(Resource text)
defaultSystem(Consumer<PromptSystemSpec> consumer)
```

### defaultUser()

支持：

```text
defaultUser(String text)
defaultUser(Resource text)
defaultUser(Consumer<UserSpec> consumer)
```

### defaultTemplateRenderer()

```java
.defaultTemplateRenderer(templateRenderer)
```

为 ChatClient template 设置 default renderer。

### defaultAdvisors()

```java
.defaultAdvisors(
        MessageChatMemoryAdvisor.builder(chatMemory).build(),
        QuestionAnswerAdvisor.builder(vectorStore).build())
```

为每次 ChatClient 调用注册 default Advisor。

也可以使用 `Consumer<AdvisorSpec>`：

```java
.defaultAdvisors(spec -> spec
        .advisors(myAdvisor)
        .param("key", "value"))
```

## Runtime Override

Builder default 可以在 runtime 通过不带 `default` 前缀的对应方法补充或覆盖：

```text
defaultOptions(...)          -> options(...)
defaultTools(...)            -> tools(...)
defaultToolContext(...)      -> toolContext(...)
defaultSystem(...)           -> system(...)
defaultUser(...)             -> user(...)
defaultTemplateRenderer(...) -> templateRenderer(...)
defaultAdvisors(...)         -> advisors(...)
```

另外还可以直接添加 Message：

```java
.messages(message1, message2)
```

或：

```java
.messages(List.of(message1, message2))
```

> default 与 runtime configuration 的具体 merge / override 行为取决于配置类型。测试复杂配置时，应检查最终交给 AI Model 的 Prompt 和 options。

## Mutating ChatClient

`mutate()` 可以根据已有 `ChatClient` 或 request spec 的配置创建新 Builder，适合派生新 client，不需重复定义所有配置。

### 从 ChatClient 派生

```java
ChatClient conciseClient = baseChatClient.mutate()
        .defaultSystem("Answer concisely.")
        .build();
```

### 从 ChatClientRequestSpec 派生

`ChatClientRequestSpec.mutate()` 会返回一个基于当前 request settings 初始化的 `ChatClient.Builder`。

> `mutate()` 不是原地修改已有 `ChatClient`，而是创建用于构建新 client 的 Builder。

## Advisors

Advisors API 允许在 AI interaction 前后拦截、修改和增强 request / response。

常见用途包括：

- 添加业务 context。
- 管理 conversation memory。
- 实现 RAG。
- Logging。
- Content Safety。
- Tool Calling。
- 修改 Prompt。
- 处理 response。

AI Model API 是 stateless 的。如果需要它“记住”用户姓名或之前的交互，必须在新请求中重新提供 conversation history。Memory Advisor 可以自动完成这一过程。

## AdvisorSpec

`ChatClient` fluent API 使用 `AdvisorSpec` 配置 Advisor 和 Advisor parameter。

```java
interface AdvisorSpec {
    AdvisorSpec param(String key, Object value);
    AdvisorSpec params(Map<String, Object> params);
    AdvisorSpec advisors(Advisor... advisors);
    AdvisorSpec advisors(List<Advisor> advisors);
}
```

示例：

```java
String answer = chatClient.prompt()
        .advisors(spec -> spec
                .advisors(
                        MessageChatMemoryAdvisor.builder(chatMemory).build(),
                        QuestionAnswerAdvisor.builder(vectorStore).build())
                .param(
                        ChatMemory.CONVERSATION_ID,
                        conversationId))
        .user(userText)
        .call()
        .content();
```

### Advisor Order

Advisor order 非常重要，前一个 Advisor 对 Prompt 或 context 的修改会传递给下一个 Advisor。

在上述示例中：

1. `MessageChatMemoryAdvisor` 先将 conversation history 加入 Prompt。
2. `QuestionAnswerAdvisor` 再根据当前问题与已添加的 history 执行 retrieval。

需要精确控制顺序时，应同时理解 Advisor 的 `getOrder()` 与 Chain 的 stack-like 执行方式。

### ChatMemory.CONVERSATION_ID

当前 `2.0.0` 官方文档要求，使用 Memory Advisor 的每次调用都必须显式传入：

```java
.advisors(spec -> spec.param(
        ChatMemory.CONVERSATION_ID,
        conversationId))
```

否则会抛出 `IllegalArgumentException`。

本项目 Spring AI `1.1.2` 仍存在 `DEFAULT_CONVERSATION_ID`，但为避免不同用户的记忆串话，应始终显式传入唯一 conversation ID。

## Retrieval Augmented Generation

`QuestionAnswerAdvisor` 可以实现基础 RAG 模式，根据 user text 从 `VectorStore` 检索相关 Document，并将检索结果添加到 Prompt。

```java
ChatClient chatClient = ChatClient.builder(chatModel)
        .defaultAdvisors(
                QuestionAnswerAdvisor.builder(vectorStore).build())
        .build();
```

更复杂的 Modular RAG 流程通常使用 `RetrievalAugmentationAdvisor`。

## Logging Advisor

`SimpleLoggerAdvisor` 用于记录 `ChatClient` request 和 response，适合 debugging 和 monitoring。

```java
ChatResponse response = chatClient.prompt()
        .advisors(new SimpleLoggerAdvisor())
        .user("Tell me a joke")
        .call()
        .chatResponse();
```

建议将 Logging Advisor 放在 Advisor Chain 较后的位置，但实际位置取决于希望记录原始 Prompt 还是经过其他 Advisor 增强后的 Prompt。

开启 DEBUG log：

```properties
logging.level.org.springframework.ai.chat.client.advisor=DEBUG
```

自定义 request / response 日志内容：

```java
SimpleLoggerAdvisor loggerAdvisor = new SimpleLoggerAdvisor(
        request -> "Request: " + request.prompt().getUserMessage(),
        response -> "Response: " + response.getResult(),
        0);
```

> 生产环境中要谨慎记录 Prompt、Chat Memory、RAG Document 和 Tool result，这些数据可能包含隐私、API key 或商业敏感信息。

## Tool Calling

```java
String response = chatClient.prompt()
        .user("What day is tomorrow?")
        .tools(new DateTimeTools())
        .call()
        .content();
```

Tool 可以是：

- `ToolCallback`
- `ToolCallbackProvider`
- 包含 `@Tool` method 的 object

### ToolCallingAdvisor 自动注册（2.0.0）

当前官方 `2.0.0` 文档中，`ChatClient` 默认会自动向 Advisor Chain 注册 `ToolCallingAdvisor`，除非显式禁用。

这样即使 Tool 是在 runtime 由其他 Advisor 动态注入，Tool Call 也可以被正确处理。

默认 order 为：

```text
Ordered.HIGHEST_PRECEDENCE + 300
```

### 全局禁用自动注册（2.0.0）

```properties
spring.ai.chat.client.tool-calling.enabled=false
```

禁用后，Tool definition 仍然会发送给 AI Model，但 AI Model 返回的 Tool Call 不会被自动执行。如需执行，必须自行管理 Tool Calling loop。

### 单次禁用（2.0.0）

```java
String response = chatClient.prompt()
        .user("What day is tomorrow?")
        .tools(new DateTimeTools())
        .advisors(
                AdvisorParams.toolCallingAdvisorAutoRegister(false))
        .call()
        .content();
```

### 自定义 ToolCallingAdvisor（2.0.0）

如果 Chain 中已经存在实现 `ToolAdvisor` 的自定义 Advisor，`ChatClient` 会认为 Tool Calling 已经由用户控制，因此不再自动注册第二个 `ToolCallingAdvisor`。

```java
chatClient.prompt()
        .user("What day is tomorrow?")
        .tools(new DateTimeTools())
        .advisors(customToolAdvisor)
        .call()
        .content();
```

### Tool Calling Configuration（2.0.0）

| Property | Default | 作用 |
| --- | --- | --- |
| `spring.ai.chat.client.tool-calling.enabled` | `true` | 是否自动注册 `ToolCallingAdvisor` |
| `spring.ai.chat.client.tool-calling.advisor-order` | `Ordered.HIGHEST_PRECEDENCE + 300` | 设置 Advisor Chain 中的位置 |

调整 order 时需要考虑：

- 位于 Tool Calling loop 内部的 Advisor 会在每次 Tool iteration 重复执行。
- 位于 loop 外部的 Advisor 每次 user request 只执行一次。

> 本节的自动注册和 marker interface 主要是 Spring AI `2.0.0` 行为。本项目 `1.1.2` 中的 Tool Calling 机制应以当前依赖源码为准。

## Chat Memory

`ChatMemory` 表示 conversation memory，提供：

- 向 conversation 添加 Message。
- 根据 conversation ID 取回 Message。
- 清空 conversation memory。

常用 Memory 策略是 `MessageWindowChatMemory`，它保留有限数量的最近 Message，默认最大数量为 20。

```java
ChatMemory chatMemory = MessageWindowChatMemory.builder()
        .maxMessages(10)
        .chatMemoryRepository(
                new InMemoryChatMemoryRepository())
        .build();
```

### MessageChatMemoryAdvisor

```java
ChatClient chatClient = ChatClient.builder(chatModel)
        .defaultAdvisors(
                MessageChatMemoryAdvisor.builder(chatMemory).build())
        .build();
```

每次调用显式传入 conversation ID：

```java
String answer = chatClient.prompt()
        .user(message)
        .advisors(spec -> spec.param(
                ChatMemory.CONVERSATION_ID,
                conversationId))
        .call()
        .content();
```

### ChatMemoryRepository

`MessageWindowChatMemory` 通过 `ChatMemoryRepository` 存储 conversation memory。官方文档列出的 Repository 包括：

- `InMemoryChatMemoryRepository`
- `JdbcChatMemoryRepository`
- `CassandraChatMemoryRepository`
- `Neo4jChatMemoryRepository`
- `MongoChatMemoryRepository`
- `RedisChatMemoryRepository`

`ChatMemory` 决定保留哪些 Message，`ChatMemoryRepository` 决定这些当前 Memory 存储在哪里。它们不等于业务上的完整 Chat History。

### MemoryAdvisor Marker Interface（2.0.0）

`MemoryAdvisor` 是 `BaseChatMemoryAdvisor` 扩展的 marker interface。`DefaultChatClient` 使用它检测 Advisor Chain 中是否存在 Memory Advisor。

自定义 Memory Advisor 如果没有继承 `BaseChatMemoryAdvisor`，但需要参与这类检测，应实现 `MemoryAdvisor`。

> `MemoryAdvisor` 主要是当前 Spring AI `2.0.0` API，本项目 `1.1.2` 中未提供该 marker interface。

## Implementation Notes

`ChatClient` 同时支持 imperative 和 reactive programming model，这带来了一些实现上的特殊点。

### HTTP Client Configuration

如果自定义 Model 实现的 HTTP client interaction，可能需要同时配置：

- `RestClient`
- `WebClient`

因为 non-streaming 与 streaming 可能使用不同的 client stack。

### Streaming 需要 Reactive Stack

Streaming 只能通过 Reactive stack 实现。即使主应用是 imperative application，如果需要 Streaming，也需要包含 Reactive stack，例如：

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-webflux</artifactId>
</dependency>
```

### Non-Streaming 与 Servlet Stack

当前官方实现中，non-streaming 依赖 Servlet stack。纯 reactive application 如果调用 non-streaming API，可能仍需要：

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-web</artifactId>
</dependency>
```

并且需要接受部分调用是 blocking 的。

### Tool Calling 的 Blocking 特性

Tool Calling 当前是 imperative 的，可能导致 blocking workflow。这也可能导致 Micrometer observation 不完整或断开，例如 ChatClient span 与 Tool Calling span 没有完整连接。

### Advisor 的 Blocking / Non-Blocking 行为

内置 Advisor 在标准 `call()` 中通常执行 blocking operation，在 `stream()` 中执行 non-blocking operation。

Streaming Advisor 使用的 Reactor `Scheduler` 通常可以在各个 Advisor Builder 上配置。

## 与本项目 LoveApp 的对照

当前 `LoveApp` 已经使用了 `ChatClient` 的多项核心能力：

```java
this.chatClient = ChatClient.builder(dashScopeChatModel)
        .defaultSystem(SYSTEM_PROMPT)
        .defaultAdvisors(
                MessageChatMemoryAdvisor.builder(chatMemory).build(),
                new MyLoggerAdvisor())
        .build();
```

对应关系：

| LoveApp 代码 | ChatClient 概念 |
| --- | --- |
| `ChatClient.builder(dashScopeChatModel)` | 绑定 `ChatModel` |
| `.defaultSystem(SYSTEM_PROMPT)` | default `SystemMessage` |
| `.defaultAdvisors(...)` | default Advisor Chain |
| `.prompt()` | 开始构建单次 request |
| `.user(message)` | 添加 `UserMessage` |
| `.advisors(spec -> spec.param(...))` | 传入 conversation ID |
| `.call().chatResponse()` | 获取完整 `ChatResponse` |
| `.call().entity(LoveReport.class)` | Structured Output |

## 常见问题

### call() 和 content() 哪个真正发送请求？

`call()` 主要选择 synchronous mode，`content()`、`chatResponse()`、`entity()` 等 terminal method 才会触发 model invocation。

### content() 和 chatResponse() 应该选哪个？

- 只需文本：`content()`
- 需要 token usage、finish reason 或 provider metadata：`chatResponse()`
- 还需要 Advisor context：`chatClientResponse()`

### entity() 为什么偶尔转换失败？

默认 Structured Output 通常依赖 Prompt 指导 AI Model 生成 JSON，属于 best effort。模型可能返回多余文字、错误字段类型或非法 JSON。

### 为什么 List<T> 需要 ParameterizedTypeReference？

Java type erasure 会让 `List.class` 丢失元素类型。`ParameterizedTypeReference<List<T>>` 能保留完整 generic type 信息。

### Streaming 是否一定是 non-blocking？

Streaming response 基于 Reactive stack，但如果在 processing chain 中调用 `.block()`、执行 blocking Tool 或 blocking Advisor，整体 workflow 仍可能发生 blocking。

### defaultSystem() 后还能在单次 request 中传参吗？

可以。default system text 中可以包含 placeholder，单次 request 通过 `.system(spec -> spec.param(...))` 填入参数。

### templateRenderer() 会改变 Advisor 内部 template 吗？

不会。ChatClient 的 `templateRenderer()` 主要影响 request chain 中直接声明的 user/system template。Advisor 通常有自己的 template configuration。

### 为什么要显式传入 conversationId？

因为 Memory Advisor 需要根据 conversation ID 隔离不同会话。如果不同用户共用同一 ID，可能发生记忆串话和隐私泄漏。

## 最佳实践

1. **优先使用 auto-configured `ChatClient.Builder`**  
   保留 Spring Boot 配置的 Observability 和 customizer。

2. **将通用配置放在 Builder default 中**  
   例如 system text、Advisor、Tool 和常用 options。

3. **单次 request 只声明动态内容**  
   例如 user message、conversation ID 和 runtime parameter。

4. **根据业务需求选择最小 response type**  
   不需要 metadata 时直接使用 `content()`；需要观测数据时使用 `ChatResponse`。

5. **Structured Output 必须处理失败**  
   不要假设 `entity()` 永远成功，应增加 validation、retry 和 fallback。

6. **谨慎设计 Advisor order**  
   Memory、RAG、Logging 和 Safety Advisor 的顺序会影响最终 Prompt 和 response。

7. **始终隔离 conversation ID**  
   conversation ID 应与真实 session 绑定，不能让不同用户共用。

8. **不要在生产日志中无条件输出完整 Prompt**  
   需要进行脱敏、分级和访问控制。

9. **不要在 Reactive chain 中随意 block**  
   `.block()` 会破坏 end-to-end non-blocking 特性。

10. **升级 Spring AI 时重新对照 API**  
    `EntityParamSpec`、Tool Calling auto-registration 和 marker interface 等 API 在 `1.1.x` 和 `2.0.x` 之间存在差异。

## 学习自测

1. `prompt()`、`call()` 和 `content()` 在一次 ChatClient 调用中分别负责什么？
2. `content()`、`chatResponse()` 和 `chatClientResponse()` 的区别是什么？
3. 什么情况应该使用 `entity()`？
4. 为什么 `List<T>` 需要 `ParameterizedTypeReference<T>`？
5. `call()` 为什么不一定是真正的 terminal operation？
6. `defaultSystem()` 和单次 `.system()` 分别适合什么场景？
7. ChatClient 的 `TemplateRenderer` 为什么不会自动影响 Advisor template？
8. Advisor order 为什么会影响 Memory 与 RAG 的结果？
9. `ChatMemory.CONVERSATION_ID` 为什么必须与用户 session 正确绑定？
10. Streaming API 为什么不等于整个 workflow 一定 non-blocking？
11. Spring AI `2.0.0` 中的 `EntityParamSpec` 可以配置哪两类行为？
12. 为什么不应该盲目将 `2.0.0` 示例复制到本项目 `1.1.2`？

## 参考资料

- [Spring AI ChatClient API](https://docs.spring.io/spring-ai/reference/api/chatclient.html)
- [Spring AI Prompt](https://docs.spring.io/spring-ai/reference/api/prompt.html)
- [Spring AI Advisors API](https://docs.spring.io/spring-ai/reference/api/advisors.html)
- [Spring AI Structured Output Converter](https://docs.spring.io/spring-ai/reference/api/structured-output-converter.html)
- [Spring AI Chat Memory](https://docs.spring.io/spring-ai/reference/api/chat-memory.html)
- [Spring AI Tool Calling](https://docs.spring.io/spring-ai/reference/api/tools.html)
- [Spring AI Retrieval Augmented Generation](https://docs.spring.io/spring-ai/reference/api/retrieval-augmented-generation.html)
