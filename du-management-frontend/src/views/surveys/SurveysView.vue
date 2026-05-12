<script setup lang="ts">
import { ref, onMounted, watch, onUnmounted } from 'vue'
import { useAuthStore } from '@/stores/auth'
import { surveysApi } from '@/api/surveys'
import { membersApi } from '@/api/members'
import { groupsApi } from '@/api/groups'
import type { SurveyResponse, SurveyRequest, SurveyProgressResponse, MemberResponse, GroupResponse } from '@/types'
import { wsService } from '@/services/websocket'
import DataTable from 'primevue/datatable'
import Column from 'primevue/column'
import Button from 'primevue/button'
import Dialog from 'primevue/dialog'
import InputText from 'primevue/inputtext'
import DatePicker from 'primevue/datepicker'
import Select from 'primevue/select'
import SelectButton from 'primevue/selectbutton'
import ProgressBar from 'primevue/progressbar'
import { useToast } from 'primevue/usetoast'

const auth = useAuthStore()
const toast = useToast()
const assignModeOptions = [
  { label: 'Individual User', value: 'user' },
  { label: 'Group', value: 'group' },
]
const surveys = ref<SurveyResponse[]>([])
const total = ref(0); const loading = ref(false); const pg = ref(0); const rows = ref(10)
const dialogVisible = ref(false); const editing = ref(false); const editId = ref<number|null>(null)
const form = ref<SurveyRequest>({ title: '', link: '', deadline: '' })
const formDate = ref<Date|null>(null)
const assignDialog = ref(false); const assignSurveyId = ref(0)
const assignMode = ref<'user' | 'group'>('user')
const assignUserId = ref<number | null>(null)
const assignGroupId = ref<number | null>(null)
const assignUsers = ref<MemberResponse[]>([])
const assignGroups = ref<GroupResponse[]>([])
const progressDialog = ref(false); const progress = ref<SurveyProgressResponse|null>(null)

let sub: any = null

async function load() {
  loading.value = true
  try { const r = await surveysApi.getAll({ page: pg.value, size: rows.value }); surveys.value = r.data.content; total.value = r.data.totalElements }
  finally { loading.value = false }
}
function onPage(e: any) { pg.value = e.page; rows.value = e.rows; load() }
function openCreate() { editing.value = false; editId.value = null; form.value = { title: '', link: '', deadline: '' }; formDate.value = null; dialogVisible.value = true }
function openEdit(s: SurveyResponse) { editing.value = true; editId.value = s.id; form.value = { title: s.title, link: s.link, deadline: s.deadline }; formDate.value = s.deadline ? new Date(s.deadline) : null; dialogVisible.value = true }

async function save() {
  if (formDate.value) form.value.deadline = formDate.value.toISOString()
  try {
    if (editing.value && editId.value) await surveysApi.update(editId.value, form.value)
    else await surveysApi.create(form.value)
    toast.add({ severity: 'success', summary: editing.value ? 'Updated' : 'Created', life: 3000 }); dialogVisible.value = false; load()
  } catch (err: any) { toast.add({ severity: 'error', summary: 'Error', detail: err.response?.data?.message, life: 4000 }) }
}

async function openAssign(surveyId: number) {
  assignSurveyId.value = surveyId
  assignMode.value = 'user'
  assignUserId.value = null
  assignGroupId.value = null
  assignDialog.value = true
  try {
    const [usersRes, groupsRes] = await Promise.all([
      membersApi.search({ size: 1000 }),
      groupsApi.getAll(),
    ])
    assignUsers.value = usersRes.data.content.filter(u => u.status === 'ACTIVE')
    assignGroups.value = groupsRes.data
  } catch {}
}

async function assign() {
  try {
    if (assignMode.value === 'group' && assignGroupId.value) {
      await surveysApi.assign(assignSurveyId.value, undefined, assignGroupId.value)
    } else if (assignMode.value === 'user' && assignUserId.value) {
      await surveysApi.assign(assignSurveyId.value, assignUserId.value)
    } else {
      toast.add({ severity: 'warn', summary: 'Please select a user or group', life: 2000 })
      return
    }
    toast.add({ severity: 'success', summary: 'Assigned successfully', life: 2000 })
    assignDialog.value = false
  } catch (err: any) { toast.add({ severity: 'error', summary: 'Error', detail: err.response?.data?.message, life: 3000 }) }
}

async function showProgress(id: number) {
  try { 
    const r = await surveysApi.getProgress(id); 
    progress.value = r.data; 
    progressDialog.value = true
    
    if (sub) sub.unsubscribe()
    sub = wsService.subscribe(`/topic/surveys/${id}`, (message) => {
      progress.value = JSON.parse(message.body) as SurveyProgressResponse
    })
  } catch (err: any) { toast.add({ severity: 'error', summary: 'Error', detail: err.response?.data?.message, life: 3000 }) }
}

async function markCompleted(surveyId: number, completed: boolean) {
  if (!auth.userId) {
    toast.add({ severity: 'error', summary: 'Cannot update survey', detail: 'Missing user identity. Please log in again.', life: 3000 })
    return
  }
  try {
    await surveysApi.complete(surveyId, { userId: auth.userId, completed })
    toast.add({ severity: 'success', summary: completed ? 'Marked as completed' : 'Marked as incomplete', life: 2500 })
  } catch (err: any) {
    toast.add({ severity: 'error', summary: 'Error', detail: err.response?.data?.message || 'Failed to update survey status', life: 3000 })
  }
}

watch(progressDialog, (newVal) => {
  if (!newVal && sub) {
    sub.unsubscribe()
    sub = null
  }
})

function fmtDate(d: string) { return d ? new Date(d).toLocaleDateString('en-US', { month: 'short', day: 'numeric', year: 'numeric' }) : '' }

onMounted(load)

onUnmounted(() => {
  if (sub) sub.unsubscribe()
})
</script>

<template>
  <div class="page-container">
    <div class="page-header">
      <div><h2>Surveys</h2><p class="page-subtitle">Create surveys and track completion</p></div>
      <Button v-if="auth.isAdminOrHR" label="Create Survey" icon="pi pi-plus" @click="openCreate" />
    </div>
    <div class="content-card">
      <DataTable :value="surveys" :loading="loading" :paginator="true" :rows="rows" :totalRecords="total" :lazy="true" @page="onPage" stripedRows>
        <Column field="title" header="Title" />
        <Column field="link" header="Link"><template #body="{data}"><a :href="data.link" target="_blank" rel="noopener noreferrer" style="color:var(--theme-blue);">Open <i class="pi pi-external-link" style="font-size:11px"></i></a></template></Column>
        <Column field="deadline" header="Deadline"><template #body="{data}">{{ fmtDate(data.deadline) }}</template></Column>
        <Column header="Actions" style="width:220px">
          <template #body="{data}">
            <div style="display:flex;gap:4px;">
              <Button v-if="auth.isMember" icon="pi pi-check" text rounded severity="success" v-tooltip="'Mark completed'" @click="markCompleted(data.id, true)" />
              <Button v-if="auth.isMember" icon="pi pi-times" text rounded severity="secondary" v-tooltip="'Mark incomplete'" @click="markCompleted(data.id, false)" />
              <Button v-if="auth.isAdminOrHR" icon="pi pi-user-plus" text rounded v-tooltip="'Assign'" @click="openAssign(data.id)" />
              <Button v-if="auth.isAdminOrHR" icon="pi pi-chart-bar" text rounded v-tooltip="'Progress'" @click="showProgress(data.id)" />
              <Button v-if="auth.isAdminOrHR" icon="pi pi-pencil" text rounded severity="info" @click="openEdit(data)" />
            </div>
          </template>
        </Column>
      </DataTable>
    </div>
    <Dialog v-model:visible="dialogVisible" :header="editing?'Edit Survey':'Create Survey'" modal :style="{width:'480px'}">
      <div style="display:flex;flex-direction:column;gap:var(--space-4);">
        <div class="form-field"><label>Title</label><InputText v-model="form.title" fluid /></div>
        <div class="form-field"><label>Link (URL)</label><InputText v-model="form.link" placeholder="https://..." fluid /></div>
        <div class="form-field"><label>Deadline</label><DatePicker v-model="formDate" showTime hourFormat="24" fluid /></div>
      </div>
      <template #footer><Button label="Cancel" text @click="dialogVisible=false" /><Button :label="editing?'Update':'Create'" icon="pi pi-check" @click="save" /></template>
    </Dialog>
    <Dialog v-model:visible="assignDialog" header="Assign Survey" modal :style="{width:'480px'}">
      <div style="display:flex;flex-direction:column;gap:var(--space-4);">
        <div class="form-field">
          <label>Assignment Mode</label>
          <SelectButton v-model="assignMode" :options="assignModeOptions" option-label="label" option-value="value" fluid />
        </div>
        <div v-if="assignMode === 'user'" class="form-field">
          <label>User</label>
          <Select
            v-model="assignUserId"
            :options="assignUsers"
            option-label="fullName"
            option-value="id"
            placeholder="Search and select a user"
            filter
            :filter-fields="['username', 'fullName', 'email']"
            fluid
          >
            <template #option="{ option }">
              <div>{{ option.fullName }} <span style="color:var(--theme-text-weak);font-size:12px;">(@{{ option.username }})</span></div>
            </template>
          </Select>
        </div>
        <div v-if="assignMode === 'group'" class="form-field">
          <label>Group</label>
          <Select
            v-model="assignGroupId"
            :options="assignGroups"
            option-label="name"
            option-value="id"
            placeholder="Select a group"
            fluid
          >
            <template #option="{ option }">
              <div>{{ option.name }} <span style="color:var(--theme-text-weak);font-size:12px;">({{ option.memberCount }} members)</span></div>
            </template>
          </Select>
        </div>
      </div>
      <template #footer><Button label="Cancel" text @click="assignDialog=false" /><Button label="Assign" icon="pi pi-check" @click="assign" /></template>
    </Dialog>
    <Dialog v-model:visible="progressDialog" header="Survey Progress" modal :style="{width:'400px'}">
      <div v-if="progress" style="display:flex;flex-direction:column;gap:var(--space-4);">
        <div style="display:flex;justify-content:space-between;"><span class="caption">Assigned</span><strong>{{ progress.totalAssigned }}</strong></div>
        <div style="display:flex;justify-content:space-between;"><span class="caption">Completed</span><strong>{{ progress.completedCount }}</strong></div>
        <ProgressBar :value="progress.totalAssigned > 0 ? Math.round(progress.completedCount / progress.totalAssigned * 100) : 0" />
      </div>
    </Dialog>
  </div>
</template>
