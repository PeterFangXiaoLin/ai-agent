<script setup>
import { computed, nextTick, onBeforeUnmount, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AppIcon from './components/AppIcon.vue'
import { apps, getApp } from './data/apps.js'
import { createSession, deleteSession, sessions, storageWarning } from './stores/chat.js'

const route = useRoute()
const router = useRouter()
const mobileOpen = ref(false)
const collapsed = ref(false)
const modal = ref(null)
const modalType = ref('search')
const query = ref('')
const searchInput = ref(null)
const pendingDelete = ref(null)
const recent = computed(() => [...sessions].filter((session) => session.messages.length).sort((a, b) => b.updatedAt - a.updatedAt).slice(0, 5))
const searchApps = computed(() => apps.filter((app) => `${app.name}${app.description}${app.tags.join('')}`.toLowerCase().includes(query.value.toLowerCase())))
const searchSessions = computed(() => [...sessions].filter((session) => session.messages.length && session.title.toLowerCase().includes(query.value.toLowerCase())).sort((a, b) => b.updatedAt - a.updatedAt))

watch(() => route.fullPath, () => { mobileOpen.value = false })

function openModal(type) {
  modalType.value = type
  query.value = ''
  modal.value.showModal()
  if (type === 'search') nextTick(() => searchInput.value?.focus())
}

function startChat(app) {
  modal.value?.close()
  const created = createSession(app)
  router.push(`/chat/${app}/${created.id}`)
}

function openSession(session) {
  modal.value.close()
  router.push(`/chat/${session.app}/${session.id}`)
}

function requestDelete(session) {
  pendingDelete.value = session
  openModal('delete')
}

function confirmDelete() {
  const deleted = pendingDelete.value
  deleteSession(deleted.id)
  modal.value.close()
  pendingDelete.value = null
  if (route.params.id === deleted.id) router.push('/')
}

function shortcut(event) {
  if ((event.ctrlKey || event.metaKey) && event.key.toLowerCase() === 'k') {
    event.preventDefault()
    openModal('search')
  }
}

window.addEventListener('keydown', shortcut)
onBeforeUnmount(() => window.removeEventListener('keydown', shortcut))
</script>

<template>
  <div class="app-shell" :class="{ 'sidebar-collapsed': collapsed, 'mobile-open': mobileOpen }">
    <button v-if="mobileOpen" class="sidebar-backdrop" aria-label="关闭导航" @click="mobileOpen = false"></button>
    <aside class="sidebar" aria-label="主导航">
      <RouterLink to="/" class="brand" aria-label="AI Space 首页"><span class="brand-symbol">✦</span><span class="brand-text">AI Space<span class="brand-period">.</span></span></RouterLink>

      <button class="new-chat-button" @click="route.params.app ? startChat(route.params.app) : openModal('new')"><AppIcon name="plus" :size="19" /><span>开启新对话</span><kbd>↗</kbd></button>

      <div class="sidebar-section-label">工作空间</div>
      <nav class="main-nav">
        <RouterLink to="/" :class="{ active: route.name === 'home' }" title="应用广场"><AppIcon name="home" :size="19" /><span>应用广场</span><span class="nav-active-dot"></span></RouterLink>
        <RouterLink to="/chat/love" :class="{ active: route.params.app === 'love' }" title="AI 恋爱大师"><AppIcon name="heart" :size="19" /><span>AI 恋爱大师</span></RouterLink>
        <RouterLink to="/chat/manus" :class="{ active: route.params.app === 'manus' }" title="AI 超级智能体"><AppIcon name="sparkles" :size="19" /><span>AI 超级智能体</span><span class="nav-mini-badge">NEW</span></RouterLink>
      </nav>

      <div class="sidebar-divider"></div>
      <div class="sidebar-section-label flex items-center justify-between"><span>最近对话</span><button class="history-search" aria-label="搜索历史对话" @click="openModal('search')"><AppIcon name="history" :size="14" /></button></div>
      <div v-if="!recent.length" class="empty-history"><AppIcon name="message" :size="23" /><p>还没有对话记录<small>你的小小想法，值得聊一聊</small></p></div>
      <div v-else class="sidebar-history">
        <div v-for="session in recent" :key="session.id" class="history-item" :class="{ selected: route.params.id === session.id }"><RouterLink :to="`/chat/${session.app}/${session.id}`" :title="session.title"><AppIcon name="message" :size="16" /><span>{{ session.title }}</span></RouterLink><button :aria-label="`删除对话：${session.title}`" @click="requestDelete(session)"><AppIcon name="trash" :size="14" /></button></div>
      </div>

      <div class="sidebar-bottom">
        <div class="sidebar-note"><span>给生活一点 AI 灵感</span><p>从「我有个想法」<br />到「原来可以这样」。</p><AppIcon name="sprout" :size="29" :stroke-width="1.2" /></div>
        <button class="help-button" @click="openModal('help')"><AppIcon name="help" :size="18" /><span>帮助与使用指南</span><AppIcon name="chevron" :size="15" /></button>
        <div class="profile"><div class="profile-avatar">E<span></span></div><div class="profile-info"><strong>探索者</strong><span>个人工作空间</span></div><button class="icon-button sidebar-collapse" aria-label="收起或展开导航" @click="collapsed = !collapsed"><AppIcon :name="collapsed ? 'menu' : 'collapse'" :size="17" /></button></div>
      </div>
    </aside>

    <div class="workspace">
      <header class="topbar"><div class="breadcrumb"><button class="icon-button mobile-menu" aria-label="打开导航" @click="mobileOpen = true"><AppIcon name="menu" :size="21" /></button><AppIcon name="compass" :size="17" class="breadcrumb-icon" /><RouterLink to="/">我的工作空间</RouterLink><span>/</span><strong>{{ route.name === 'home' ? '应用广场' : getApp(route.params.app)?.name }}</strong></div><div class="topbar-actions"><button class="search-trigger" @click="openModal('search')"><AppIcon name="search" :size="16" /><span>搜索应用或对话</span><kbd>Ctrl K</kbd></button><span class="topbar-divider"></span><span class="topbar-note"><span class="status-dot"></span> 灵感在线</span></div></header>
      <div v-if="storageWarning" class="storage-warning" role="alert">{{ storageWarning }}<button aria-label="关闭存储提示" @click="storageWarning = ''"><AppIcon name="close" :size="16" /></button></div>
      <main class="workspace-main" :class="{ 'is-chat': route.name === 'chat' }"><RouterView /></main>
    </div>

    <dialog ref="modal" class="app-dialog" @click="(event) => { if (event.target === modal) modal.close() }">
      <template v-if="modalType === 'search'">
        <div class="dialog-search"><AppIcon name="search" :size="21" /><input ref="searchInput" v-model="query" placeholder="搜索应用或历史对话…" aria-label="搜索应用或历史对话" /><button class="dialog-close" aria-label="关闭搜索" @click="modal.close()"><kbd>Esc</kbd></button></div>
        <div class="search-results"><div class="dialog-section-label">应用</div><button v-for="item in searchApps" :key="item.id" class="search-result" @click="startChat(item.id)"><span class="search-result-icon" :class="item.id"><AppIcon :name="item.icon" :size="19" /></span><span>{{ item.name }}<small>{{ item.tagline }}</small></span><AppIcon name="arrow-right" :size="17" /></button><template v-if="searchSessions.length"><div class="dialog-section-label">历史对话</div><button v-for="item in searchSessions" :key="item.id" class="search-result" @click="openSession(item)"><AppIcon name="message" :size="18" /><span>{{ item.title }}<small>{{ getApp(item.app).name }}</small></span><AppIcon name="chevron" :size="17" /></button></template><p v-if="!searchApps.length && !searchSessions.length" class="no-results">没有找到相关内容，试试其他关键词吧。</p></div>
      </template>
      <template v-else-if="modalType === 'new'">
        <div class="dialog-header"><div><span class="eyebrow">A FRESH START</span><h2>今天，想聊点什么？</h2></div><button class="icon-button" aria-label="关闭" @click="modal.close()"><AppIcon name="close" :size="20" /></button></div><div class="dialog-body"><button v-for="item in apps" :key="item.id" class="new-app-choice" @click="startChat(item.id)"><span class="search-result-icon" :class="item.id"><AppIcon :name="item.icon" :size="24" /></span><span><strong>{{ item.name }}</strong><small>{{ item.tagline }}</small></span><AppIcon name="arrow-right" :size="19" /></button></div>
      </template>
      <template v-else-if="modalType === 'help'">
        <div class="dialog-header"><div><span class="eyebrow">A LITTLE HELP</span><h2>让对话，轻松开始。</h2></div><button class="icon-button" aria-label="关闭" @click="modal.close()"><AppIcon name="close" :size="20" /></button></div><div class="dialog-body help-content"><div><span>01</span><p><strong>选择你的 AI 搭档</strong>恋爱大师陪你梳理情感与关系，超级智能体帮你研究、规划并执行任务。</p></div><div><span>02</span><p><strong>把想法说具体一点</strong>输入问题后按 Enter 发送。回复会实时出现，点击停止按钮可随时中断。超级智能体的每条任务独立执行。</p></div><div><span>03</span><p><strong>让每段对话都有迹可循</strong>对话保存在当前浏览器，可从左侧继续，也可导出为 Markdown。清理浏览器数据会删除本地记录。</p></div><div class="help-privacy"><AppIcon name="shield" :size="18" /><p>发送的消息会交给你配置的 AI 后端处理。恋爱大师的会话 ID 用于区分上下文；删除本地记录不会删除后端记忆。</p></div></div>
      </template>
      <template v-else-if="modalType === 'delete'">
        <div class="dialog-header"><h2>删除这段对话？</h2><button class="icon-button" aria-label="关闭" @click="modal.close()"><AppIcon name="close" :size="20" /></button></div><div class="dialog-body"><p class="delete-description">「{{ pendingDelete?.title }}」将从当前浏览器移除，删除后无法恢复。</p><div class="dialog-footer"><button class="secondary-button" @click="modal.close()">再想想</button><button class="danger-button" @click="confirmDelete">删除对话</button></div></div>
      </template>
    </dialog>
  </div>
</template>
