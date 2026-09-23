import axios from 'axios'

const BASE_URL = '/api'

/**
 * SSE 流式对话：普通模式
 * 后端 /ai/chat 为 POST + JSON 请求体
 */
export function chatStream(message, memoryId, onData, onError, onDone) {
  return sseFetch(`${BASE_URL}/ai/chat`, {
    method: 'POST',
    body: JSON.stringify({ message, memoryId })
  }, onData, onError, onDone)
}

/**
 * SSE 流式对话：专家模式（三段式自动路由）
 * 后端 /modeling/chat 为 POST + JSON 请求体
 */
export function modelingChat(message, memoryId, onData, onError, onDone) {
  return sseFetch(`${BASE_URL}/modeling/chat`, {
    method: 'POST',
    body: JSON.stringify({ message, memoryId })
  }, onData, onError, onDone)
}

/**
 * SSE 流式对话：强制策略A（完整赛题分析）
 * 后端为 GET + query 参数
 */
export function modelingAnalyze(problem, memoryId, onData, onError, onDone) {
  const url = `${BASE_URL}/modeling/analyze?problem=${encodeURIComponent(problem)}&memoryId=${memoryId}`
  return sseFetch(url, {}, onData, onError, onDone)
}

/**
 * SSE 流式对话：强制策略B（单一环节求助）
 * 后端为 GET + query 参数
 */
export function modelingSection(section, question, memoryId, onData, onError, onDone) {
  const url = `${BASE_URL}/modeling/section?section=${encodeURIComponent(section)}&question=${encodeURIComponent(question)}&memoryId=${memoryId}`
  return sseFetch(url, {}, onData, onError, onDone)
}

/**
 * 通用 SSE fetch 处理
 * 后端返回 text/event-stream 格式
 *
 * 关键：SSE 规范中，data 内的换行符会被编码为多个 data: 行，
 * 必须按"事件"分组、组内 data 行用 \n 连接还原，
 * 否则 chunk 中的换行符会丢失（导致代码挤在一行）
 */
function sseFetch(url, options, onData, onError, onDone) {
  const controller = new AbortController()

  const isPost = (options.method || 'GET').toUpperCase() === 'POST'
  const fetchOptions = {
    method: options.method || 'GET',
    signal: controller.signal
  }
  // POST JSON 请求需要显式声明 Content-Type
  if (isPost) {
    fetchOptions.headers = { 'Content-Type': 'application/json' }
    fetchOptions.body = options.body
  }

  fetch(url, fetchOptions).then(async (response) => {
    if (!response.ok) {
      onError?.(`HTTP Error: ${response.status}`)
      return
    }

    const reader = response.body.getReader()
    const decoder = new TextDecoder()
    let buffer = ''

    while (true) {
      const { done, value } = await reader.read()
      if (done) break

      buffer += decoder.decode(value, { stream: true })
      // 归一化换行（部分服务端使用 \r\n）
      buffer = buffer.replace(/\r\n/g, '\n')

      // SSE 事件以空行（\n\n）分隔，一次只处理完整事件
      const events = buffer.split('\n\n')
      buffer = events.pop() || ''

      for (const event of events) {
        if (!event) continue

        // 收集事件内所有 data: 行，按规范用 \n 连接
        const dataLines = []
        for (const line of event.split('\n')) {
          if (line.startsWith('data:')) {
            // 注意：Spring 的 SSE 输出 "data:" 后不加分隔空格，
            // 数据原样跟随（包括前导空格/缩进），因此不能剥离前导空格，
            // 否则代码缩进和 token 开头的空格会丢失
            dataLines.push(line.slice(5))
          }
        }
        if (dataLines.length === 0) continue

        const data = dataLines.join('\n')
        if (data === '[DONE]') {
          onDone?.()
          return
        }
        onData?.(data)
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