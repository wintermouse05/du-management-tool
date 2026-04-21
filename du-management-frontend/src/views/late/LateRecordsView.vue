<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { lateRecordsApi } from '@/api/lateRecords'
import type { LateRecordResponse, LateSummaryResponse } from '@/types'
import DataTable from 'primevue/datatable'
import Column from 'primevue/column'
import Button from 'primevue/button'
import Dialog from 'primevue/dialog'
import InputNumber from 'primevue/inputnumber'
import InputText from 'primevue/inputtext'
import DatePicker from 'primevue/datepicker'
import Tabs from 'primevue/tabs'
import TabList from 'primevue/tablist'
import Tab from 'primevue/tab'
import TabPanels from 'primevue/tabpanels'
import TabPanel from 'primevue/tabpanel'
import { useToast } from 'primevue/usetoast'

const toast = useToast()
const activeTab = ref('0')

// All records
const records = ref<LateRecordResponse[]>([])
const total = ref(0); const loading = ref(false); const pg = ref(0); const rows = ref(10)
const dialog = ref(false)
const form = ref({ userId: 0, recordDate: '', minutesLate: 0, reason: '' })
const formDate = ref<Date|null>(null)

async function load() { loading.value = true; try { const r = await lateRecordsApi.getAll({ page: pg.value, size: rows.value }); records.value = r.data.content; total.value = r.data.totalElements } finally { loading.value = false } }
function onPage(e: any) { pg.value = e.page; rows.value = e.rows; load() }

async function create() {
  if (formDate.value) form.value.recordDate = formDate.value.toISOString().split('T')[0]
  try { await lateRecordsApi.create(form.value); toast.add({ severity:'success', summary:'Record created', life:2000 }); dialog.value = false; load()
  } catch (e: any) { toast.add({ severity:'error', summary:'Error', detail: e.response?.data?.message, life:3000 }) }
}

// Summary
const summaries = ref<LateSummaryResponse[]>([])
const sumLoading = ref(false)
const sumYear = ref(new Date().getFullYear()); const sumMonth = ref(new Date().getMonth() + 1)

async function loadSummary() { sumLoading.value = true; try { const r = await lateRecordsApi.getMonthlySummary(sumYear.value, sumMonth.value, { size: 100 }); summaries.value = r.data.content } finally { sumLoading.value = false } }

onMounted(load)
</script>

<template>
  <div class="page-container">
    <div class="page-header">
      <div><h2>Late Records</h2><p class="page-subtitle">Track and manage late arrivals</p></div>
      <Button label="Add Record" icon="pi pi-plus" @click="dialog=true" />
    </div>
    <div class="content-card">
      <Tabs :value="activeTab">
        <TabList><Tab value="0">All Records</Tab><Tab value="1">Monthly Summary</Tab></TabList>
        <TabPanels>
          <TabPanel value="0">
            <DataTable :value="records" :loading="loading" :paginator="true" :rows="rows" :totalRecords="total" :lazy="true" @page="onPage" stripedRows>
              <Column field="fullName" header="Name" /><Column field="recordDate" header="Date" /><Column field="minutesLate" header="Minutes Late" /><Column field="reason" header="Reason" />
            </DataTable>
          </TabPanel>
          <TabPanel value="1">
            <div style="display:flex;gap:var(--space-3);margin-bottom:var(--space-4);align-items:center;">
              <InputNumber v-model="sumYear" :useGrouping="false" style="width:100px" /><InputNumber v-model="sumMonth" :min="1" :max="12" style="width:80px" />
              <Button label="Load" icon="pi pi-search" outlined size="small" @click="loadSummary" />
            </div>
            <DataTable :value="summaries" :loading="sumLoading" stripedRows>
              <Column field="fullName" header="Name" /><Column field="totalLateTimes" header="Times Late" /><Column field="totalMinutesLate" header="Total Minutes" />
            </DataTable>
          </TabPanel>
        </TabPanels>
      </Tabs>
    </div>
    <Dialog v-model:visible="dialog" header="Add Late Record" modal :style="{width:'420px'}">
      <div style="display:flex;flex-direction:column;gap:var(--space-4);">
        <div class="form-field"><label>User ID</label><InputNumber v-model="form.userId" fluid /></div>
        <div class="form-field"><label>Date</label><DatePicker v-model="formDate" fluid /></div>
        <div class="form-field"><label>Minutes Late</label><InputNumber v-model="form.minutesLate" :min="1" fluid /></div>
        <div class="form-field"><label>Reason</label><InputText v-model="form.reason" fluid /></div>
      </div>
      <template #footer><Button label="Cancel" text @click="dialog=false" /><Button label="Create" icon="pi pi-check" @click="create" /></template>
    </Dialog>
  </div>
</template>
