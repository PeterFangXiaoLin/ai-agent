<script setup>
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import AppIcon from '../components/AppIcon.vue'
import ArtSculpture from '../components/ArtSculpture.vue'
import { apps, getApp } from '../data/apps.js'
import { createSession, sessions } from '../stores/chat.js'

const router = useRouter()
const selectedFilter = ref('all')
const appSection = ref(null)
const filteredApps = computed(() => selectedFilter.value === 'all' ? apps : apps.filter((app) => app.id === selectedFilter.value))
const recent = computed(() => [...sessions].filter((session) => session.messages.length).sort((a, b) => b.updatedAt - a.updatedAt).slice(0, 3))

function openApp(app, prompt) {
  const session = createSession(app)
  router.push({ name: 'chat', params: { app, id: session.id }, state: prompt ? { prompt } : {} })
}

function relativeDate(timestamp) {
  const days = Math.floor((Date.now() - timestamp) / 86400000)
  return days === 0 ? '今天' : days === 1 ? '昨天' : `${days} 天前`
}
</script>

<template>
  <div class="home-page">
    <div class="home-heading flex items-center justify-between gap-4">
      <div>
        <div class="eyebrow">YOUR PERSONAL AI WORKSPACE</div>
        <h1>你好，探索者 <span class="greeting-spark">✳</span></h1>
        <p>今天，想和 AI 一起创造什么？</p>
      </div>
      <div class="workspace-label"><span class="status-dot"></span> 让灵感自由发生</div>
    </div>

    <section class="hero-card">
      <div class="hero-content">
        <div class="hero-kicker"><span></span> 多一点灵感，多一种可能</div>
        <h2>你的每一个想法，<br />都有 <span>AI</span> 回应。</h2>
        <p>懂你的心事，也接得住你的奇思妙想。<br class="hidden sm:block" />在这里，找到与你同频的 AI 搭档。</p>
        <button class="hero-button" @click="appSection.scrollIntoView({ behavior: 'smooth', block: 'start' })">
          探索 AI 应用 <AppIcon name="arrow-right" :size="17" />
        </button>
        <div class="hero-footnote"><span class="tiny-stars">✦</span> 好想法，从一次对话开始</div>
      </div>
      <ArtSculpture />
      <div class="hero-page-mark">01 <span>/</span> INFINITE POSSIBILITIES</div>
    </section>

    <section ref="appSection" class="apps-section">
      <div class="section-heading flex items-center justify-between gap-3 flex-wrap">
        <div class="flex items-center gap-3"><h2>发现你的 AI 搭档</h2><span class="count-badge">02</span></div>
        <div class="filter-tabs" role="group" aria-label="筛选应用">
          <button :class="{ active: selectedFilter === 'all' }" :aria-pressed="selectedFilter === 'all'" @click="selectedFilter = 'all'">全部应用</button>
          <button :class="{ active: selectedFilter === 'love' }" :aria-pressed="selectedFilter === 'love'" @click="selectedFilter = 'love'">情感陪伴</button>
          <button :class="{ active: selectedFilter === 'manus' }" :aria-pressed="selectedFilter === 'manus'" @click="selectedFilter = 'manus'">效率提升</button>
        </div>
      </div>

      <div class="app-grid">
        <article v-for="app in filteredApps" :key="app.id" class="app-card" :class="`app-${app.id}`">
          <div class="app-card-top flex items-start justify-between">
            <div class="app-tile-icon"><AppIcon :name="app.icon" :size="29" :stroke-width="1.65" /></div>
            <span class="app-category"><span></span>{{ app.id === 'love' ? '你的情感树洞' : '你的全能搭档' }}</span>
          </div>
          <div class="app-card-title"><h3>{{ app.name }}</h3><span>{{ app.english }}</span></div>
          <h4>{{ app.tagline }}</h4>
          <p class="app-description">{{ app.description }}</p>
          <div class="app-tags"><span v-for="tag in app.tags" :key="tag">{{ tag }}</span></div>
          <div class="app-card-bottom flex items-center justify-between">
            <span class="app-stream-label"><AppIcon name="zap" :size="13" /> {{ app.id === 'love' ? '温暖陪伴 · 实时对话' : '深度思考 · 自主执行' }}</span>
            <button class="app-launch" @click="openApp(app.id)">开始对话 <AppIcon name="arrow-up" :size="16" class="launch-arrow" /></button>
          </div>
        </article>
      </div>
    </section>

    <section class="inspiration-section">
      <div class="section-heading"><div class="flex items-center gap-2"><AppIcon name="lightbulb" :size="19" /><h2>不如，从这里开始</h2></div><span>一个小问题，打开新思路</span></div>
      <div class="inspiration-grid">
        <button @click="openApp('love', '怎么和喜欢的人自然地开启聊天？')"><span class="prompt-icon peach"><AppIcon name="message" :size="19" /></span><span>和喜欢的人，聊点什么？<small>让心动有个自然的开场</small></span><AppIcon name="arrow-right" :size="17" class="prompt-arrow" /></button>
        <button @click="openApp('manus', '帮我制定一份杭州三日旅行计划，兼顾自然风景和当地美食。')"><span class="prompt-icon sage"><AppIcon name="compass" :size="19" /></span><span>计划一场说走就走的旅行<small>把期待变成出发的计划</small></span><AppIcon name="arrow-right" :size="17" class="prompt-arrow" /></button>
        <button @click="openApp('manus', '我想在一个月内入门 Vue 3，请帮我制定每周学习和实践计划。')"><span class="prompt-icon lavender"><AppIcon name="sprout" :size="19" /></span><span>解锁一项新技能<small>让每一天都有一点进步</small></span><AppIcon name="arrow-right" :size="17" class="prompt-arrow" /></button>
      </div>
    </section>

    <section v-if="recent.length" class="recent-section">
      <div class="section-heading"><h2>接着上次的话题</h2><span>每一个想法，都值得被记住</span></div>
      <div class="recent-list">
        <RouterLink v-for="session in recent" :key="session.id" :to="`/chat/${session.app}/${session.id}`"><span class="recent-icon" :class="session.app"><AppIcon :name="getApp(session.app).icon" :size="18" /></span><span class="recent-title">{{ session.title }}<small>{{ getApp(session.app).name }}</small></span><span class="recent-date">{{ relativeDate(session.updatedAt) }}</span><AppIcon name="chevron" :size="16" /></RouterLink>
      </div>
    </section>

    <footer class="home-footer"><span class="flex items-center gap-1.5"><AppIcon name="shield" :size="14" /> 对话记录保存在当前浏览器</span><span>Made for your next idea <span class="footer-star">✳</span></span></footer>
  </div>
</template>
