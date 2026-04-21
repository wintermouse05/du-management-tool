<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { notificationsApi } from '@/api/notifications'
import type {
  NotificationJobResponse,
  NotificationChannelRequest,
  NotificationChannelResponse,
  NotificationTemplateRequest,
  NotificationTemplateResponse,
} from '@/types'
import DataTable from 'primevue/datatable'
import Column from 'primevue/column'
import Button from 'primevue/button'
import InputNumber from 'primevue/inputnumber'
import InputText from 'primevue/inputtext'
import Textarea from 'primevue/textarea'
import Select from 'primevue/select'
import Dialog from 'primevue/dialog'
import Tag from 'primevue/tag'
import { useToast } from 'primevue/usetoast'

const toast = useToast()
const jobs = ref<NotificationJobResponse[]>([])
const templates = ref<NotificationTemplateResponse[]>([])
const channels = ref<NotificationChannelResponse[]>([])
const loading = ref(false)
const templatesLoading = ref(false)
const channelsLoading = ref(false)
const surveyId = ref<number>(0)

const templateDialogVisible = ref(false)
const editingTemplate = ref(false)
const editingTemplateCode = ref('')
const templateForm = ref<NotificationTemplateRequest>({
  code: '',
  name: '',
  subjectTemplate: '',
  bodyTemplate: '',
  enabled: true,
})
const enabledOptions = [
  { label: 'Enabled', value: true },
  { label: 'Disabled', value: false },
]
const channelTypeOptions = [
  { label: 'EMAIL', value: 'EMAIL' },
  { label: 'WEBHOOK', value: 'WEBHOOK' },
]

const channelDialogVisible = ref(false)
const editingChannel = ref(false)
const editingChannelId = ref<number | null>(null)
const channelForm = ref<NotificationChannelRequest>({
  type: 'WEBHOOK',
  endpoint: '',
  enabled: true,
})

onMounted(async () => {
  await Promise.all([loadJobs(), loadTemplates(), loadChannels()])
})

async function loadJobs() {
  loading.value = true
  try {
    const r = await notificationsApi.getJobs()
    jobs.value = r.data
  } finally {
    loading.value = false
  }
}

async function loadTemplates() {
  templatesLoading.value = true
  try {
    const r = await notificationsApi.getTemplates()
    templates.value = r.data
  } finally {
    templatesLoading.value = false
  }
}

async function loadChannels() {
  channelsLoading.value = true
  try {
    const r = await notificationsApi.getChannels()
    channels.value = r.data
  } finally {
    channelsLoading.value = false
  }
}

async function triggerReminder() {
  if (!surveyId.value) return
  try { const r = await notificationsApi.triggerSurveyReminder(surveyId.value); toast.add({ severity:'success', summary:'Triggered', detail: r.data.message, life:3000 })
  } catch (e: any) { toast.add({ severity:'error', summary:'Error', detail: e.response?.data?.message, life:3000 }) }
}

async function toggleJob(job: NotificationJobResponse) {
  try {
    await notificationsApi.setJobEnabled(job.code, { enabled: !job.enabled })
    toast.add({
      severity: 'success',
      summary: 'Updated',
      detail: `${job.code} has been ${job.enabled ? 'disabled' : 'enabled'}`,
      life: 2500,
    })
    await loadJobs()
  } catch (e: any) {
    toast.add({ severity: 'error', summary: 'Error', detail: e.response?.data?.message, life: 3000 })
  }
}

function openCreateTemplate() {
  editingTemplate.value = false
  editingTemplateCode.value = ''
  templateForm.value = {
    code: '',
    name: '',
    subjectTemplate: '',
    bodyTemplate: '',
    enabled: true,
  }
  templateDialogVisible.value = true
}

function openEditTemplate(template: NotificationTemplateResponse) {
  editingTemplate.value = true
  editingTemplateCode.value = template.code
  templateForm.value = {
    code: template.code,
    name: template.name,
    subjectTemplate: template.subjectTemplate,
    bodyTemplate: template.bodyTemplate,
    enabled: template.enabled,
  }
  templateDialogVisible.value = true
}

async function saveTemplate() {
  try {
    if (editingTemplate.value) {
      await notificationsApi.updateTemplate(editingTemplateCode.value, templateForm.value)
    } else {
      await notificationsApi.createTemplate(templateForm.value)
    }
    toast.add({ severity: 'success', summary: 'Saved template', life: 2500 })
    templateDialogVisible.value = false
    await loadTemplates()
  } catch (e: any) {
    toast.add({ severity: 'error', summary: 'Error', detail: e.response?.data?.message, life: 3000 })
  }
}

async function deleteTemplate(template: NotificationTemplateResponse) {
  try {
    await notificationsApi.deleteTemplate(template.code)
    toast.add({ severity: 'warn', summary: 'Template deleted', life: 2500 })
    await loadTemplates()
  } catch (e: any) {
    toast.add({ severity: 'error', summary: 'Error', detail: e.response?.data?.message, life: 3000 })
  }
}

function openCreateChannel() {
  editingChannel.value = false
  editingChannelId.value = null
  channelForm.value = {
    type: 'WEBHOOK',
    endpoint: '',
    enabled: true,
  }
  channelDialogVisible.value = true
}

function openEditChannel(channel: NotificationChannelResponse) {
  editingChannel.value = true
  editingChannelId.value = channel.id
  channelForm.value = {
    type: channel.type,
    endpoint: channel.endpoint,
    enabled: channel.enabled,
  }
  channelDialogVisible.value = true
}

async function saveChannel() {
  try {
    if (editingChannel.value && editingChannelId.value) {
      await notificationsApi.updateChannel(editingChannelId.value, channelForm.value)
    } else {
      await notificationsApi.createChannel(channelForm.value)
    }
    toast.add({ severity: 'success', summary: 'Saved channel', life: 2500 })
    channelDialogVisible.value = false
    await loadChannels()
  } catch (e: any) {
    toast.add({ severity: 'error', summary: 'Error', detail: e.response?.data?.message, life: 3000 })
  }
}

async function deleteChannel(channel: NotificationChannelResponse) {
  try {
    await notificationsApi.deleteChannel(channel.id)
    toast.add({ severity: 'warn', summary: 'Channel deleted', life: 2500 })
    await loadChannels()
  } catch (e: any) {
    toast.add({ severity: 'error', summary: 'Error', detail: e.response?.data?.message, life: 3000 })
  }
}

function formatDate(dateTime: string | null): string {
  if (!dateTime) return 'Never'
  const parsed = new Date(dateTime)
  if (Number.isNaN(parsed.getTime())) return dateTime
  return parsed.toLocaleString()
}
</script>

<template>
  <div class="page-container">
    <div class="page-header"><div><h2>Notifications</h2><p class="page-subtitle">Scheduled jobs and manual triggers</p></div></div>
    <div class="content-card" style="margin-bottom:var(--space-6);">
      <h3 style="margin-bottom:var(--space-4);">Scheduled Jobs</h3>
      <DataTable :value="jobs" :loading="loading" stripedRows>
        <Column field="code" header="Job Code"><template #body="{data}"><Tag :value="data.code" severity="info" /></template></Column>
        <Column field="schedule" header="Cron"><template #body="{data}"><code style="background:var(--theme-surface-light);padding:4px 8px;border-radius:4px;font-size:13px;">{{ data.schedule }}</code></template></Column>
        <Column field="description" header="Description" />
        <Column field="enabled" header="Status">
          <template #body="{ data }"><Tag :value="data.enabled ? 'Enabled' : 'Disabled'" :severity="data.enabled ? 'success' : 'secondary'" /></template>
        </Column>
        <Column field="lastRunAt" header="Last Run">
          <template #body="{ data }">{{ formatDate(data.lastRunAt) }}</template>
        </Column>
        <Column header="Actions" style="width:130px">
          <template #body="{ data }">
            <Button
              :label="data.enabled ? 'Disable' : 'Enable'"
              :severity="data.enabled ? 'danger' : 'success'"
              size="small"
              @click="toggleJob(data)"
            />
          </template>
        </Column>
      </DataTable>
    </div>

    <div class="content-card" style="margin-bottom:var(--space-6);">
      <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:var(--space-4);">
        <h3>Notification Templates</h3>
        <Button label="Add Template" icon="pi pi-plus" size="small" @click="openCreateTemplate" />
      </div>
      <DataTable :value="templates" :loading="templatesLoading" stripedRows>
        <Column field="code" header="Code" />
        <Column field="name" header="Name" />
        <Column field="enabled" header="Status">
          <template #body="{ data }"><Tag :value="data.enabled ? 'Enabled' : 'Disabled'" :severity="data.enabled ? 'success' : 'secondary'" /></template>
        </Column>
        <Column field="updatedAt" header="Updated At">
          <template #body="{ data }">{{ formatDate(data.updatedAt) }}</template>
        </Column>
        <Column header="Actions" style="width:150px">
          <template #body="{ data }">
            <Button icon="pi pi-pencil" text rounded severity="info" @click="openEditTemplate(data)" />
            <Button icon="pi pi-trash" text rounded severity="danger" @click="deleteTemplate(data)" />
          </template>
        </Column>
      </DataTable>
    </div>

    <div class="content-card" style="margin-bottom:var(--space-6);">
      <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:var(--space-4);">
        <h3>Notification Channels</h3>
        <Button label="Add Channel" icon="pi pi-plus" size="small" @click="openCreateChannel" />
      </div>
      <DataTable :value="channels" :loading="channelsLoading" stripedRows>
        <Column field="type" header="Type" />
        <Column field="endpoint" header="Endpoint" />
        <Column field="enabled" header="Status">
          <template #body="{ data }"><Tag :value="data.enabled ? 'Enabled' : 'Disabled'" :severity="data.enabled ? 'success' : 'secondary'" /></template>
        </Column>
        <Column header="Actions" style="width:150px">
          <template #body="{ data }">
            <Button icon="pi pi-pencil" text rounded severity="info" @click="openEditChannel(data)" />
            <Button icon="pi pi-trash" text rounded severity="danger" @click="deleteChannel(data)" />
          </template>
        </Column>
      </DataTable>
    </div>

    <div class="content-card">
      <h3 style="margin-bottom:var(--space-4);">Manual Trigger</h3>
      <div style="display:flex;gap:var(--space-3);align-items:flex-end;">
        <div class="form-field"><label>Survey ID</label><InputNumber v-model="surveyId" style="width:160px" /></div>
        <Button label="Send Survey Reminder" icon="pi pi-send" @click="triggerReminder" />
      </div>
    </div>

    <Dialog v-model:visible="templateDialogVisible" :header="editingTemplate ? 'Edit Template' : 'Add Template'" modal :style="{ width: '640px' }">
      <div style="display:grid;grid-template-columns:1fr 1fr;gap:var(--space-4);">
        <div class="form-field">
          <label>Code</label>
          <InputText v-model="templateForm.code" :disabled="editingTemplate" fluid />
        </div>
        <div class="form-field">
          <label>Name</label>
          <InputText v-model="templateForm.name" fluid />
        </div>
        <div class="form-field" style="grid-column:1 / -1;">
          <label>Subject Template</label>
          <InputText v-model="templateForm.subjectTemplate" fluid />
        </div>
        <div class="form-field" style="grid-column:1 / -1;">
          <label>Body Template</label>
          <Textarea v-model="templateForm.bodyTemplate" rows="5" fluid />
        </div>
        <div class="form-field">
          <label>Status</label>
          <Select v-model="templateForm.enabled" :options="enabledOptions" optionLabel="label" optionValue="value" fluid />
        </div>
      </div>
      <template #footer>
        <Button label="Cancel" text @click="templateDialogVisible = false" />
        <Button :label="editingTemplate ? 'Update' : 'Create'" icon="pi pi-check" @click="saveTemplate" />
      </template>
    </Dialog>

    <Dialog v-model:visible="channelDialogVisible" :header="editingChannel ? 'Edit Channel' : 'Add Channel'" modal :style="{ width: '520px' }">
      <div style="display:grid;grid-template-columns:1fr 1fr;gap:var(--space-4);">
        <div class="form-field">
          <label>Type</label>
          <Select v-model="channelForm.type" :options="channelTypeOptions" optionLabel="label" optionValue="value" fluid />
        </div>
        <div class="form-field">
          <label>Status</label>
          <Select v-model="channelForm.enabled" :options="enabledOptions" optionLabel="label" optionValue="value" fluid />
        </div>
        <div class="form-field" style="grid-column:1 / -1;">
          <label>Endpoint</label>
          <InputText v-model="channelForm.endpoint" placeholder="Webhook URL or email channel identifier" fluid />
        </div>
      </div>
      <template #footer>
        <Button label="Cancel" text @click="channelDialogVisible = false" />
        <Button :label="editingChannel ? 'Update' : 'Create'" icon="pi pi-check" @click="saveChannel" />
      </template>
    </Dialog>
  </div>
</template>
