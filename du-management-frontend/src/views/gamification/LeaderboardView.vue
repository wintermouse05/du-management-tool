<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue'
import { gamificationApi } from '@/api/gamification'
import type { LeaderboardEntryResponse } from '@/types'
import { wsService } from '@/services/websocket'

const entries = ref<LeaderboardEntryResponse[]>([])
const loading = ref(true)

let sub: any = null

async function loadLeaderboard() {
  try { const r = await gamificationApi.getLeaderboard({ size: 50 }); entries.value = r.data.content }
  finally { loading.value = false }
}

onMounted(() => {
  loadLeaderboard()
  sub = wsService.subscribe('/topic/leaderboard', () => {
    loadLeaderboard()
  })
})

onUnmounted(() => {
  if (sub) sub.unsubscribe()
})

function medal(i: number) { return i === 0 ? '🥇' : i === 1 ? '🥈' : i === 2 ? '🥉' : `${i + 1}` }
function bgClass(i: number) { return i === 0 ? 'rank-gold' : i === 1 ? 'rank-silver' : i === 2 ? 'rank-bronze' : '' }
</script>

<template>
  <div class="page-container">
    <div class="page-header"><div><h2>🏆 Leaderboard</h2><p class="page-subtitle">Top contributors ranked by total points</p></div></div>
    <div class="content-card">
      <div v-if="loading" style="text-align:center;padding:var(--space-12);color:var(--theme-text-weak);">Loading...</div>
      <div v-else-if="entries.length === 0" style="text-align:center;padding:var(--space-12);color:var(--theme-text-weak);">No data yet</div>
      <div v-else class="lb-list">
        <div v-for="(u, i) in entries" :key="u.userId" class="lb-entry" :class="bgClass(i)">
          <div class="lb-pos">{{ medal(i) }}</div>
          <div class="lb-avatar" :style="{ background: i < 3 ? 'var(--theme-blue)' : 'var(--theme-border)' }">{{ u.fullName.charAt(0).toUpperCase() }}</div>
          <div class="lb-info"><div class="lb-name">{{ u.fullName }}</div></div>
          <div class="lb-score">{{ u.totalPoints }} <span>pts</span></div>
          <div class="lb-bar-wrap"><div class="lb-bar" :style="{ width: entries[0].totalPoints > 0 ? (u.totalPoints / entries[0].totalPoints * 100) + '%' : '0%' }"></div></div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.lb-list { display: flex; flex-direction: column; gap: var(--space-2); }
.lb-entry { display: flex; align-items: center; gap: var(--space-4); padding: 16px 20px; border-radius: var(--radius-btn); transition: all var(--transition-fast); }
.lb-entry:hover { background: var(--theme-bg-hover); }
.rank-gold { background: rgba(250,204,21,0.06); }
.rank-silver { background: rgba(156,163,175,0.06); }
.rank-bronze { background: rgba(180,83,9,0.04); }
.lb-pos { width: 36px; text-align: center; font-size: 20px; font-weight: 700; }
.lb-avatar { width: 40px; height: 40px; border-radius: var(--radius-full); color: white; display: flex; align-items: center; justify-content: center; font-size: 16px; font-weight: 700; flex-shrink: 0; }
.lb-info { flex: 1; min-width: 0; }
.lb-name { font-size: 15px; font-weight: 600; color: var(--theme-text-primary); }
.lb-score { font-size: 18px; font-weight: 800; color: var(--theme-blue); min-width: 80px; text-align: right; }
.lb-score span { font-size: 12px; font-weight: 500; color: var(--theme-text-weak); }
.lb-bar-wrap { width: 120px; height: 6px; background: var(--theme-divider); border-radius: 3px; overflow: hidden; }
.lb-bar { height: 100%; background: var(--theme-blue); border-radius: 3px; transition: width 0.6s ease; }
</style>
