<script setup>
import { computed } from 'vue'
import AppIcon from './AppIcon.vue'

const props = defineProps({
  progress: { type: Array, default: () => [] },
  status: String,
  hasAnswer: Boolean,
  legacyContent: { type: String, default: '' },
})

const busy = computed(() => props.status === 'streaming')
const active = computed(() => [...props.progress].reverse().find((item) => item.status === 'running'))
const heading = computed(() => {
  if (props.legacyContent) return '此前的执行记录'
  if (busy.value) return active.value?.title || '正在准备答复'
  if (props.status === 'error') return '任务未完成'
  if (props.status === 'stopped') return '任务已停止'
  const count = props.progress.filter((item) => item.id.startsWith('tool-') && item.status === 'completed').length
  return count ? `已完成 ${count} 项操作` : '已完成任务分析'
})
const labels = { running: '进行中', completed: '已完成', failed: '未成功', stopped: '已停止' }
</script>

<template>
  <details class="agent-progress" :open="busy && !hasAnswer">
    <summary>
      <span class="agent-progress-symbol" :class="{ 'is-active': busy }"><AppIcon :name="busy ? 'sparkles' : status === 'done' ? 'check' : 'help'" :size="15" /></span>
      <span class="agent-progress-heading">{{ heading }}</span>
      <span class="agent-progress-hint">{{ busy ? '执行中' : '查看过程' }}</span>
      <AppIcon name="chevron-down" :size="14" class="agent-progress-chevron" />
    </summary>
    <ol v-if="progress.length" class="agent-progress-list">
      <li v-for="item in progress" :key="item.id" :class="`progress-${item.status}`">
        <span class="progress-dot"><AppIcon :name="item.status === 'completed' ? 'check' : item.status === 'running' ? 'more' : 'help'" :size="12" /></span>
        <span>{{ item.title }}</span><small>{{ labels[item.status] }}</small>
      </li>
    </ol>
    <template v-if="legacyContent"><p class="legacy-agent-note">重新生成可获取整理后的答复。</p><pre class="legacy-agent-log">{{ legacyContent }}</pre></template>
  </details>
</template>
