<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { notificationsApi } from '@/api/notifications'
import type { NotificationScheduleResponse } from '@/types'
import { getApiErrorDetail } from '@/utils/apiError'
import DataTable from 'primevue/datatable'
import Column from 'primevue/column'
import Button from 'primevue/button'
import InputText from 'primevue/inputtext'
import Tag from 'primevue/tag'
import { useToast } from 'primevue/usetoast'

const toast = useToast()
const schedules = ref<NotificationScheduleResponse[]>([])
const loading = ref(false)
const runningLateReport = ref(false)

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
    toast.add({ severity: 'error', summary: 'Error', detail: getApiErrorDetail(e), life: 3000 })
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
    toast.add({ severity: 'error', summary: 'Error', detail: getApiErrorDetail(e), life: 3000 })
  }
}

async function runLateReportNow() {
  runningLateReport.value = true
  try {
    const response = await notificationsApi.runLatePenaltySchedule()
    toast.add({
      severity: response.data.sent ? 'success' : 'info',
      summary: response.data.sent ? 'Late report sent' : 'Late report not sent',
      detail: response.data.message,
      life: 4000,
    })
  } catch (e: any) {
    toast.add({ severity: 'error', summary: 'Error', detail: getApiErrorDetail(e), life: 3500 })
  } finally {
    runningLateReport.value = false
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
        <template #empty>
          No Notification Schedule found. Please configure a schedule.
        </template>
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
        <Column header="Actions" style="width:230px">
          <template #body="{ data }">
            <div class="schedule-actions">
              <Button
                :label="data.enabled ? 'Disable' : 'Enable'"
                :severity="data.enabled ? 'danger' : 'success'"
                size="small"
                @click="toggleEnabled(data)"
              />
              <Button
                v-if="data.type === 'LATE'"
                label="Run now"
                icon="pi pi-send"
                size="small"
                outlined
                :loading="runningLateReport"
                @click="runLateReportNow"
              />
            </div>
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

<style scoped>
.schedule-actions {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  white-space: nowrap;
}
</style>


