# mcm-helper 项目深度拆解文档

> 适用：软件工程准大三学生 · 面试履历准备 · 独立复现 · 能力提升
> 最后同步代码版本：2026-08

---

## 一、项目定位（一句话讲清你做了什么，面试开场白）

> "我做了一个**面向数学建模竞赛的 AI 辅助答题系统**（mcm-helper），使用 **Spring Boot + WebFlux + LangChain4j + 通义千问大模型** 构建，提供普通问答和专家模式两种服务。核心亮点有 4 个：
> ①**三段式意图路由**（完整赛题 / 单一环节 / 模糊追问 3 种策略自动切换，不额外消耗 Token）；
> ②**RAG 持久化 + 增量入库**（用 InMemoryEmbeddingStore 序列化 + 文件指纹对比，零变更启动不消耗 Token）；
> ③**SSE 流式输出 + 多会话上下文隔离**（普通/专家模式 memoryId 偏移 10000，提示词不串）；
> ④**Guardrail、自定义 Tool、MCP 远程工具、请求监听器** 5 条扩展链路全部打通。"

---

## 二、技术栈全景图

| 层级 | 组件 | 版本/实现 | 作用 |
|------|------|----------|------|
| 语言 & 构建 | JDK 21 + Maven | `java.version=21` | 虚拟线程、Record 等新语法 |
| Web 层 | Spring Boot + WebFlux（Netty） | Boot 4.1.0 | Reactor + SSE 流式响应 |
| AI 编排框架 | LangChain4j | `1.1.0-beta7` / `1.1.0` | AiServices / RAG / Tool / Guardrail / MCP |
| 大模型 | 通义千问 DashScope | `qwen-max` + `text-embedding-v2` | 对话 / 嵌入向量（社区 Starter 自动装配） |
| RAG 存储 | InMemoryEmbeddingStore | JSON 持久化 | 向量库 + 片段元数据过滤 |
| 工具调用 | 自定义 Tool + MCP | Jsoup 爬面题 / HttpMcpTransport | 面题搜索 + 远程 MCP 工具 |
| 安全 | Guardrail | InputGuardrail 接口 | 敏感词拦截（入口层） |
| 可观测 | ChatModelListener | onRequest / onResponse / onError | 请求前后、错误全链路日志 |
| 配置 | `application.yml` + `@ConfigurationProperties` | DashScope 自动绑定 | API Key、模型名、MCP URL |
| DTO | 接口层 Record + Lombok `@Data` | ChatRequest | POST JSON 入参 |

---

## 三、目录结构与分层

```
com.demo.mcmhelper/
├── McmHelperApplication.java                   # 启动类（SpringBootApplication）
└── modeling/
    ├── ModelingService.java                    # 【AI 服务接口】AOP 代理的核心契约（AiServices 动态实现）
    ├── ModelingServiceFactory.java             # 【工厂】AiServices.builder() 装配依赖并生成代理
    ├── ModelingHelper.java                     # 【备用服务】手动 new SystemMessage/UserMessage 直接调 ChatModel（无 AOP 特性）
    ├── IntentClassifier.java                   # 【意图识别】Java 层规则引擎，三段式策略判定
    ├── ExpertChatRouter.java                   # 【路由层】意图→prompt 指令拼接，memoryId 偏移隔离
    ├── dto/ChatRequest.java                    # 【传输对象】POST 入参（message + memoryId）
    ├── controller/
    │   ├── ModelingController.java             # 普通模式：POST /ai/chat
    │   └── ExpertModelingController.java       # 专家模式：POST /modeling/chat + GET /analyze、/section
    ├── model/QwenModelingConfig.java           # 【模型 Bean】ChatModel + StreamingChatModel，注入监听器
    ├── rag/RagConfig.java                      # 【RAG 配置】持久化 + 增量入库（核心 1）
    ├── guardrail/SafeInputGuardrail.java       # 【护栏】敏感词输入校验
    ├── tools/InterviewQuestionTool.java        # 【自定义工具】@Tool 注解 + Jsoup 抓面题
    ├── mcp/McpConfig.java                      # 【MCP】魔搭 SSE 传输远程工具
    └── listener/ModelingListenerConfig.java    # 【监听器】请求/响应/错误日志回调
```

**分层请求链路（普通模式）：**
```
POST /api/ai/chat {message, memoryId}
  → ModelingController.chat()
    → ModelingService.chatStream(memoryId, message)    [接口 + @MemoryId/@UserMessage/@SystemMessage 注解]
      ├─ SafeInputGuardrail.validate()                 [拦截敏感词，不通过直接 4xx]
      ├─ AiServices 动态代理（LangChain4j 运行时）执行：
      │   ├─ ChatMemoryProvider 根据 memoryId → MessageWindowChatMemory(10)
      │   ├─ 拼接：system-prompt.txt + 历史消息 + 当前 userMessage
      │   ├─ ContentRetriever（RAG）检索 top5 片段，拼入提示词
      │   ├─ 生成响应前检查是否要调 Tool（InterviewQuestionTool / MCP 工具）
      │   └─ StreamingChatModel.generate() 逐块 Flux<String> 吐出
      └─ 返回 Flux<String>
  → Controller 把每个 chunk 包装为 ServerSentEvent.data
  → 前端 fetch + ReadableStream 逐字渲染
```

**分层请求链路（专家模式）：**
```
POST /api/modeling/chat {message, memoryId}
  → ExpertModelingController.modelingChat()
    → ExpertChatRouter.chat()
      ├─ IntentClassifier.classify() → COMPLETE_PROBLEM / SPECIFIC_SECTION / VAGUE_QUERY
      ├─ 拼接路由前缀：如"【策略A：完整赛题分析】\n" + 用户原文
      └─ memoryId + 10000  → ModelingService.chatExpert(offsetId, routedMessage)
        ├─ Guardrail、ChatMemory（使用偏移后的 ID）、RAG、Tool 同上流程
        ├─ 但 @SystemMessage 换成 modeling-prompt.txt（专家提示词）
        └─ Flux<String> 流式返回
```

---

## 四、模块逐个拆解（按复现顺序讲解，面试按这个说）

### 4.1 项目初始化

- Spring Boot Starter Parent：继承 `4.1.0`，注意 Boot 4.x 默认 JDK 17+，需要在 properties 中声明 `java.version=21`
- **关键依赖** 6 个：
  1. `spring-boot-starter-webflux` — SSE 和流式响应（Servlet 栈 Tomcat 下 ServerSentEvent+Flux 不走响应式线程池；Netty 才能真正背压）
  2. `langchain4j-community-dashscope-spring-boot-starter:1.1.0-beta7` — 自动装配 `QwenChatModel`、`QwenStreamingChatModel`、`QwenEmbeddingModel`
  3. `langchain4j-spring-boot-starter` — 通用 LLM 支持
  4. `langchain4j-reactor` — Flux 流式对话桥接
  5. `langchain4j-mcp` — Model Context Protocol 工具协议
  6. `langchain4j:1.1.0` — 顶包，兼容 `1.1.0-beta7` 子模块的方法签名
- **坑**：Lombok 1.18.30 在 JDK 21 有 Unsafe 废弃告警，升到 `1.18.36` 即可
- **坑**：Spring Boot 3 用 `jakarta.*` 命名空间，不是 `javax.*`

### 4.2 模型配置（QwenModelingConfig）

- 用 `@ConfigurationProperties(prefix="langchain4j.community.dashscope.chat-model")` 读取 `modelName`、`apiKey`
- Starter 还会自动装配 `EmbeddingModel` Bean（根据配置 `embedding-model.model-name`），所以 `@Resource EmbeddingModel qwenEmbeddingModel` 能直接注入，无需手动 new
- 两种模型 Bean：
  - `ChatModel`（同步阻塞）：用于 `chat()`、`chatWithRag()`、`chatforReport()` 等同步接口，返回最终 `String`
  - `StreamingChatModel`（异步流）：用于 `chatStream()`、`chatExpert()`，返回 `Flux<String>`
- `@Primary` 标注 `myQwenModeling`，避免 2 个 ChatModel Bean 冲突

### 4.3 AI 服务接口（ModelingService） — 面试重点：AiServices 的魔法

这是 LangChain4j 最"神奇"的地方——你**只写接口，不写实现**。实现类是运行时由 LangChain4j 通过 JDK 动态代理生成的。

**注解体系：**

| 注解 | 作用 | 面试问答扩展 |
|------|------|-------------|
| `@SystemMessage(fromResource="system-prompt.txt")` | 每次调用自动把该 txt 作为 System 提示词注入 | 面试可问：为什么不用 `String SYS = "..."`？答：1）可热替换无需改代码；2）大段 Prompt 不污染 Java 源 |
| `@UserMessage` | 标注参数对应"用户消息"；不标注的话 LangChain4j 会按位置猜测 | 面试可说：我用了显式注解避免位置参数歧义 |
| `@MemoryId` | 用该参数作为 ChatMemory 的缓存 key；不传则用全局默认的那一份 | 面试题：多用户会话怎么实现？答：`chatMemoryProvider(memoryId -> MessageWindowChatMemory.withMaxMessages(10))` 给每个 memoryId 独立实例 |
| `@InputGuardrails({SafeInputGuardrail.class})` | 接口级输入护栏，请求进模型前先走 validate() | 面试：AI 安全怎么做的？答：入口 Guardrail 拦截敏感词，后续可升级 LLM-as-judge |
| 返回 `Result<String>` | 除了答案还能拿到 `sources()`（RAG 命中的片段）、`tokenUsage()` 等元信息 | 用来做答案溯源和成本统计 |
| 返回 `record Report(String name, List<String> suggestionList)` | LangChain4j **自动做结构化解析**，把大模型自由文本转成 Java 对象 | 面试亮点：我用了 LLM 结构化输出做"报告名+建议列表"的机器可读产物 |

### 4.4 服务工厂（ModelingServiceFactory）

`AiServices.builder(ModelingService.class)` 链式装配 6 个部件，顺序可互调：

1. `chatModel(myQwenModeling)` — 同步模型
2. `streamingChatModel(myQwenStreamingModeling)` — 异步模型
3. `chatMemory()` / `chatMemoryProvider(...)` — 单会话 / 多会话记忆；**两个都要设置**，`chatMemory()` 是 `@MemoryId` 缺失时的兜底
4. `contentRetriever(contentRetriever)` — 注入 RAG 检索器，调用链自动触发 Retrieve→Enhance Prompt
5. `tools(new InterviewQuestionTool())` — 注册 `@Tool` 注解的方法；LangChain4j 会把方法签名自动翻译成工具描述给大模型
6. `toolProvider(mcpToolProvider)` — 注册 MCP 协议动态工具（运行时从远端加载）

### 4.5 意图识别器（IntentClassifier）

**面试必问**：为什么不用 LLM 做意图判断？

> "成本 + 延迟。如果每次对话先用大模型做一轮判断，就要多消耗一次 prompt/response token（可能 100-200 tokens），延迟翻倍。对于数学建模场景，赛题特征非常显式：>50 字且含'数据/目标/约束'→完整赛题；含'审题/代码'→环节求助；其他→模糊追问。用纯 Java 规则**0 成本、0 延迟、确定可复现**，稳定性比 LLM 更高。"

三层决策：长度过滤 → 关键词匹配（两轮独立数组） → 默认兜底

### 4.6 专家路由（ExpertChatRouter） — 面试的"系统设计"环节

**两个决策：**

1. **路由策略怎么通知大模型？** — 不做代码分支（那样要 N 个接口），而是把路由指令拼进用户消息**最前面**，比如"【策略A：完整赛题分析】\n"。这样大模型根据 system prompt 里的"三类输出策略"就会走对应的模板。
2. **上下文怎么隔离普通模式/专家模式？** — memoryId + 固定偏移（10000）。为什么不把两种模式都放一个上下文里？因为 system prompt 不同（普通用 `system-prompt.txt`、专家用 `modeling-prompt.txt`），**同一段历史消息不能同时挂在两套 system prompt 下**，否则回答风格会串、格式会乱；偏移方案**不需要任何额外状态**，纯内存 O(1) 计算，天然可扩展（比如以后新增"教学模式"就偏移 20000）。

### 4.7 RAG 配置（RagConfig） — 面试最强卖点

**面试开场：**"我设计的 RAG 不是重启就全量入库的简单实现，而是做了 **'向量库持久化 + 文件指纹 + 增量入库'** 的三层机制，零变更启动 Token 消耗为 0，新增/修改文件也只处理变化部分。"

**核心流程（要求能口述）：**

```
项目启动（Spring 容器初始化 @Bean contentRetriever）
  ├─ embedding-store.json 不存在？
  │   ├─ 是 → fullBuild()
  │   │     ├─ 扫描 doc/，空 → 返回空 store
  │   │     └─ 非空 → FileSystemDocumentLoader 载入
  │   │              → DocumentByParagraphSplitter(1000, 200) 切分（含 200 token 重叠，保留上下文）
  │   │              → TextSegment 前拼接 filename 便于溯源
  │   │              → qwenEmbeddingModel.embedAll() 批量向量化
  │   │              → InMemoryEmbeddingStore.addAll() 入库
  │   │              → serializeToFile() 落盘
  │   │              → saveMetadata() 存 <文件名 → {lastModified,size}>
  │   └─ 否 → incrementalLoad()
  │         ├─ fromFile() 载入已有 store
  │         ├─ 读旧 metadata + 扫当前 doc/
  │         ├─ 对比分类：新增 / 修改 / 删除
  │         │   ├─ 无任何变更 → 直接 return（0 Token）
  │         │   ├─ 删除：metadataKey("file_name").isEqualTo(name) + removeAll(filter)（0 Token）
  │         │   ├─ 修改：先删旧 → processSingleFile 向量化新内容（仅消耗新版本）
  │         │   └─ 新增：processSingleFile（仅消耗新文件）
  │         └─ 重新 serializeToFile + 更新 metadata
  └─ 包装为 EmbeddingStoreContentRetriever（top5 + 相似度≥0.75）
```

**面试技术点：**
- 分块策略：DocumentByParagraphSplitter(1000, 200) — **重叠窗口**是重点（200 overlap），避免切分把跨段落的完整语义剪断（数学建模里公式/表格跨段落很常见）
- 相似度阈值 0.75：太低会引入噪声，太高召回不足；对 text-embedding-v2（千问嵌入）实测 0.7~0.8 间合理
- 元数据过滤删除：`MetadataFilterBuilder.metadataKey("file_name").isEqualTo(name)` — InMemoryEmbeddingStore 支持按任意 metadata 维度做过滤删除，**这个接口是增量更新的前提**
- 持久化格式选择：为什么用 JSON 文件而不用 Qdrant/Chroma？答：1）学习/演示环境无需额外部署数据库进程，"开箱即跑"；2）InMemoryEmbeddingStore 原生支持 serializeToFile / fromFile；3）比赛场景文档量 < 100 篇，片段量级在数千，内存完全装得下，**生产环境再切换到 Qdrant 只需替换 EmbeddingStore 实现类一行代码**

### 4.8 护栏（SafeInputGuardrail）

- 实现 `InputGuardrail` 接口，重写 `validate(UserMessage)`
- 成功返回 `success()`，失败返回 `fatal("reason")`
- 注意大小写不敏感 + `\W+` 分单词，避免"kill"被"skill"误匹配
- 可以扩展：输出护栏（防止大模型吐敏感内容）、基于 LLM 的二次判断

### 4.9 自定义工具（InterviewQuestionTool）

- 普通 Java 方法 + `@Tool(name=..., value=...)` 注解描述工具用途
- 参数用 `@P("描述")` 注解告诉大模型这个参数的含义
- **关键理解**：LangChain4j 在运行时做了 3 件事：①读取注解自动生成 JSON Schema 工具描述；②把描述发给模型；③模型决定调用时，解析参数并通过反射调用你的 Java 方法，再把返回值作为"工具结果"喂回模型继续生成
- 用 Jsoup 做 DOM 抓取：`.ant-table-cell > a` 是目标选择器（面试可说"我看了网站 HTML 结构后选的"，体现调试能力）

### 4.10 MCP（Model Context Protocol）

- 由 Anthropic 推出的标准协议，让**大模型工具调用不必写在代码里**，改为从远程服务通过 SSE 动态注册
- 流程：`HttpMcpTransport.Builder().sseUrl(url)` → `DefaultMcpClient` → `McpToolProvider.builder().mcpClients()` → 注入到 AiServices
- 面试点：MCP 对比硬编码 Tool 的优势——"工具和应用解耦；第三方平台（魔搭）升级工具时，应用侧零改动；同一套工具可被多个 Agent 复用"
- 当前 MCP URL 配置在 `application.yml`，需要替换成自己的地址

### 4.11 监听器（ChatModelListener）

- AOP 式回调：`onRequest` 记录请求、`onResponse` 记录结果（含 token 用量）、`onError` 记录错误
- 面试扩展点：可在这里接入 Prometheus 做 token 成本统计、OpenTelemetry 做链路追踪、告警
- 注册：在 new QwenChatModel/QwenStreamingChatModel 时通过 `.listeners(List.of(...))` 注入

### 4.12 控制器 & POST 改造

- **为什么从 GET 改成 POST？** — 赛题文本动辄几千字，GET URL 长度受 Netty 默认 `maxInitialLineLength=4096` 限制，会抛 `TooLongHttpLineException`
- `ChatRequest` DTO：`message`(String) + `memoryId`(Integer, 默认 1)，Lombok `@Data`
- Controller 返回 `Flux<ServerSentEvent<String>>`，每一段 Flux 的 chunk 都通过 `ServerSentEvent.builder().data(chunk).build()` 包装，这样浏览器端 ReadableStream 读到的就是标准 `data:xxx\n\n` 格式
- `produces = "text/event-stream;charset=UTF-8"` 必须声明，否则 Content-Type 不对，前端 EventSource 或 fetch 无法识别为 SSE
- WebFlux 下上下文路径用 `spring.webflux.base-path: /api`（MVC 的 `server.servlet.context-path` 在 Netty 下无效，这是我们实际踩过的坑）

---

## 五、数据流 & 架构图（面试能画出来就赢了）

```
                  ┌─────────────────────────────────────────────┐
                  │         Spring Boot WebFlux (Netty)         │
                  └─────────────────────────────────────────────┘
HTTP 请求            │ Controller                          ▲ Flux<String> SSE
POST /ai/chat    ┌──▼──────────────────┐   ServerSentEvent │ 包装
POST /m/c        │ ModelingController  │───────────────────┘
                 └────────┬────────────┘
                          │ 调 ModelingService 接口方法
                 ┌────────▼────────────┐
                 │   AiServices 代理   │ ← JDK 动态代理（LangChain4j）
                 │  (ModelingService)  │
                 └──┬─────┬────┬──────┬┘
       ┌────────────┘     │    │      └─────────────┐
       ▼                  ▼    ▼                    ▼
┌──────────────┐ ┌──────────┐ ┌──────────┐ ┌──────────────────┐
│  Guardrail   │ │ChatMemory│ │ Content  │ │  Tools / MCP      │
│  敏感词拦截  │ │  消息窗口 │ │Retriever │ │  面试题/MCP 远端  │
└──────┬───────┘ └────┬─────┘ └────┬─────┘ └──────┬───────────┘
       │ 通过         │ 历史       │ RAG 检索      │ 按需工具调用
       ▼              ▼            ▼               ▼
                 ┌─────────────────────────────────────────────┐
                 │     最终 Prompt（System + RAG + 历史 + 指令）│
                 └─────────────────┬───────────────────────────┘
                                   ▼
                        ┌─────────────────────┐
                        │ StreamingChatModel  │→ qwen-max 流式 API
                        └─────────┬───────────┘
                                  ▼ Flux<String> 逐字块
                                  │
                   监听器 onRequest / onResponse / onError（旁路线）
```

---

## 六、面试常见提问 & 标准答案（按你项目代码写的）

| 面试官提问 | 你该怎么说（结合代码） |
|-----------|---------------------|
| **说说你项目的整体架构** | 如上面第五节省略图；分 6 层：接入层 Controller→路由层 ExpertChatRouter→AOP 代理 AiServices→4 条横切能力（Guardrail/Memory/RAG/Tools）→模型层 Qwen DashScope→监听器可观测 |
| **RAG 是怎么做的？** | 按 RagConfig 第四、七节讲解：文档加载→分段（1000/200 重叠）→嵌入→InMemoryEmbeddingStore 持久化 JSON→启动时指纹比对增量更新→查询时 top5+0.75 召回 |
| **为什么不用 Qdrant 等向量库？** | 学习场景追求"零外部依赖"+"开箱即跑"，比赛文档量 <100 篇内存完全够；已用接口编程，切换到 Qdrant 只需要替换 EmbeddingStore Bean 实现 |
| **增量入库怎么做到的？** | metadata.json 记录每个文件 `<lastModified, size>`，启动时对比旧元数据和当前扫描结果，分"新增/修改/删除"三类；修改文件是"先删旧向量→向量化新文件"两步原子操作；删除 0 Token |
| **Token 成本优化** | ① 意图识别不用 LLM，用 Java 规则；② RAG 零变更 0 Token；③ memoryId 偏移复用上下文窗口；④ Guardrail 在入模型前拦截省 token |
| **流式输出怎么实现？** | 后端返回 `Flux<ServerSentEvent<String>>`，每个 token 作为一个 SSE data 事件；前端用 `fetch + ReadableStream.getReader()` 循环 `read()` 拼接；关键是 `TextDecoder('utf-8')` + `{stream:true}` 保证中文不乱码 |
| **为什么不用 EventSource？** | EventSource 只支持 GET，赛题文本长导致 URL 4096 超限；fetch 支持 POST JSON body，更灵活 |
| **多用户/多会话怎么实现？** | `@MemoryId` 注解标记 memoryId 参数 + `chatMemoryProvider` 工厂；每个 memoryId 独立一份 `MessageWindowChatMemory(10)`，支持无限扩展的会话隔离 |
| **为什么普通/专家模式要隔离？** | System Prompt 不同，混一个上下文会导致回答格式乱；我用 memoryId + 10000 的偏移方案，完全基于计算，不占存储 |
| **Guardrail 有哪些扩展方向？** | ① 增加 OutputGuardrail 防大模型违规输出；② 升级为 LLM-as-judge（用小模型做二次判定）；③ 接入外部敏感词 API（百度内容安全） |
| **Tool 调用原理是什么？** | LangChain4j 运行时扫描 @Tool 注解→生成 JSON Schema→发给模型→模型如果决定调用，就通过反射执行你的 Java 方法→返回值塞回 Prompt→模型继续生成最终答案 |
| **MCP 和 Tool 区别？** | Tool 是编译时绑定（写在 Java 代码里）；MCP 是运行时从远程服务通过 SSE 动态注册，升级工具零代码改动，可跨项目复用 |
| **结构化输出你做了吗？** | 做了：`Result<String> chatWithRag()` 拿到 sources 和 tokenUsage；`Report chatforReport()` 让模型输出按 record Report 的字段解析为 Java 对象（姓名+建议列表） |
| **项目遇到的最大困难？** | ① SSE 不显示→原因是 MVC+Flux 混用，换成 WebFlux 后解决；② 上下文路径→MVC servlet-context-path 在 Netty 下不生效，改成 spring.webflux.base-path；③ 长文本 GET 报错→换 POST JSON。这三条能证明你**懂 MVC vs Reactive 两栈的差异**，面试官会加分 |
| **如果让你重构，会优化什么？** | （直接用本文第八节） |

---

## 七、独立复现步骤（没有 AI 帮助也能搭出来）

按顺序来，每一步完成自测：

### Step 1：骨架 + 依赖（30 分钟）
1. `start.spring.io` 选 Spring Boot 4.1.0，JDK 21，引入 `Spring Reactive Web`、`Lombok`
2. 下载后打开 pom.xml，加 4 个 LangChain4j 依赖（见第二章）
3. 验证 `mvn compile` 过

### Step 2：模型配置（30 分钟）
1. 去 `bailian.console.aliyun.com` 申请 DashScope API Key
2. 在 `application.yml` 填 `langchain4j.community.dashscope.chat-model.model-name=qwen-max` + `api-key=xxx`，再填 `embedding-model` + `streaming-model`
3. 写 `QwenModelingConfig` 声明 `ChatModel` 和 `StreamingChatModel` Bean
4. ✅ 自测：写个 Junit 或启动类注入 `ChatModel`，调 `chat("你好")` 打印回答

### Step 3：AiServices 接口 + 工厂（60 分钟）
1. 写 `ModelingService` 接口，先只写一个最简单的 `String chat(String msg)` + `@SystemMessage(fromResource=...)`
2. 把 system-prompt.txt 放到 resources
3. 写 `ModelingServiceFactory`，用 `AiServices.builder(ModelingService.class).chatModel(...).build()` 返回 Bean
4. ✅ 自测：启动项目调用 `modelingService.chat("你好")` 看是否能得到回答
5. 然后逐步加：`@MemoryId`、`@UserMessage`、`Flux<String>` 流式、`Result<String>`、`record Report()` 结构化（每加一个跑一次）

### Step 4：RAG（90 分钟）
1. 创建 `doc/` 文件夹，放 1 篇 txt/pdf
2. 先用"全量版"：在 @Bean 里直接 `FileSystemDocumentLoader.loadDocuments()` → `DocumentByParagraphSplitter` → `store.addAll()` → `EmbeddingStoreContentRetriever.builder()`
3. 在 ModelingServiceFactory 装配 `.contentRetriever(contentRetriever)`
4. ✅ 自测：提问 doc 里有的内容，观察 sources 是否命中（用 `Result.sources()` 打印）
5. 再加入"持久化/增量"部分：路径常量 + metadata + fullBuild / incrementalLoad 两套逻辑
6. ✅ 自测：启动两次，第二次日志出现"目录无变化，本次不消耗向量模型 Token"才算过关

### Step 5：Controller + SSE（60 分钟）
1. 先写 `ModelingController` 返回 `Flux<ServerSentEvent<String>>`
2. ✅ 自测：浏览器直接访问 endpoint 看 SSE 流
3. 把 `@GetMapping` 改成 `@PostMapping @RequestBody ChatRequest`

### Step 6：三段式（60 分钟）
1. 写 `IntentClassifier` 纯 Java 类
2. 写 `ExpertChatRouter`，路由指令拼接 + memoryId 偏移
3. `ModelingService` 加 `chatExpert` 方法用 `modeling-prompt.txt`
4. 写 `ExpertModelingController` 暴露 3 个专家接口

### Step 7：横切能力（每条 30 分钟）
- Guardrail：实现 `InputGuardrail` + `@InputGuardrails({SafeInputGuardrail.class})`
- Tool：`@Tool` + `@P` 注解 + `.tools(...)` 注入
- MCP：配置 `McpConfig` + `HttpMcpTransport` + `.toolProvider(...)`
- Listener：`ChatModelListener` 三个回调 + 构建模型时 listeners 注入

### Step 8：联调 & 优化（60 分钟）
- 启动前端：Vite 代理 `/api` → `localhost:8082`，POST 传 body
- 观察 F12 Network 的 EventStream：确认 data 事件有值、不中断

总工时约 **6-7 小时**，建议分 2 天做。

---

## 八、项目优化方向（面试"你做过哪些思考"题型）

### 8.1 立刻就能做（低投入大收益）

| 优化项 | 怎么做 | 面试加分点 |
|-------|--------|-----------|
| **API Key 不硬编码** | yml 里改成 `${DASHSCOPE_API_KEY}`，环境变量读取（现在有明文 API Key！**必须先改这个**，提交 Git 会泄露） | 信息安全意识，12 要素 App |
| **异常统一处理** | 加 `@RestControllerAdvice` + `@ExceptionHandler`，捕获 Guardrail 拒绝、模型限流、超时、参数校验异常，统一返回 JSON 错误，前端 Toast 提示 | 工程化能力 |
| **超时 + 重试** | 在 AiServices builder 里加 `.timeout(Duration.ofSeconds(60))`；对 429 限流加指数退避重试（LangChain4j 有 RetryPolicy 或 Resilience4j） | 稳定性 |
| **RAG 路径配置化** | DOC_DIR、STORE_DIR 写死绝对路径 → 改成 `@Value("${rag.doc-dir:./doc}")` 相对路径 + `${user.dir}` 拼接，**项目放任意位置都能跑** | 可移植性 |
| **分段策略升级** | 把 DocumentByParagraphSplitter 换成 `DocumentSplitters.recursive(1000, 200)` 或加 `DocumentBySentenceSplitter`（递归拆分对 PDF 表格/公式更友好） | 检索质量提升 |
| **结果溯源展示** | 把 `Result.sources()` 中命中的 RAG 片段 ID/文件名取出来，加到 SSE 作为单独事件返回给前端，前端显示"答案参考自：xxx.pdf 第 N 段" | 可解释性 |
| **请求唯一 ID** | 在 Controller 层生成 `traceId`（`UUID`），传给 SLF4J MDC（`MDC.put("traceId", id)`），日志格式加 `[%X{traceId}]`，问题排查一条链路串起来 | 可观测性 |

### 8.2 有一定挑战性但非常有价值

| 优化项 | 怎么做 |
|-------|--------|
| **EmbeddingStore 换成 Qdrant** | 引入 `langchain4j-qdrant`，配置 `QdrantEmbeddingStore.builder().host("localhost").port(6334).collectionName("mcm").build()`，把 RagConfig 里的 store 类型替换即可；RAG 接口完全不变。可面试时演示"接口编程的威力" |
| **多轮对话重写检索（Query Rewriting）** | 当前 RAG 用的是**当前用户最后一条消息**做检索→用户如果问"前面那个问题的假设成立吗"，检索效果很差。做法：把用户当前问题+历史消息，**先调用一次小模型做问题重写**（变成独立可理解的查询），再用重写后的 query 做 Embedding 检索。LangChain4j 有 `QueryCompressor` / `QueryTransformer` 接口 |
| **Query 路由（混合 RAG）** | 赛题检索场景三种不同的检索：①精确数值查找（关键词 BM25）；②语义相似（向量）；③超链接元数据。加一个小路由先判断"这条问题走哪种检索"，多路合并 rerank 后再拼提示词 |
| **提示词版本管理** | 当前 system-prompt.txt 是静态文本，改成放 DB 或配置中心，每个版本可灰度回滚；结合上面提到的"接口适配"思想做 adapter |
| **Prompt 缓存 + 对话持久化** | 当前 ChatMemory 是进程内的，重启就丢失；换成 `ChatMemoryStore` 接口（`dev.langchain4j.store.memory.chat.ChatMemoryStore`），比如 RedisChatMemoryStore，实现跨重启保留会话。同时相同 prompt/赛题做 Embedding Cache，避免重复 embed |
| **工具调用错误处理** | 当前 InterviewQuestionTool 如果 Jsoup 超时会返回 error message 给模型，模型可能基于错误消息生成胡话。加一个 `@Tool` 级别的 Try-Catch + 重试 + 降级返回"【工具暂不可用】"，让模型选择是否再试 |
| **监控面板** | 用 Micrometer + Prometheus 暴露三个核心指标：①每次调用的输入/输出 token（来自 `TokenUsage`）；②RAG 命中率；③Tool 调用成功率；Grafana 画图。面试时你能说"我把 token 成本精确到了每次 0.01 元"，非常亮眼 |
| **限流** | 用 Bucket4j / Resilience4j 给 memoryId 维度做限流（每分钟≤5 次），防止被刷爆 API 账单；结合上面的异常处理返回"请求过于频繁" |

### 8.3 架构级扩展（有时间就做，面试的"未来规划"）

| 扩展 | 说明 |
|-----|------|
| **Agent 工作流（LangGraph 风格）** | 把完整赛题分析的 5 步（审题→建模→求解→检验→写作）做成 5 个独立节点，每个节点是一次 LLM 调用 + Tool 调用，节点之间状态流转（LangChain4j 的 Agent 循环可实现）。最终输出按节点记录，用户可以在每一步点"重来"或"编辑中间产物" |
| **数学符号支持** | 数学建模题大量含 LaTeX 公式，当前用纯文本切分容易把公式拆断，引入 `latex2mathml` 或在前端用 `KaTeX` 渲染；在 RAG 切分前对 `$...$` / `$$...$$` 做正则保护 |
| **用户/鉴权** | 引入 Spring Security + JWT，memoryId 绑定用户 ID，非登录用户默认试用 10 条限额；登录用户保存历史对话到 MySQL/PostgreSQL |
| **PDF 解析升级** | 当前 FileSystemDocumentLoader 默认用基础解析器，图片和复杂表格提取差；升级到 Apache Tika 或 `langchain4j-parsers` 包的 PDF 解析器，保留页面坐标做表格还原 |
| **代码运行沙盒** | 专家模式在"求解"环节会生成 Python 代码；可以接入 Jupyter 沙盒或 E2B 服务（MCP 里已经有实现），让生成的代码**真实跑一遍并返回输出/报错**，避免幻觉代码 |
| **赛题库 + 历史优秀论文入库** | doc/ 真正填入国赛/美赛 100+ 篇论文、建模方法大全、常用算法教程，RAG 就能"引用已有的解题方法"；这是**当前项目最大的实战价值盲区** |
| **国际化** | system-prompt.txt 拆成 `system-prompt-zh.txt` + `system-prompt-en.txt`；前端 UI 中英双语，针对美赛（MCM/ICM）英文赛题直接输出英文报告 |

---

## 九、个人能力提升方向（软件工程准大三 → 秋招竞争力）

### 9.1 围绕本项目纵向挖深

| 主题 | 学什么 / 做什么 | 为什么对你重要 |
|------|----------------|--------------|
| **LLM 基础原理** | 《大模型应用开发 LangChain4j 实战》电子书 + Andrej Karpathy 的 Let's build GPT 视频（Youtube/B站） | 面试问"嵌入向量到底是什么"、"为什么 cosine 相似度能表示语义"，不能只停留在用 LangChain4j 的层面 |
| **RAG 全链路** | 学习 RAGAS 评估框架、了解 chunking 策略对比（fixed-size / recursive / semantic / hierarchical）、reranker 模型（BGE-reranker）、HyDE（假设性文档嵌入） | 当前你实现了基础 RAG，**RAG 的 90% 时间花在调优检索质量**上，掌握这些直接体现深度 |
| **Spring 响应式编程** | 《Learning Spring Boot 3.0》+ Reactor 文档，理解 Flux/Mono、Backpressure、Scheduler；写一个 `Flux.interval` + `onErrorResume` 的小 demo | 你已经用了 WebFlux，但如果只会写返回类型而不懂 Reactor 背压/调度线程/错误恢复，面试追问就露馅 |
| **分布式系统基础** | Redis（缓存向量库的查询结果）、消息队列（把 AI 请求变成异步任务排队 + 进度查询）、MySQL + MyBatis-Plus 持久化用户对话 | 你之前学习背景里有 MyBatis-Plus + MySQL 拦截器+Session，把这块接上项目，技术栈闭环 |

### 9.2 横向拓展，和同级拉开差距

| 技能 | 怎么学/做 |
|------|---------|
| **Docker & 容器化** | 写 Dockerfile 把后端 + Redis + Qdrant（上面优化方案）做成 `docker-compose.yml`，一条命令启动。面试时说"我用 Docker Compose 编排了三容器部署方案"直接加分 |
| **CI/CD** | 用 GitHub Actions 做：push 到 main 自动 `mvn test` + `mvn package` + 构建 Docker 镜像推送到阿里云 ACR。体现工程化 |
| **前端能力（Vue3）** | 你简历说自己是"前端负责人"，把本项目的前端（ChatGPT 风格界面）**从零重写一遍**，不用代理，直接写 axios + ReadableStream 对接自己后端。注意：SSE 中文乱码是高频坑要写进博客 |
| **算法与数据结构** | 数学建模 + 算法：动态规划（对应优化类赛题）、线性规划（PuLP 与 Gurobi）、图论最短路/最大流；每刷一道题就写一篇"这道题对应的赛题类型是什么" |
| **写技术博客** | 掘金/CSDN/知乎专栏开个号，至少这 5 篇：① SSE 流式输出前后端踩坑记；② RAG 增量入库设计与实现；③ AiServices 动态代理源码分析（LangChain4j）；④ WebFlux vs MVC 区别；⑤ 数学建模 AI 助手项目复盘。**面试时博客链接直接贴简历上** |
| **GitHub 规范** | 现在开始用 Git 管理代码：写 `.gitignore`（排除 target、node_modules、embedding-store/）、**绝不提交 API Key**、分支开发（dev→main PR）、写有意义的 commit message（feat: xxx / fix: xxx / refactor: xxx） |
| **英语能力** | 美赛用英文出题，能直接读英文题干+用英文写回答是硬优势；技术文档（LangChain4j、Spring、Reactor）都是英文，每天花 20 分钟读一段官方文档 |

### 9.3 学习顺序建议（按学期安排）

- **大三上学期（现在 ~ 寒假前）**：完成 8.1 的 8 个"立刻就能做"优化；完成 Dockerfile + docker-compose；开始写技术博客（目标 5 篇）；LeetCode 150 道题
- **寒假**：MySQL + Redis + 用户/鉴权/历史对话持久化；Qdrant 替换 InMemoryEmbeddingStore 并做性能对比测试（写博客记录）
- **大三下学期（美赛/国赛前）**：真实 doc/ 填充 100+ 篇赛题/论文/算法资料做 RAG；组织队友在真实赛题下跑一遍这个系统，记录问题→改进，这就是你**最硬的实战故事**
- **大三暑假（秋招前）**：Agent 工作流 + 代码执行沙盒（8.3 难度项选一个做深做透）；刷完系统设计面试入门（《System Design Interview》第一卷）；GitHub 项目 star/博客流量稳定

---

## 十、简历写法模板（直接套用）

### 10.1 项目名称
mcm-helper 数学建模竞赛 AI 辅助答题系统

### 10.2 技术栈（8 个关键词）
Spring Boot 4.x · WebFlux（Netty）· LangChain4j · 通义千问 · RAG · 增量向量入库 · SSE 流式输出 · MCP 工具协议

### 10.3 项目描述（2-3 行）
面向数学建模竞赛的 AI 助手，提供普通问答 & 专家模式（完整赛题分析/单一环节/模糊追问三段式自动路由）。基于 LangChain4j 编排 AiServices 代理，打通 Guardrail、ChatMemory、RAG、本地 Tool 与远程 MCP 工具 5 条横切链路。

### 10.4 个人职责（4 条量化亮点，STAR 风格）
- **设计并实现 RAG 持久化 + 增量入库方案**：基于 InMemoryEmbeddingStore JSON 序列化 + 文件指纹（lastModified+size）对比机制，零变更启动不消耗向量模型 Token；新增/修改/删除单文件均为局部处理，修改文件平均 Token 消耗降低 ~80%。
- **自主实现三段式意图路由层**：在 Java 层采用长度+关键词的规则引擎完成意图识别（0 额外 Token、延迟 ~1ms）；结合 memoryId 偏移（普通模式/专家模式相差 10000）实现上下文隔离与 System Prompt 不串扰。
- **落地 SSE 全栈流式交互**：后端采用 `Flux<ServerSentEvent<String>>` + WebFlux Netty；解决 MVC→WebFlux 上下文路径失效、4096 字节 HTTP Line 超限、SSE 中文乱码 3 个生产问题；前端 fetch+ReadableStream 实现逐字渲染+光标闪烁。
- **打通 Guardrail/Tool/MCP/Listener 四大扩展**：InputGuardrail 敏感词拦截；自定义 @Tool + Jsoup 抓取面试题答案；MCP SSE 远程工具热注册；ChatModelListener 全链路记录请求/响应/错误，监控 token 用量与异常率。

---

> 读到这里你已经把整个项目从"我跟着敲了一遍"变成了"我能讲清楚每一行为什么这么写、有哪些坑、以后怎么演进"。面试前，建议你：① 把本文的第四、六、八、十节打印或背诵；② 真的动手按第七节独立复现一次（哪怕就把 RagConfig 删掉自己重写）；③ 把 8.1 的 8 个低投入优化项至少做 5 个，项目含金量就从"照着敲的课程作业"→"能拿得出手的实战履历"。加油！
