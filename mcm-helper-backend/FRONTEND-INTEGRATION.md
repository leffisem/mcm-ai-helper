# 前端对接修改文档（mcm-helper 后端已就绪）

> 交给前端工作区执行。后端已完成 POST 改造，前端需同步修改 2 个文件。
> 前端项目位置：`D:\langchain4j_ai_program\frontend`

---

## 一、后端接口现状（以此为准）

后端已从 Spring MVC 切换为 **WebFlux（Netty）**，端口 **8082**，上下文路径 **/api**（配置为 `spring.webflux.base-path: /api`）。

| 功能 | 方法 | 完整路径 | 请求参数 |
|------|------|---------|---------|
| 普通模式对话 | **POST** | `/api/ai/chat` | JSON Body |
| 专家模式（三段式路由） | **POST** | `/api/modeling/chat` | JSON Body |
| 强制策略A（完整赛题分析） | GET | `/api/modeling/analyze` | `?problem=xxx&memoryId=0` |
| 强制策略B（单一环节求助） | GET | `/api/modeling/section` | `?section=xxx&question=xxx&memoryId=0` |

### POST 请求体格式（ChatRequest）

```json
{
  "message": "这里是用户的完整消息，支持 5000 字以上长文本",
  "memoryId": 1
}
```

- `message`：String，用户消息
- `memoryId`：Integer，**默认 1**（后端有默认值，可不传）

### memoryId 约定

- 普通模式：固定 `1`
- 专家模式：固定 `10001`（后端用 memoryId 偏移隔离普通/专家两种模式的上下文，**不要混用同一个值**）

### 响应格式（SSE 流式）

- Content-Type：`text/event-stream;charset=UTF-8`
- 后端逐块输出：`data:某段文字\n\n`
- 结束时流自动关闭（无 `[DONE]` 标记，**以流关闭（reader done）为结束信号**）

---

## 二、需要修改的文件（共 2 个）

### 修改 1：`vite.config.js` — 统一代理到 8082

当前问题：代理分开配置且 `/api/ai` 指向了错误的 8081 端口。

**改为：**

```js
import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  plugins: [vue()],
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://localhost:8082',
        changeOrigin: true
      }
    }
  }
})
```

要点：
- **只保留一条 `/api` 规则**，统一转发到 `http://localhost:8082`
- 不要拆分成 `/api/ai`、`/api/modeling` 多条规则，容易端口不一致
- 后端已有 `/api` 前缀（base-path），代理**不需要 rewrite 去掉前缀**

### 修改 2：`src/api/chat.js` — GET 改 POST

当前问题：`chatStream` 和 `modelingChat` 把几千字的赛题拼进 URL，导致后端报 `TooLongHttpLineException: An HTTP line is larger than 4096 bytes`。

**改为（完整可用版本）：**

```js
const BASE_URL = '/api'

/**
 * SSE 流式对话：普通模式（POST JSON，支持长文本）
 */
export function chatStream(message, memoryId, onData, onError, onDone) {
  return sseRequest(`${BASE_URL}/ai/chat`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ message, memoryId })
  }, onData, onError, onDone)
}

/**
 * SSE 流式对话：专家模式（三段式自动路由，POST JSON）
 */
export function modelingChat(message, memoryId, onData, onError, onDone) {
  return sseRequest(`${BASE_URL}/modeling/chat`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ message, memoryId })
  }, onData, onError, onDone)
}

/**
 * SSE 流式对话：强制策略A（完整赛题分析，仍为 GET）
 */
export function modelingAnalyze(problem, memoryId, onData, onError, onDone) {
  const url = `${BASE_URL}/modeling/analyze?problem=${encodeURIComponent(problem)}&memoryId=${memoryId}`
  return sseRequest(url, {}, onData, onError, onDone)
}

/**
 * SSE 流式对话：强制策略B（单一环节求助，仍为 GET）
 */
export function modelingSection(section, question, memoryId, onData, onError, onDone) {
  const url = `${BASE_URL}/modeling/section?section=${encodeURIComponent(section)}&question=${encodeURIComponent(question)}&memoryId=${memoryId}`
  return sseRequest(url, {}, onData, onError, onDone)
}

/**
 * 通用 SSE fetch 处理（支持 GET / POST）
 * 后端返回 text/event-stream，每条事件格式：data:内容\n\n
 */
function sseRequest(url, options, onData, onError, onDone) {
  const controller = new AbortController()

  fetch(url, {
    signal: controller.signal,
    ...options
  }).then(async (response) => {
    if (!response.ok) {
      onError?.(`HTTP Error: ${response.status}`)
      return
    }

    const reader = response.body.getReader()
    const decoder = new TextDecoder('utf-8')
    let buffer = ''

    while (true) {
      const { done, value } = await reader.read()
      if (done) break

      buffer += decoder.decode(value, { stream: true })

      // 按行切分，最后一段可能不完整，留在 buffer
      const lines = buffer.split('\n')
      buffer = lines.pop() || ''

      for (const line of lines) {
        const trimmed = line.trim()
        if (trimmed.startsWith('data:')) {
          const data = trimmed.slice(5)
          if (data) {
            onData?.(data)
          }
        }
      }
    }
    onDone?.()
  }).catch((err) => {
    if (err.name !== 'AbortError') {
      onError?.(err.message)
    }
  })

  // 返回取消函数
  return () => controller.abort()
}

export default {
  chatStream,
  modelingChat,
  modelingAnalyze,
  modelingSection
}
```

要点：
- POST 的 body 用 `JSON.stringify({ message, memoryId })`，不再拼接 URL
- SSE 解析**不要用 `slice(5).trim()`**——后端输出的文字块可能以空格开头，trim 会吞掉空格导致中英文混排间距丢失；用 `slice(5)` 保留原样
- `TextDecoder('utf-8')` 显式指定编码，避免中文乱码
- axios 不支持流式读取 SSE，**必须用 fetch + ReadableStream**（可移除 axios 依赖）

---

## 三、流式渲染注意事项（排查"AI 回答不显示"）

如果请求发出去了但页面不显示，按此清单检查：

1. **每次 onData 触发都要追加渲染**，不要等结束才赋值：

```js
// ✅ 正确：追加式
onData: (chunk) => {
  this.messages[this.messages.length - 1].content += chunk
}

// ❌ 错误：覆盖式
onData: (chunk) => {
  this.messages[this.messages.length - 1].content = chunk
}
```

2. **Vue 响应式**：如果用 `reactive`/`ref` 存消息数组，确保用 `push` 或索引赋值，不要整体替换数组引用后丢失追踪。

3. **发送前先压入一条空的 AI 消息占位**：

```js
this.messages.push({ role: 'user', content: message })
this.messages.push({ role: 'assistant', content: '' })  // 占位，流式往里追加
this.streaming = true
```

4. **markdown 渲染**（如使用 marked/markdown-it）：AI 输出未完成时 markdown 语法不完整可能渲染异常，可先按纯文本渲染，流结束后再整体 markdown 化；或对不完整语法做容错。

5. **自动滚动**：追加内容后调用滚动到底部。

---

## 四、验收标准

| 序号 | 验收项 | 预期结果 |
|------|--------|---------|
| 1 | `npm run dev` 启动后访问 5173 | 页面正常加载 |
| 2 | 普通模式发送"你好" | AI 回复逐字流式显示，光标闪烁动画正常 |
| 3 | 专家模式发送短消息 | 走追问/澄清策略，流式显示 |
| 4 | 专家模式粘贴 5000 字以上赛题 | 正常传输，**后端不再报 TooLongHttpLineException** |
| 5 | F12 → Network → 请求详情 | Method 为 POST，Content-Type 为 application/json，Response 为 text/event-stream 逐行 data: |
| 6 | 模式切换 | 对话历史不清空，占位文字变化，接口自动切换 |
| 7 | 中文内容 | 无乱码 |

## 五、调试技巧

- 后端直连测试（绕过前端）：浏览器访问 `http://localhost:8082/api/modeling/analyze?problem=你好&memoryId=10001`，能看到逐行 `data:xxx` 说明后端正常，问题在前端
- F12 → Network → 选中请求 → Response/EventStream 标签页，能看到每条 data 事件
- Vite 代理日志：终端会打印 `[vite] http proxy /api/ai/chat -> http://localhost:8082` 之类转发记录，确认目标端口是 8082
