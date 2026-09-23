# MCM-AI-Helper 数学建模 AI 问答小助手

基于 **LangChain4j** 框架的数学建模智能问答助手，面向备战数学建模竞赛的用户，提供**普通问答**与**专家模式**两种交互方式，通过 SSE 流式输出 AI 回答，用于模型讲解、算法对比、代码调试、论文写作等场景。

## 项目结构

```
mcm-helper
├── mcm-helper-backend    # 后端：Spring Boot WebFlux + LangChain4j
└── mcm-helper-frontend   # 前端：Vue 3 + Vite
```

## 技术栈

| 端 | 技术 |
| --- | --- |
| 后端 | Spring Boot WebFlux、LangChain4j 1.1.0、LLM（阿里云 DashScope Qwen）、Reactor、MCP、RAG、jsoup、Java 21 |
| 前端 | Vue 3、Vite 5、Axios、marked、highlight.js、KaTeX |

## 功能特性

- **普通问答模式**：面向数学建模备赛的日常问答，由系统提示词约束 AI 职责范围（模型原理、算法对比、代码调试、论文写作）。
- **专家模式**：针对完整赛题做整体分析与分段回答，内置意图分类与路由（`ExpertChatRouter`、`IntentClassifier`）。
- **流式输出**：所有回答均以 SSE（Server-Sent Events）流式返回，前端实时渲染。
- **Markdown 渲染**：前端使用 marked 渲染富文本、highlight.js 代码高亮、KaTeX 数学公式。
- **扩展能力**：接入 MCP（Model Context Protocol）工具，支持 RAG 检索增强与结构化提问（`InterviewQuestionTool`）。

## 快速开始

### 1. 后端

要求：JDK 21、Maven。

```bash
cd mcm-helper-backend
# 配置密钥（也可在 application-local.yml 中覆盖）
export DASHSCOPE_API_KEY=你的阿里云DashScope密钥
mvn spring-boot:run
```

后端默认运行在 `http://localhost:8084`，全局接口前缀 `/api`。

> 若需 RAG/MCP 能力，请参考 `application.yml` 中 `modelScope.mcp.url`、embedding 模型配置并替换为你的实际地址。

### 2. 前端

要求：Node.js 18+。

```bash
cd mcm-helper-frontend
npm install
npm run dev
```

前端运行在 `http://localhost:5173`，开发环境下 `/api` 请求会自动代理到后端 `8084` 端口。

## 接口清单

### 普通模式（`ModelingController`）

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/api/ai/chat` | 普通问答，SSE 流式返回。Body：`{ "message": "...", "memoryId": 1 }` |

### 专家模式（`ExpertModelingController`）

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/api/modeling/chat` | 专家模式对话，SSE 流式返回。Body：`{ "message": "...", "memoryId": 1 }` |
| GET | `/api/modeling/analyze` | 对完整赛题进行整体分析。参数：`problem`（赛题内容）、`memoryId` |
| GET | `/api/modeling/section` | 针对赛题指定部分作答。参数：`section`、`question`、`memoryId` |

> 接口均返回 `text/event-stream`，读取时以流关闭为准，不依赖 `[DONE]` 标记。

## 后端核心模块

| 模块 | 职责 |
| --- | --- |
| `ExpertChatRouter` | 专家模式业务路由，编排 `chat` / `analyze` / `section` 流程 |
| `IntentClassifier` | 意图识别与分类，决定走普通问答还是专家流程 |
| `ModelingService` / `ModelingServiceFactory` | 普通模式流式对话服务与工厂 |
| `QwenModelingConfig` | Qwen 聊天、流式、Embedding 模型配置 |
| `SafeInputGuardrail` | 输入安全护栏 |
| `RagConfig` | 检索增强生成配置 |
| `McpConfig` | MCP 工具接入配置 |
| `InterviewQuestionTool` | 结构化提问工具 |
| `ModelingListenerConfig` | 流式响应的监听与事件处理 |

## 关键配置说明

- `application.yml` 中的 `api-key` 已通过 `${DASHSCOPE_API_KEY:your-api-key-here}` 占位符脱敏，请通过环境变量注入，避免密钥泄露到仓库。
- 本地覆盖配置请使用 `application_local.yml`（已被 `.gitignore` 忽略，不会提交）。
