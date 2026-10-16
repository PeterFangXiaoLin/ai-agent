# AI Agent

## 技术栈

| 分类 | 技术 / 框架 | 版本 | 用途 |
| --- | --- | --- | --- |
| 开发语言 | Java | 21 | 项目主要开发语言 |
| 基础框架 | Spring Boot | 3.5.11 | 应用启动、自动配置与依赖管理 |
| Web | Spring Web（Spring MVC） | 由 Spring Boot 管理 | 构建 RESTful API |
| 切面编程 | Spring AOP | 由 Spring Boot 管理 | 权限校验等横切逻辑 |
| 参数校验 | Jakarta Validation | 由 Spring Boot 管理 | 请求参数校验 |
| AI 框架 | Spring AI Alibaba Agent Framework | 1.1.2.0 | 构建 AI Agent 应用 |
| 大模型接入 | Spring AI Alibaba DashScope | 1.1.2.0 | 接入阿里云百炼 DashScope 模型服务 |
| 持久层框架 | MyBatis-Plus | 3.5.15 | 数据访问、分页及 CRUD |
| 数据库 | MySQL | 由 Spring Boot 管理驱动版本 | 业务数据存储 |
| 接口文档 | Knife4j | 4.4.0 | 增强 API 文档界面 |
| 接口文档生成 | springdoc-openapi | 2.8.17 | 生成 OpenAPI 3 文档，兼容 Spring Boot 3.5 |
| 工具库 | Hutool | 5.8.38 | 提供常用 Java 工具类 |
| 代码简化 | Lombok | 1.18.36 | 减少样板代码 |
| 测试框架 | Spring Boot Test / JUnit 5 | 由 Spring Boot 管理 | 单元测试与集成测试 |
| 构建工具 | Maven | - | 依赖管理与项目构建 |



### 接入 AI

* SDK 接入
* HTTP 接入
* SpringAI 接入
* LangChain4j 接入



### 需求分析

参考各大AI平台上传的AI应用，随便一个都能做出一个项目

### 细化需求

利用 AI 帮助我们细化需求

### 方案设计

#### 系统提示词设计

利用 AI 生成系统提示词

#### 多轮对话实现

要让AI 能够记忆上下文，不能出现断片



***

综上，需要实现系统提示词的记录和对话记忆

封装一个 ChatClient 方便复用



### 自定义 Advisor

#### 自定义 Log Advisor

> 虽然 Spring AI 提供了 SimpleLoggerAdvisor 但是默认debug级别输出

自定义 MyLoggerAdvisor 拦截器

自定义 Re2 Advisor 



#### 结构化输出





### RAG

#### RAG工作流程

* 文档收集和切割

* 向量转换和存储
* 文档过滤和检索
* 查询增加和关联



##### 1. 文档收集和切割

文档收集：从各种来源

文档预处理：文档清洗

文档切割：将文档切割成适当大小的片段

* 基于固定大小
* 基于语义边界
* 递归切割
* AI切割



##### 2. 片段转向量并存储

转向量：使用 embedding 模型

存储：向量数据库 ，如redis  es pg,neo4j



##### 3. 文档过滤和检索

查询处理：把用户的问题转成向量表示

* 查询改写：问题富化，指代消除（第三人称消除）
* 问题扩展：把原问题扩展成几个相似的问题，一起转成向量去向量数据库检索
* 问题回溯：用户问了一个具体问题，转成一个更加通用的问题进行查询
* 问题拆解：用户一个提问可能包含多个子问题，可以把这些子问题拆出来单独去向量数据库检索

过滤机制：支持元数据过滤

相似度搜索：余弦相似度，向量点积等

上下文组装：把用户问题和检索出来的文档一起发给大模型



## Spring AI + 本地知识库



#### 1. 文档准备

可以使用 AI 生成文档，或者自己私有的一些文档



加载代码类的文档的时候，可以把代码去掉，或者把图片去掉（针对纯文本的模型）



#### 2. 文档读取 (ETL)

* 文档读取： DocumentReader 加载文档

* 文档切割 
* 向量存储： 先调用上面的文档读取获取文档列表，使用embedding 模型将文档转成向量， SimpleVectorStore 保存向量和文档， 调用add方法实现



原始文档尽量优化成结构化

新建一个 LoveAppDocumentLoader 恋爱大师文档加载器

创建单元测试类进行测试







#### 3. 向量存储

自定义一个内存的向量存储 LoveAppVectorStoreConfig



#### 4. 查询增强

提供了两种使用RAG的方式

* QuestionAnswerAdvisor
* RetrievalAugmentationAdvisor



增加 doChatWithRag 方法使用 QuestionAnswerAdvisor



## SpringAI + 云知识库





## 文档收集和切割 - ETL



### 文档

* document
  * media 数据类型
  * metadata 元信息



### 抽取 （Extract）

DocumentReader：read 方法实现把文件读取成 document 列表

* `JsonReader`
* `TextReader`

***

上面两个是 Spring AI 自带的，下面的需要引入依赖

* `JsoupDocumentReader`
* `MarkdownDocumentReader`
* `PagePdfDocumentReader`
* `ParagraphPdfDocumentReader`





### 转换（Transform）

DocumentTransform: apply 方法， 传入 List<Document> 返回 List<Document>





#### 1) TextSplitter 文本分割器

* TokenTextSplitter





#### 2) MetadaEnricher 元数据增强器(需要调用大模型实现)

* KeyWordMetadataEnricher
* SummaryMetadataEnricher



#### 3) ContentFormatter 内容格式化

* 文档内容与元数据进行合并
* 元数据过滤
* 自定义模板



### 加载（Load）

`DocumentWriter` 

* FileDocumentWriter 将文档写入到文件中
* VectorStoreWriter 将文档写入向量数据库



### 向量转换和存储

VectoreStore 接口提供增删改查方法

各种向量数据库的存储实现这个接口

QueryRequest 构造搜索请求，similarSearch 方法传入实现搜索

阿里云提供了 DashScopeCloudStroe 实现 把向量保存到百炼



#### 基于 PGVector 实现向量存储

####  

#### 安装 pg 和 插件

创建数据库并为该库添加插件



#### 引入 maven 依赖



#### 修改 application.yml 配置连接



上面实现了自动注入，与ollma 冲突，也可以引入别的依赖自动配置

编写测试类，测试 VectorStore



手动定义的bean 需要排除依赖避免自动注入



修改之前的基于内存的向量存储，改为使用向量数据库的检索



### 文档过滤和检索

即 Spring AI 提供 Modules RAG



#### 预检索（pre-Retrievel）： 优化用户查询

优化用户的原始查询

提供 接口 `QueryTransformer` 

##### 查询改写 RewriteQueryTransformer (调用 大模型，改写用户的问题，让问题更加清洗)

##### 查询翻译 TranslationQueryTransformer (调用 大模型把查询翻译成指定语言)

##### 查询压缩 CompressQueryTransformer (调用大模型，把用户的问题根据上下文记忆进行修改，比如一些第三人称的指代消除)

##### 多查询扩展 MultiQueryExpander 扩展查询，把问题改写成多个意思相近但是表达不一样的问题



#### 检索 （Retrievel）：提高查询相关性

`DocumentRetrievel` 实现

* VectoreStoreDocumentRetrievel 实现元数据过滤，设置相似度阈值

##### 文档合并

`ContatenationDocumetJoiner` 文档合并，过滤重复文档



#### 检索后（Post-Retrievel）

无



#### 发给大模型生成答案（ Generation）

`QueryAugmentation` 使用提示词模板，把查询到的文档和用户的问题加到提示词模板中，构造回答

##### ContextualQueryAugmenter

允许指定没有查到文档时的回答



## RAG 调优

### 文档收集和切割

#### 1. 优化原始文档

1）内容结构化

2）内容规范化

3）格式标准化



把规则给AI，让其优化



#### 2. 文档切片

最优是AI切片加人工审核

自定义基于Token 数量的切片



#### 3. 元数据标注

* 手动提取文件名称等状态信息

* 利用AI提取更多的元数据



从文件名称中提取出 status

自定义基于AI的元数据提取



### 向量转换和存储

#### 向量存储方式

#### 选择合适的embedding模型



### 文档过滤和检索

#### 多查询扩展



#### 查询重写和翻译



#### 检索器配置





## 工具调用（Tool Calling）

用于AI无法自己完成的任务



注解式和编程式

1）注解式

```java
class WeatherTools {
    @Tool(description = "获取指定城市的当前天气情况")
    String getWeather(@ToolParam(description = "城市名称") String city) {
        // 获取天气的实现逻辑
        return "北京今天晴朗，气温25°C";
    }
}

```

2）编程式

先定义工具

```java
class WeatherTools {
    String getWeather(String city) {
        // 获取天气的实现逻辑
        return "北京今天晴朗，气温25°C";
    }
}

```

```java
Method method = ReflectionUtils.findMethod(WeatherTools.class, "getWeather", String.class);
ToolCallback toolCallback = MethodToolCallback.builder()
    .toolDefinition(ToolDefinition.builder(method)
            .description("获取指定城市的当前天气情况")
            .build())
    .toolMethod(method)
    .toolObject(new WeatherTools())
    .build();

```





### 文件操作

读取文件和写入文件

1. 定义 tmp目录存放文件

2. 读取文件，给出文件名称
3. 写入文件，给出写入的内容和文件名称
4. 编写测试类测试



### 联网搜索工具

使用 SearchAPI 实现



### 网页抓取工具

使用 Jsoup 实现



### 资源下载工具

使用hutool download 工具实现



### PDF生成工具

使用 itext 库生成 pdf



### 终端命令操作工具

ProcessBuild



### 注册所有工具

ToolCallbacks.from



编写调用工具方法 doChatWithTools





### 高级工具特性

1. 可以增加 ToolContext 参数，传递当前的登录用户信息等不需要询问用户就可以获得的信息
2. 方法 Tool 描述可以增加 returnDirect ，不需要再次调用大模型，直接返回工具结果



## MCP （Model Context Protocol）

### Mcp Client（客户端）  调用 Mcp Server

* Stdio 调用： 本地调用
* SSE 远程调用：远程调用



### Mcp Server (服务端)

可以提供多种类型的信息





### 使用MCP

pom.xml 引入

```xml
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-starter-mcp-client</artifactId>
</dependency>
```

新建 mcp-servers.json

```json
{
  "mcpServers": {
    "amap-maps": {
      "args": [
        "/c",
        "npx",
        "-y",
        "@amap/amap-maps-mcp-server"
      ],
      "command": "cmd.exe",
      "env": {
        "AMAP_MAPS_API_KEY": "你的apiKey"
      }
    }
  }
}
```

修改application.yml 配置

```yaml
spring:
  ai:
    mcp:
      client:
        enabled: true
        type: SYNC
        request-timeout: 120s
        stdio:
          servers-configuration: classpath:mcp-servers.json
```



### 开发 mcp server

#### MCP 服务端开发

##### stdio 模式

1、新建项目

选择依赖 lombok

添加依赖

```xml
<dependencyManagement>
        <dependencies>
            <dependency>
                <groupId>com.alibaba.cloud.ai</groupId>
                <artifactId>spring-ai-alibaba-bom</artifactId>
                <version>1.1.2.0</version>
                <type>pom</type>
                <scope>import</scope>
            </dependency>
            <dependency>
                <groupId>org.springframework.ai</groupId>
                <artifactId>spring-ai-bom</artifactId>
                <version>1.1.2</version>
                <type>pom</type>
                <scope>import</scope>
            </dependency>
            <dependency>
                <groupId>com.alibaba.cloud.ai</groupId>
                <artifactId>spring-ai-alibaba-extensions-bom</artifactId>
                <version>1.1.2.1</version>
                <type>pom</type>
                <scope>import</scope>
            </dependency>
        </dependencies>
    </dependencyManagement>
```

```xml
<dependency>
  <groupId>org.springframework.ai</groupId>
  <artifactId>spring-ai-starter-mcp-server-webmvc</artifactId>
</dependency>
```



**stdio 需要关闭控制台的所有输出打印，包括日志、banner等内容**

2、修改 `application.yml` 

```yaml
spring:
  application:
    name: mcp-server-stdio
    
  main:
    web-application-type: none
    banner-mode: off

  ai:
    mcp:
      server:
        name: mcp-server
        version: 1.0.0
        stdio: true
        enabled: true
        type: SYNC

logging:
  level:
    root: OFF

```

3、 定义工具

```java
@Service
public class WeatherService {

    @Tool(description = "根据城市名称查询天气信息")
    public String getWeather(@ToolParam(description = "城市名称") String city) {
        if (city == null) {
            return "请提供城市名称";
        }
        return switch (city) {
            case "北京" -> "北京: 晴, 25°C";
            case "上海" -> "上海: 多云, 22°C";
            case "深圳" -> "深圳: 小雨, 28°C";
            default -> city + ": 下雪, -20°C";
        };
    }
}
```



4、注册工具

```java
    @Bean
    public ToolCallbackProvider weatherTools(WeatherService weatherService) {
        return MethodToolCallbackProvider.builder()
                .toolObjects(weatherService)
                .build();
    }
```

5、 maven 打包

![image-20261015183913431](https://raw.githubusercontent.com/PeterFangXiaoLin/imgs/main/test/20261015183922317.png)



6、 修改环境变量

java 版本为java 21 或者 修改下面的命令

![image-20261015193102688](https://raw.githubusercontent.com/PeterFangXiaoLin/imgs/main/test/20261015193104080.png)

7、 cursor 添加mcp

```json
{
  "mcpServers": {
    "weather-stdio": {
      "disabled": false,
      "timeout": 60,
      "type": "stdio",
      "command": "java",
      "args": [
        "-jar",
         # 改成自己jar包位置
        "D:\\LLMentor\\LLMentor\\mcp\\mcp-server-stdio\\target\\mcp-server-stdio-1.0.0-SNAPSHOT.jar"
      ]
    }
  }
}
```

8、 使用mcp

![image-20261015193135070](https://raw.githubusercontent.com/PeterFangXiaoLin/imgs/main/test/20261015193136597.png)



##### SSE

1、新建项目，依赖同上面一样 **工具代码也是一样的**

2、修改 `application.yml`

```yaml
spring:
  application:
    name: mcp-server-sse
  ai:
    mcp:
      server:
        enabled: true
        name: weather-sse-server
        version: 1.0.0
        type: SYNC
        capabilities:
          tool: true
          resource: false
          prompt: false
          completion: false
        sse-message-endpoint: /mcp/messages   # 客户端发送消息的HTTP endpoint ("写信发消息")
        sse-endpoint: /sse                    # 客户端订阅SSE的endpoint （"听收音机"）

server:
  port: 8082

```



3、启动应用

![image-20261015200129101](https://raw.githubusercontent.com/PeterFangXiaoLin/imgs/main/test/20261015200137559.png)



4、访问地址 http://localhost:8082/sse

![image-20261015200234391](https://raw.githubusercontent.com/PeterFangXiaoLin/imgs/main/test/20261015200235711.png)





SSE 的 `endpoint` 事件中，`data:` 后面的 `/mcp/messages?sessionId=e92d9a21-50c2-4d79-a26f-b2cc7acc7d63` 就是客户端向服务端发送请求的相对地址。`data:` 是 SSE 字段名，不属于请求 URL。

在 Postman 中先发送 `GET http://localhost:8082/sse`，设置请求头 `Accept: text/event-stream`，并保持该连接打开。在另一个请求标签页中，按下面的顺序发送四次请求，公共配置如下：

* 请求方法：`POST`
* 请求 URL：`http://localhost:8082/mcp/messages?sessionId=e92d9a21-50c2-4d79-a26f-b2cc7acc7d63`
* 请求头：`Content-Type: application/json`
* Body：选择 `raw` → `JSON`，依次填入下面四个请求体。

`sessionId` 必须替换为当前 SSE 连接返回的实际值，四次 POST 使用同一个值；重新连接后，需要使用新连接返回的地址。

发送工具请求前，需要先完成初始化。以下示例使用适用于该 SSE 流程的 MCP 协议版本 `2024-11-05`。

1） 初始化

```json
{
  "jsonrpc": "2.0",
  "id": 1,
  "method": "initialize",
  "params": {
    "protocolVersion": "2024-11-05",
    "capabilities": {},
    "clientInfo": {
      "name": "postman-client",
      "version": "1.0.0"
    }
  }
}
```

回到保持连接的 SSE 标签页，等待 `id: 1` 的成功响应（包含 `result.protocolVersion`、`result.capabilities` 和 `result.serverInfo`），再发送下一步。



![image-20261015201715354](https://raw.githubusercontent.com/PeterFangXiaoLin/imgs/main/test/20261015201716748.png)



![image-20261015201735119](https://raw.githubusercontent.com/PeterFangXiaoLin/imgs/main/test/20261015201740780.png)

2）客户端发送 initialized 通知

```json
{
  "jsonrpc": "2.0",
  "method": "notifications/initialized"
}
```

通知不带 `id`，服务端不会为它返回 JSON-RPC 响应。POST 提交成功后即可继续查询工具列表。



![image-20261015201915362](https://raw.githubusercontent.com/PeterFangXiaoLin/imgs/main/test/20261015201917104.png)



![image-20261015201942071](https://raw.githubusercontent.com/PeterFangXiaoLin/imgs/main/test/20261015201943155.png)



3）查询工具列表

```json
{
  "jsonrpc": "2.0",
  "id": 2,
  "method": "tools/list",
  "params": {}
}
```

在 SSE 标签页查看 `id: 2` 的响应，`result.tools` 就是服务端注册的工具列表。

4）调用工具

完成初始化和 initialized 通知后，继续向当前 SSE 连接对应的 `/mcp/messages?sessionId=...` 地址发送 `POST`，请求头保持 `Content-Type: application/json`，Body 选择 `raw` → `JSON`：

```json
{
  "jsonrpc": "2.0",
  "id": 3,
  "method": "tools/call",
  "params": {
    "name": "getWeather",
    "arguments": {
      "city": "北京"
    }
  }
}
```

`params.name` 使用 `tools/list` 返回的工具名称；当前天气工具名为 `getWeather`，参数名为 `city`，可将城市改为 `上海` 或 `深圳`。

保持 SSE 连接打开，在 SSE 标签页查看 `id: 3` 的响应，天气调用结果位于 `result.content` 中。

注意：该 SSE 模式通过 POST 发送消息，通过原来的 SSE 连接接收 JSON-RPC 响应，因此 POST 的响应体可能为空，初始化结果和工具列表要在 SSE 的 `message` 事件中查看。

协议参考：[连接生命周期](https://modelcontextprotocol.io/specification/2024-11-05/basic/lifecycle)、[SSE 传输](https://modelcontextprotocol.io/specification/2024-11-05/basic/transports)、[工具列表](https://modelcontextprotocol.io/specification/2024-11-05/server/tools)。



![image-20261015202111637](https://raw.githubusercontent.com/PeterFangXiaoLin/imgs/main/test/20261015202112914.png)

![image-20261015202526421](https://raw.githubusercontent.com/PeterFangXiaoLin/imgs/main/test/20261015202528365.png)

中文乱码解决，修改 `application.yml`

```yaml
server:
  port: 8082
  servlet:
    encoding:
      charset: UTF-8
      force: true
      enabled: true
```



5、cursor 接入

```json
{
  "mcpServers": {
    "weather-sse": {
      "type": "sse",
      "url": "http://127.0.0.1:8082/sse",
      "autoApprove": [],
      "timeout": 60,
      "disabled": false
    }
  }
}
```

![image-20261015202835619](https://raw.githubusercontent.com/PeterFangXiaoLin/imgs/main/test/20261015202836751.png)



##### Streamable Http

1、 新建项目，依赖同上面，工具代码同上面

2、修改 `application.yml` 然后启动

```yaml
spring:
  application:
    name: mcp-server-streamablehttp
  ai:
    mcp:
      server:
        ## 这个地方改成STATELESS，就是无状态模式
        protocol: STREAMABLE
        name: streamable-mcp-server
        version: 1.0.0
        type: SYNC
        instructions: "这个服务是用来查询城市天气的。"
        resource-change-notification: true
        tool-change-notification: true
        prompt-change-notification: true
        streamable-http:
          mcp-endpoint: /api/mcp
          keep-alive-interval: 30s

server:
  port: 8083
  servlet:
    encoding:
      charset: UTF-8
      force: true
      enabled: true
```

3、访问 `mcp-endpoint` 端点完成初始化（Postman）

当前配置为 `protocol: STREAMABLE`，按顺序执行：`initialize` → `notifications/initialized` → `tools/list` → `tools/call`。

下面四步都向 `http://localhost:8083/api/mcp` 发送 `POST` 请求，Body 选择 `raw` → `JSON`，公共请求头如下：

```http
Content-Type: application/json
Accept: application/json, text/event-stream
```

这四步直接通过 POST 完成，无需先建立单独的 GET SSE 连接。初始化结果、工具列表和工具调用结果在各自 POST 的响应中查看：响应类型为 `application/json` 时读取 JSON，为 `text/event-stream` 时读取 SSE 事件的 `data` 中的 JSON。

1）初始化

首次请求不带 `Mcp-Session-Id`，请求体如下：

```json
{
  "jsonrpc": "2.0",
  "id": 1,
  "method": "initialize",
  "params": {
    "protocolVersion": "2025-06-18",
    "capabilities": {},
    "clientInfo": {
      "name": "postman-client",
      "version": "1.0.0"
    }
  }
}
```

等待收到 `id: 1` 的成功响应，再进行下一步。在 Postman 中保存以下两个环境变量或集合变量：

* `mcpSessionId`：复制初始化响应 **Headers** 中的 `Mcp-Session-Id`，这是服务端分配的会话 ID。
* `mcpProtocolVersion`：复制初始化响应 JSON 中的 `result.protocolVersion`，后续使用实际协商出的协议版本，示例请求的版本为 `2025-06-18`。

后面的通知和工具请求，除公共请求头外，都要添加：

```http
Mcp-Session-Id: {{mcpSessionId}}
MCP-Protocol-Version: {{mcpProtocolVersion}}
```

`Mcp-Session-Id` 通过请求头传递，无需在 URL 中拼接 `?sessionId=...`。如果服务端初始化响应未分配会话 ID，则省略该请求头；当前有状态的 Streamable HTTP 配置应使用服务端返回的会话 ID。



![image-20261015210736733](https://raw.githubusercontent.com/PeterFangXiaoLin/imgs/main/test/20261015210738236.png)



2）客户端发送 initialized 通知

保持 `POST http://localhost:8083/api/mcp`，携带上面的公共请求头、会话 ID 和协议版本，请求体如下：

```json
{
  "jsonrpc": "2.0",
  "method": "notifications/initialized"
}
```

该通知不带 `id`。服务端接受通知后返回 `202 Accepted`，响应体为空，不会返回对应的 JSON-RPC 响应；此时即可继续查询工具列表。



![image-20261015210800959](https://raw.githubusercontent.com/PeterFangXiaoLin/imgs/main/test/20261015210801886.png)



3）查询工具列表

保持相同 URL 和请求头，请求体如下：

```json
{
  "jsonrpc": "2.0",
  "id": 2,
  "method": "tools/list",
  "params": {}
}
```

在本次 POST 响应中找到 `id: 2` 的 JSON-RPC 响应，`result.tools` 就是工具列表。



![image-20261015211650237](https://raw.githubusercontent.com/PeterFangXiaoLin/imgs/main/test/20261015211652723.png)



4）调用工具

完成初始化和 initialized 通知后，继续发送 `POST http://localhost:8083/api/mcp`，使用当前会话的请求头：

```http
Content-Type: application/json
Accept: application/json, text/event-stream
Mcp-Session-Id: {{mcpSessionId}}
MCP-Protocol-Version: {{mcpProtocolVersion}}
```

Body 选择 `raw` → `JSON`：

```json
{
  "jsonrpc": "2.0",
  "id": 3,
  "method": "tools/call",
  "params": {
    "name": "getWeather",
    "arguments": {
      "city": "北京"
    }
  }
}
```

`params.name` 使用 `tools/list` 返回的工具名称，`arguments` 的字段与该工具的 `inputSchema` 一致。当前工具为 `getWeather`，接收字符串参数 `city`，可将城市改为 `上海` 或 `深圳`。

在本次 POST 响应中查看 `id: 3` 的 JSON-RPC 响应，天气调用结果位于 `result.content` 中；如果响应为 SSE，则查看事件 `data` 中的 JSON。

如果携带会话 ID 的请求返回 `404`，需要重新发送不带 `Mcp-Session-Id` 的初始化请求，更新保存的变量，再发送 initialized 通知和工具请求。

协议参考：[初始化与 initialized 通知](https://modelcontextprotocol.io/specification/2025-06-18/basic/lifecycle)、[Streamable HTTP 请求头与会话管理](https://modelcontextprotocol.io/specification/2025-06-18/basic/transports)、[工具调用](https://modelcontextprotocol.io/specification/2025-06-18/server/tools#calling-tools)。



4、cursor 接入使用

```json
{
  "mcpServers": {
    "weather-streamable": {
      "url": "http://127.0.0.1:8083/api/mcp",
      "type": "streamableHttp",
      "timeout": 60,
      "disabled": false
    }
  }
}
```

![image-20261015212435812](https://raw.githubusercontent.com/PeterFangXiaoLin/imgs/main/test/20261015212437150.png)



#### MCP 客户端开发

1、引入依赖

```xml
<dependency>
  <groupId>org.springframework.ai</groupId>
  <artifactId>spring-ai-starter-mcp-client</artifactId>
</dependency>
```

2、修改 `application.yml`

```yaml
    mcp:
      client:
        enabled: true
        name: my-mcp-client
        version: 1.0.0
        type: sync
        # 初始化握手也会发出 MCP 请求，首次 npx 下载需要为这两个超时都留出时间。
        request-timeout: 60s
        stdio:
          connections:
            weather-stdio:
              command: java
              args:
                - -jar
                - "D:\\MyCode\\ai-agent\\mcp-server-stdio\\target\\mcp-server-stdio-0.0.1-SNAPSHOT.jar"
        # https://docs.spring.io/spring-ai/reference/api/mcp/mcp-client-boot-starter-docs.html
#          servers-configuration: classpath:mcp-servers.json
        sse:
          connections:
            weather-sse:
              url: http://localhost:8082
              sse-endpoint: /sse
        streamable-http:
          connections:
            weather-streamable:
              url: http://localhost:8083/
              endpoint: api/mcp
```



stdio 也可以通过 servers-configuration: 引用json 配置文件进行配置





## 智能体



### CoT思维链

通过提示词：对于问题需要一步一步思考



### Agent Loop 

while(true) 执行



### ReAct Agent

推理 -> 行动 -> 思考



### Open Manus

分层架构

1、BaseAgent

定义基本的状态信息和执行循环

2、ReactAgent

先思考（先调用一次AI），AI回答说要调用工具

程序调用工具，并把工具结果一起发送给AI，AI觉得是否还要继续调用工具，还是直接输出结果

实现父类的抽象方法

3、ToolCallAgent

扩展完善 ReactAgent 的工具调用能力

4、Manus

实现具体的 agent 能力



#### 核心实现

1、BaseAgent

AgentLoop 实现

使用 模板方法设计模型， 父类定义执行流程，具体执行方法（step）子类实现



2、ReActAgent

实现 step 流程 一个 thinking 一个 Act， 具体的 thinking 和 Act 由子类去实现，也是模板方法设计模式



3、ToolCallAgent

实现 thinking 和 Act

1. thinking: 与 AI交互，并从结果从判断是否要调用工具
2. Act: 执行工具
3. observe: 将工具执行结果告诉 AI



