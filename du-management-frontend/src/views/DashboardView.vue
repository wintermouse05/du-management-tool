<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue'
import { useAuthStore } from '@/stores/auth'
import { membersApi } from '@/api/members'
import { eventsApi } from '@/api/events'
import { surveysApi } from '@/api/surveys'
import { gamificationApi } from '@/api/gamification'
import type { LeaderboardEntryResponse } from '@/types'
import { wsService } from '@/services/websocket'

const auth = useAuthStore()
const stats = ref({ members: 0, events: 0, surveys: 0 })
const topUsers = ref<LeaderboardEntryResponse[]>([])
const loading = ref(true)

async function fetchDashboardData() {
  try {
    const [m, e, s, lb] = await Promise.all([
      membersApi.getAll({ size: 1 }).catch(() => ({ data: { totalElements: 0 } })),
      eventsApi.getAll({ size: 1 }).catch(() => ({ data: { totalElements: 0 } })),
      surveysApi.getAll({ size: 1 }).catch(() => ({ data: { totalElements: 0 } })),
      gamificationApi.getLeaderboard({ size: 5 }).catch(() => ({ data: { content: [] } })),
    ])
    stats.value = {
      members: (m.data as any).totalElements || 0,
      events: (e.data as any).totalElements || 0,
      surveys: (s.data as any).totalElements || 0,
    }
    topUsers.value = (lb.data as any).content || []
  } finally { loading.value = false }
}

let sub: any = null

onMounted(async () => {
  await fetchDashboardData()
  sub = wsService.subscribe('/topic/leaderboard', () => {
    gamificationApi.getLeaderboard({ size: 5 }).then(res => {
      topUsers.value = (res.data as any).content || []
    })
  })
})

onUnmounted(() => {
  if (sub) sub.unsubscribe()
})

function getRankClass(i: number) {
  return i === 0 ? 'gold' : i === 1 ? 'silver' : i === 2 ? 'bronze' : ''
}
</script>

<template>
  <div class="page-container">
    <div class="page-header">
      <div>
        <h2>Welcome back, {{ auth.username }} 👋</h2>
        <p class="page-subtitle">Here's what's happening in your DU today.</p>
      </div>
    </div>

    <div class="stats-grid">
      <div class="stat-card">
        <div class="stat-icon" style="background: var(--theme-blue-light); color: var(--theme-blue);">
          <i class="pi pi-users"></i>
        </div>
        <div class="stat-value">{{ stats.members }}</div>
        <div class="stat-label">Total Members</div>
      </div>
      <div class="stat-card">
        <div class="stat-icon" style="background: var(--theme-success-bg); color: var(--theme-success);">
          <i class="pi pi-calendar"></i>
        </div>
        <div class="stat-value">{{ stats.events }}</div>
        <div class="stat-label">Events</div>
      </div>
      <div class="stat-card">
        <div class="stat-icon" style="background: var(--theme-warning-bg); color: var(--theme-warning);">
          <i class="pi pi-check-square"></i>
        </div>
        <div class="stat-value">{{ stats.surveys }}</div>
        <div class="stat-label">Active Surveys</div>
      </div>
      <div class="stat-card">
        <div class="stat-icon" style="background: rgba(147,51,234,0.08); color: #9333ea;">
          <i class="pi pi-trophy"></i>
        </div>
        <div class="stat-value">{{ topUsers.length > 0 ? topUsers[0].totalPoints : 0 }}</div>
        <div class="stat-label">Top Score</div>
      </div>
    </div>

    <div class="dashboard-grid">
      <div class="content-card">
        <h3 style="margin-bottom: var(--space-5);">🏆 Top Contributors</h3>
        <div v-if="topUsers.length === 0" class="empty-state">No data yet</div>
        <div v-else class="leaderboard-mini">
          <div v-for="(user, i) in topUsers" :key="user.userId" class="lb-row" :class="getRankClass(i)">
            <div class="lb-rank">{{ i + 1 }}</div>
            <div class="lb-avatar">{{ user.fullName.charAt(0) }}</div>
            <div class="lb-name">{{ user.fullName }}</div>
            <div class="lb-pts">{{ user.totalPoints }} pts</div>
          </div>
        </div>
      </div>
      <div class="content-card">
        <h3 style="margin-bottom: var(--space-5);">⚡ Quick Actions</h3>
        <div class="quick-actions">
          <router-link to="/events" class="qa-item">
            <i class="pi pi-calendar"></i><span>View Events</span>
          </router-link>
          <router-link to="/orders" class="qa-item">
            <i class="pi pi-shopping-cart"></i><span>Order Food</span>
          </router-link>
          <router-link to="/seminars" class="qa-item">
            <i class="pi pi-microphone"></i><span>Seminars</span>
          </router-link>
          <router-link to="/leaderboard" class="qa-item">
            <i class="pi pi-trophy"></i><span>Leaderboard</span>
          </router-link>
          <router-link v-if="auth.isAdminOrHR" to="/members" class="qa-item">
            <i class="pi pi-users"></i><span>Members</span>
          </router-link>
          <router-link v-if="auth.isAdminOrHR" to="/late-records" class="qa-item">
            <i class="pi pi-clock"></i><span>Late Records</span>
          </router-link>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.dashboard-grid { display: grid; grid-template-columns: 1fr 1fr; gap: var(--space-6); }
.leaderboard-mini { display: flex; flex-direction: column; gap: var(--space-2); }
.lb-row { display: flex; align-items: center; gap: var(--space-3); padding: 10px 14px; border-radius: var(--radius-md); transition: background var(--transition-fast); }
.lb-row:hover { background: var(--theme-bg-hover); }
.lb-row.gold { background: rgba(250,204,21,0.08); }
.lb-row.silver { background: rgba(156,163,175,0.08); }
.lb-row.bronze { background: rgba(180,83,9,0.06); }
.lb-rank { width: 24px; height: 24px; border-radius: var(--radius-full); background: var(--theme-surface-light); display: flex; align-items: center; justify-content: center; font-size: 12px; font-weight: 700; color: var(--theme-text-weak); }
.lb-row.gold .lb-rank { background: #fbbf24; color: white; }
.lb-row.silver .lb-rank { background: #9ca3af; color: white; }
.lb-row.bronze .lb-rank { background: #b45309; color: white; }
.lb-avatar { width: 32px; height: 32px; border-radius: var(--radius-full); background: var(--theme-blue); color: white; display: flex; align-items: center; justify-content: center; font-size: 13px; font-weight: 600; }
.lb-name { flex: 1; font-size: 14px; font-weight: 500; }
.lb-pts { font-size: 13px; font-weight: 700; color: var(--theme-blue); }
.quick-actions { display: grid; grid-template-columns: 1fr 1fr; gap: var(--space-3); }
.qa-item { display: flex; align-items: center; gap: var(--space-3); padding: 14px 16px; border: 1px solid var(--theme-border); border-radius: var(--radius-btn); font-size: 14px; font-weight: 500; color: var(--theme-text-primary); transition: all var(--transition-fast); text-decoration: none; }
.qa-item:hover { border-color: var(--theme-blue); background: var(--theme-blue-light); color: var(--theme-blue); }
.qa-item i { font-size: 18px; color: var(--theme-text-weak); }
.qa-item:hover i { color: var(--theme-blue); }
.empty-state { padding: var(--space-8); text-align: center; color: var(--theme-text-weak); font-size: 14px; }
@media (max-width: 1024px) { .dashboard-grid { grid-template-columns: 1fr; } }
@media (max-width: 768px) {
  .quick-actions { grid-template-columns: 1fr; }
  .qa-item { padding: 12px 14px; font-size: 13px; }
  .lb-row { padding: 8px 10px; gap: var(--space-2); }
  .lb-avatar { width: 28px; height: 28px; font-size: 11px; }
  .lb-name { font-size: 13px; }
}
</style>
