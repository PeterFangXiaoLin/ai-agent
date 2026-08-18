# Spring AI Chat Memory 中文整理

> 原文：[Chat Memory - Spring AI Reference](https://docs.spring.io/spring-ai/reference/api/chat-memory.html)  
> 整理日期：2026-08-10  
> 说明：本文使用中文忠实转述官方文档，保留 Spring AI 类名、API 名称及相关专业术语的英文写法，不是机械逐字直译。

## 版本提示

原文链接当前默认展示 Spring AI **2.0.0** 文档，本项目当前使用 Spring AI **1.1.2**。核心抽象基本一致，但部分内置 Repository、配置项和消息淘汰逻辑存在版本差异。

### 本项目最需注意的差异

Spring AI `2.0.0` 的 `MessageWindowChatMemory` 会尽量在完整 **turn boundary** 上淘汰消息，避免从一轮对话的中间截断。

本项目使用的 Spring AI `1.1.2` 仍然是按单条 `Message` 从旧到新淘汰。因此：

- `maxMessages(1)` 表示最多保留 1 条 `Message`，不是 1 个对话轮次。
- 每轮结束后，很可能只保留最新的 `AssistantMessage`。
- 下一轮仍可以从该 `AssistantMessage` 中读到上一轮提及的信息。

这也是之前将 `maxMessages` 设置为 `1` 后，模型仍然知道“小红”的原因。

## Chat Memory 概述

LLM 本身是 **stateless** 的。每次请求对模型来说都可以是一次全新调用，它不会天然保留之前对话的信息。

如果需要在多次调用中保持 context 或 state，应用程序必须在发起新请求时，重新向 LLM 提供相关信息。

Spring AI 为此提供了 Chat Memory 功能，用于在多轮 LLM 交互之间保存和取回必要消息。

## Chat Memory 与 Chat History

选择 Memory 类型前，首先要区分两个概念。

### Chat Memory

Chat Memory 是 LLM 在当前对话中为了保持 context awareness 而需要看到的信息。

它的关注点是：

- 当前请求最需要哪些历史信息。
- 哪些旧消息可以淘汰。
- 怎样将记忆控制在 model context window 之内。
- 如何避免无关信息干扰当前回答。

### Chat History

Chat History 是用户与模型之间交换过的完整消息记录。

它通常用于：

- 在界面上显示完整对话。
- 用户审计和客服工单。
- 数据分析。
- 业务归档。
- 合规保留。

### 为什么不应混为一谈

`ChatMemory` 专门管理与当前 context 相关的消息，可以按策略删除过时或超出限制的消息。它不是保存完整 Chat History 的最佳选择。

如果业务必须保留所有对话，应将 Chat History 独立存储，例如通过 Spring Data 保存到数据库。

```text
Chat History: 业务上完整、可审计的对话记录
Chat Memory : 当前 LLM 请求所需的有限 context
```

## 核心职责划分

Spring AI 将 Memory 策略和底层存储拆分成两层。

| 抽象 | 职责 | 典型问题 |
| --- | --- | --- |
| `ChatMemory` | 决定保留哪些消息、何时淘汰消息 | 保留最近 N 条、按 token limit 淘汰、按 TTL 删除 |
| `ChatMemoryRepository` | 负责存储和取回消息 | 存内、JDBC、Cassandra、Neo4j、MongoDB、Redis |

也就是说：

```text
ChatMemory           = 策略层
ChatMemoryRepository = 存储层
```

它们不是同一个概念。自定义“怎样记住”通常实现 `ChatMemory`；自定义“存到哪里”通常实现 `ChatMemoryRepository`。

## ChatMemory API

在本项目的 Spring AI `1.1.2` 中，`ChatMemory` 接口的核心结构如下：

```java
public interface ChatMemory {

    String DEFAULT_CONVERSATION_ID = "default";
    String CONVERSATION_ID = "chat_memory_conversation_id";

    default void add(String conversationId, Message message) {
        add(conversationId, List.of(message));
    }

    void add(String conversationId, List<Message> messages);

    List<Message> get(String conversationId);

    void clear(String conversationId);
}
```

### add()

将一条或多条 `Message` 加入指定 conversation。自定义 Memory 可以在此方法中执行：

- 去重。
- 淘汰旧消息。
- token 计算。
- 时间过期判断。
- 保留 `SystemMessage`。
- 向 `ChatMemoryRepository` 保存处理后的消息。

### get()

获取指定 conversation 的当前 Memory。返回的消息顺序通常应是从旧到新，以便直接交给 LLM。

### clear()

清空指定 conversation 的 Memory，不应该影响其他 conversation。

## ChatMemoryRepository API

Spring AI `1.1.2` 中的接口结构如下：

```java
public interface ChatMemoryRepository {

    List<String> findConversationIds();

    List<Message> findByConversationId(String conversationId);

    void saveAll(String conversationId, List<Message> messages);

    void deleteByConversationId(String conversationId);
}
```

### findConversationIds()

返回 Repository 中的 conversation ID 列表。

### findByConversationId()

查询指定 conversation 的所有当前 Memory 消息。

### saveAll()

用传入的消息列表替换该 conversation 已有的全部消息。

> `saveAll()` 不是单纯 append。实现自定义 Repository 时，必须留意它的 replacement 语义。

### deleteByConversationId()

删除指定 conversation 的 Memory。

## Quick Start

Spring AI 可以自动配置一个 `ChatMemory` bean，在应用中可以直接注入：

```java
@Autowired
ChatMemory chatMemory;
```

默认组合是：

- `MessageWindowChatMemory`：负责 Memory 策略。
- `InMemoryChatMemoryRepository`：负责在当前 JVM 内存中存储消息。

如果已经配置了 Cassandra、JDBC 或 Neo4j 等其他 Repository，Spring AI 会使用已配置的 Repository。

## Memory Types

### MessageWindowChatMemory

`MessageWindowChatMemory` 维护一个最大容量受限的 sliding window。当消息数量超出限制时，会从较旧消息开始淘汰，同时优先保留 `SystemMessage`。

默认 window size 是 20 条消息。

```java
MessageWindowChatMemory memory = MessageWindowChatMemory.builder()
        .maxMessages(10)
        .build();
```

如果没有显式指定 Repository，builder 会使用 `InMemoryChatMemoryRepository`。

```java
ChatMemoryRepository repository =
        new InMemoryChatMemoryRepository();

MessageWindowChatMemory memory = MessageWindowChatMemory.builder()
        .chatMemoryRepository(repository)
        .maxMessages(10)
        .build();
```

### SystemMessage 处理

`MessageWindowChatMemory` 会对 `SystemMessage` 做特殊处理：

- 超出 window size 时，淘汰其他类型消息，并尽量保留 `SystemMessage`。
- 加入新的 `SystemMessage` 时，会移除之前的 `SystemMessage`，避免同一 conversation 同时保留多组过时 system instructions。

### Turn-boundary eviction（2.0.0）

Spring AI `2.0.0` 在需要淘汰消息时，会尽量删除完整 turn，而不是在一轮对话中间截断。

一个 turn 从 `UserMessage` 开始，包含后续的：

- `AssistantMessage`
- Tool Call
- Tool Response
- 直到下一个 `UserMessage` 出现之前的其他消息

如果按数量计算的截断点落在某个 turn 中间，截断点会向后移动到下一个 `UserMessage`，确保保留的 window 从完整 turn 开始。

因此，`maxMessages` 是保存消息数量的上限，实际保存数量可能更少。

> 如果 `maxMessages` 小于一个完整 turn 的消息数，例如一次 multi-step Tool Calling 已经超过 window size，所有非 `SystemMessage` 都可能被淘汰。应根据实际对话中一个代表性 turn 的大小，合理设置 `maxMessages`。

## Memory Storage

Spring AI 使用 `ChatMemoryRepository` 屏蔽底层存储差异。可以选择内置实现，也可以自定义 Repository。

## In-Memory Repository

`InMemoryChatMemoryRepository` 使用 `ConcurrentHashMap` 在当前进程内存中保存消息。

```java
ChatMemoryRepository repository =
        new InMemoryChatMemoryRepository();
```

优点：

- 不需要外部存储。
- 配置简单。
- 读写延迟低。
- 适合本地开发和单元测试。

限制：

- 应用重启后记忆丢失。
- 多实例之间不共享数据。
- 不适合需要持久化的生产环境。

## JdbcChatMemoryRepository

`JdbcChatMemoryRepository` 通过 JDBC 将消息存储到关系型数据库，适用于需要 persistent Chat Memory 的应用。

官方文档中的 Maven 依赖为：

```xml
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-starter-model-chat-memory-repository-jdbc</artifactId>
</dependency>
```

> 该 artifact 属于当前官方 `2.0.0` 文档。添加到本项目前，必须根据 Spring AI `1.1.2` 确认对应的 artifact 名称与版本。

### 与 MessageWindowChatMemory 组合

```java
@Autowired
JdbcChatMemoryRepository repository;

ChatMemory chatMemory = MessageWindowChatMemory.builder()
        .chatMemoryRepository(repository)
        .maxMessages(10)
        .build();
```

### 手动构建

```java
ChatMemoryRepository repository = JdbcChatMemoryRepository.builder()
        .jdbcTemplate(jdbcTemplate)
        .dialect(new PostgresChatMemoryRepositoryDialect())
        .build();

ChatMemory chatMemory = MessageWindowChatMemory.builder()
        .chatMemoryRepository(repository)
        .maxMessages(10)
        .build();
```

### 消息顺序和 timestamp

`JdbcChatMemoryRepository` 按从旧到新的顺序返回消息，这是 LLM conversation history 所期望的顺序。

Spring AI `2.0.0` 使用 `sequence_id` 维护 conversation 内的消息顺序。每条消息还会记录创建时间，并将 timestamp 以 `java.time.Instant` 的形式放在 message metadata 中：

```java
List<Message> messages = chatMemory.get(conversationId);

for (Message message : messages) {
    Instant createdAt = (Instant) message.getMetadata()
            .get(JdbcChatMemoryRepository.CONVERSATION_TS);
}
```

重新保存 conversation 时，已有消息会保留原始 timestamp。

### 支持的数据库

Spring AI `2.0.0` 通过 dialect abstraction 内置支持：

- PostgreSQL
- MySQL / MariaDB
- SQL Server
- HSQLDB
- Oracle Database

使用 `JdbcChatMemoryRepositoryDialect.from(DataSource)` 时，可以根据 JDBC URL 自动识别 dialect。如果要支持其他数据库，可以实现 `JdbcChatMemoryRepositoryDialect`。

### JDBC 配置项

| Property | 作用 | 默认值 |
| --- | --- | --- |
| `spring.ai.chat.memory.repository.jdbc.initialize-schema` | 控制何时初始化 Schema，可选 `embedded` / `always` / `never` | `embedded` |
| `spring.ai.chat.memory.repository.jdbc.schema` | Schema SQL 脚本位置 | `classpath:org/springframework/ai/chat/memory/repository/jdbc/schema-@@platform@@.sql` |
| `spring.ai.chat.memory.repository.jdbc.platform` | 替换 `@@platform@@` 占位符的 platform | 自动识别 |

示例：

```properties
spring.ai.chat.memory.repository.jdbc.initialize-schema=always
spring.ai.chat.memory.repository.jdbc.schema=classpath:/custom/schema-mysql.sql
```

如果项目使用 Flyway 或 Liquibase，通常应设置：

```properties
spring.ai.chat.memory.repository.jdbc.initialize-schema=never
```

### Tool Calling 限制

当前官方文档指出，`JdbcChatMemoryRepository` 不保存 Tool Call 消息。含 Tool Call 的 `AssistantMessage` 以及 `ToolResponseMessage` 在保存时会被过滤。

如果应用必须持久化完整 Tool Calling 过程，需要选择能正确保存全部 Message 类型的方案。

## CassandraChatMemoryRepository

`CassandraChatMemoryRepository` 使用 Apache Cassandra 保存消息，适用于重视 availability、durability、scale 和 TTL 的应用。

```xml
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-starter-model-chat-memory-repository-cassandra</artifactId>
</dependency>
```

使用自动配置的 Repository：

```java
@Autowired
CassandraChatMemoryRepository repository;

ChatMemory chatMemory = MessageWindowChatMemory.builder()
        .chatMemoryRepository(repository)
        .maxMessages(10)
        .build();
```

手动构建：

```java
ChatMemoryRepository repository = CassandraChatMemoryRepository.create(
        CassandraChatMemoryRepositoryConfig.builder()
                .withCqlSession(cqlSession));
```

### Cassandra 特点

- 使用 time-series Schema。
- 可以保留过去的 Chat Memory windows，对 governance 和 auditing 有价值。
- 建议根据业务设置 TTL，例如保留三年。
- 消息按 timestamp 从旧到新返回。
- 当前文档同样提示不支持完整 Tool Call 消息的持久化。

### Cassandra 主要配置

| Property | 默认值 / 说明 |
| --- | --- |
| `spring.cassandra.contactPoints` | `127.0.0.1` |
| `spring.cassandra.port` | `9042` |
| `spring.cassandra.localDatacenter` | `datacenter1` |
| `spring.ai.chat.memory.cassandra.time-to-live` | 消息 TTL |
| `spring.ai.chat.memory.cassandra.keyspace` | `springframework` |
| `spring.ai.chat.memory.cassandra.table` | `ai_chat_memory` |
| `spring.ai.chat.memory.cassandra.initialize-schema` | `true` |

## Neo4jChatMemoryRepository

`Neo4jChatMemoryRepository` 将 Chat Message 作为 node 和 relationship 保存在 Neo4j property graph 中，适合需要利用 graph 能力的应用。

```xml
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-starter-model-chat-memory-repository-neo4j</artifactId>
</dependency>
```

自动配置：

```java
@Autowired
Neo4jChatMemoryRepository repository;
```

手动构建：

```java
ChatMemoryRepository repository = Neo4jChatMemoryRepository.builder()
        .driver(driver)
        .build();
```

消息按 message index 从旧到新返回。Repository 会自动为 conversation ID 和 message index 创建索引。

### Neo4j 主要配置

| Property | 默认值 |
| --- | --- |
| `spring.ai.chat.memory.repository.neo4j.session-label` | `Session` |
| `spring.ai.chat.memory.repository.neo4j.message-label` | `Message` |
| `spring.ai.chat.memory.repository.neo4j.tool-call-label` | `ToolCall` |
| `spring.ai.chat.memory.repository.neo4j.metadata-label` | `Metadata` |
| `spring.ai.chat.memory.repository.neo4j.tool-response-label` | `ToolResponse` |
| `spring.ai.chat.memory.repository.neo4j.media-label` | `Media` |

## MongoChatMemoryRepository

`MongoChatMemoryRepository` 使用 MongoDB 保存消息，适合需要灵活 document-oriented storage 的应用。

```xml
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-starter-model-chat-memory-repository-mongodb</artifactId>
</dependency>
```

自动配置：

```java
@Autowired
MongoChatMemoryRepository repository;
```

手动构建：

```java
ChatMemoryRepository repository = MongoChatMemoryRepository.builder()
        .mongoTemplate(mongoTemplate)
        .build();
```

消息按 timestamp 从旧到新返回。auto-configuration 可以在启动时创建 `ai_chat_memory` collection。

### MongoDB 配置

| Property | 作用 | 默认值 |
| --- | --- | --- |
| `spring.ai.chat.memory.repository.mongo.create-indices` | 启动时是否创建或重建索引 | `false` |
| `spring.ai.chat.memory.repository.mongo.ttl` | 消息 TTL，单位秒 | `0`，表示永不过期 |

当前官方文档同样提示，MongoDB Repository 不保存完整 Tool Call 消息。

## RedisChatMemoryRepository

`RedisChatMemoryRepository` 使用 Redis Stack（包含 Redis Query Engine 和 RedisJSON）保存 Chat Memory，适用于追求高性能、低延迟、TTL 和高级查询的应用。

```xml
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-starter-model-chat-memory-repository-redis</artifactId>
</dependency>
```

Repository 将消息保存为 JSON document，并创建 search index。消息按 timestamp 从旧到新返回。

自动配置：

```java
@Autowired
RedisChatMemoryRepository repository;
```

手动构建：

```java
RedisClient redisClient = RedisClient.builder()
        .hostAndPort("localhost", 6379)
        .build();

ChatMemoryRepository repository = RedisChatMemoryRepository.builder()
        .jedisClient(redisClient)
        .indexName("my-chat-index")
        .keyPrefix("my-chat:")
        .timeToLive(Duration.ofHours(24))
        .build();
```

### Redis 配置

| Property | 默认值 / 说明 |
| --- | --- |
| `spring.ai.chat.memory.redis.host` | `localhost` |
| `spring.ai.chat.memory.redis.port` | `6379` |
| `spring.ai.chat.memory.redis.index-name` | `chat-memory-idx` |
| `spring.ai.chat.memory.redis.key-prefix` | `chat-memory:` |
| `spring.ai.chat.memory.redis.time-to-live` | 默认不过期 |
| `spring.ai.chat.memory.redis.initialize-schema` | `true` |
| `spring.ai.chat.memory.redis.max-conversation-ids` | `1000` |
| `spring.ai.chat.memory.redis.max-messages-per-conversation` | `1000` |

### Advanced Querying

`RedisChatMemoryRepository` 还实现了 `AdvancedRedisChatMemoryRepository`，可以按消息类型、文本、时间范围和 metadata 查询。

```java
AdvancedRedisChatMemoryRepository advancedRepository =
        (AdvancedRedisChatMemoryRepository) repository;

List<MessageWithConversation> userMessages =
        advancedRepository.findByType(MessageType.USER, 100);

List<MessageWithConversation> results =
        advancedRepository.findByContent("Spring AI", 50);

List<MessageWithConversation> recentMessages =
        advancedRepository.findByTimeRange(
                conversationId,
                Instant.now().minus(Duration.ofHours(1)),
                Instant.now(),
                100);

List<MessageWithConversation> priorityMessages =
        advancedRepository.findByMetadata("priority", "high", 50);
```

还可以执行自定义 Redis Query：

```java
List<MessageWithConversation> results =
        advancedRepository.executeQuery(
                "@type:USER @content:Redis",
                100);
```

### Metadata Field Indexing

要高效查询自定义 metadata，可以声明需要建立索引的字段：

```properties
spring.ai.chat.memory.redis.metadata-fields[0].name=priority
spring.ai.chat.memory.redis.metadata-fields[0].type=tag

spring.ai.chat.memory.redis.metadata-fields[1].name=score
spring.ai.chat.memory.redis.metadata-fields[1].type=numeric

spring.ai.chat.memory.redis.metadata-fields[2].name=category
spring.ai.chat.memory.redis.metadata-fields[2].type=text
```

支持的字段类型：

- `tag`：精确匹配。
- `text`：full-text search。
- `numeric`：数值范围查询。

### Redis 环境要求

- Redis Stack 7.0 或更高版本。
- Redis Query Engine。
- RedisJSON。
- Jedis client library，通常由 starter 引入。

## Repository 选型参考

| Repository | 持久化 | 典型适用场景 | 主要注意点 |
| --- | --- | --- | --- |
| `InMemoryChatMemoryRepository` | 否 | 开发、测试、单实例 Demo | 重启丢失，多实例不共享 |
| `JdbcChatMemoryRepository` | 是 | 关系型数据库已是主存储的业务系统 | Schema migration、Tool Call 持久化限制 |
| `CassandraChatMemoryRepository` | 是 | 高可用、大规模、TTL、time-series | 运维复杂度、Tool Call 限制 |
| `Neo4jChatMemoryRepository` | 是 | 需要 graph relationship 和图查询 | 需要 Neo4j 基础设施 |
| `MongoChatMemoryRepository` | 是 | document-oriented 存储、结构灵活 | Tool Call 持久化限制 |
| `RedisChatMemoryRepository` | 是 | 低延迟、TTL、高级检索 | 需要 Redis Stack，不是普通 Redis 服务即可 |

## Memory in ChatClient

使用 `ChatClient` 时，通常通过 Memory Advisor 将 `ChatMemory` 接入请求流程。

Spring AI 提供的相关 Advisor 包括：

### MessageChatMemoryAdvisor

`MessageChatMemoryAdvisor` 使用传入的 `ChatMemory` 管理对话。每次交互时：

1. 根据 conversation ID 从 Memory 取回历史消息。
2. 将这些消息作为结构化 `Message` 列表添加到 Prompt。
3. 将当前 `UserMessage` 加入 Memory。
4. 在 AI Model 响应后将 `AssistantMessage` 加入 Memory。

### VectorStoreChatMemoryAdvisor

`VectorStoreChatMemoryAdvisor` 使用 `VectorStore` 管理记忆。它从 VectorStore 检索相关对话信息，然后将结果作为普通文本追加到 system message。

与 `MessageChatMemoryAdvisor` 的主要区别是：

```text
MessageChatMemoryAdvisor:
    传入结构化 Message 序列

VectorStoreChatMemoryAdvisor:
    检索相关内容，以普通文本合并到 system message
```

## 为 ChatClient 配置 Memory

```java
ChatMemory chatMemory = MessageWindowChatMemory.builder()
        .maxMessages(10)
        .build();

ChatClient chatClient = ChatClient.builder(chatModel)
        .defaultAdvisors(
                MessageChatMemoryAdvisor.builder(chatMemory).build())
        .build();
```

调用时通过 Advisor Context 传入 conversation ID：

```java
String conversationId = "007";

String content = chatClient.prompt()
        .user("Do I have a license to code?")
        .advisors(advisor -> advisor.param(
                ChatMemory.CONVERSATION_ID,
                conversationId))
        .call()
        .content();
```

### conversation ID 的版本注意事项

当前 Spring AI `2.0.0` 官方文档要求所有 Memory Advisor 调用都显式传入 `ChatMemory.CONVERSATION_ID`，否则会抛出 `IllegalArgumentException`。

本项目使用的 `1.1.2` 仍然提供 `ChatMemory.DEFAULT_CONVERSATION_ID`，某些调用可能回退到 `default`。但为了避免不同用户共用同一份记忆，也为了方便未来升级，建议始终显式传入 conversation ID。

> conversation ID 必须能唯一区分用户或会话。不能让不同用户使用同一个固定 ID，否则可能出现记忆串话和隐私泄漏。

## VectorStoreChatMemoryAdvisor 自定义 Template

`VectorStoreChatMemoryAdvisor` 使用默认 template 将检索出的 Memory 合并到 system message。可以通过 builder 的 `.promptTemplate()` 提供自定义 `PromptTemplate`。

这个 template 必须包含两个占位符：

- `instructions`：接收原始 system message。
- `long_term_memory`：接收检索出的 conversation memory。

Advisor 的 `.promptTemplate()` 与 `ChatClient.templateRenderer()` 不是同一件事：

- Advisor 中的 `PromptTemplate` 控制 Memory 如何合并到 system message。
- `ChatClient` 级别的 `TemplateRenderer` 控制 Advisor 运行前，初始 user/system Prompt 如何渲染。

## Tool Calling 的 Memory 限制

当前官方文档提示，进行 Tool Calling 时，LLM 与 Tool 之间的某些中间消息不会被保存到 Chat Memory。

这会影响：

- 完整重放 Tool Calling 过程。
- 审计 Tool 调用。
- 在下一轮中精确恢复中间状态。

如果业务需要完整保存这些消息，需要使用 User Controlled Tool Execution 或其他能完整持久化 Tool Calling 消息的方案。

## Memory in ChatModel

如果直接使用 `ChatModel` 而不是 `ChatClient`，则需要显式管理 Memory。

```java
ChatMemory chatMemory = MessageWindowChatMemory.builder()
        .maxMessages(10)
        .build();

String conversationId = "007";
```

### 第一轮

```java
UserMessage firstUserMessage =
        new UserMessage("My name is James Bond");

chatMemory.add(conversationId, firstUserMessage);

ChatResponse firstResponse = chatModel.call(
        new Prompt(chatMemory.get(conversationId)));

chatMemory.add(
        conversationId,
        firstResponse.getResult().getOutput());
```

### 第二轮

```java
UserMessage secondUserMessage =
        new UserMessage("What is my name?");

chatMemory.add(conversationId, secondUserMessage);

ChatResponse secondResponse = chatModel.call(
        new Prompt(chatMemory.get(conversationId)));

chatMemory.add(
        conversationId,
        secondResponse.getResult().getOutput());
```

由于第一轮的消息被加入了 Memory，第二轮的 Prompt 中会包含“James Bond”这一上下文。

直接使用 `ChatModel` 时，不要遗漏任何一个步骤：

```text
1. 加入 UserMessage
2. 从 Memory 取出消息构建 Prompt
3. 调用 ChatModel
4. 加入 AssistantMessage
```

`ChatClient + MessageChatMemoryAdvisor` 会帮助自动完成这些工作。

## 如何自定义 ChatMemory

自定义 ChatMemory 的核心是实现一种新的 Memory 保留策略，而不是重复实现数据库读写。

例如，可以实现：

- `TokenLimitChatMemory`：根据 token 数量淘汰旧消息。
- `TimeToLiveChatMemory`：只保留指定时间内的消息。
- `SummarizingChatMemory`：将较旧对话压缩成 summary。
- `PriorityChatMemory`：根据 metadata 中的优先级决定是否淘汰。
- `HybridChatMemory`：最近对话使用 message window，更早内容使用 VectorStore 检索。

### 建议的实现结构

```java
public final class TokenLimitChatMemory implements ChatMemory {

    private final ChatMemoryRepository repository;
    private final int maxTokens;
    private final TokenCountEstimator tokenCountEstimator;

    public TokenLimitChatMemory(
            ChatMemoryRepository repository,
            int maxTokens,
            TokenCountEstimator tokenCountEstimator) {
        this.repository = repository;
        this.maxTokens = maxTokens;
        this.tokenCountEstimator = tokenCountEstimator;
    }

    @Override
    public void add(String conversationId, List<Message> newMessages) {
        List<Message> existing =
                repository.findByConversationId(conversationId);

        List<Message> merged = new ArrayList<>(existing);
        merged.addAll(newMessages);

        List<Message> retained = retainWithinTokenLimit(merged);
        repository.saveAll(conversationId, retained);
    }

    @Override
    public List<Message> get(String conversationId) {
        return repository.findByConversationId(conversationId);
    }

    @Override
    public void clear(String conversationId) {
        repository.deleteByConversationId(conversationId);
    }

    private List<Message> retainWithinTokenLimit(
            List<Message> messages) {
        // 自己实现 token 计数、SystemMessage 保留和淘汰逻辑
        throw new UnsupportedOperationException("TODO");
    }
}
```

这个 skeleton 刻意留下了 `retainWithinTokenLimit()`，可以作为自定义 ChatMemory 练习。

### 自定义时必须考虑的问题

1. **保留策略**  
   是按消息数、token、时间、优先级，还是按语义相关性？

2. **SystemMessage 如何处理**  
   是否总是保留？新 `SystemMessage` 是否覆盖旧值？

3. **是否保持完整 turn**  
   如果只删除 `UserMessage` 却留下 `AssistantMessage`，LLM 看到的 context 可能不完整。

4. **Tool Calling 消息如何处理**  
   Tool Call 和 Tool Response 通常是成对的，不应随意拆分。

5. **消息顺序**  
   `get()` 返回时应保证从旧到新。

6. **Thread Safety**  
   同一 conversation 可能同时收到多个请求。`find -> merge -> saveAll` 不是天然 atomic operation。

7. **conversation isolation**  
   不同 conversation ID 的消息不能相互污染。

8. **失败策略**  
   Repository 不可用时，是让请求失败、回退到无 Memory 模式，还是使用本地缓存？

9. **数据安全**  
   Chat Memory 可能含有用户隐私，需要考虑加密、TTL、访问控制和删除权。

## 自定义 ChatMemoryRepository

如果 Memory 策略可以继续使用 `MessageWindowChatMemory`，只是希望将消息存到自己的数据库，应实现 `ChatMemoryRepository`，而不是 `ChatMemory`。

例如：

```java
public final class MySqlChatMemoryRepository
        implements ChatMemoryRepository {

    @Override
    public List<String> findConversationIds() {
        // 查询所有 conversation ID
        return List.of();
    }

    @Override
    public List<Message> findByConversationId(
            String conversationId) {
        // 按从旧到新顺序查询并还原 Message
        return List.of();
    }

    @Override
    public void saveAll(
            String conversationId,
            List<Message> messages) {
        // 用 messages 替换当前 conversation 的已有数据
    }

    @Override
    public void deleteByConversationId(
            String conversationId) {
        // 删除指定 conversation
    }
}
```

再将它与内置 Memory 策略组合：

```java
ChatMemory chatMemory = MessageWindowChatMemory.builder()
        .chatMemoryRepository(mySqlChatMemoryRepository)
        .maxMessages(20)
        .build();
```

## 常见问题

### maxMessages(10) 是 10 轮对话吗？

不是。它是消息数量限制。一轮普通对话通常至少包含一条 `UserMessage` 和一条 `AssistantMessage`；Tool Calling 会产生更多消息。

### 使用了 InMemoryChatMemoryRepository，为什么重启后记忆没了？

因为它只保存在当前 JVM 内存中，应用重启后数据会消失。

### 为什么不同用户之间出现了记忆串话？

最常见原因是所有用户共用了同一个 conversation ID，或者没有显式传入 ID，导致都回退到 `default`。

### Chat Memory 能替代业务对话记录吗？

不应该。Chat Memory 会为了控制 context 而删除消息，业务上需要完整保留的 Chat History 应该另行持久化。

### 更换 Repository 后还能用 MessageWindowChatMemory 吗？

可以。`MessageWindowChatMemory` 只负责保留策略，可以与任意符合 `ChatMemoryRepository` 契约的存储实现组合。

### 为什么不直接把整个 Chat History 每次都发给 LLM？

因为它会：

- 消耗越来越多 token。
- 增加请求延迟和费用。
- 超出 model context window。
- 让无关的旧信息干扰当前问题。

## 最佳实践

1. **始终使用明确的 conversation ID**  
   ID 应绑定到真实会话，并且不能在不同用户之间共用。

2. **区分 Memory 和 History**  
   Memory 为 LLM 服务，History 为业务、审计和 UI 服务。

3. **根据 token 而不只是消息条数评估容量**  
   一条非常长的消息可能比十条短消息消耗更多 context window。

4. **不要破坏完整 turn**  
   尤其是涉及 Tool Call 时，应保持请求和响应的结构完整。

5. **生产环境使用 persistent Repository**  
   除非明确接受重启丢失和多实例不共享，否则不应依赖 In-Memory Repository。

6. **为 Memory 设置生命周期**  
   对不再活跃的 conversation 使用 TTL 或定期清理，避免无限增长。

7. **测试并发修改**  
   同一 conversation 的并发请求可能导致消息覆盖或顺序错乱。

8. **保护隐私数据**  
   对 Chat Memory 使用合理的访问控制、加密、日志脱敏和删除机制。

9. **升级时检查 Schema 与淘汰行为**  
   Spring AI `2.0.0` 的 JDBC Schema 和 turn-boundary eviction 都与旧版本存在差异。

## 自定义 ChatMemory 练习建议

如果要自己实现第一个 ChatMemory，建议从难度适中的 `TokenLimitChatMemory` 开始：

1. 实现 `ChatMemory` 接口。
2. 通过构造器注入 `ChatMemoryRepository`。
3. 在 `add()` 中合并旧消息和新消息。
4. 保留 `SystemMessage`。
5. 从最旧的完整 turn 开始淘汰。
6. 保证总 token 数不超过设定值。
7. 通过 `repository.saveAll()` 保存最终列表。
8. 编写单元测试，覆盖边界情况。

建议至少测试：

- 没有消息时 `get()` 返回空列表。
- 未超过 token limit 时不淘汰。
- 超过限制时从最旧 turn 开始淘汰。
- `SystemMessage` 被保留。
- 新 `SystemMessage` 替换旧 `SystemMessage`。
- 不从 `AssistantMessage` 或 `ToolResponseMessage` 中间截断。
- `clear()` 只删除指定 conversation。
- 不同 conversation 之间完全隔离。

## 学习自测

1. `ChatMemory` 和 `ChatMemoryRepository` 分别负责什么？
2. Chat Memory 和 Chat History 有什么区别？
3. `maxMessages(10)` 为什么不等于 10 轮对话？
4. Spring AI `1.1.2` 和 `2.0.0` 的消息淘汰行为有什么不同？
5. `MessageChatMemoryAdvisor` 在一次调用前后分别做了什么？
6. 为什么 conversation ID 不能在不同用户之间共用？
7. 如果只想把消息存到 MySQL，应该实现 `ChatMemory` 还是 `ChatMemoryRepository`？
8. 如果想按 token limit 淘汰消息，应该实现哪个抽象？
9. 为什么 `saveAll()` 的 replacement 语义非常重要？
10. 为什么 Tool Call 和 Tool Response 不应该随意分开淘汰？

## 参考资料

- [Spring AI Chat Memory](https://docs.spring.io/spring-ai/reference/api/chat-memory.html)
- [Spring AI ChatClient API](https://docs.spring.io/spring-ai/reference/api/chatclient.html)
- [Spring AI Advisors API](https://docs.spring.io/spring-ai/reference/api/advisors.html)
- [Spring AI Tool Calling](https://docs.spring.io/spring-ai/reference/api/tools.html)
