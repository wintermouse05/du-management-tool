<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useAuthStore } from '@/stores/auth'
import { gamificationApi } from '@/api/gamification'
import type { PointRuleResponse, PointHistoryResponse } from '@/types'
import DataTable from 'primevue/datatable'
import Column from 'primevue/column'
import Button from 'primevue/button'
import Dialog from 'primevue/dialog'
import InputText from 'primevue/inputtext'
import InputNumber from 'primevue/inputnumber'
import Textarea from 'primevue/textarea'
import Select from 'primevue/select'
import Tabs from 'primevue/tabs'
import TabList from 'primevue/tablist'
import Tab from 'primevue/tab'
import TabPanels from 'primevue/tabpanels'
import TabPanel from 'primevue/tabpanel'
import { useToast } from 'primevue/usetoast'

const auth = useAuthStore()
const toast = useToast()
const activeTab = ref('0')

// Rules
const rules = ref<PointRuleResponse[]>([])
const rulesLoading = ref(false)
const ruleDialog = ref(false); const editingRule = ref(false); const editRuleId = ref<number|null>(null)
const ruleForm = ref({ actionCode: '', pointValue: 0 })

async function loadRules() { rulesLoading.value = true; try { const r = await gamificationApi.getRules({ size: 100 }); rules.value = r.data.content } finally { rulesLoading.value = false } }
function openCreateRule() { editingRule.value = false; editRuleId.value = null; ruleForm.value = { actionCode: '', pointValue: 0 }; ruleDialog.value = true }
function openEditRule(r: PointRuleResponse) { editingRule.value = true; editRuleId.value = r.id; ruleForm.value = { actionCode: r.actionCode, pointValue: r.pointValue }; ruleDialog.value = true }
async function saveRule() {
  try {
    if (editingRule.value && editRuleId.value) await gamificationApi.updateRule(editRuleId.value, ruleForm.value)
    else await gamificationApi.createRule(ruleForm.value)
    toast.add({ severity: 'success', summary: 'Saved', life: 2000 }); ruleDialog.value = false; loadRules()
  } catch (e: any) { toast.add({ severity: 'error', summary: 'Error', detail: e.response?.data?.message, life: 3000 }) }
}

// Manual Adjust
const adjustForm = ref({ userId: 0, ruleId: null as number|null, pointsChanged: 0, reason: '' })

function onRuleSelect() {
  if (adjustForm.value.ruleId) {
    const selectedRule = rules.value.find(r => r.id === adjustForm.value.ruleId)
    if (selectedRule) {
      adjustForm.value.pointsChanged = selectedRule.pointValue
      adjustForm.value.reason = `Applied rule: ${selectedRule.actionCode}`
    }
  }
}

async function adjustPoints() {
  try { await gamificationApi.adjustManual(adjustForm.value); toast.add({ severity: 'success', summary: 'Points adjusted', life: 2000 }); adjustForm.value = { userId: 0, ruleId: null, pointsChanged: 0, reason: '' }
  } catch (e: any) { toast.add({ severity: 'error', summary: 'Error', detail: e.response?.data?.message, life: 3000 }) }
}

// History
const historyUserId = ref<number>(0)
const history = ref<PointHistoryResponse[]>([])
const histLoading = ref(false)
async function loadHistory() {
  if (!historyUserId.value) return
  histLoading.value = true; try { const r = await gamificationApi.getUserHistory(historyUserId.value, { size: 50 }); history.value = r.data.content } finally { histLoading.value = false }
}

function fmtDate(d: string) { return d ? new Date(d).toLocaleDateString('en-US', { month:'short', day:'numeric', hour:'2-digit', minute:'2-digit' }) : '' }

onMounted(loadRules)
</script>

<template>
  <div class="page-container">
    <div class="page-header"><div><h2>Gamification</h2><p class="page-subtitle">Point rules, manual adjustments, and history</p></div></div>
    <div class="content-card">
      <Tabs :value="activeTab">
        <TabList><Tab value="0">Point Rules</Tab><Tab value="1">Manual Adjust</Tab><Tab value="2">Point History</Tab></TabList>
        <TabPanels>
          <TabPanel value="0">
            <div style="display:flex;justify-content:flex-end;margin-bottom:var(--space-4)"><Button v-if="auth.isAdmin" label="Add Rule" icon="pi pi-plus" size="small" @click="openCreateRule" /></div>
            <DataTable :value="rules" :loading="rulesLoading" stripedRows>
              <Column field="actionCode" header="Action Code" /><Column field="pointValue" header="Points">
                <template #body="{data}"><span :style="{color: data.pointValue >= 0 ? 'var(--theme-success)' : 'var(--theme-danger)', fontWeight: 700}">{{ data.pointValue >= 0 ? '+' : '' }}{{ data.pointValue }}</span></template>
              </Column>
              <Column v-if="auth.isAdmin" header="" style="width:80px"><template #body="{data}"><Button icon="pi pi-pencil" text rounded severity="info" @click="openEditRule(data)" /></template></Column>
            </DataTable>
          </TabPanel>
          <TabPanel value="1">
            <div style="max-width:480px;display:flex;flex-direction:column;gap:var(--space-4);padding:var(--space-4) 0;">
              <div class="form-field"><label>User ID</label><InputNumber v-model="adjustForm.userId" fluid /></div>
              <div class="form-field">
                <label>Point Rule (Optional)</label>
                <Select v-model="adjustForm.ruleId" :options="rules" optionLabel="actionCode" optionValue="id" placeholder="Select a predefined rule" @change="onRuleSelect" fluid showClear />
              </div>
              <div class="form-field"><label>Points Change</label><InputNumber v-model="adjustForm.pointsChanged" :showButtons="true" fluid /></div>
              <div class="form-field"><label>Reason</label><Textarea v-model="adjustForm.reason" rows="2" fluid /></div>
              <Button label="Adjust Points" icon="pi pi-bolt" @click="adjustPoints" style="align-self:flex-start" />
            </div>
          </TabPanel>
          <TabPanel value="2">
            <div style="display:flex;gap:var(--space-3);margin-bottom:var(--space-4);align-items:center;">
              <InputNumber v-model="historyUserId" placeholder="User ID" style="width:160px" /><Button label="Load" icon="pi pi-search" outlined size="small" @click="loadHistory" />
            </div>
            <DataTable :value="history" :loading="histLoading" stripedRows>
              <Column field="fullName" header="User" /><Column field="actionCode" header="Action"><template #body="{data}">{{ data.actionCode || '—' }}</template></Column>
              <Column field="pointsChanged" header="Points"><template #body="{data}"><span :style="{color: data.pointsChanged>=0?'var(--theme-success)':'var(--theme-danger)', fontWeight:700}">{{ data.pointsChanged>=0?'+':'' }}{{ data.pointsChanged }}</span></template></Column>
              <Column field="reason" header="Reason" /><Column field="createdAt" header="Date"><template #body="{data}">{{ fmtDate(data.createdAt) }}</template></Column>
            </DataTable>
          </TabPanel>
        </TabPanels>
      </Tabs>
    </div>
    <Dialog v-model:visible="ruleDialog" :header="editingRule?'Edit Rule':'Add Rule'" modal :style="{width:'380px'}">
      <div style="display:flex;flex-direction:column;gap:var(--space-4);">
        <div class="form-field"><label>Action Code</label><InputText v-model="ruleForm.actionCode" fluid /></div>
        <div class="form-field"><label>Point Value</label><InputNumber v-model="ruleForm.pointValue" :showButtons="true" fluid /></div>
      </div>
      <template #footer><Button label="Cancel" text @click="ruleDialog=false" /><Button :label="editingRule?'Update':'Create'" icon="pi pi-check" @click="saveRule" /></template>
    </Dialog>
  </div>
</template>
