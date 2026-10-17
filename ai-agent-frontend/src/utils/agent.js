export const isArtifactPath = (url) => typeof url === 'string' && /^\/ai\/artifacts\/pdf\/[^/?#]+$/.test(url)

/** Progress and files are separate from the answer, so they never enter copy/export text. */
export function applyAgentEvent(message, event) {
  if (event.type === 'answer') message.content = event.content
  if (event.type === 'delta') message.content += event.content
  if (event.type === 'progress') {
    message.progress ||= []
    const item = { id: event.id, title: event.title, status: event.status }
    const existing = message.progress.find((progress) => progress.id === event.id)
    if (existing) Object.assign(existing, item)
    else message.progress.push(item)
  }
  if (event.type === 'artifact' && isArtifactPath(event.url)) {
    message.artifacts ||= []
    if (!message.artifacts.some((file) => file.url === event.url)) {
      message.artifacts.push({ title: event.title, url: event.url })
    }
  }
}

export function settleAgentProgress(message, status) {
  for (const item of message.progress || []) {
    if (item.status === 'running') item.status = status === 'done' ? 'completed' : status === 'stopped' ? 'stopped' : 'failed'
  }
}

export const isLegacyAgentLog = (message) => /^Step\s+\d+:/i.test(message.content || '') && !message.progress?.length
