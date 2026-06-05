<script setup lang="ts">
import { ref, onMounted, watch } from 'vue'
import { lateRecordsApi } from '@/api/lateRecords'
import { membersApi } from '@/api/members'
import { LateRecordStatus, type LateRecordRequest, type LateRecordResponse, type LateSummaryResponse } from '@/types'
import { findMemberDisplayName, toMemberDisplayOption, type MemberDisplayOption } from '@/utils/memberDisplay'
import DataTable from 'primevue/datatable'
import Column from 'primevue/column'
import Button from 'primevue/button'
import Dialog from 'primevue/dialog'
import InputNumber from 'primevue/inputnumber'
import InputText from 'primevue/inputtext'
import DatePicker from 'primevue/datepicker'
import Select from 'primevue/select'
import Tag from 'primevue/tag'
import Tabs from 'primevue/tabs'
import TabList from 'primevue/tablist'
import Tab from 'primevue/tab'
import TabPanels from 'primevue/tabpanels'
import TabPanel from 'primevue/tabpanel'
import { useToast } from 'primevue/usetoast'
import { useAuthStore } from '@/stores/auth'
import { toLocalDate } from '@/utils/datetime'
import { getApiErrorDetail } from '@/utils/apiError'

const toast = useToast()
const auth = useAuthStore()
const activeTab = ref('0')

const statusLabelMap: Record<LateRecordStatus, string> = {
  [LateRecordStatus.FIRST_TIME]: 'First Time',
  [LateRecordStatus.UNPAID]: 'Unpaid',
  [LateRecordStatus.PAID]: 'Paid',
  [LateRecordStatus.IGNORE]: 'Ignore',
}

const statusSeverityMap: Record<LateRecordStatus, 'secondary' | 'warn' | 'success' | 'contrast'> = {
  [LateRecordStatus.FIRST_TIME]: 'secondary',
  [LateRecordStatus.UNPAID]: 'warn',
  [LateRecordStatus.PAID]: 'success',
  [LateRecordStatus.IGNORE]: 'contrast',
}

// All records
const records = ref<LateRecordResponse[]>([])
const total = ref(0); const loading = ref(false); const pg = ref(0); const rows = ref(10)
const monthCursor = ref(new Date(new Date().getFullYear(), new Date().getMonth(), 1))
const dialog = ref(false)
const form = ref<LateRecordRequest>({ userId: 0, recordDate: '', minutesLate: 0, reason: '' })
const formDate = ref<Date|null>(null)
const memberOptions = ref<MemberDisplayOption[]>([])
const membersLoading = ref(false)

async function load() {
  loading.value = true
  try {
    const params: any = { page: pg.value + 1, size: rows.value }
    params.fromDate = toLocalDate(new Date(monthCursor.value.getFullYear(), monthCursor.value.getMonth(), 1))
    params.toDate = toLocalDate(new Date(monthCursor.value.getFullYear(), monthCursor.value.getMonth() + 1, 0))
    const r = await lateRecordsApi.getAll(params)
    records.value = r.data.content; total.value = r.data.totalElements
  } finally { loading.value = false }
}
function onPage(e: any) { pg.value = e.page; rows.value = e.rows; load() }

function monthLabel() {
  return monthCursor.value.toLocaleDateString('en-US', { month: 'long', year: 'numeric' })
}

function moveMonth(offset: number) {
  monthCursor.value = new Date(monthCursor.value.getFullYear(), monthCursor.value.getMonth() + offset, 1)
  pg.value = 0
  load()
}

async function loadMemberOptions() {
  membersLoading.value = true
  try {
    const response = await membersApi.search({ page: 0, size: 1000, sort: 'fullName,asc' })
    memberOptions.value = response.data.content.map(toMemberDisplayOption)
  } catch (e: any) {
    memberOptions.value = []
    toast.add({ severity: 'error', summary: 'Error', detail: getApiErrorDetail(e, 'Failed to load members'), life: 3000 })
  } finally {
    membersLoading.value = false
  }
}

function openCreateDialog() {
  form.value = { userId: 0, recordDate: '', minutesLate: 0, reason: '' }
  formDate.value = null
  dialog.value = true
  if (memberOptions.value.length === 0) {
    loadMemberOptions()
  }
}

async function create() {
  if (!form.value.userId) {
    toast.add({ severity: 'warn', summary: 'Please select a user', life: 2500 })
    return
  }
  if (!formDate.value) {
    toast.add({ severity: 'warn', summary: 'Please select a date', life: 2500 })
    return
  }
  const payload: LateRecordRequest = {
    ...form.value,
    recordDate: toLocalDate(formDate.value),
  }
  try { await lateRecordsApi.create(payload); toast.add({ severity:'success', summary:'Record created', life:2000 }); dialog.value = false; load()
  } catch (e: any) { toast.add({ severity:'error', summary:'Error', detail: getApiErrorDetail(e), life:3000 }) }
}

async function deleteRecord(id: number) {
  try { await lateRecordsApi.deleteRecord(id); toast.add({ severity:'warn', summary:'Record deleted', life:2000 }); load()
  } catch (e: any) { toast.add({ severity:'error', summary:'Error', detail: getApiErrorDetail(e), life:3000 }) }
}

async function updateStatus(id: number, status: LateRecordStatus, summary: string) {
  try {
    await lateRecordsApi.updateStatus(id, status)
    toast.add({ severity: 'success', summary, life: 2000 })
    load()
  } catch (e: any) {
    toast.add({ severity: 'error', summary: 'Error', detail: getApiErrorDetail(e, 'Unable to update status'), life: 3000 })
  }
}

async function checkNow() {
  try {
    const r = await lateRecordsApi.checkNow()
    toast.add({ severity:'success', summary:'Check completed', detail: r.data.message, life:3000 })
    load()
  } catch (e: any) { toast.add({ severity:'error', summary:'Check failed', detail: getApiErrorDetail(e), life:3000 }) }
}

// Summary
const summaries = ref<LateSummaryResponse[]>([])
const sumLoading = ref(false)
const sumYear = ref(new Date().getFullYear()); const sumMonth = ref(new Date().getMonth() + 1)
const currentYear = new Date().getFullYear()
const minSummaryYear = 2010
const yearOptions = Array.from(
  { length: currentYear - minSummaryYear + 1 },
  (_, index) => currentYear - index,
)
const monthOptions = [
  { label: 'January', value: 1 },
  { label: 'February', value: 2 },
  { label: 'March', value: 3 },
  { label: 'April', value: 4 },
  { label: 'May', value: 5 },
  { label: 'June', value: 6 },
  { label: 'July', value: 7 },
  { label: 'August', value: 8 },
  { label: 'September', value: 9 },
  { label: 'October', value: 10 },
  { label: 'November', value: 11 },
  { label: 'December', value: 12 },
]
let latestSummaryRequestSeq = 0

async function loadSummary() {
  const requestSeq = ++latestSummaryRequestSeq
  sumLoading.value = true
  try {
    const r = await lateRecordsApi.getMonthlySummary(sumYear.value, sumMonth.value, { size: 100 })
    if (requestSeq !== latestSummaryRequestSeq) {
      return
    }
    summaries.value = r.data.content
  } finally {
    if (requestSeq === latestSummaryRequestSeq) {
      sumLoading.value = false
    }
  }
}

async function exportCsv() {
  try {
    const params = activeTab.value === '1' ? { year: sumYear.value, month: sumMonth.value } : undefined
    const res = await lateRecordsApi.exportCsv(params)
    const blob = new Blob([res.data], { type: 'text/csv;charset=utf-8' })
    const link = document.createElement('a')
    link.href = URL.createObjectURL(blob)
    link.download = 'late-records.csv'
    link.click()
    URL.revokeObjectURL(link.href)
  } catch (e: any) {
    toast.add({ severity: 'error', summary: 'Export failed', detail: getApiErrorDetail(e, 'Unable to export records'), life: 3000 })
  }
}

function statusLabel(status: LateRecordStatus) {
  return statusLabelMap[status] || status
}

function statusSeverity(status: LateRecordStatus) {
  return statusSeverityMap[status] || 'secondary'
}

function formatMoney(amount: number) {
  return `${(amount || 0).toLocaleString('vi-VN')} VND`
}

onMounted(() => {
  load()
  loadMemberOptions()
})

watch([activeTab, sumYear, sumMonth], async ([tab]) => {
  if (tab === '1') {
    await loadSummary()
  }
})
</script>

<template>
  <div class="page-container">
    <div class="page-header">
      <div><h2>Late Records</h2><p class="page-subtitle">Track and manage late arrivals</p></div>
      <div style="display:flex;gap:8px;">
        <Button label="Check Now" icon="pi pi-sync" severity="secondary" outlined @click="checkNow" />
        <Button label="Export CSV" icon="pi pi-download" severity="secondary" outlined @click="exportCsv" />
        <Button label="Add Record" icon="pi pi-plus" @click="openCreateDialog" />
      </div>
    </div>
    <div class="content-card">
      <Tabs :value="activeTab">
        <TabList><Tab value="0">All Records</Tab><Tab value="1">Monthly Summary</Tab></TabList>
        <TabPanels>
          <TabPanel value="0">
            <div style="display:flex;gap:var(--space-3);margin-bottom:var(--space-4);align-items:center;">
              <Button label="Previous" icon="pi pi-chevron-left" size="small" outlined @click="moveMonth(-1)" />
              <Tag :value="monthLabel()" severity="secondary" />
              <Button label="Next" icon="pi pi-chevron-right" iconPos="right" size="small" outlined @click="moveMonth(1)" />
            </div>
            <DataTable :value="records" :loading="loading" :paginator="true" :rows="rows" :totalRecords="total" :lazy="true" @page="onPage" stripedRows>
              <template #empty>
                No Late Record found for current filters.
              </template>
              <Column field="fullName" header="Name" />
              <Column field="recordDate" header="Date" />
              <Column field="minutesLate" header="Minutes Late" />
              <Column field="status" header="Status">
                <template #body="{ data }">
                  <Tag :value="statusLabel(data.status)" :severity="statusSeverity(data.status)" />
                </template>
              </Column>
              <Column field="fineAmount" header="Fine">
                <template #body="{ data }">
                  {{ formatMoney(data.fineAmount) }}
                </template>
              </Column>
              <Column field="reason" header="Reason" />
              <Column header="Actions" style="width:280px">
                <template #body="{ data }">
                  <div style="display:flex;gap:6px;flex-wrap:wrap;">
                    <Button
                      v-if="auth.isAdmin && data.status !== LateRecordStatus.IGNORE"
                      label="Ignore"
                      size="small"
                      severity="secondary"
                      outlined
                      @click="updateStatus(data.id, LateRecordStatus.IGNORE, 'Updated to Ignore')"
                    />
                    <Button
                      v-if="auth.isAdmin && data.payable && data.status !== LateRecordStatus.PAID"
                      label="Paid"
                      size="small"
                      severity="success"
                      @click="updateStatus(data.id, LateRecordStatus.PAID, 'Marked paid')"
                    />
                    <Button
                      v-if="auth.isAdmin && data.payable && data.status !== LateRecordStatus.UNPAID"
                      label="Unpaid"
                      size="small"
                      severity="warn"
                      outlined
                      @click="updateStatus(data.id, LateRecordStatus.UNPAID, 'Marked unpaid')"
                    />
                    <Button icon="pi pi-trash" text rounded severity="danger" @click="deleteRecord(data.id)" />
                  </div>
                </template>
              </Column>
            </DataTable>
          </TabPanel>
          <TabPanel value="1">
            <div style="display:flex;gap:var(--space-3);margin-bottom:var(--space-4);align-items:center;">
              <Select
                v-model="sumYear"
                :options="yearOptions"
                placeholder="Select year"
                style="width:130px"
              />
              <Select
                v-model="sumMonth"
                :options="monthOptions"
                optionLabel="label"
                optionValue="value"
                placeholder="Select month"
                style="width:170px"
              />
            </div>
            <DataTable :value="summaries" :loading="sumLoading" stripedRows>
              <template #empty>
                No Monthly Summary data found for selected period.
              </template>
              <Column field="fullName" header="Name" /><Column field="totalLateTimes" header="Times Late" /><Column field="totalMinutesLate" header="Total Minutes" />
            </DataTable>
          </TabPanel>
        </TabPanels>
      </Tabs>
    </div>
    <Dialog v-model:visible="dialog" header="Add Late Record" modal :style="{width:'420px'}">
      <div style="display:flex;flex-direction:column;gap:var(--space-4);">
        <div class="form-field">
          <label>User</label>
          <Select
            v-model="form.userId"
            :options="memberOptions"
            optionLabel="displayName"
            optionValue="id"
            optionDisabled="disabled"
            placeholder="Select user"
            filter
            :loading="membersLoading"
            fluid
          >
            <template #option="{ option }">
              <div style="display:flex;flex-direction:column;gap:2px;">
                <span>{{ option.displayName }}</span>
                <span class="caption">{{ option.email }}</span>
              </div>
            </template>
            <template #value="{ value, placeholder }">
              <span v-if="value">{{ findMemberDisplayName(memberOptions, value, 'Selected user') }}</span>
              <span v-else>{{ placeholder }}</span>
            </template>
          </Select>
        </div>
        <div class="form-field"><label>Date</label><DatePicker v-model="formDate" fluid /></div>
        <div class="form-field"><label>Minutes Late</label><InputNumber v-model="form.minutesLate" :min="1" fluid /></div>
        <div class="form-field"><label>Reason</label><InputText v-model="form.reason" fluid /></div>
      </div>
      <template #footer><Button label="Cancel" text @click="dialog=false" /><Button label="Create" icon="pi pi-check" @click="create" /></template>
    </Dialog>
  </div>
</template>
