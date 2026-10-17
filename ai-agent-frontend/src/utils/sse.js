/** Parse SSE lines incrementally, retaining incomplete lines across network chunks. */
export function createSseParser(onEvent) {
  let buffer = ''
  let data = []
  let type = 'message'
  let firstLine = true

  function line(value) {
    if (firstLine) {
      value = value.replace(/^\uFEFF/, '')
      firstLine = false
    }

    if (value === '') {
      if (data.length) onEvent({ event: type, data: data.join('\n') })
      data = []
      type = 'message'
      return
    }

    if (value.startsWith(':')) return
    const colon = value.indexOf(':')
    const field = colon < 0 ? value : value.slice(0, colon)
    let content = colon < 0 ? '' : value.slice(colon + 1)
    if (content.startsWith(' ')) content = content.slice(1)
    if (field === 'data') data.push(content)
    if (field === 'event') type = content || 'message'
  }

  return {
    feed(chunk) {
      buffer += chunk

      // A final CR may be the first half of CRLF in the next network chunk.
      while (true) {
        const match = /[\r\n]/.exec(buffer)
        if (!match) break
        const index = match.index
        if (buffer[index] === '\r' && index === buffer.length - 1) break
        const length = buffer[index] === '\r' && buffer[index + 1] === '\n' ? 2 : 1
        line(buffer.slice(0, index))
        buffer = buffer.slice(index + length)
      }
    },
    finish() {
      if (buffer.endsWith('\r')) buffer = buffer.slice(0, -1)
      if (buffer) line(buffer)
      buffer = ''
      line('')
    },
  }
}

export function decodeSseEvent(event) {
  if (['done', 'complete', 'end'].includes(event.event) || event.data.trim() === '[DONE]') {
    return { done: true }
  }
  if (event.event === 'error') throw new Error(event.data || '服务暂时不可用，请稍后重试。')

  if (['progress', 'answer', 'delta', 'artifact'].includes(event.event)) {
    let value
    try { value = JSON.parse(event.data) } catch { throw new Error('收到无效的任务响应，请重试。') }
    if (!value || typeof value !== 'object') throw new Error('收到无效的任务响应，请重试。')
    if (['answer', 'delta'].includes(event.event) && typeof value.content !== 'string') {
      throw new Error('任务答复格式不正确，请重试。')
    }
    if (event.event === 'progress' && (typeof value.id !== 'string' || typeof value.title !== 'string' ||
      !['running', 'completed', 'failed'].includes(value.status))) {
      throw new Error('任务进度格式不正确，请重试。')
    }
    if (event.event === 'artifact' && (typeof value.title !== 'string' || typeof value.url !== 'string')) {
      throw new Error('文件信息格式不正确，请重试。')
    }
    return { agentEvent: { ...value, type: event.event } }
  }
  if (event.event !== 'message') return {}

  // Spring emits plain text. JSON string events are also accepted without losing spaces.
  try {
    const value = JSON.parse(event.data)
    if (typeof value === 'string') return { text: value }
    if (value && typeof value === 'object') {
      if (value.error) throw new Error(typeof value.error === 'string' ? value.error : value.error.message || '生成失败')
      const text = value.content ?? value.text ?? value.delta?.content
      if (typeof text === 'string') return { text }
    }
  } catch (error) {
    if (!(error instanceof SyntaxError)) throw error
  }

  return { text: event.data }
}
