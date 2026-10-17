import assert from 'node:assert/strict'
import test from 'node:test'
import { applyAgentEvent, isArtifactPath, isLegacyAgentLog, settleAgentProgress } from '../src/utils/agent.js'
import { createSseParser, decodeSseEvent } from '../src/utils/sse.js'

test('structured events keep progress out of the final answer and replace deltas without duplication', () => {
  const message = { content: '' }
  const parser = createSseParser((frame) => {
    const result = decodeSseEvent(frame)
    if (result.agentEvent) applyAgentEvent(message, result.agentEvent)
  })
  parser.feed('event:progress\ndata:{"id":"tool-1","title":"检索相关资料","status":"running"}\n\n')
  assert.equal(message.content, '')
  parser.feed('event:progress\ndata:{"id":"tool-1","title":"检索相关资料","status":"completed"}\n\n')
  parser.feed('event:delta\ndata:{"content":"## 约会"}\n\nevent:delta\ndata:{"content":"计划"}\n\n')
  assert.equal(message.content, '## 约会计划')
  parser.feed('event:answer\ndata:{"content":"## 约会计划\\n整理后的完整答复"}\n\n')
  parser.finish()
  assert.equal(message.progress.length, 1)
  assert.equal(message.progress[0].status, 'completed')
  assert.equal(message.content, '## 约会计划\n整理后的完整答复')
})

test('download cards only accept application PDF artifact paths and deduplicate them', () => {
  const message = { content: '' }
  const file = { type: 'artifact', title: '约会计划.pdf', url: '/ai/artifacts/pdf/plan.pdf' }
  applyAgentEvent(message, file)
  applyAgentEvent(message, file)
  applyAgentEvent(message, { ...file, url: 'javascript:alert(1)' })
  assert.equal(message.artifacts.length, 1)
  assert.equal(message.content, '')
  for (const url of ['https://example.com/a.pdf', '//example.com/a.pdf', '/ai/artifacts/pdf/../secret', '/other/file.pdf']) {
    assert.equal(isArtifactPath(url), false)
  }
})

test('unfinished progress becomes stopped or failed without rewriting completed actions', () => {
  const message = { progress: [{ status: 'running' }, { status: 'completed' }] }
  settleAgentProgress(message, 'stopped')
  assert.deepEqual(message.progress.map((item) => item.status), ['stopped', 'completed'])
  message.progress[0].status = 'running'
  settleAgentProgress(message, 'error')
  assert.equal(message.progress[0].status, 'failed')
})

test('old step logs are recognizable without changing saved content', () => {
  assert.equal(isLegacyAgentLog({ content: 'Step 1: 工具 searchWeb 完成了任务' }), true)
  assert.equal(isLegacyAgentLog({ content: '这是整理后的答复' }), false)
})

test('invalid structured events fail clearly while unknown events never enter the answer', () => {
  assert.throws(() => decodeSseEvent({ event: 'progress', data: 'not json' }), /无效/)
  assert.throws(() => decodeSseEvent({ event: 'answer', data: '{}' }), /格式/)
  assert.deepEqual(decodeSseEvent({ event: 'internal', data: '{"secret":"raw result"}' }), {})
})
