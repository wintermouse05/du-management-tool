<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { systemLogsApi } from '@/api/systemLogs'
import {
  SystemLogCategory,
  SystemLogSeverity,
  SystemLogStatus,
  type SystemLogDetailResponse,
  type SystemLogListResponse,
  type SystemLogSearchParams,
} from '@/types'
import { formatLocalDateTime, toLocalDateTime } from '@/utils/datetime'
import { getApiErrorDetail } from '@/utils/apiError'
import Button from 'primevue/button'
import Column from 'primevue/column'
import DataTable from 'primevue/datatable'
import DatePicker from 'primevue/datepicker'
import Dialog from 'primevue/dialog'
import InputNumber from 'primevue/inputnumber'
import InputText from 'primevue/inputtext'
import Select from 'primevue/select'
import Tab from 'primevue/tab'
import TabList from 'primevue/tablist'
import TabPanel from 'primevue/tabpanel'
import TabPanels from 'primevue/tabpanels'
import Tabs from 'primevue/tabs'
import Tag from 'primevue/tag'
import { useToast } from 'primevue/usetoast'

const toast = useToast()

type TabKey = 'all' | 'database' | 'api' | 'tasks'

const activeTab = ref<TabKey>('all')
const logs = ref<SystemLogListResponse[]>([])
const total = ref(0)
const loading = ref(false)
const detailLoading = ref(false)
const settingsLoading = ref(false)
const settingsSaving = ref(false)
const detailDialog = ref(false)
const selectedLog = ref<SystemLogDetailResponse | null>(null)
const page = ref(0)
const rows = ref(25)
const sortField = ref('occurredAt')
const sortOrder = ref(-1)

const q = ref('')
const source = ref('')
const actor = ref('')
const correlationId = ref('')
const severity = ref<SystemLogSeverity | null>(null)
const status = ref<SystemLogStatus | null>(null)
const fromDate = ref<Date | null>(null)
const toDate = ref<Date | null>(null)
const retentionDays = ref<number | null>(null)
const retentionMinDays = ref(1)
const retentionMaxDays = ref(3650)

const severityOptions = Object.values(SystemLogSeverity)
const statusOptions = Object.values(SystemLogStatus)

const tabCategories: Record<TabKey, SystemLogCategory[]> = {
  all: [],
  database: [SystemLogCategory.DATABASE, SystemLogCategory.BACKEND_LOG],
  api: [SystemLogCategory.HTTP_REQUEST, SystemLogCategory.EXTERNAL_API, SystemLogCategory.MESSAGE],
  tasks: [SystemLogCategory.TASK],
}

const currentCategories = computed(() => tabCategories[activeTab.value])

onMounted(() => {
  load()
  loadSettings()
})

watch(activeTab, () => {
  page.value = 0
  load()
})

function buildParams(): SystemLogSearchParams {
  const params: SystemLogSearchParams = {
    page: page.value,
    size: rows.value,
    sort: `${sortField.value},${sortOrder.value === 1 ? 'asc' : 'desc'}`,
  }

  if (q.value.trim()) params.q = q.value.trim()
  if (source.value.trim()) params.source = source.value.trim()
  if (actor.value.trim()) params.actor = actor.value.trim()
  if (correlationId.value.trim()) params.correlationId = correlationId.value.trim()
  if (severity.value) params.severity = severity.value
  if (status.value) params.status = status.value
  if (fromDate.value) params.from = toLocalDateTime(fromDate.value)
  if (toDate.value) params.to = toLocalDateTime(toDate.value)
  if (currentCategories.value.length) params.category = currentCategories.value.join(',')

  return params
}

async function load() {
  loading.value = true
  try {
    const response = await systemLogsApi.searchLogs(buildParams())
    logs.value = response.data.content
    total.value = response.data.totalElements
  } catch (e: any) {
    toast.add({ severity: 'error', summary: 'Unable to load logs', detail: getApiErrorDetail(e), life: 3500 })
  } finally {
    loading.value = false
  }
}

async function loadSettings() {
  settingsLoading.value = true
  try {
    const response = await systemLogsApi.getSettings()
    retentionDays.value = response.data.retentionDays
    retentionMinDays.value = response.data.minRetentionDays
    retentionMaxDays.value = response.data.maxRetentionDays
  } catch (e: any) {
    toast.add({ severity: 'error', summary: 'Unable to load log settings', detail: getApiErrorDetail(e), life: 3500 })
  } finally {
    settingsLoading.value = false
  }
}

async function saveSettings() {
  if (retentionDays.value === null) {
    toast.add({ severity: 'warn', summary: 'Retention days is required', life: 2500 })
    return
  }

  settingsSaving.value = true
  try {
    const response = await systemLogsApi.updateSettings({ retentionDays: retentionDays.value })
    retentionDays.value = response.data.retentionDays
    retentionMinDays.value = response.data.minRetentionDays
    retentionMaxDays.value = response.data.maxRetentionDays
    toast.add({ severity: 'success', summary: 'Log settings saved', life: 2200 })
  } catch (e: any) {
    toast.add({ severity: 'error', summary: 'Unable to save log settings', detail: getApiErrorDetail(e), life: 3500 })
  } finally {
    settingsSaving.value = false
  }
}

function applyFilters() {
  page.value = 0
  load()
}

function clearFilters() {
  q.value = ''
  source.value = ''
  actor.value = ''
  correlationId.value = ''
  severity.value = null
  status.value = null
  fromDate.value = null
  toDate.value = null
  applyFilters()
}

function onPage(event: any) {
  page.value = event.page
  rows.value = event.rows
  load()
}

function onSort(event: any) {
  sortField.value = event.sortField || 'occurredAt'
  sortOrder.value = event.sortOrder || -1
  page.value = 0
  load()
}

async function openDetail(row: SystemLogListResponse) {
  detailDialog.value = true
  detailLoading.value = true
  selectedLog.value = null
  try {
    const response = await systemLogsApi.getLogDetail(row.id)
    selectedLog.value = response.data
  } catch (e: any) {
    toast.add({ severity: 'error', summary: 'Unable to load log detail', detail: getApiErrorDetail(e), life: 3500 })
    detailDialog.value = false
  } finally {
    detailLoading.value = false
  }
}

async function copyValue(value?: string | number | null) {
  if (value === null || value === undefined || String(value).trim() === '') return
  await navigator.clipboard?.writeText(String(value))
  toast.add({ severity: 'success', summary: 'Copied', life: 1500 })
}

function formatTarget(row: SystemLogListResponse) {
  if (!row.targetType && !row.targetId) return ''
  if (!row.targetId) return row.targetType || ''
  if (!row.targetType) return row.targetId
  return `${row.targetType} #${row.targetId}`
}

function formatDuration(value: number | null) {
  if (value === null || value === undefined) return ''
  return `${value.toLocaleString()} ms`
}

function severityValue(value: SystemLogSeverity) {
  return value
}

function severityTone(value: SystemLogSeverity) {
  if (value === SystemLogSeverity.ERROR) return 'danger'
  if (value === SystemLogSeverity.WARN) return 'warn'
  return 'info'
}

function statusTone(value: SystemLogStatus) {
  if (value === SystemLogStatus.FAILED) return 'danger'
  if (value === SystemLogStatus.SKIPPED) return 'secondary'
  return 'success'
}

function prettyDetails(value?: string | null) {
  if (!value) return ''
  try {
    return JSON.stringify(JSON.parse(value), null, 2)
  } catch {
    return value
  }
}
</script>

<template>
  <div class="page-container">
    <div class="page-header">
      <div>
        <h2>System Logs</h2>
        <p class="page-subtitle">Review operational activity across backend, integrations, and schedules</p>
      </div>
      <div class="page-actions">
        <div class="retention-control">
          <label for="system-log-retention-days">Retention days</label>
          <InputNumber
            inputId="system-log-retention-days"
            v-model="retentionDays"
            :min="retentionMinDays"
            :max="retentionMaxDays"
            :maxFractionDigits="0"
            :useGrouping="false"
            showButtons
            fluid
            :disabled="settingsLoading || settingsSaving"
            @keyup.enter="saveSettings"
          />
          <Button icon="pi pi-save" label="Save" :loading="settingsSaving" :disabled="settingsLoading" @click="saveSettings" />
        </div>
        <Button icon="pi pi-refresh" label="Refresh" severity="secondary" outlined :loading="loading" @click="load" />
      </div>
    </div>

    <div class="content-card logs-surface">
      <div class="filter-grid">
        <span class="form-field search-field">
          <label>Search</label>
          <InputText v-model="q" placeholder="Message, action, source, target" fluid @keyup.enter="applyFilters" />
        </span>
        <span class="form-field">
          <label>Severity</label>
          <Select v-model="severity" :options="severityOptions" showClear placeholder="Any" fluid />
        </span>
        <span class="form-field">
          <label>Status</label>
          <Select v-model="status" :options="statusOptions" showClear placeholder="Any" fluid />
        </span>
        <span class="form-field">
          <label>Source</label>
          <InputText v-model="source" placeholder="Controller, service, host" fluid @keyup.enter="applyFilters" />
        </span>
        <span class="form-field">
          <label>Actor</label>
          <InputText v-model="actor" placeholder="Username" fluid @keyup.enter="applyFilters" />
        </span>
        <span class="form-field">
          <label>Correlation ID</label>
          <InputText v-model="correlationId" placeholder="Trace request" fluid @keyup.enter="applyFilters" />
        </span>
        <span class="form-field">
          <label>From</label>
          <DatePicker v-model="fromDate" showTime hourFormat="24" fluid />
        </span>
        <span class="form-field">
          <label>To</label>
          <DatePicker v-model="toDate" showTime hourFormat="24" fluid />
        </span>
        <div class="filter-actions">
          <Button label="Search" icon="pi pi-search" @click="applyFilters" />
          <Button label="Clear" icon="pi pi-filter-slash" severity="secondary" text @click="clearFilters" />
        </div>
      </div>

      <Tabs v-model:value="activeTab">
        <TabList>
          <Tab value="all">All</Tab>
          <Tab value="database">Database & Backend</Tab>
          <Tab value="api">API & Messages</Tab>
          <Tab value="tasks">Tasks</Tab>
        </TabList>
        <TabPanels>
          <TabPanel value="all">
            <DataTable
              :value="logs"
              :loading="loading"
              :paginator="true"
              :rows="rows"
              :totalRecords="total"
              :lazy="true"
              dataKey="id"
              stripedRows
              removableSort
              scrollable
              scrollHeight="58vh"
              @page="onPage"
              @sort="onSort"
              @row-dblclick="openDetail($event.data)"
            >
              <template #empty>No System Log found for current filters.</template>
              <Column field="occurredAt" header="Time" sortable style="min-width:180px">
                <template #body="{ data }">{{ formatLocalDateTime(data.occurredAt) }}</template>
              </Column>
              <Column field="severity" header="Severity" sortable style="width:120px">
                <template #body="{ data }"><Tag :value="severityValue(data.severity)" :severity="severityTone(data.severity)" /></template>
              </Column>
              <Column field="category" header="Category" sortable style="min-width:150px" />
              <Column field="status" header="Status" sortable style="width:120px">
                <template #body="{ data }"><Tag :value="data.status" :severity="statusTone(data.status)" /></template>
              </Column>
              <Column field="action" header="Action" sortable style="min-width:150px" />
              <Column field="source" header="Source" sortable style="min-width:180px" />
              <Column field="actorUsername" header="Actor" sortable style="min-width:130px" />
              <Column header="Target" style="min-width:150px">
                <template #body="{ data }">{{ formatTarget(data) }}</template>
              </Column>
              <Column field="durationMs" header="Duration" sortable style="width:120px">
                <template #body="{ data }">{{ formatDuration(data.durationMs) }}</template>
              </Column>
              <Column field="message" header="Message" style="min-width:280px">
                <template #body="{ data }">
                  <span class="message-cell">{{ data.message }}</span>
                </template>
              </Column>
              <Column header="" style="width:64px">
                <template #body="{ data }">
                  <Button icon="pi pi-search" text rounded severity="secondary" aria-label="View details" @click="openDetail(data)" />
                </template>
              </Column>
            </DataTable>
          </TabPanel>
          <TabPanel value="database">
            <DataTable
              :value="logs"
              :loading="loading"
              :paginator="true"
              :rows="rows"
              :totalRecords="total"
              :lazy="true"
              dataKey="id"
              stripedRows
              removableSort
              scrollable
              scrollHeight="58vh"
              @page="onPage"
              @sort="onSort"
              @row-dblclick="openDetail($event.data)"
            >
              <template #empty>No Database or Backend Log found for current filters.</template>
              <Column field="occurredAt" header="Time" sortable style="min-width:180px"><template #body="{ data }">{{ formatLocalDateTime(data.occurredAt) }}</template></Column>
              <Column field="severity" header="Severity" sortable style="width:120px"><template #body="{ data }"><Tag :value="data.severity" :severity="severityTone(data.severity)" /></template></Column>
              <Column field="category" header="Category" sortable style="min-width:150px" />
              <Column field="status" header="Status" sortable style="width:120px"><template #body="{ data }"><Tag :value="data.status" :severity="statusTone(data.status)" /></template></Column>
              <Column field="action" header="Action" sortable style="min-width:150px" />
              <Column field="source" header="Source" sortable style="min-width:180px" />
              <Column header="Target" style="min-width:150px"><template #body="{ data }">{{ formatTarget(data) }}</template></Column>
              <Column field="durationMs" header="Duration" sortable style="width:120px"><template #body="{ data }">{{ formatDuration(data.durationMs) }}</template></Column>
              <Column field="message" header="Message" style="min-width:320px"><template #body="{ data }"><span class="message-cell">{{ data.message }}</span></template></Column>
              <Column header="" style="width:64px"><template #body="{ data }"><Button icon="pi pi-search" text rounded severity="secondary" aria-label="View details" @click="openDetail(data)" /></template></Column>
            </DataTable>
          </TabPanel>
          <TabPanel value="api">
            <DataTable
              :value="logs"
              :loading="loading"
              :paginator="true"
              :rows="rows"
              :totalRecords="total"
              :lazy="true"
              dataKey="id"
              stripedRows
              removableSort
              scrollable
              scrollHeight="58vh"
              @page="onPage"
              @sort="onSort"
              @row-dblclick="openDetail($event.data)"
            >
              <template #empty>No API or Message Log found for current filters.</template>
              <Column field="occurredAt" header="Time" sortable style="min-width:180px"><template #body="{ data }">{{ formatLocalDateTime(data.occurredAt) }}</template></Column>
              <Column field="severity" header="Severity" sortable style="width:120px"><template #body="{ data }"><Tag :value="data.severity" :severity="severityTone(data.severity)" /></template></Column>
              <Column field="category" header="Category" sortable style="min-width:150px" />
              <Column field="status" header="Status" sortable style="width:120px"><template #body="{ data }"><Tag :value="data.status" :severity="statusTone(data.status)" /></template></Column>
              <Column field="action" header="Action" sortable style="min-width:150px" />
              <Column field="source" header="Source" sortable style="min-width:180px" />
              <Column field="actorUsername" header="Actor" sortable style="min-width:130px" />
              <Column field="durationMs" header="Duration" sortable style="width:120px"><template #body="{ data }">{{ formatDuration(data.durationMs) }}</template></Column>
              <Column field="message" header="Message" style="min-width:320px"><template #body="{ data }"><span class="message-cell">{{ data.message }}</span></template></Column>
              <Column header="" style="width:64px"><template #body="{ data }"><Button icon="pi pi-search" text rounded severity="secondary" aria-label="View details" @click="openDetail(data)" /></template></Column>
            </DataTable>
          </TabPanel>
          <TabPanel value="tasks">
            <DataTable
              :value="logs"
              :loading="loading"
              :paginator="true"
              :rows="rows"
              :totalRecords="total"
              :lazy="true"
              dataKey="id"
              stripedRows
              removableSort
              scrollable
              scrollHeight="58vh"
              @page="onPage"
              @sort="onSort"
              @row-dblclick="openDetail($event.data)"
            >
              <template #empty>No Task Log found for current filters.</template>
              <Column field="occurredAt" header="Time" sortable style="min-width:180px"><template #body="{ data }">{{ formatLocalDateTime(data.occurredAt) }}</template></Column>
              <Column field="severity" header="Severity" sortable style="width:120px"><template #body="{ data }"><Tag :value="data.severity" :severity="severityTone(data.severity)" /></template></Column>
              <Column field="status" header="Status" sortable style="width:120px"><template #body="{ data }"><Tag :value="data.status" :severity="statusTone(data.status)" /></template></Column>
              <Column field="action" header="Task" sortable style="min-width:220px" />
              <Column field="source" header="Source" sortable style="min-width:180px" />
              <Column header="Target" style="min-width:150px"><template #body="{ data }">{{ formatTarget(data) }}</template></Column>
              <Column field="durationMs" header="Duration" sortable style="width:120px"><template #body="{ data }">{{ formatDuration(data.durationMs) }}</template></Column>
              <Column field="message" header="Message" style="min-width:320px"><template #body="{ data }"><span class="message-cell">{{ data.message }}</span></template></Column>
              <Column header="" style="width:64px"><template #body="{ data }"><Button icon="pi pi-search" text rounded severity="secondary" aria-label="View details" @click="openDetail(data)" /></template></Column>
            </DataTable>
          </TabPanel>
        </TabPanels>
      </Tabs>
    </div>

    <Dialog v-model:visible="detailDialog" modal header="Log Detail" :style="{ width: 'min(980px, 94vw)' }">
      <div v-if="detailLoading" class="detail-loading">Loading...</div>
      <div v-else-if="selectedLog" class="detail-grid">
        <div class="detail-row">
          <span>Time</span>
          <strong>{{ formatLocalDateTime(selectedLog.occurredAt) }}</strong>
        </div>
        <div class="detail-row">
          <span>Severity</span>
          <Tag :value="selectedLog.severity" :severity="severityTone(selectedLog.severity)" />
        </div>
        <div class="detail-row">
          <span>Status</span>
          <Tag :value="selectedLog.status" :severity="statusTone(selectedLog.status)" />
        </div>
        <div class="detail-row">
          <span>Category</span>
          <strong>{{ selectedLog.category }}</strong>
        </div>
        <div class="detail-row">
          <span>Action</span>
          <strong>{{ selectedLog.action }}</strong>
        </div>
        <div class="detail-row">
          <span>Source</span>
          <strong>{{ selectedLog.source }}</strong>
        </div>
        <div class="detail-row">
          <span>Actor</span>
          <strong>{{ selectedLog.actorUsername }}</strong>
        </div>
        <div class="detail-row copy-row">
          <span>Correlation</span>
          <button type="button" @click="copyValue(selectedLog.correlationId)">{{ selectedLog.correlationId || '-' }}</button>
        </div>
        <div class="detail-row">
          <span>Target</span>
          <strong>{{ formatTarget(selectedLog) || '-' }}</strong>
        </div>
        <div class="detail-row">
          <span>Duration</span>
          <strong>{{ formatDuration(selectedLog.durationMs) || '-' }}</strong>
        </div>
        <div class="detail-block">
          <span>Message</span>
          <p>{{ selectedLog.message || '-' }}</p>
        </div>
        <div class="detail-block">
          <span>Details</span>
          <pre>{{ prettyDetails(selectedLog.detailsJson) || '-' }}</pre>
        </div>
        <div v-if="selectedLog.exceptionClass || selectedLog.stackTrace" class="detail-block">
          <span>Exception</span>
          <strong>{{ selectedLog.exceptionClass }}</strong>
          <pre>{{ selectedLog.stackTrace || '-' }}</pre>
        </div>
      </div>
    </Dialog>
  </div>
</template>

<style scoped>
.logs-surface {
  display: flex;
  flex-direction: column;
  gap: var(--space-4);
}

.page-actions {
  display: flex;
  align-items: end;
  justify-content: flex-end;
  gap: var(--space-3);
  flex-wrap: wrap;
}

.retention-control {
  display: grid;
  grid-template-columns: minmax(132px, 168px) auto;
  gap: var(--space-2);
  align-items: end;
}

.retention-control label {
  grid-column: 1 / -1;
  color: var(--theme-text-muted);
  font-size: 12px;
  font-weight: 600;
  text-transform: uppercase;
}

.filter-grid {
  display: grid;
  grid-template-columns: minmax(220px, 2fr) repeat(3, minmax(150px, 1fr));
  gap: var(--space-3);
  align-items: end;
}

.filter-actions {
  display: flex;
  align-items: center;
  gap: var(--space-2);
}

.message-cell {
  display: inline-block;
  max-width: 520px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  vertical-align: middle;
}

.detail-loading {
  min-height: 180px;
  display: grid;
  place-items: center;
  color: var(--theme-text-muted);
}

.detail-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--space-3);
}

.detail-row,
.detail-block {
  border: 1px solid var(--theme-border);
  border-radius: 8px;
  padding: var(--space-3);
  min-width: 0;
  background: var(--theme-surface);
}

.detail-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-3);
}

.detail-row span,
.detail-block span {
  color: var(--theme-text-muted);
  font-size: 12px;
  font-weight: 600;
  text-transform: uppercase;
}

.detail-row strong {
  min-width: 0;
  overflow-wrap: anywhere;
  text-align: right;
}

.detail-block {
  grid-column: 1 / -1;
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
}

.detail-block p {
  margin: 0;
  overflow-wrap: anywhere;
}

.detail-block pre {
  margin: 0;
  max-height: 340px;
  overflow: auto;
  white-space: pre-wrap;
  word-break: break-word;
  font-size: 12px;
  line-height: 1.5;
}

.copy-row button {
  border: 0;
  background: transparent;
  color: var(--theme-primary);
  cursor: pointer;
  min-width: 0;
  overflow-wrap: anywhere;
  text-align: right;
}

@media (max-width: 960px) {
  .filter-grid {
    grid-template-columns: 1fr 1fr;
  }

  .search-field,
  .filter-actions {
    grid-column: 1 / -1;
  }

  .detail-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 640px) {
  .page-actions,
  .retention-control {
    width: 100%;
  }

  .page-actions {
    justify-content: flex-start;
  }

  .retention-control {
    grid-template-columns: minmax(0, 1fr) auto;
  }

  .filter-grid {
    grid-template-columns: 1fr;
  }

  .filter-actions {
    flex-wrap: wrap;
  }
}
</style>
