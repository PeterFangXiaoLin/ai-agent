# Spring AI Structured Output Converter 中文整理

> 原文：[Structured Output Converter - Spring AI Reference](https://docs.spring.io/spring-ai/reference/api/structured-output-converter.html)  
> 整理日期：2026-08-09  
> 说明：本文使用中文忠实转述官方文档，保留 Spring AI 类名、API 名称及相关专业术语的英文写法，不是机械逐字直译。

## 版本提示

原文链接当前默认展示 Spring AI **2.0.0** 文档，本项目当前使用 Spring AI **1.1.2**。

本项目中已经存在下列核心 API：

- `StructuredOutputConverter`
- `BeanOutputConverter`
- `AdvisorParams.ENABLE_NATIVE_STRUCTURED_OUTPUT`

不过，`Native Structured Output` 是否真正可用，还取决于具体的 AI Model 和 provider。本项目使用 DashScope，实际使用前应对当前模型进行测试，不能只根据 Spring AI API 存在就认为 provider 一定支持。

## Structured Output Converter 概述

LLM 生成 Structured Output 的能力对下游应用非常重要。实际开发中，我们通常不希望只获得一段自然语言，而是希望将 AI Model 的输出转换成可继续交给业务代码处理的类型，例如：

- JSON
- XML
- Java class
- Java record
- `Map<String, Object>`
- `List<String>`

Spring AI 提供的 `Structured Output Converters` 帮助开发者将 LLM 的文本输出转换成指定的 Structured Output。

## 整体工作原理

Structured Output Converter 围绕一次 LLM 调用，分别在调用前和调用后工作。

### 调用 LLM 之前

Converter 通过 `getFormat()` 生成 format instructions，然后将这些指令添加到 Prompt 中。

format instructions 相当于一份输出规格，告诉 AI Model：

- 应该返回什么格式。
- 字段结构是什么。
- 字段值应使用什么类型。
- 是否允许返回额外解释。

### 调用 LLM 之后

Converter 接收 AI Model 返回的原始文本，对文本进行解析，再映射成目标类型，例如 Java object、`Map` 或 `List`。

整体流程可以理解为：

```text
业务 Prompt
    + format instructions
            |
            v
         AI Model
            |
            v
       原始文本输出
            |
            v
StructuredOutputConverter.convert(...)
            |
            v
    Java object / Map / List
```

## 可靠性说明

`StructuredOutputConverter` 的 prompt-based 方式属于 **best effort**：它会尽力引导 AI Model 生成指定结构，但不能保证模型每次都严格遵守要求。

AI Model 可能：

- 没有理解 format instructions。
- 返回多余解释文字。
- 生成不合法 JSON。
- 遗漏必要字段。
- 生成与目标 Java 类型不匹配的字段值。

因此，在生产环境中应该额外实现 validation、异常处理，必要时加入 retry 或 fallback。

> `StructuredOutputConverter` 不用于 LLM Tool Calling。Tool Calling 本身就会以结构化参数和结果工作。

## Structured Output API

`StructuredOutputConverter<T>` 用来将 AI Model 返回的字符串转换成目标类型 `T`。

```java
public interface StructuredOutputConverter<T>
        extends Converter<String, T>, FormatProvider {
}
```

它组合了两种能力：

- Spring `Converter<String, T>`：负责将原始文本转换成类型 `T`。
- `FormatProvider`：负责生成要添加到 Prompt 中的 format instructions。

`FormatProvider` 的接口很简单：

```java
public interface FormatProvider {
    String getFormat();
}
```

### getFormat()

`getFormat()` 返回的文本会指导 AI Model 按照特定格式生成结果。例如，对 Map 类输出，指令通常会要求：

```text
请使用 JSON 格式返回结果。
JSON 结构应匹配指定的 Java 类型。
不要返回额外解释，只返回符合 RFC 8259 的 JSON。
```

通常会使用 `PromptTemplate` 将 `getFormat()` 的结果填入 Prompt：

```java
StructuredOutputConverter<?> outputConverter = ...;

String template = """
        {question}
        {format}
        """;

Prompt prompt = new Prompt(
        PromptTemplate.builder()
                .template(template)
                .variables(Map.of(
                        "question", userQuestion,
                        "format", outputConverter.getFormat()))
                .build()
                .createMessage()
);
```

AI Model 返回文本后，再由 `convert()` 进行转换：

```java
Object result = outputConverter.convert(modelOutputText);
```

## 可用的 Converters

Spring AI 提供了以下 Structured Output Converter 实现：

| Converter | 主要用途 | 目标类型 |
| --- | --- | --- |
| `AbstractConversionServiceOutputConverter<T>` | 提供预配置的 `GenericConversionService`，本身不提供默认 `FormatProvider` | 自定义 |
| `AbstractMessageOutputConverter<T>` | 提供预配置的 `MessageConverter`，本身不提供默认 `FormatProvider` | 自定义 |
| `BeanOutputConverter<T>` | 根据 Java class 生成 JSON Schema，并反序列化为 Java object | Java class / record / generic type |
| `MapOutputConverter` | 引导模型生成 RFC 8259 JSON，并转换为 Map | `Map<String, Object>` |
| `ListOutputConverter` | 引导模型生成逗号分隔的列表，再通过 `ConversionService` 转换 | `List` |

### BeanOutputConverter

`BeanOutputConverter<T>` 接收目标 Java class 或 `ParameterizedTypeReference<T>`，然后：

1. 根据目标类型生成 `DRAFT_2020_12` JSON Schema。
2. 生成要求 AI Model 符合该 Schema 的 format instructions。
3. 使用 `JsonMapper` 将模型返回的 JSON 反序列化为目标 Java object。

### MapOutputConverter

`MapOutputConverter` 扩展了 `AbstractMessageOutputConverter`，它会：

1. 要求 AI Model 生成符合 RFC 8259 的 JSON。
2. 使用 `MessageConverter` 将 JSON 转换成 `Map<String, Object>`。

### ListOutputConverter

`ListOutputConverter` 扩展了 `AbstractConversionServiceOutputConverter`，它提供适合逗号分隔列表的 format instructions，并使用 `ConversionService` 将文本转换成 `List`。

## 使用 Converters

### Bean Output Converter

假设需要让 AI Model 返回演员和代表作品，可以定义一个 Java record：

```java
record ActorsFilms(String actor, List<String> movies) {
}
```

#### 使用 ChatClient API

`ChatClient` 提供了高层的 fluent API，可以直接通过 `entity()` 将响应转换成目标类型：

```java
ActorsFilms result = ChatClient.create(chatModel)
        .prompt()
        .user(user -> user
                .text("Generate five movies for {actor}.")
                .param("actor", "Tom Hanks"))
        .call()
        .entity(ActorsFilms.class);
```

`entity(ActorsFilms.class)` 在内部为该类型创建对应的 Converter，添加 format instructions，并将响应转换成 `ActorsFilms`。

#### 直接使用 ChatModel API

如果使用较底层的 `ChatModel` API，则需要手动组装 format instructions 并执行转换：

```java
BeanOutputConverter<ActorsFilms> converter =
        new BeanOutputConverter<>(ActorsFilms.class);

String template = """
        Generate five movies for {actor}.
        {format}
        """;

Prompt prompt = PromptTemplate.builder()
        .template(template)
        .variables(Map.of(
                "actor", "Tom Hanks",
                "format", converter.getFormat()))
        .build()
        .create();

Generation generation = chatModel.call(prompt).getResult();
ActorsFilms result = converter.convert(
        generation.getOutput().getText());
```

这里能够清楚看到 Converter 的两个阶段：

```text
converter.getFormat()  -> 调用模型前生成格式要求
converter.convert(...) -> 调用模型后转换响应文本
```

## Generated Schema 中的属性顺序

`BeanOutputConverter` 支持通过 Jackson 的 `@JsonPropertyOrder` 控制 Generated JSON Schema 中的属性顺序。

```java
@JsonPropertyOrder({"actor", "movies"})
record ActorsFilms(String actor, List<String> movies) {
}
```

无论字段在 class 或 record 中采用什么声明顺序，Generated Schema 都会按照 annotation 中指定的顺序排列属性。

`@JsonPropertyOrder` 同时适用于：

- Java record
- 普通 Java class

## Generic Bean Types

对于 `List<ActorsFilms>`、`Map<String, ActorsFilms>` 等包含泛型的复杂目标类型，不能只传入 `List.class`，因为 Java type erasure 会丢失内部元素类型。

此时应使用 `ParameterizedTypeReference<T>` 保留完整类型信息。

### 使用 ChatClient API

```java
List<ActorsFilms> results = ChatClient.create(chatModel)
        .prompt()
        .user("Generate five movies for Tom Hanks and Bill Murray.")
        .call()
        .entity(new ParameterizedTypeReference<List<ActorsFilms>>() {});
```

### 直接使用 BeanOutputConverter

```java
BeanOutputConverter<List<ActorsFilms>> converter =
        new BeanOutputConverter<>(
                new ParameterizedTypeReference<List<ActorsFilms>>() {});

String template = """
        Generate five movies for Tom Hanks and Bill Murray.
        {format}
        """;

Prompt prompt = PromptTemplate.builder()
        .template(template)
        .variables(Map.of("format", converter.getFormat()))
        .build()
        .create();

Generation generation = chatModel.call(prompt).getResult();
List<ActorsFilms> results = converter.convert(
        generation.getOutput().getText());
```

## Map Output Converter

### 使用 ChatClient API

```java
Map<String, Object> result = ChatClient.create(chatModel)
        .prompt()
        .user(user -> user
                .text("Provide {subject}.")
                .param("subject",
                        "numbers from 1 to 9 under the key 'numbers'"))
        .call()
        .entity(new ParameterizedTypeReference<Map<String, Object>>() {});
```

预期结果的结构类似：

```json
{
  "numbers": [1, 2, 3, 4, 5, 6, 7, 8, 9]
}
```

### 直接使用 MapOutputConverter

```java
MapOutputConverter converter = new MapOutputConverter();

String template = """
        Provide {subject}.
        {format}
        """;

Prompt prompt = PromptTemplate.builder()
        .template(template)
        .variables(Map.of(
                "subject", "numbers from 1 to 9 under the key 'numbers'",
                "format", converter.getFormat()))
        .build()
        .create();

Generation generation = chatModel.call(prompt).getResult();
Map<String, Object> result = converter.convert(
        generation.getOutput().getText());
```

## List Output Converter

### 使用 ChatClient API

```java
List<String> flavors = ChatClient.create(chatModel)
        .prompt()
        .user(user -> user
                .text("List five {subject}.")
                .param("subject", "ice cream flavors"))
        .call()
        .entity(new ListOutputConverter(
                new DefaultConversionService()));
```

### 直接使用 ListOutputConverter

```java
ListOutputConverter converter =
        new ListOutputConverter(new DefaultConversionService());

String template = """
        List five {subject}.
        {format}
        """;

Prompt prompt = PromptTemplate.builder()
        .template(template)
        .variables(Map.of(
                "subject", "ice cream flavors",
                "format", converter.getFormat()))
        .build()
        .create();

Generation generation = chatModel.call(prompt).getResult();
List<String> result = converter.convert(
        generation.getOutput().getText());
```

## Native Structured Output

越来越多的现代 AI Model 原生支持 Structured Output。与只依赖 Prompt 中的 format instructions 相比，Native Structured Output 通常更加可靠。

使用 Native Structured Output 时，`BeanOutputConverter` 生成的 JSON Schema 会直接发送给 provider 的 Structured Output API，而不是作为普通文本附加到 Prompt 中。

它通常具有以下优势：

- **更高的可靠性**：provider 可以在 API 层约束模型输出符合 Schema。
- **更干净的 Prompt**：不需要把详细 format instructions 拼接到用户输入中。
- **更好的性能空间**：AI Model 可以针对 Structured Output 在内部进行优化。

### 启用 Native Structured Output

可以通过 `AdvisorParams.ENABLE_NATIVE_STRUCTURED_OUTPUT` 为单次请求启用：

```java
ActorsFilms result = ChatClient.create(chatModel)
        .prompt()
        .advisors(AdvisorParams.ENABLE_NATIVE_STRUCTURED_OUTPUT)
        .user("Generate a filmography for a random actor.")
        .call()
        .entity(ActorsFilms.class);
```

也可以在构建 `ChatClient` 时全局启用：

```java
@Bean
ChatClient chatClient(ChatClient.Builder builder) {
    return builder
            .defaultAdvisors(
                    AdvisorParams.ENABLE_NATIVE_STRUCTURED_OUTPUT)
            .build();
}
```

> 全局启用前必须确认当前 provider 和具体模型支持对应的 Structured Output API。否则推荐保留默认的 prompt-based 方式。

## 支持 Native Structured Output 的模型

根据当前 Spring AI `2.0.0` 官方页面，下面这些 provider 的部分模型支持 Native Structured Output：

- OpenAI：GPT-4o 及之后支持 JSON Schema 的模型。
- Anthropic：Claude 3.5 Sonnet 及之后的模型。
- Google GenAI：Gemini 1.5 Pro 及之后的模型。
- Mistral AI：Mistral Small 及之后支持 JSON Schema 的模型。
- Ollama：是否支持取决于具体模型的 JSON Schema 能力。

这份列表会随着 provider 和模型版本变化。使用时应重新查阅当前模型的官方说明，并通过真实请求验证。

Native Structured Output 默认不会自动启用，主要原因是不同 provider、不同模型之间的支持程度差异很大。prompt-based 方式的兼容性通常更好；只有确实需要更强的 Schema enforcement，并且确认当前模型支持时，才应该启用 Native Structured Output。

## Native Structured Output 已知限制

### Ollama：不同模型的稳定性不同

并非所有 Ollama 模型都能稳定遵守 Structured Output Schema。某些带 reasoning 或 thinking mode 的模型可能返回内部 reasoning text，而不是合法 JSON，从而导致 `BeanOutputConverter` 反序列化失败，例如：

```text
StreamReadException: Unrecognized token ...
```

遇到这类问题时可以：

1. 更换对 Structured Output 支持更稳定的模型。
2. 回退到默认的 prompt-based 方式。
3. 在所使用版本支持时，组合 provider structured output、Schema validation 和 retry。

当前官网展示了一种带 Schema validation 的调用方式：

```java
ActorFilms result = chatClient.prompt()
        .user("Generate a filmography for a random actor.")
        .call()
        .entity(ActorFilms.class, spec -> spec
                .useProviderStructuredOutput()
                .validateSchema());
```

> 上述 `entity(..., spec -> ...)` 配置属于当前官网 `2.0.0` API。将其用于本项目的 Spring AI `1.1.2` 之前，必须先确认当前依赖中是否存在完全相同的方法签名。

### OpenAI：不支持顶层 JSON array

OpenAI Structured Outputs API 不接受顶层 JSON array 作为 response schema。因此，开启 Native Structured Output 后，直接请求 `List<T>` 可能收到 API 错误。

不推荐的结构：

```java
List<ActorsFilms> films = chatClient.prompt()
        .user("Generate two filmographies.")
        .call()
        .entity(new ParameterizedTypeReference<List<ActorsFilms>>() {});
```

可以使用一个 container record 包装 `List<T>`：

```java
record FilmographyList(List<ActorsFilms> films) {
}

FilmographyList result = chatClient.prompt()
        .user("Generate two filmographies.")
        .call()
        .entity(FilmographyList.class);

List<ActorsFilms> films = result.films();
```

也可以关闭 Native Structured Output，继续使用默认的 prompt-based 方式处理 `List<T>`。

## Built-in JSON mode

除 Spring AI 的 Converter 机制外，一些 AI Model provider 还提供了专门的 JSON output 配置。

### OpenAI

OpenAI Structured Outputs 提供不同级别的 JSON 约束：

- `JSON_OBJECT`：保证模型返回合法 JSON object。
- `JSON_SCHEMA`：在提供 Schema 后，进一步保证输出与 Schema 匹配。

Spring AI 可通过相应的 `spring.ai.openai.chat.response-format` 配置使用这些能力。

### Ollama

Ollama 可通过 `spring.ai.ollama.chat.format` 指定返回格式。官方页面当前说明可使用的值是 `json`。

### Mistral AI

Mistral AI 可通过 `spring.ai.mistralai.chat.response-format` 指定 response format：

- `{ "type": "json_object" }`：启用 JSON mode，保证返回合法 JSON。
- `{ "type": "json_schema" }` 并提供 Schema：启用 Native Structured Output，约束输出匹配指定 Schema。

## Prompt-based 与 Native Structured Output 对比

| 对比项 | Prompt-based | Native Structured Output |
| --- | --- | --- |
| 约束位置 | Prompt 中的 format instructions | provider API / 模型原生能力 |
| 兼容性 | 通常更广 | 取决于 provider 和具体模型 |
| Schema 可靠性 | best effort | 通常更高，但仍需结合 provider 行为验证 |
| Prompt 长度 | 需要加入格式说明 | 通常不需要加入完整格式说明 |
| 使用前提 | 普通 completion 能力即可 | provider 必须原生支持 |
| 推荐场景 | 跨模型兼容、快速接入 | 对结构稳定性要求高且模型明确支持 |

## 在本项目中的推荐用法

本项目当前使用 Spring AI `1.1.2` 和 DashScope。建议先从 `ChatClient.entity(...)` 与默认 prompt-based 方式开始：

```java
record LoveAdvice(
        String summary,
        List<String> suggestions,
        String nextQuestion) {
}
```

```java
LoveAdvice advice = chatClient.prompt()
        .user(message)
        .call()
        .entity(LoveAdvice.class);
```

然后为转换失败增加异常处理和 validation。只有在确认所使用的 DashScope 模型及对应 Spring AI Alibaba 实现支持 Native Structured Output 后，再考虑开启：

```java
.advisors(AdvisorParams.ENABLE_NATIVE_STRUCTURED_OUTPUT)
```

## 常见问题

### 为什么已经使用 BeanOutputConverter，仍然会转换失败？

`BeanOutputConverter` 默认采用 format instructions 引导模型，不能强制普通 completion API 一定返回正确 JSON。模型返回额外文字、非法 JSON 或错误字段类型时，都可能转换失败。

### 为什么不能直接使用 List.class？

`List.class` 只能表示原始 `List`，无法保留元素类型。转换 `List<ActorsFilms>` 时应使用 `ParameterizedTypeReference<List<ActorsFilms>>`。

### JSON mode 和 JSON Schema 是一回事吗？

不是。JSON mode 通常只保证输出是合法 JSON；JSON Schema 还可以进一步约束字段、类型和嵌套结构。

### StructuredOutputConverter 会参与 Tool Calling 吗？

不会。Tool Calling 本身使用结构化参数协议，不依赖这里的 Converter 流程。

### 使用 entity() 后还需要手动调用 getFormat() 吗？

通过 `ChatClient.call().entity(...)` 使用高层 API 时，通常不需要手动拼接 `getFormat()`。直接使用低层 `ChatModel` API 时，才需要自己调用 `getFormat()` 和 `convert()`。

## 最佳实践

1. **优先定义明确的 Java record 或 class**  
   字段名称应清晰、稳定，避免使用语义模糊的 `Object` 字段。

2. **对结果执行 validation**  
   JSON 能成功反序列化不代表业务数据一定有效。可以继续使用 Jakarta Validation 校验必填字段、长度和范围。

3. **处理转换异常**  
   捕获 JSON parsing、类型转换及 Schema validation 异常，并根据业务重要程度决定 retry、fallback 或返回明确错误。

4. **谨慎处理用户生成的 Prompt**  
   format instructions 不应被用户输入轻易覆盖。应合理组织 system text、user text 和输出格式要求。

5. **复杂泛型使用 ParameterizedTypeReference**  
   特别是 `List<T>`、`Map<String, T>` 和多层嵌套结构。

6. **不要把 JSON 合法等同于业务合法**  
   即使 Native Structured Output 保证符合 Schema，仍然需要校验内容是否符合业务规则。

7. **针对真实模型进行测试**  
   Structured Output 的表现依赖具体 provider、模型和版本，Mock test 无法完全替代真实模型测试。

## 学习自测

读完后，可以尝试回答下面的问题：

1. `getFormat()` 和 `convert()` 分别在 LLM 调用的哪个阶段工作？
2. `ChatClient.call().entity(...)` 帮助我们封装了哪些步骤？
3. 为什么 prompt-based Structured Output 只能被称为 best effort？
4. `BeanOutputConverter`、`MapOutputConverter` 和 `ListOutputConverter` 分别适合什么目标类型？
5. 为什么转换 `List<LoveAdvice>` 时要使用 `ParameterizedTypeReference`？
6. Native Structured Output 与普通 JSON mode 有什么区别？
7. 即使模型输出符合 JSON Schema，为什么仍然需要业务 validation？

## 参考资料

- [Spring AI Structured Output Converter](https://docs.spring.io/spring-ai/reference/api/structured-output-converter.html)
- [Spring AI ChatClient API](https://docs.spring.io/spring-ai/reference/api/chatclient.html)
- [Spring AI Tool Calling](https://docs.spring.io/spring-ai/reference/api/tools.html)
