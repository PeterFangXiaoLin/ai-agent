import axios from 'axios'
import { createSseParser, decodeSseEvent } from '../utils/sse.js'
import { isArtifactPath } from '../utils/agent.js'

const apiBase = (import.meta.env?.VITE_API_BASE_URL || '/api').replace(/\/$/, '')
export const getArtifactUrl = (path) => isArtifactPath(path) ? `${apiBase}${path}` : null

const http = axios.create({
  baseURL: apiBase,
  adapter: 'fetch',
  responseType: 'stream',
  headers: { Accept: 'text/event-stream' },
})

export function doChatWithLoveAppSse(message, chatId, options) {
  return streamChat('/ai/love_app/chat/sse', { message, chatId }, options)
}

export function doChatWithManus(message, options) {
  return streamChat('/ai/manus/chat', { message }, { ...options, requireCompletion: true })
}

async function streamChat(path, params, { signal, onChunk, onAgentEvent, requireCompletion = false }) {
  const controller = new AbortController()
  const abort = () => controller.abort(signal?.reason)
  signal?.addEventListener('abort', abort, { once: true })
  if (signal?.aborted) abort()
  let reader
  let timer
  let timedOut = false
  let complete = false

  // Reset an inactivity timeout on every chunk, allowing long but active agent tasks.
  const resetTimeout = () => {
    clearTimeout(timer)
    timer = setTimeout(() => {
      timedOut = true
      controller.abort()
    }, 120_000)
  }

  try {
    resetTimeout()
    const response = await http.get(path, { params, signal: controller.signal })
    if (!response.headers.get('content-type')?.includes('text/event-stream')) {
      throw new Error('服务未返回 SSE 数据，请检查后端接口配置。')
    }

    reader = response.data.getReader()
    const decoder = new TextDecoder('utf-8')
    const parser = createSseParser((event) => {
      if (complete) return
      const result = decodeSseEvent(event)
      if (result.done) { complete = true; return }
      if (result.agentEvent) onAgentEvent?.(result.agentEvent)
      if (result.text !== undefined) onChunk?.(result.text)
    })

    while (!complete) {
      const { done, value } = await reader.read()
      if (done) break
      resetTimeout()
      parser.feed(decoder.decode(value, { stream: true }))
    }

    parser.feed(decoder.decode())
    parser.finish()
    if (signal?.aborted) throw new DOMException('已停止生成', 'AbortError')
    if (requireCompletion && !complete) throw new Error('任务连接提前结束，已保留收到的内容，请重试。')
  } catch (error) {
    if (timedOut) throw new Error('等待回复超时，请稍后重试。')
    if (signal?.aborted) throw new DOMException('已停止生成', 'AbortError')
    if (axios.isAxiosError(error)) {
      if (error.response) throw new Error(`服务请求失败（${error.response.status}），请稍后重试。`)
      throw new Error('暂时无法连接 AI 服务，请确认后端已在 localhost:8080 启动。')
    }
    throw error
  } finally {
    clearTimeout(timer)
    signal?.removeEventListener('abort', abort)
    if (reader) await reader.cancel().catch(() => {})
    controller.abort()
  }
}
