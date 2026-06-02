<script setup lang="ts">
import { computed, ref, onMounted, onUnmounted } from 'vue'
import Button from 'primevue/button'
import { useAuthStore } from '@/stores/auth'
import { membersApi } from '@/api/members'
import { eventsApi } from '@/api/events'
import { surveysApi } from '@/api/surveys'
import { gamificationApi } from '@/api/gamification'
import { chatopsApi } from '@/api/chatops'
import { projectsApi } from '@/api/projects'
import type { ChatopsLeaveRequestResponse, ChatopsLeaveRequestSummaryResponse, LeaderboardEntryResponse } from '@/types'
import { wsService } from '@/services/websocket'

const auth = useAuthStore()
const stats = ref({ members: 0, events: 0, surveys: 0, openProjects: 0, availableMembers: 0 })
const topUsers = ref<LeaderboardEntryResponse[]>([])
const leaveSummary = ref<ChatopsLeaveRequestSummaryResponse | null>(null)
const leaveLoadFailed = ref(false)
const leaveRefreshing = ref(false)
const loading = ref(true)
const todayLeaveRequests = computed(() => leaveSummary.value?.requests || [])

async function fetchDashboardData() {
  try {
    leaveLoadFailed.value = false
    const [m, e, s, projectAvailability, lb, leave] = await Promise.all([
      membersApi.getAll({ size: 1 }).catch(() => ({ data: { totalElements: 0 } })),
      eventsApi.getAll({ size: 1 }).catch(() => ({ data: { totalElements: 0 } })),
      surveysApi.getAll({ size: 1 }).catch(() => ({ data: { totalElements: 0 } })),
      projectsApi.getAvailabilitySummary().catch(() => ({ data: { openProjectCount: 0, availableMemberCount: 0 } })),
      gamificationApi.getLeaderboard({ size: 5 }).catch(() => ({ data: { content: [] } })),
      chatopsApi.getTodayLeaveRequests().catch(() => {
        leaveLoadFailed.value = true
        return null
      }),
    ])
    stats.value = {
      members: (m.data as any).totalElements || 0,
      events: (e.data as any).totalElements || 0,
      surveys: (s.data as any).totalElements || 0,
      openProjects: (projectAvailability.data as any).openProjectCount || 0,
      availableMembers: (projectAvailability.data as any).availableMemberCount || 0,
    }
    topUsers.value = (lb.data as any).content || []
    leaveSummary.value = leave?.data || null
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

async function refreshLeaveRequests() {
  leaveRefreshing.value = true
  leaveLoadFailed.value = false
  try {
    const response = await chatopsApi.refreshTodayLeaveRequests()
    leaveSummary.value = response.data
  } catch {
    leaveLoadFailed.value = true
  } finally {
    leaveRefreshing.value = false
  }
}

function formatTime(value: string | null | undefined) {
  if (!value) return '--:--'
  return new Date(value).toLocaleTimeString('vi-VN', { hour: '2-digit', minute: '2-digit' })
}

function requesterInitial(request: ChatopsLeaveRequestResponse) {
  return request.requesterName?.trim().charAt(0).toUpperCase() || '?'
}
</script>

<template>
  <div class="page-container">
    <div class="page-header">
      <div>
        <h2>Welcome back, {{ auth.displayName }} 👋</h2>
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
        <div class="stat-icon" style="background: rgba(16,185,129,0.1); color: #059669;">
          <i class="pi pi-briefcase"></i>
        </div>
        <div class="stat-value">{{ stats.openProjects }}</div>
        <div class="stat-label">Open Projects</div>
      </div>
      <div class="stat-card">
        <div class="stat-icon" style="background: rgba(14,165,233,0.1); color: #0284c7;">
          <i class="pi pi-user-plus"></i>
        </div>
        <div class="stat-value">{{ stats.availableMembers }}</div>
        <div class="stat-label">Available Members</div>
      </div>
      <div class="stat-card">
        <div class="stat-icon" style="background: rgba(147,51,234,0.08); color: #9333ea;">
          <i class="pi pi-trophy"></i>
        </div>
        <div class="stat-value">{{ topUsers.length > 0 ? topUsers[0].totalPoints : 0 }}</div>
        <div class="stat-label">Top Score</div>
      </div>
      <div class="stat-card">
        <div class="stat-icon" style="background: rgba(14,165,233,0.1); color: #0284c7;">
          <i class="pi pi-briefcase"></i>
        </div>
        <div class="stat-value">{{ leaveSummary?.total || 0 }}</div>
        <div class="stat-label">WFH/OFF Today</div>
      </div>
    </div>

    <div class="dashboard-grid">
      <div class="content-card leave-card">
        <div class="leave-header">
          <div>
            <h3>Today's WFH/OFF Requests</h3>
            <p v-if="leaveSummary?.fetchedAt" class="leave-subtitle">Updated {{ formatTime(leaveSummary.fetchedAt) }}</p>
            <p v-else class="leave-subtitle">Synced from the ChatOps channel</p>
          </div>
          <div class="leave-actions">
            <div class="leave-pills" v-if="leaveSummary?.chatopsEnabled">
              <span class="leave-pill wfh">WFH {{ leaveSummary.wfhCount }}</span>
              <span class="leave-pill off">OFF {{ leaveSummary.offCount }}</span>
            </div>
            <Button
              v-if="auth.isAdminOrHR"
              label="Check now"
              icon="pi pi-refresh"
              size="small"
              outlined
              :loading="leaveRefreshing"
              :disabled="leaveRefreshing"
              @click="refreshLeaveRequests"
            />
          </div>
        </div>

        <div v-if="leaveLoadFailed" class="empty-state">Unable to load ChatOps requests right now.</div>
        <div v-else-if="leaveSummary?.errorMessage" class="empty-state">{{ leaveSummary.errorMessage }}</div>
        <div v-else-if="leaveSummary && !leaveSummary.chatopsEnabled" class="empty-state">ChatOps is not enabled yet.</div>
        <div v-else-if="todayLeaveRequests.length === 0" class="empty-state">No WFH/OFF requests found for today.</div>
        <div v-else class="leave-list">
          <article
            v-for="request in todayLeaveRequests"
            :key="request.postId || `${request.userId}-${request.postedAt}-${request.message}`"
            class="leave-item"
          >
            <div class="leave-avatar">{{ requesterInitial(request) }}</div>
            <div class="leave-body">
              <div class="leave-meta">
                <strong>{{ request.requesterName }}</strong>
                <span>{{ formatTime(request.postedAt) }}</span>
                <span class="leave-type" :class="request.type.toLowerCase()">{{ request.type }}</span>
              </div>
              <p class="leave-message">{{ request.message }}</p>
            </div>
          </article>
        </div>
      </div>

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
.leave-card { grid-column: 1 / -1; }
.leave-header { display: flex; align-items: flex-start; justify-content: space-between; gap: var(--space-4); margin-bottom: var(--space-5); }
.leave-header h3 { margin: 0; }
.leave-subtitle { margin-top: var(--space-1); font-size: 13px; color: var(--theme-text-weak); }
.leave-actions { display: flex; align-items: center; gap: var(--space-3); flex-wrap: wrap; justify-content: flex-end; }
.leave-pills { display: flex; gap: var(--space-2); flex-wrap: wrap; justify-content: flex-end; }
.leave-pill { border-radius: var(--radius-full); padding: 6px 10px; font-size: 12px; font-weight: 700; letter-spacing: 0.02em; }
.leave-pill.wfh { background: rgba(14,165,233,0.1); color: #0284c7; }
.leave-pill.off { background: var(--theme-warning-bg); color: var(--theme-warning); }
.leave-list { display: grid; grid-template-columns: repeat(auto-fit, minmax(320px, 1fr)); gap: var(--space-3); }
.leave-item { display: flex; gap: var(--space-3); padding: 14px; border: 1px solid var(--theme-border); border-radius: var(--radius-btn); background: var(--theme-surface-light); }
.leave-avatar { flex: 0 0 36px; width: 36px; height: 36px; border-radius: var(--radius-full); background: linear-gradient(135deg, #0284c7, #10b981); color: white; display: flex; align-items: center; justify-content: center; font-size: 13px; font-weight: 800; }
.leave-body { min-width: 0; flex: 1; }
.leave-meta { display: flex; align-items: center; gap: var(--space-2); flex-wrap: wrap; font-size: 13px; color: var(--theme-text-weak); margin-bottom: var(--space-2); }
.leave-meta strong { color: var(--theme-text-primary); font-size: 14px; }
.leave-type { border-radius: var(--radius-full); padding: 3px 8px; font-size: 11px; font-weight: 800; }
.leave-type.wfh { background: rgba(14,165,233,0.1); color: #0284c7; }
.leave-type.off { background: var(--theme-warning-bg); color: var(--theme-warning); }
.leave-message { margin: 0; color: var(--theme-text-secondary); font-size: 13px; line-height: 1.55; white-space: pre-line; overflow-wrap: anywhere; }
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
  .leave-header { flex-direction: column; align-items: stretch; }
  .leave-actions,
  .leave-pills { justify-content: flex-start; }
  .leave-list { grid-template-columns: 1fr; }
  .leave-item { padding: 12px; }
  .quick-actions { grid-template-columns: 1fr; }
  .qa-item { padding: 12px 14px; font-size: 13px; }
  .lb-row { padding: 8px 10px; gap: var(--space-2); }
  .lb-avatar { width: 28px; height: 28px; font-size: 11px; }
  .lb-name { font-size: 13px; }
}
</style>
