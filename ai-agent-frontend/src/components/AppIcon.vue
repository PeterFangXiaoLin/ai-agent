<script setup>
import {
  ArrowDown, ArrowDownToLine, ArrowLeft, ArrowRight, ArrowUp, Check, ChevronDown,
  ChevronRight, CircleHelp, Coffee, Compass, Copy, Ellipsis, FileText, Heart,
  History, House, Lightbulb, Menu, MessageCircle, PanelLeftClose, Plus, Search,
  Send, ShieldCheck, Sparkles, Sprout, Square, Trash2, X, Zap, RotateCcw,
} from 'lucide-vue-next'

defineProps({ name: String, size: { type: Number, default: 20 }, strokeWidth: { type: Number, default: 1.7 } })

const icons = {
  'arrow-down': ArrowDown, download: ArrowDownToLine, 'arrow-left': ArrowLeft,
  'arrow-right': ArrowRight, 'arrow-up': ArrowUp, check: Check, chevron: ChevronRight,
  'chevron-down': ChevronDown, help: CircleHelp, coffee: Coffee, compass: Compass,
  copy: Copy, more: Ellipsis, file: FileText, heart: Heart, history: History,
  home: House, lightbulb: Lightbulb, menu: Menu, message: MessageCircle,
  collapse: PanelLeftClose, plus: Plus, search: Search, send: Send,
  shield: ShieldCheck, sparkles: Sparkles, sprout: Sprout, stop: Square,
  trash: Trash2, close: X, zap: Zap, retry: RotateCcw,
}
</script>

<template>
  <component :is="icons[name] || Sparkles" :size="size" :stroke-width="strokeWidth" aria-hidden="true" />
</template>
