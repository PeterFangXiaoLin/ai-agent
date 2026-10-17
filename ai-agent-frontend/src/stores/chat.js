import { reactive, ref, watch } from 'vue'
import { doChatWithLoveAppSse, doChatWithManus } from '../services/chat.js'
import { applyAgentEvent, isArtifactPath, settleAgentProgress } from '../utils/agent.js'

const STORAGE_KEY = 'ai-space-sessions-v1'
export const storageWarning = ref('')
export const sessions = reactive(loadSessions())
export const running = reactive({})
const controllers = new Map()

export const makeId = () => globalThis.crypto?.randomUUID?.() || `${Date.now().toString(36)}-${Math.random().toString(36).slice(2)}`

function loadSessions() {
  try {
    const saved = JSON.parse(localStorage.getItem(STORAGE_KEY) || '[]')
    if (!Array.isArray(saved)) return []

    return saved.filter((session) =>
      session && typeof session.id === 'string' && ['love', 'manus'].includes(session.app) &&
      typeof session.title === 'string' && typeof session.updatedAt === 'number' && Array.isArray(session.messages),
    ).map((session) => ({
      ...session,
      messages: session.messages.filter((message) =>
        message && typeof message.id === 'string' && ['user', 'assistant'].includes(message.role) &&
        typeof message.content === 'string',
      ).map((message) => ({
        ...message,
        status: message.status === 'streaming' ? 'stopped' : message.status,
        progress: Array.isArray(message.progress) ? message.progress.filter((item) =>
          item && typeof item.id === 'string' && typeof item.title === 'string' &&
          ['running', 'completed', 'failed', 'stopped'].includes(item.status),
        ).map((item) => ({ ...item, status: item.status === 'running' ? 'stopped' : item.status })) : [],
        artifacts: Array.isArray(message.artifacts) ? message.artifacts.filter((item) =>
          item && typeof item.title === 'string' && isArtifactPath(item.url),
        ) : [],
      })),
    }))
  } catch {
    storageWarning.value = '浏览器存储不可用，当前对话可能无法在刷新后保留。'
    return []
  }
}

let saveTimer
function saveSessions() {
  clearTimeout(saveTimer)
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(sessions))
  } catch {
    storageWarning.value = '本地保存失败，当前对话仍可继续。请导出需要保留的聊天记录。'
  }
}

// Batch token updates to avoid blocking rendering with a storage write per token.
watch(sessions, () => {
  clearTimeout(saveTimer)
  saveTimer = setTimeout(saveSessions, 350)
}, { deep: true })
window.addEventListener('pagehide', saveSessions)

export function createSession(app) {
  const session = { id: makeId(), app, title: '新的对话', createdAt: Date.now(), updatedAt: Date.now(), messages: [] }
  sessions.unshift(session)
  return sessions[0]
}

export function deleteSession(id) {
  stopGeneration(id)
  const index = sessions.findIndex((session) => session.id === id)
  if (index >= 0) sessions.splice(index, 1)
}

export function stopGeneration(id) {
  controllers.get(id)?.abort()
}

export async function sendMessage(session, text, retry = false) {
  if (running[session.id] || !text.trim()) return

  if (retry) {
    const last = session.messages.at(-1)
    if (last?.role === 'assistant') session.messages.pop()
  } else {
    session.messages.push({ id: makeId(), role: 'user', content: text.trim(), createdAt: Date.now() })
    if (session.messages.filter((message) => message.role === 'user').length === 1) {
      session.title = text.trim().slice(0, 24)
    }
  }

  session.updatedAt = Date.now()
  session.messages.push({ id: makeId(), role: 'assistant', content: '', progress: [], artifacts: [], status: 'streaming', createdAt: Date.now(), error: '' })
  const answer = session.messages.at(-1)
  const controller = new AbortController()
  controllers.set(session.id, controller)
  running[session.id] = true

  try {
    const options = {
      signal: controller.signal,
      onChunk: (chunk) => { answer.content += chunk },
      onAgentEvent: (event) => applyAgentEvent(answer, event),
    }
    if (session.app === 'love') await doChatWithLoveAppSse(text, session.id, options)
    else await doChatWithManus(text, options)

    if (!answer.content) throw new Error('AI 暂时没有返回内容，请重试。')
    answer.status = 'done'
  } catch (error) {
    answer.status = error.name === 'AbortError' ? 'stopped' : 'error'
    answer.error = answer.status === 'error' ? error.message : ''
  } finally {
    settleAgentProgress(answer, answer.status)
    running[session.id] = false
    controllers.delete(session.id)
    session.updatedAt = Date.now()
    saveSessions()
  }
}
