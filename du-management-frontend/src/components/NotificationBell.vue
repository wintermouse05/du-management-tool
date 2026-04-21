<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import Button from 'primevue/button'
import { notificationsApi } from '@/api/notifications'
import type { NotificationInboxResponse } from '@/types'

const router = useRouter()
const rootEl = ref<HTMLElement | null>(null)
const panelOpen = ref(false)
const loading = ref(false)
const markingAll = ref(false)
const unreadCount = ref(0)
const totalElements = ref(0)
const notifications = ref<NotificationInboxResponse[]>([])
const pageSize = 8

const unreadBadgeText = computed(() => {
  if (unreadCount.value > 99) return '99+'
  return String(unreadCount.value)
})

onMounted(() => {
  void loadUnreadCount()
  document.addEventListener('mousedown', onClickOutside)
  window.addEventListener('du-notification-received', onRealtimeNotification as EventListener)
})

onUnmounted(() => {
  document.removeEventListener('mousedown', onClickOutside)
  window.removeEventListener('du-notification-received', onRealtimeNotification as EventListener)
})

async function togglePanel() {
  panelOpen.value = !panelOpen.value
  if (panelOpen.value) {
    await loadNotifications()
  }
}

async function loadNotifications() {
  loading.value = true
  try {
    const response = await notificationsApi.getMyNotifications({ page: 0, size: pageSize })
    notifications.value = response.data.content
    totalElements.value = response.data.totalElements
  } finally {
    loading.value = false
  }
}

async function loadUnreadCount() {
  const response = await notificationsApi.getMyUnreadCount()
  unreadCount.value = response.data.unreadCount
}

async function markAsRead(notification: NotificationInboxResponse) {
  if (notification.read) return
  const response = await notificationsApi.markAsRead(notification.id)
  const updated = response.data
  const index = notifications.value.findIndex(item => item.id === notification.id)
  if (index >= 0) {
    notifications.value[index] = updated
  }
  unreadCount.value = Math.max(0, unreadCount.value - 1)
}

async function markAllAsRead() {
  if (unreadCount.value === 0) return
  markingAll.value = true
  try {
    await notificationsApi.markAllAsRead()
    notifications.value = notifications.value.map(item => ({
      ...item,
      read: true,
      readAt: item.readAt ?? new Date().toISOString(),
    }))
    unreadCount.value = 0
  } finally {
    markingAll.value = false
  }
}

async function openNotification(notification: NotificationInboxResponse) {
  if (!notification.read) {
    await markAsRead(notification)
  }

  if (notification.actionUrl) {
    panelOpen.value = false
    await router.push(notification.actionUrl)
  }
}

function formatDate(dateTime: string): string {
  const parsed = new Date(dateTime)
  if (Number.isNaN(parsed.getTime())) {
    return dateTime
  }
  return parsed.toLocaleString()
}

function onClickOutside(event: MouseEvent) {
  if (!panelOpen.value || !rootEl.value) return
  if (!rootEl.value.contains(event.target as Node)) {
    panelOpen.value = false
  }
}

function onRealtimeNotification(event: Event) {
  const customEvent = event as CustomEvent<Partial<NotificationInboxResponse>>
  const detail = customEvent.detail
  if (!detail || typeof detail.id !== 'number') {
    void loadUnreadCount()
    return
  }

  const exists = notifications.value.some(item => item.id === detail.id)
  if (!exists) {
    notifications.value.unshift({
      id: detail.id,
      title: detail.title ?? 'Notification',
      message: detail.message ?? '',
      type: detail.type ?? 'INFO',
      read: false,
      actionUrl: detail.actionUrl ?? null,
      createdAt: detail.createdAt ?? new Date().toISOString(),
      readAt: null,
    })

    if (notifications.value.length > pageSize) {
      notifications.value.pop()
    }
    totalElements.value += 1
    unreadCount.value += 1
  }
}
</script>

<template>
  <div ref="rootEl" class="notification-bell">
    <Button icon="pi pi-bell" rounded outlined aria-label="Notifications" @click="togglePanel" />
    <span v-if="unreadCount > 0" class="notification-badge">{{ unreadBadgeText }}</span>

    <div v-if="panelOpen" class="notification-panel">
      <div class="notification-panel-header">
        <h4>Notifications</h4>
        <Button
          v-if="unreadCount > 0"
          label="Mark all read"
          text
          size="small"
          :loading="markingAll"
          @click="markAllAsRead"
        />
      </div>

      <div v-if="loading" class="panel-state">Loading...</div>
      <div v-else-if="notifications.length === 0" class="panel-state">No notifications yet.</div>

      <ul v-else class="notification-list">
        <li
          v-for="item in notifications"
          :key="item.id"
          class="notification-item"
          :class="{ unread: !item.read }"
        >
          <button class="notification-content" @click="openNotification(item)">
            <span class="notification-title">{{ item.title }}</span>
            <span class="notification-message">{{ item.message }}</span>
            <span class="notification-time">{{ formatDate(item.createdAt) }}</span>
          </button>
          <Button
            v-if="!item.read"
            icon="pi pi-check"
            text
            rounded
            size="small"
            aria-label="Mark as read"
            @click.stop="markAsRead(item)"
          />
        </li>
      </ul>

    </div>
  </div>
</template>

<style scoped>
.notification-bell {
  position: fixed;
  top: 16px;
  right: 20px;
  z-index: 120;
}

.notification-badge {
  position: absolute;
  top: -5px;
  right: -5px;
  min-width: 18px;
  height: 18px;
  padding: 0 5px;
  border-radius: 999px;
  background: #dc2626;
  color: #fff;
  font-size: 11px;
  line-height: 18px;
  text-align: center;
  font-weight: 700;
}

.notification-panel {
  position: absolute;
  top: 46px;
  right: 0;
  width: 360px;
  max-height: 440px;
  background: var(--theme-surface);
  border: 1px solid var(--theme-border);
  border-radius: var(--radius-lg);
  box-shadow: var(--theme-shadow-elevated);
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.notification-panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: var(--space-3) var(--space-4);
  border-bottom: 1px solid var(--theme-divider);
}

.notification-panel-header h4 {
  margin: 0;
  font-size: 14px;
  font-weight: 700;
  color: var(--theme-text-primary);
}

.panel-state {
  padding: var(--space-5);
  text-align: center;
  color: var(--theme-text-secondary);
  font-size: 14px;
}

.notification-list {
  margin: 0;
  padding: 0;
  list-style: none;
  overflow-y: auto;
}

.notification-item {
  display: flex;
  align-items: flex-start;
  gap: var(--space-2);
  padding: var(--space-3) var(--space-3);
  border-bottom: 1px solid var(--theme-divider);
}

.notification-item.unread {
  background: var(--theme-blue-light);
}

.notification-content {
  border: 0;
  background: transparent;
  text-align: left;
  width: 100%;
  padding: 0;
  cursor: pointer;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.notification-title {
  color: var(--theme-text-primary);
  font-size: 13px;
  font-weight: 700;
}

.notification-message {
  color: var(--theme-text-secondary);
  font-size: 13px;
  line-height: 1.35;
}

.notification-time {
  color: var(--theme-text-weak);
  font-size: 12px;
}

@media (max-width: 1024px) {
  .notification-bell {
    top: 8px;
    right: 62px;
  }

  .notification-panel {
    width: min(360px, calc(100vw - 16px));
    right: -46px;
  }
}
</style>