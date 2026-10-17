import assert from 'node:assert/strict'
import test from 'node:test'
import { createSseParser, decodeSseEvent } from '../src/utils/sse.js'

function parse(chunks) {
  const events = []
  const parser = createSseParser((event) => events.push(event))
  for (const chunk of chunks) parser.feed(chunk)
  parser.finish()
  return events
}

test('reassembles arbitrary splits without losing token spaces or newlines', () => {
  assert.deepEqual(parse(['da', 'ta: 你好\nd', 'ata: 世界\n\nda', 'ta:  \n\n']), [
    { event: 'message', data: '你好\n世界' },
    { event: 'message', data: ' ' },
  ])
})

test('supports LF, CRLF split across chunks, and bare CR', () => {
  assert.deepEqual(parse(['data: one\r', '\n\r', '\ndata: two\r\rdata: three\n\n']), [
    { event: 'message', data: 'one' },
    { event: 'message', data: 'two' },
    { event: 'message', data: 'three' },
  ])
})

test('ignores BOM, comments, IDs and retry fields; flushes the last event', () => {
  assert.deepEqual(parse(['\uFEFF: keepalive\nretry: 100\nid: 1\n\nevent: step\ndata: Step 1: 规划\n']), [
    { event: 'step', data: 'Step 1: 规划' },
  ])
})

test('preserves empty data fields but ignores heartbeat-only frames', () => {
  assert.deepEqual(parse([':\n\ndata:\n\ndata: first\ndata:\ndata: third\n\n']), [
    { event: 'message', data: '' },
    { event: 'message', data: 'first\n\nthird' },
  ])
})

test('decoder handles Chinese UTF-8 split at every byte', () => {
  const events = []
  const parser = createSseParser((event) => events.push(event))
  const decoder = new TextDecoder()
  for (const byte of new TextEncoder().encode('data: 心动 💚\n\n')) {
    parser.feed(decoder.decode(new Uint8Array([byte]), { stream: true }))
  }
  parser.feed(decoder.decode())
  parser.finish()
  assert.equal(events[0].data, '心动 💚')
})

test('decodes plain text, JSON string tokens, and structured content', () => {
  assert.deepEqual(decodeSseEvent({ event: 'message', data: '普通文本' }), { text: '普通文本' })
  assert.deepEqual(decodeSseEvent({ event: 'message', data: '"  hello\\n"' }), { text: '  hello\n' })
  assert.deepEqual(decodeSseEvent({ event: 'message', data: '{"content":"世界"}' }), { text: '世界' })
})

test('recognizes completion and surfaces server errors', () => {
  assert.deepEqual(decodeSseEvent({ event: 'message', data: '[DONE]' }), { done: true })
  assert.deepEqual(decodeSseEvent({ event: 'complete', data: '' }), { done: true })
  assert.throws(() => decodeSseEvent({ event: 'error', data: '模型不可用' }), /模型不可用/)
  assert.throws(() => decodeSseEvent({ event: 'message', data: '{"error":"超出限制"}' }), /超出限制/)
})
