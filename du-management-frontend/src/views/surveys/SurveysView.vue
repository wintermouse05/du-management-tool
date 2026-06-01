<script setup lang="ts">
import { ref, onMounted, watch, onUnmounted } from 'vue'
import { useAuthStore } from '@/stores/auth'
import { surveysApi } from '@/api/surveys'
import { membersApi } from '@/api/members'
import { notificationsApi } from '@/api/notifications'
import { UserStatus, type SurveyResponse, type SurveyRequest, type SurveyProgressResponse, type MemberResponse } from '@/types'
import { wsService } from '@/services/websocket'
import { formatLocalDateTime, parseApiDate, toLocalDateTime } from '@/utils/datetime'
import { getApiErrorDetail } from '@/utils/apiError'
import DataTable from 'primevue/datatable'
import Column from 'primevue/column'
import Button from 'primevue/button'
import Dialog from 'primevue/dialog'
import InputText from 'primevue/inputtext'
import DatePicker from 'primevue/datepicker'
import MultiSelect from 'primevue/multiselect'
import ProgressBar from 'primevue/progressbar'
import Tag from 'primevue/tag'
import { useToast } from 'primevue/usetoast'

const auth = useAuthStore()
const toast = useToast()
const surveys = ref<SurveyResponse[]>([])
const total = ref(0); const loading = ref(false); const pg = ref(0); const rows = ref(10)
const dialogVisible = ref(false); const editing = ref(false); const editId = ref<number|null>(null)
const form = ref<SurveyRequest>({ title: '', link: '', deadline: '' })
const formDate = ref<Date|null>(null)
const formAssignUserIds = ref<number[]>([])
const assignUsers = ref<MemberResponse[]>([])
const assignOptionsLoading = ref(false)
const progressDialog = ref(false); const progress = ref<SurveyProgressResponse|null>(null)
const progressBySurveyId = ref<Record<number, SurveyProgressResponse>>({})
const remindingSurveyId = ref<number | null>(null)
const ADMIN_USERNAME = 'admin'

let sub: any = null

async function load() {
  loading.value = true
  try {
    const r = await surveysApi.getAll({ page: pg.value, size: rows.value })
    surveys.value = r.data.content
    total.value = r.data.totalElements
  } finally {
    loading.value = false
  }
  await loadProgressForCurrentPage()
}

async function loadProgressForCurrentPage() {
  if (!auth.isAdminOrHR || surveys.value.length === 0) {
    progressBySurveyId.value = {}
    return
  }

  const nextProgressBySurveyId: Record<number, SurveyProgressResponse> = {}
  await Promise.all(
    surveys.value.map(async (survey) => {
      try {
        const response = await surveysApi.getProgress(survey.id)
        nextProgressBySurveyId[survey.id] = response.data
      } catch {
        // Ignore per-survey failures so remaining rows still render.
      }
    }),
  )
  progressBySurveyId.value = nextProgressBySurveyId
}

function onPage(e: any) { pg.value = e.page; rows.value = e.rows; load() }

async function fetchAssignTargets() {
  assignOptionsLoading.value = true
  try {
    const usersRes = await membersApi.search({ size: 1000, status: UserStatus.ACTIVE })
    assignUsers.value = usersRes.data.content.filter(
      u => (u.username || '').toLowerCase() !== ADMIN_USERNAME,
    )
  } catch {
    toast.add({ severity: 'error', summary: 'Error', detail: 'Failed to load members', life: 3000 })
  } finally {
    assignOptionsLoading.value = false
  }
}

async function openCreate() {
  editing.value = false
  editId.value = null
  form.value = { title: '', link: '', deadline: '' }
  formDate.value = null
  formAssignUserIds.value = []
  dialogVisible.value = true
  await fetchAssignTargets()
}

async function openEdit(s: SurveyResponse) {
  editing.value = true
  editId.value = s.id
  form.value = { title: s.title, link: s.link, deadline: s.deadline }
  formDate.value = parseApiDate(s.deadline)
  formAssignUserIds.value = []
  dialogVisible.value = true
  await Promise.all([fetchAssignTargets(), loadAssignedMembers(s.id)])
}

async function loadAssignedMembers(surveyId: number) {
  try {
    const response = await surveysApi.getProgress(surveyId)
    formAssignUserIds.value = response.data.assignments.map(assignment => assignment.userId)
  } catch {
    formAssignUserIds.value = []
    toast.add({ severity: 'warn', summary: 'Could not load assigned members', detail: 'You can still edit and assign members manually.', life: 3000 })
  }
}

async function save() {
  if (formDate.value) form.value.deadline = toLocalDateTime(formDate.value)
  try {
    const selectedUserIds = [...new Set(formAssignUserIds.value)]
    if (editing.value && editId.value) {
      await surveysApi.update(editId.value, form.value)
      const replaceResponse = await surveysApi.replaceAssignments(editId.value, { userIds: selectedUserIds })
      progressBySurveyId.value = { ...progressBySurveyId.value, [editId.value]: replaceResponse.data }
      if (progressDialog.value && progress.value?.surveyId === editId.value) {
        progress.value = replaceResponse.data
      }
      toast.add({ severity: 'success', summary: `Updated and assigned ${selectedUserIds.length} member(s)`, life: 3000 })
    } else {
      const created = await surveysApi.create(form.value)
      if (selectedUserIds.length > 0) {
        await surveysApi.replaceAssignments(created.data.id, { userIds: selectedUserIds })
        toast.add({ severity: 'success', summary: `Created and assigned ${selectedUserIds.length} member(s)`, life: 3000 })
      } else {
        toast.add({ severity: 'success', summary: 'Created', life: 3000 })
      }
    }
    formAssignUserIds.value = []
    dialogVisible.value = false
    await load()
  } catch (err: any) { toast.add({ severity: 'error', summary: 'Error', detail: getApiErrorDetail(err), life: 4000 }) }
}

async function showProgress(id: number) {
  try {
    const r = await surveysApi.getProgress(id)
    progress.value = r.data
    progressBySurveyId.value = { ...progressBySurveyId.value, [id]: r.data }
    progressDialog.value = true

    if (sub) sub.unsubscribe()
    sub = wsService.subscribe(`/topic/surveys/${id}`, (message) => {
      const latest = JSON.parse(message.body) as SurveyProgressResponse
      progress.value = latest
      progressBySurveyId.value = { ...progressBySurveyId.value, [id]: latest }
    })
  } catch (err: any) { toast.add({ severity: 'error', summary: 'Error', detail: getApiErrorDetail(err), life: 3000 }) }
}

async function markCompleted(surveyId: number, completed: boolean) {
  if (!auth.userId) {
    toast.add({ severity: 'error', summary: 'Cannot update survey', detail: 'Missing user identity. Please log in again.', life: 3000 })
    return
  }
  try {
    const response = await surveysApi.complete(surveyId, { userId: auth.userId, completed })
    if (auth.isAdminOrHR) {
      progressBySurveyId.value = { ...progressBySurveyId.value, [surveyId]: response.data }
    }
    if (progressDialog.value && progress.value?.surveyId === surveyId) {
      progress.value = response.data
    }
    toast.add({ severity: 'success', summary: completed ? 'Marked as completed' : 'Marked as incomplete', life: 2500 })
  } catch (err: any) {
    toast.add({ severity: 'error', summary: 'Error', detail: getApiErrorDetail(err, 'Failed to update survey status'), life: 3000 })
  }
}

async function remindPendingMembers(survey: SurveyResponse) {
  remindingSurveyId.value = survey.id
  try {
    const response = await notificationsApi.triggerSurveyReminder(survey.id)
    toast.add({ severity: 'success', summary: 'Reminder queued', detail: response.data.message, life: 3200 })
  } catch (err: any) {
    toast.add({ severity: 'error', summary: 'Error', detail: getApiErrorDetail(err, 'Failed to send reminder'), life: 3000 })
  } finally {
    remindingSurveyId.value = null
  }
}

function getPendingCount(surveyId: number) {
  const item = progressBySurveyId.value[surveyId]
  if (!item) return 0
  return Math.max(item.totalAssigned - item.completedCount, 0)
}

function getProgressSummary(surveyId: number) {
  const item = progressBySurveyId.value[surveyId]
  if (!item) return '--/--'
  return `${item.completedCount}/${item.totalAssigned}`
}

function hasLoadedProgress(surveyId: number) {
  return !!progressBySurveyId.value[surveyId]
}

function isSurveyExpired(deadline: string) {
  return new Date(deadline).getTime() < Date.now()
}

watch(progressDialog, (newVal) => {
  if (!newVal && sub) {
    sub.unsubscribe()
    sub = null
  }
})

function fmtDate(d: string) { return formatLocalDateTime(d) }

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
    <div class="content-card survey-table-card">
      <DataTable
        class="survey-table"
        :value="surveys"
        :loading="loading"
        :paginator="true"
        :rows="rows"
        :totalRecords="total"
        :lazy="true"
        tableStyle="min-width: 1120px"
        @page="onPage"
        stripedRows
      >
        <template #empty>
          No Survey has been created yet. Please create a new Survey.
        </template>
        <Column field="title" header="Title" />
        <Column field="link" header="Link"><template #body="{data}"><a :href="data.link" target="_blank" rel="noopener noreferrer" style="color:var(--theme-blue);">Open <i class="pi pi-external-link" style="font-size:11px"></i></a></template></Column>
        <Column field="deadline" header="Deadline"><template #body="{data}">{{ fmtDate(data.deadline) }}</template></Column>
        <Column v-if="auth.isAdminOrHR" header="Completed" style="width:160px">
          <template #body="{ data }">
            <Tag
              v-if="hasLoadedProgress(data.id)"
              :value="getProgressSummary(data.id)"
              :severity="getPendingCount(data.id) === 0 ? 'success' : 'info'"
            />
            <span v-else class="caption">--/--</span>
          </template>
        </Column>
        <Column header="Actions" style="width:500px;min-width:500px">
          <template #body="{data}">
            <div class="survey-actions">
              <div v-if="auth.isAuthenticated && !isSurveyExpired(data.deadline)" class="survey-actions__group">
                <Button
                  label="Complete"
                  icon="pi pi-check"
                  size="small"
                  outlined
                  severity="success"
                  @click="markCompleted(data.id, true)"
                />
                <Button
                  label="Incomplete"
                  icon="pi pi-times"
                  size="small"
                  outlined
                  severity="secondary"
                  @click="markCompleted(data.id, false)"
                />
              </div>
              <div v-if="auth.isAdminOrHR" class="survey-actions__group">
                <Button label="Progress" icon="pi pi-chart-bar" size="small" outlined @click="showProgress(data.id)" />
                <Button
                  label="Remind"
                  icon="pi pi-send"
                  size="small"
                  outlined
                  severity="warning"
                  :loading="remindingSurveyId === data.id"
                  :disabled="hasLoadedProgress(data.id) && getPendingCount(data.id) === 0"
                  @click="remindPendingMembers(data)"
                />
                <Button
                  icon="pi pi-pencil"
                  text
                  rounded
                  severity="info"
                  aria-label="Edit survey"
                  v-tooltip.top="'Edit survey'"
                  @click="openEdit(data)"
                />
              </div>
              <span v-if="isSurveyExpired(data.deadline)" class="expired-label">Expired</span>
            </div>
          </template>
        </Column>
      </DataTable>
    </div>
    <Dialog v-model:visible="dialogVisible" :header="editing?'Edit Survey':'Create Survey'" modal :style="{width:'560px'}">
      <div style="display:flex;flex-direction:column;gap:var(--space-4);">
        <div class="form-field"><label class="required">Title</label><InputText v-model="form.title" fluid /></div>
        <div class="form-field"><label class="required">Link (URL)</label><InputText v-model="form.link" placeholder="https://..." fluid /></div>
        <div class="form-field"><label class="required">Deadline</label><DatePicker v-model="formDate" showTime hourFormat="24" fluid /></div>
        <div class="form-field">
          <label>Assign Members <span class="optional-hint">(optional)</span></label>
          <MultiSelect
            v-model="formAssignUserIds"
            :options="assignUsers"
            option-label="fullName"
            option-value="id"
            placeholder="Select members"
            filter
            display="chip"
            :filter-fields="['username', 'fullName', 'email']"
            :loading="assignOptionsLoading"
            fluid
          >
            <template #option="{ option }">
              <div>{{ option.fullName }} <span style="color:var(--theme-text-weak);font-size:12px;">(@{{ option.username }})</span></div>
            </template>
          </MultiSelect>
        </div>
      </div>
      <template #footer><Button label="Cancel" text @click="dialogVisible=false" /><Button :label="editing?'Update':'Create'" icon="pi pi-check" @click="save" /></template>
    </Dialog>
    <Dialog v-model:visible="progressDialog" header="Survey Progress" modal :style="{width:'620px'}">
      <div v-if="progress" style="display:flex;flex-direction:column;gap:var(--space-4);">
        <div style="display:flex;justify-content:space-between;"><span class="caption">Members Assigned</span><strong>{{ progress.totalAssigned }}</strong></div>
        <div style="display:flex;justify-content:space-between;"><span class="caption">Completed</span><strong>{{ progress.completedCount }}</strong></div>
        <small class="caption">Admin accounts are excluded from these totals.</small>
        <ProgressBar :value="progress.totalAssigned > 0 ? Math.round(progress.completedCount / progress.totalAssigned * 100) : 0" />
        <div>
          <h4 style="margin:0 0 var(--space-3);">Assigned Members (Non-admin)</h4>
          <DataTable :value="progress.assignments || []" stripedRows size="small">
            <template #empty>
              No members have been assigned to this survey yet.
            </template>
            <Column field="fullName" header="Member" />
            <Column header="Completed" style="width:140px">
              <template #body="{ data }">
                <Tag :value="data.completed ? 'Yes' : 'No'" :severity="data.completed ? 'success' : 'secondary'" />
              </template>
            </Column>
          </DataTable>
        </div>
      </div>
    </Dialog>
  </div>
</template>

<style scoped>
.survey-table-card {
  overflow-x: auto;
  -webkit-overflow-scrolling: touch;
}

.survey-actions {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  min-width: max-content;
  white-space: nowrap;
}

.survey-actions__group {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  white-space: nowrap;
}

.survey-actions__group + .survey-actions__group {
  border-left: 1px solid var(--theme-divider);
  margin-left: 2px;
  padding-left: 10px;
}

.survey-actions :deep(.p-button) {
  flex: 0 0 auto;
}

.survey-actions :deep(.p-button-icon-only) {
  height: 32px;
  min-width: 32px;
  padding: 0 !important;
  width: 32px;
}

.expired-label {
  display: inline-flex;
  align-items: center;
  flex: 0 0 auto;
  border-radius: 999px;
  background: #1f2937;
  color: #ffffff;
  font-size: 12px;
  font-weight: 600;
  padding: 4px 10px;
}
</style>

