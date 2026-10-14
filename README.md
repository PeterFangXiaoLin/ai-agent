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
| 接口文档 | Knife4j / OpenAPI 3 | 4.4.0 | 生成与增强 API 文档 |
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
