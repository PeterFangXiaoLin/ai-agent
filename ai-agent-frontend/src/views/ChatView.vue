<script setup>
import { computed, nextTick, onBeforeUnmount, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { marked } from 'marked'
import DOMPurify from 'dompurify'
import AppIcon from '../components/AppIcon.vue'
import AgentProgress from '../components/AgentProgress.vue'
import { getArtifactUrl } from '../services/chat.js'
import { isArtifactPath, isLegacyAgentLog } from '../utils/agent.js'
import { getApp } from '../data/apps.js'
import { createSession, running, sendMessage, sessions, stopGeneration } from '../stores/chat.js'

const route = useRoute()
const router = useRouter()
const app = computed(() => getApp(route.params.app))
const session = computed(() => sessions.find((item) => item.id === route.params.id && item.app === route.params.app))
const busy = computed(() => Boolean(running[session.value?.id]))
const draft = ref('')
const textarea = ref(null)
const messageArea = ref(null)
const followOutput = ref(true)
const copied = ref('')
const notice = ref('')
let copyTimer
let noticeTimer

watch(() => [route.params.app, route.params.id], async () => {
  if (route.name !== 'chat') return
  draft.value = ''
  followOutput.value = true
  if (!session.value) {
    const created = createSession(route.params.app)
    await router.replace({ name: 'chat', params: { app: created.app, id: created.id } })
  }
  await nextTick()
  scrollBottom()
  textarea.value?.focus()

  const prompt = window.history.state.prompt
  if (prompt && !session.value?.messages.length) {
    window.history.replaceState({ ...window.history.state, prompt: null }, '')
    draft.value = prompt
    resizeInput()
  }
}, { immediate: true })

watch(() => session.value?.messages.map((message) => message.content + message.status +
  (message.progress || []).map((item) => item.title + item.status).join('') + message.artifacts?.length).join(''), async () => {
  if (!followOutput.value) return
  await nextTick()
  scrollBottom()
})

function scrollBottom() {
  if (messageArea.value) messageArea.value.scrollTop = messageArea.value.scrollHeight
}

function onScroll() {
  const area = messageArea.value
  followOutput.value = area.scrollHeight - area.scrollTop - area.clientHeight < 100
}

function resizeInput() {
  nextTick(() => {
    if (!textarea.value) return
    textarea.value.style.height = 'auto'
    textarea.value.style.height = `${Math.min(textarea.value.scrollHeight, 160)}px`
  })
}

function submit() {
  const text = draft.value.trim()
  if (!text || busy.value || !session.value || text.length > 6000) return
  draft.value = ''
  resizeInput()
  followOutput.value = true
  sendMessage(session.value, text)
  textarea.value?.focus()
}

function onKeydown(event) {
  if (event.key === 'Enter' && !event.shiftKey && !event.isComposing && event.keyCode !== 229) {
    event.preventDefault()
    submit()
  }
}

function retry() {
  const question = [...session.value.messages].reverse().find((message) => message.role === 'user')
  if (question) { followOutput.value = true; sendMessage(session.value, question.content, true) }
}

async function copyMessage(message) {
  try {
    await navigator.clipboard.writeText(message.content)
    copied.value = message.id
    clearTimeout(copyTimer)
    copyTimer = setTimeout(() => { copied.value = '' }, 2000)
  } catch { showNotice('复制失败，请选中文本后手动复制。') }
}

function showNotice(text) {
  notice.value = text
  clearTimeout(noticeTimer)
  noticeTimer = setTimeout(() => { notice.value = '' }, 3500)
}

function exportChat() {
  const current = session.value
  if (!current?.messages.length) { showNotice('开始一段对话后，即可导出聊天记录。'); return }
  const content = `# ${current.title}\n\n应用：${app.value.name}\n会话 ID：${current.id}\n\n` + current.messages.map((message) => `## ${message.role === 'user' ? '我' : app.value.name}\n\n${message.content || message.error || '（已停止生成）'}\n`).join('\n')
  const url = URL.createObjectURL(new Blob([content], { type: 'text/markdown;charset=utf-8' }))
  const link = document.createElement('a')
  link.href = url
  link.download = `AI-Space-${current.app}-${current.id.slice(0, 8)}.md`
  link.click()
  setTimeout(() => URL.revokeObjectURL(url), 1000)
}

const renderMarkdown = (text) => DOMPurify.sanitize(marked.parse(text, { breaks: true, gfm: true }))
onBeforeUnmount(() => { clearTimeout(copyTimer); clearTimeout(noticeTimer) })
</script>

<template>
  <div v-if="app" class="chat-page" :class="`chat-${app.id}`">
    <header class="chat-toolbar">
      <div class="flex items-center gap-3 min-w-0"><span class="chat-toolbar-icon"><AppIcon :name="app.icon" :size="21" /></span><div><h1>{{ app.name }}<span>{{ app.english }}</span></h1><p>{{ busy ? '正在为你认真思考…' : app.tagline }}</p></div></div>
      <div class="flex items-center gap-2"><button class="icon-button" aria-label="导出聊天记录" title="导出聊天记录" @click="exportChat"><AppIcon name="download" :size="19" /></button><button class="chat-new-button" @click="router.push(`/chat/${app.id}/${createSession(app.id).id}`)"><AppIcon name="plus" :size="17" /><span>新对话</span></button></div>
    </header>

    <div ref="messageArea" class="chat-scroll" @scroll="onScroll">
      <div v-if="!session?.messages.length" class="chat-welcome">
        <div class="welcome-symbol"><AppIcon :name="app.icon" :size="38" :stroke-width="1.3" /><span>✦</span></div>
        <div class="eyebrow">{{ app.id === 'love' ? 'A LITTLE UNDERSTANDING GOES A LONG WAY' : 'BIG IDEAS START WITH A SIMPLE CONVERSATION' }}</div>
        <h2>{{ app.welcome }}</h2>
        <p>{{ app.intro }}</p>
        <div class="welcome-prompts">
          <button v-for="suggestion in app.suggestions" :key="suggestion.title" @click="draft = suggestion.text; resizeInput(); textarea.focus()"><AppIcon :name="suggestion.icon" :size="20" /><span>{{ suggestion.title }}<small>{{ suggestion.text }}</small></span><AppIcon name="arrow-up" :size="15" class="suggestion-arrow" /></button>
        </div>
        <div v-if="app.id === 'manus'" class="manus-context-note"><AppIcon name="lightbulb" :size="14" /> 每条任务独立执行，完整描述目标可以获得更好的结果。</div>
      </div>

      <div v-else class="messages-list" role="log" aria-label="聊天记录" :aria-busy="busy">
        <div class="conversation-start">一次新的对话，一点新的可能</div>
        <article v-for="(message, index) in session.messages" :key="message.id" class="message-row" :class="message.role">
          <div class="message-avatar"><AppIcon v-if="message.role === 'assistant'" :name="app.icon" :size="19" /><span v-else>我</span></div>
          <div class="message-body">
            <div class="message-author">{{ message.role === 'user' ? '你' : app.name }}<span>{{ new Date(message.createdAt).toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' }) }}</span></div>
            <div v-if="message.role === 'user'" class="message-bubble user-bubble">{{ message.content }}</div>
            <div v-else class="message-bubble ai-bubble">
              <AgentProgress v-if="app.id === 'manus' && (message.progress?.length || isLegacyAgentLog(message))"
                :progress="message.progress || []" :status="message.status" :has-answer="Boolean(message.content) && !isLegacyAgentLog(message)"
                :legacy-content="isLegacyAgentLog(message) ? message.content : ''" />
              <div v-if="message.content && !(app.id === 'manus' && isLegacyAgentLog(message))" class="markdown-content" v-html="renderMarkdown(message.content)"></div>
              <div v-if="message.artifacts?.length" class="agent-artifacts">
                <a v-for="file in message.artifacts.filter((item) => isArtifactPath(item.url))" :key="file.url" :href="getArtifactUrl(file.url)" class="agent-artifact" target="_blank" rel="noopener" download>
                  <span class="artifact-file-icon"><AppIcon name="file" :size="21" /></span>
                  <span class="artifact-description"><strong>{{ file.title }}</strong><small>PDF 文档 · 点击下载</small></span>
                  <AppIcon name="download" :size="17" />
                </a>
              </div>
              <span v-if="message.status === 'streaming'" class="typing-indicator" aria-label="正在生成"><i></i><i></i><i></i></span>
              <div v-if="message.status === 'error'" class="message-error" role="alert"><AppIcon name="help" :size="16" /><span>{{ message.error }}</span></div>
              <span v-if="message.status === 'stopped'" class="stopped-label">已停止生成</span>
            </div>
            <div v-if="message.role === 'assistant' && message.status !== 'streaming'" class="message-actions"><button v-if="message.content" :aria-label="copied === message.id ? '已复制' : '复制回复'" @click="copyMessage(message)"><AppIcon :name="copied === message.id ? 'check' : 'copy'" :size="14" /><span>{{ copied === message.id ? '已复制' : '复制' }}</span></button><button v-if="index === session.messages.length - 1 && !busy" @click="retry"><AppIcon name="retry" :size="14" />{{ message.status === 'error' ? '重试' : '重新生成' }}</button></div>
          </div>
        </article>
      </div>
    </div>

    <div class="composer-area">
      <button v-if="!followOutput && session?.messages.length" class="scroll-bottom-button" aria-label="滚动到最新消息" @click="followOutput = true; scrollBottom()"><AppIcon name="arrow-down" :size="18" /></button>
      <div v-if="notice" class="chat-notice" role="status">{{ notice }}</div>
      <form class="composer" :class="{ 'is-busy': busy }" @submit.prevent="submit">
        <label class="sr-only" for="chat-input">发送给{{ app.name }}的消息</label>
        <textarea id="chat-input" ref="textarea" v-model="draft" :placeholder="app.placeholder" rows="1" maxlength="6000" @input="resizeInput" @keydown="onKeydown"></textarea>
        <div class="composer-bottom"><span class="composer-mode"><AppIcon :name="app.id === 'love' ? 'heart' : 'sparkles'" :size="15" />{{ app.id === 'love' ? '用心倾听' : '深度思考' }}<span class="composer-divider"></span><span>{{ busy ? '实时生成中' : '准备好就开始吧' }}</span></span><div class="flex items-center gap-3"><span v-if="draft.length > 5000" class="character-count">{{ draft.length }}/6000</span><button v-if="busy" type="button" class="send-button stop-button" aria-label="停止生成" title="停止生成" @click="stopGeneration(session.id)"><AppIcon name="stop" :size="17" /></button><button v-else type="submit" class="send-button" :disabled="!draft.trim()" aria-label="发送消息" title="发送消息"><AppIcon name="arrow-up" :size="20" /></button></div></div>
      </form>
      <div class="composer-caption"><span><kbd>Enter</kbd> 发送，<kbd>Shift + Enter</kbd> 换行</span><span>AI 也会有小失误，请理性参考</span></div>
      <div class="sr-only" role="status" aria-live="polite">{{ busy ? 'AI 正在生成回复' : '可以发送消息' }}</div>
    </div>
  </div>
</template>
