<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { notificationsApi } from '@/api/notifications'
import type { NotificationScheduleResponse } from '@/types'
import DataTable from 'primevue/datatable'
import Column from 'primevue/column'
import Button from 'primevue/button'
import InputText from 'primevue/inputtext'
import Tag from 'primevue/tag'
import { useToast } from 'primevue/usetoast'

const toast = useToast()
const schedules = ref<NotificationScheduleResponse[]>([])
const loading = ref(false)

const TIME_PATTERN = /^\d{2}:\d{2}(:\d{2})?$/

onMounted(load)

async function load() {
  loading.value = true
  try {
    const r = await notificationsApi.getSchedules()
    schedules.value = r.data
  } finally {
    loading.value = false
  }
}

async function upsert(type: string) {
  const existing = schedules.value.find(s => s.type === type)
  const sendTime = existing?.sendTime || '09:00:00'
  const channelId = existing?.channelId || ''
  const enabled = existing?.enabled ?? true
  try {
    await notificationsApi.upsertSchedule(type, { sendTime, channelId, enabled })
    toast.add({ severity: 'success', summary: `Schedule ${type} updated`, life: 2500 })
    await load()
  } catch (e: any) {
    toast.add({ severity: 'error', summary: 'Error', detail: e.response?.data?.message, life: 3000 })
  }
}

async function toggleEnabled(schedule: NotificationScheduleResponse) {
  try {
    await notificationsApi.upsertSchedule(schedule.type, {
      sendTime: schedule.sendTime,
      channelId: schedule.channelId || '',
      enabled: !schedule.enabled,
    })
    toast.add({ severity: 'success', summary: `${schedule.type} ${schedule.enabled ? 'disabled' : 'enabled'}`, life: 2500 })
    await load()
  } catch (e: any) {
    toast.add({ severity: 'error', summary: 'Error', detail: e.response?.data?.message, life: 3000 })
  }
}

function updateField(schedule: NotificationScheduleResponse, field: 'sendTime' | 'channelId', value: string) {
  if (field === 'sendTime') {
    if (!TIME_PATTERN.test(value)) {
      toast.add({ severity: 'warn', summary: 'Invalid time format', detail: 'Use HH:mm or HH:mm:ss', life: 3000 })
      return
    }
    schedule.sendTime = value
  }
  if (field === 'channelId') {
    schedule.channelId = value || null as any
  }
  upsert(schedule.type)
}
</script>

<template>
  <div class="page-container">
    <div class="page-header">
      <div>
        <h2>Notification Schedules</h2>
        <p class="page-subtitle">Manage chat-based notification delivery times</p>
      </div>
    </div>
    <div class="content-card">
      <DataTable :value="schedules" :loading="loading" stripedRows>
        <Column field="type" header="Type">
          <template #body="{ data }">
            <Tag :value="data.type" :severity="data.type === 'BIRTHDAY' || data.type === 'ANNIVERSARY' ? 'success' : 'info'" />
          </template>
        </Column>
        <Column field="sendTime" header="Send Time">
          <template #body="{ data }">
            <InputText
              :modelValue="data.sendTime"
              type="time"
              style="width:130px"
              @change="updateField(data, 'sendTime', ($event.target as HTMLInputElement).value)"
            />
          </template>
        </Column>
        <Column field="channelId" header="Channel ID">
          <template #body="{ data }">
            <InputText
              :modelValue="data.channelId"
              placeholder="Chat channel ID"
              style="width:200px"
              @change="updateField(data, 'channelId', ($event.target as HTMLInputElement).value)"
            />
          </template>
        </Column>
        <Column field="enabled" header="Status">
          <template #body="{ data }">
            <Tag :value="data.enabled ? 'Enabled' : 'Disabled'" :severity="data.enabled ? 'success' : 'secondary'" />
          </template>
        </Column>
        <Column header="Actions" style="width:130px">
          <template #body="{ data }">
            <Button
              :label="data.enabled ? 'Disable' : 'Enable'"
              :severity="data.enabled ? 'danger' : 'success'"
              size="small"
              @click="toggleEnabled(data)"
            />
          </template>
        </Column>
      </DataTable>
      <p style="margin-top:var(--space-4);color:var(--theme-text-muted);font-size:13px;">
        Changes are applied immediately and reschedule the notification task.
        Channel ID is the Mattermost channel where messages are posted.
      </p>
    </div>
  </div>
</template>
