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

