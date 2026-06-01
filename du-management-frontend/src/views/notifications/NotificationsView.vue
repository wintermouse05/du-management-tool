<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { notificationsApi } from '@/api/notifications'
import { surveysApi } from '@/api/surveys'
import { ChatopsChannelPurpose } from '@/types'
import { getApiErrorDetail } from '@/utils/apiError'
import type {
  NotificationJobResponse,
  NotificationChannelRequest,
  NotificationChannelResponse,
  ChatopsChannelConfigResponse,
  NotificationTemplateRequest,
  NotificationTemplateResponse,
  SurveyResponse,
} from '@/types'
import DataTable from 'primevue/datatable'
import Column from 'primevue/column'
import Button from 'primevue/button'
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
const surveys = ref<SurveyResponse[]>([])
const chatopsConfigsLoading = ref(false)
const chatopsSavingPurpose = ref<ChatopsChannelPurpose | null>(null)
const loading = ref(false)
const templatesLoading = ref(false)
const channelsLoading = ref(false)
const surveysLoading = ref(false)
const selectedSurveyId = ref<number | null>(null)
const surveyReminderTriggering = ref(false)

const chatopsConfigByPurpose = ref<Record<ChatopsChannelPurpose, ChatopsChannelConfigResponse | null>>({
  [ChatopsChannelPurpose.LATE_INPUT]: null,
  [ChatopsChannelPurpose.NOTIFICATION_OUTPUT]: null,
})

const chatopsForms = ref<Record<ChatopsChannelPurpose, { token: string; channelUrl: string }>>({
  [ChatopsChannelPurpose.LATE_INPUT]: { token: '', channelUrl: '' },
  [ChatopsChannelPurpose.NOTIFICATION_OUTPUT]: { token: '', channelUrl: '' },
})

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
  { label: 'CHAT', value: 'CHAT' },
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
  await Promise.all([loadJobs(), loadTemplates(), loadChannels(), loadSurveys(), loadChatopsConfigs()])
})

async function loadChatopsConfigs() {
  chatopsConfigsLoading.value = true
  try {
    const response = await notificationsApi.getChatopsChannels()
    const nextConfigs: Record<ChatopsChannelPurpose, ChatopsChannelConfigResponse | null> = {
      [ChatopsChannelPurpose.LATE_INPUT]: null,
      [ChatopsChannelPurpose.NOTIFICATION_OUTPUT]: null,
    }
    response.data.forEach((config) => {
      nextConfigs[config.purpose] = config
    })
    chatopsConfigByPurpose.value = nextConfigs
    chatopsForms.value[ChatopsChannelPurpose.LATE_INPUT].channelUrl = nextConfigs[ChatopsChannelPurpose.LATE_INPUT]?.channelUrl || ''
    chatopsForms.value[ChatopsChannelPurpose.NOTIFICATION_OUTPUT].channelUrl = nextConfigs[ChatopsChannelPurpose.NOTIFICATION_OUTPUT]?.channelUrl || ''
    chatopsForms.value[ChatopsChannelPurpose.LATE_INPUT].token = ''
    chatopsForms.value[ChatopsChannelPurpose.NOTIFICATION_OUTPUT].token = ''
  } finally {
    chatopsConfigsLoading.value = false
  }
}

async function saveChatopsConfig(purpose: ChatopsChannelPurpose) {
  const form = chatopsForms.value[purpose]
  if (!form.channelUrl.trim()) {
    toast.add({ severity: 'warn', summary: 'Please input channel URL', life: 2500 })
    return
  }
  chatopsSavingPurpose.value = purpose
  try {
    const response = await notificationsApi.upsertChatopsChannel(purpose, {
      token: form.token.trim() || undefined,
      channelUrl: form.channelUrl.trim(),
    })
    chatopsConfigByPurpose.value[purpose] = response.data
    chatopsForms.value[purpose].channelUrl = response.data.channelUrl || form.channelUrl.trim()
    chatopsForms.value[purpose].token = ''
    toast.add({ severity: 'success', summary: 'Success', life: 3000 })
  } catch (error: any) {
    toast.add({ severity: 'error', summary: 'Error', detail: getApiErrorDetail(error, 'Unable to update ChatOps channel'), life: 3500 })
  } finally {
    chatopsSavingPurpose.value = null
  }
}

async function loadJobs() {
  loading.value = true
  try {
    const response = await notificationsApi.getJobs()
    jobs.value = response.data
  } finally {
    loading.value = false
  }
}

async function loadTemplates() {
  templatesLoading.value = true
  try {
    const response = await notificationsApi.getTemplates()
    templates.value = response.data
  } finally {
    templatesLoading.value = false
  }
}

async function loadChannels() {
  channelsLoading.value = true
  try {
    const response = await notificationsApi.getChannels()
    channels.value = response.data
  } finally {
    channelsLoading.value = false
  }
}

async function loadSurveys() {
  surveysLoading.value = true
  try {
    const response = await surveysApi.getAll({ page: 0, size: 500, sort: 'deadline,desc' })
    surveys.value = response.data.content
  } finally {
    surveysLoading.value = false
  }
}

async function triggerReminder() {
  if (!selectedSurveyId.value) {
    toast.add({ severity: 'warn', summary: 'Please select a survey', life: 2500 })
    return
  }
  surveyReminderTriggering.value = true
  try {
    const response = await notificationsApi.triggerSurveyReminder(selectedSurveyId.value)
    toast.add({ severity: 'success', summary: 'Queued', detail: response.data.message, life: 3000 })
  } catch (error: any) {
    toast.add({ severity: 'error', summary: 'Error', detail: getApiErrorDetail(error), life: 3000 })
  } finally {
    surveyReminderTriggering.value = false
  }
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
  } catch (error: any) {
    toast.add({ severity: 'error', summary: 'Error', detail: getApiErrorDetail(error), life: 3000 })
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
  } catch (error: any) {
    toast.add({ severity: 'error', summary: 'Error', detail: getApiErrorDetail(error), life: 3000 })
  }
}

async function deleteTemplate(template: NotificationTemplateResponse) {
  try {
    await notificationsApi.deleteTemplate(template.code)
    toast.add({ severity: 'warn', summary: 'Template deleted', life: 2500 })
    await loadTemplates()
  } catch (error: any) {
    toast.add({ severity: 'error', summary: 'Error', detail: getApiErrorDetail(error), life: 3000 })
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
  } catch (error: any) {
    toast.add({ severity: 'error', summary: 'Error', detail: getApiErrorDetail(error), life: 3000 })
  }
}

async function deleteChannel(channel: NotificationChannelResponse) {
  try {
    await notificationsApi.deleteChannel(channel.id)
    toast.add({ severity: 'warn', summary: 'Channel deleted', life: 2500 })
    await loadChannels()
  } catch (error: any) {
    toast.add({ severity: 'error', summary: 'Error', detail: getApiErrorDetail(error), life: 3000 })
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
      <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:var(--space-4);">
        <h3>ChatOps Configuration</h3>
        <Button label="Reload" icon="pi pi-refresh" text size="small" @click="loadChatopsConfigs" :loading="chatopsConfigsLoading" />
      </div>
      <div style="display:grid;grid-template-columns:1fr 1fr;gap:var(--space-4);">
        <div style="padding:var(--space-4);border:1px solid var(--theme-border);border-radius:var(--radius-md);background:var(--theme-surface-light);">
          <h4 style="margin-bottom:var(--space-3);">Input Channel (Late List)</h4>
          <div class="form-field" style="margin-bottom:var(--space-3);">
            <label>ChatOps Token <span class="optional-hint">(optional)</span></label>
            <InputText v-model="chatopsForms.LATE_INPUT.token" type="password" placeholder="Input admin ChatOps token" fluid />
            <small v-if="chatopsConfigByPurpose.LATE_INPUT?.tokenConfigured">Saved token: {{ chatopsConfigByPurpose.LATE_INPUT?.tokenMasked }}</small>
          </div>
          <div class="form-field" style="margin-bottom:var(--space-3);">
            <label class="required">Channel URL</label>
            <InputText v-model="chatopsForms.LATE_INPUT.channelUrl" placeholder="https://mattermost.example/team/channels/channel-name" fluid />
          </div>
          <div class="form-field" style="margin-bottom:var(--space-3);">
            <label>Resolved Channel ID</label>
            <InputText :modelValue="chatopsConfigByPurpose.LATE_INPUT?.channelId || ''" readonly fluid />
          </div>
          <Button
            label="Save Input Channel"
            icon="pi pi-check"
            size="small"
            @click="saveChatopsConfig(ChatopsChannelPurpose.LATE_INPUT)"
            :loading="chatopsSavingPurpose === ChatopsChannelPurpose.LATE_INPUT"
          />
        </div>

        <div style="padding:var(--space-4);border:1px solid var(--theme-border);border-radius:var(--radius-md);background:var(--theme-surface-light);">
          <h4 style="margin-bottom:var(--space-3);">Output Channel (Notification Post)</h4>
          <div class="form-field" style="margin-bottom:var(--space-3);">
            <label>ChatOps Token <span class="optional-hint">(optional)</span></label>
            <InputText v-model="chatopsForms.NOTIFICATION_OUTPUT.token" type="password" placeholder="Input admin ChatOps token" fluid />
            <small v-if="chatopsConfigByPurpose.NOTIFICATION_OUTPUT?.tokenConfigured">Saved token: {{ chatopsConfigByPurpose.NOTIFICATION_OUTPUT?.tokenMasked }}</small>
          </div>
          <div class="form-field" style="margin-bottom:var(--space-3);">
            <label class="required">Channel URL</label>
            <InputText v-model="chatopsForms.NOTIFICATION_OUTPUT.channelUrl" placeholder="https://mattermost.example/team/channels/channel-name" fluid />
          </div>
          <div class="form-field" style="margin-bottom:var(--space-3);">
            <label>Resolved Channel ID</label>
            <InputText :modelValue="chatopsConfigByPurpose.NOTIFICATION_OUTPUT?.channelId || ''" readonly fluid />
          </div>
          <Button
            label="Save Output Channel"
            icon="pi pi-check"
            size="small"
            @click="saveChatopsConfig(ChatopsChannelPurpose.NOTIFICATION_OUTPUT)"
            :loading="chatopsSavingPurpose === ChatopsChannelPurpose.NOTIFICATION_OUTPUT"
          />
        </div>
      </div>
    </div>

    <div class="content-card" style="margin-bottom:var(--space-6);">
      <h3 style="margin-bottom:var(--space-4);">Scheduled Jobs</h3>
      <DataTable :value="jobs" :loading="loading" stripedRows>
        <template #empty>
          No Scheduled Job found.
        </template>
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
        <template #empty>
          No Notification Template has been created yet. Please create a new Template.
        </template>
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
        <template #empty>
          No Notification Channel has been created yet. Please create a new Channel.
        </template>
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
      <div style="display:flex;gap:var(--space-3);align-items:flex-end;flex-wrap:wrap;">
        <div class="form-field" style="min-width:340px;">
          <label>Survey</label>
          <Select
            v-model="selectedSurveyId"
            :options="surveys"
            optionLabel="title"
            optionValue="id"
            placeholder="Select survey"
            filter
            :loading="surveysLoading"
            fluid
          >
            <template #option="{ option }">
              <div>{{ option.title }} <span style="color:var(--theme-text-weak);font-size:12px;">(Deadline: {{ formatDate(option.deadline) }})</span></div>
            </template>
            <template #value="{ value }">
              <span v-if="value">{{ surveys.find(survey => survey.id === value)?.title || 'Selected survey' }}</span>
              <span v-else style="color:var(--theme-text-weak);">Select survey</span>
            </template>
          </Select>
        </div>
        <Button
          label="Send Survey Reminder"
          icon="pi pi-send"
          @click="triggerReminder"
          :loading="surveyReminderTriggering"
          :disabled="!selectedSurveyId || surveyReminderTriggering"
        />
      </div>
    </div>

    <Dialog v-model:visible="templateDialogVisible" :header="editingTemplate ? 'Edit Template' : 'Add Template'" modal :style="{ width: '640px' }">
      <div style="display:grid;grid-template-columns:1fr 1fr;gap:var(--space-4);">
        <div class="form-field">
          <label class="required">Code</label>
          <InputText v-model="templateForm.code" :disabled="editingTemplate" fluid />
        </div>
        <div class="form-field">
          <label class="required">Name</label>
          <InputText v-model="templateForm.name" fluid />
        </div>
        <div class="form-field" style="grid-column:1 / -1;">
          <label class="required">Subject Template</label>
          <InputText v-model="templateForm.subjectTemplate" fluid />
        </div>
        <div class="form-field" style="grid-column:1 / -1;">
          <label class="required">Body Template</label>
          <Textarea v-model="templateForm.bodyTemplate" rows="5" fluid />
        </div>
        <div class="form-field">
          <label class="required">Status</label>
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
          <label class="required">Type</label>
          <Select v-model="channelForm.type" :options="channelTypeOptions" optionLabel="label" optionValue="value" fluid />
        </div>
        <div class="form-field">
          <label class="required">Status</label>
          <Select v-model="channelForm.enabled" :options="enabledOptions" optionLabel="label" optionValue="value" fluid />
        </div>
        <div class="form-field" style="grid-column:1 / -1;">
          <label>Endpoint <span class="optional-hint">(optional)</span></label>
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


