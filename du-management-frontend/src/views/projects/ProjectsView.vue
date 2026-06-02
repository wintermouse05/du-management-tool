<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useAuthStore } from '@/stores/auth'
import { projectsApi } from '@/api/projects'
import { ProjectStatus, type ProjectRequest, type ProjectResponse } from '@/types'
import { formatLocalDateTime, parseApiDate, toLocalDateTime } from '@/utils/datetime'
import { getApiErrorDetail } from '@/utils/apiError'
import DataTable from 'primevue/datatable'
import Column from 'primevue/column'
import Button from 'primevue/button'
import Dialog from 'primevue/dialog'
import InputText from 'primevue/inputtext'
import DatePicker from 'primevue/datepicker'
import Select from 'primevue/select'
import Tag from 'primevue/tag'
import { useToast } from 'primevue/usetoast'

const auth = useAuthStore()
const toast = useToast()

const projects = ref<ProjectResponse[]>([])
const loading = ref(false)
const totalRecords = ref(0)
const page = ref(0)
const rows = ref(10)

const dialogVisible = ref(false)
const editing = ref(false)
const editId = ref<number | null>(null)
const saving = ref(false)
const form = ref<ProjectRequest>({
  name: '',
  status: ProjectStatus.PLANNED,
  startTime: '',
  endTime: '',
})
const formStartDate = ref<Date | null>(null)
const formEndDate = ref<Date | null>(null)

const deleteConfirmDialog = ref(false)
const deleteTarget = ref<ProjectResponse | null>(null)

const statusOptions = [
  { label: 'Planned', value: ProjectStatus.PLANNED },
  { label: 'Active', value: ProjectStatus.ACTIVE },
  { label: 'On Hold', value: ProjectStatus.ON_HOLD },
  { label: 'Completed', value: ProjectStatus.COMPLETED },
  { label: 'Cancelled', value: ProjectStatus.CANCELLED },
]

const canSubmit = computed(() => {
  return form.value.name.trim().length > 0
    && resolveFormDate(formStartDate.value) !== null
    && resolveFormDate(formEndDate.value) !== null
})

async function load() {
  loading.value = true
  try {
    const response = await projectsApi.getAll({ page: page.value + 1, size: rows.value })
    projects.value = response.data.content
    totalRecords.value = response.data.totalElements
  } catch (err: any) {
    toast.add({ severity: 'error', summary: 'Error', detail: getApiErrorDetail(err, 'Failed to load projects'), life: 3000 })
  } finally {
    loading.value = false
  }
}

function onPage(event: any) {
  page.value = event.page
  rows.value = event.rows
  void load()
}

function openCreate() {
  editing.value = false
  editId.value = null
  form.value = { name: '', status: ProjectStatus.PLANNED, startTime: '', endTime: '' }
  formStartDate.value = null
  formEndDate.value = null
  dialogVisible.value = true
}

function openEdit(project: ProjectResponse) {
  editing.value = true
  editId.value = project.id
  form.value = {
    name: project.name,
    status: project.status,
    startTime: project.startTime,
    endTime: project.endTime,
  }
  formStartDate.value = parseApiDate(project.startTime)
  formEndDate.value = parseApiDate(project.endTime)
  dialogVisible.value = true
}

async function save() {
  const start = resolveFormDate(formStartDate.value)
  const end = resolveFormDate(formEndDate.value)
  if (!form.value.name.trim() || !start || !end) {
    toast.add({ severity: 'warn', summary: 'Missing project details', detail: 'Please complete all required fields.', life: 3000 })
    return
  }
  if (start.getTime() > end.getTime()) {
    toast.add({ severity: 'warn', summary: 'Invalid date range', detail: 'Start time must be before or equal to end time.', life: 3000 })
    return
  }

  const payload: ProjectRequest = {
    name: form.value.name.trim(),
    status: form.value.status,
    startTime: toLocalDateTime(start),
    endTime: toLocalDateTime(end),
  }

  saving.value = true
  try {
    if (editing.value && editId.value) {
      await projectsApi.update(editId.value, payload)
      toast.add({ severity: 'success', summary: 'Project updated', life: 2500 })
    } else {
      await projectsApi.create(payload)
      toast.add({ severity: 'success', summary: 'Project created', life: 2500 })
    }
    dialogVisible.value = false
    await load()
  } catch (err: any) {
    toast.add({ severity: 'error', summary: 'Error', detail: getApiErrorDetail(err, 'Failed to save project'), life: 3500 })
  } finally {
    saving.value = false
  }
}

function confirmArchive(project: ProjectResponse) {
  deleteTarget.value = project
  deleteConfirmDialog.value = true
}

async function archiveProject() {
  if (!deleteTarget.value) return
  try {
    await projectsApi.delete(deleteTarget.value.id)
    toast.add({ severity: 'success', summary: 'Project archived', life: 2500 })
    deleteConfirmDialog.value = false
    deleteTarget.value = null
    await load()
  } catch (err: any) {
    toast.add({ severity: 'error', summary: 'Error', detail: getApiErrorDetail(err, 'Failed to archive project'), life: 3500 })
  }
}

function resolveFormDate(value: unknown): Date | null {
  if (value instanceof Date) {
    return Number.isNaN(value.getTime()) ? null : value
  }
  if (typeof value === 'string') {
    return parseApiDate(value)
  }
  return null
}

function fmtDate(value: string) {
  return formatLocalDateTime(value)
}

function getStatusSeverity(status: ProjectStatus) {
  switch (status) {
    case ProjectStatus.ACTIVE:
      return 'success'
    case ProjectStatus.ON_HOLD:
      return 'warn'
    case ProjectStatus.COMPLETED:
      return 'info'
    case ProjectStatus.CANCELLED:
      return 'danger'
    default:
      return 'secondary'
  }
}

onMounted(load)
</script>

<template>
  <div class="page-container">
    <div class="page-header">
      <div>
        <h2>Projects</h2>
        <p class="page-subtitle">Track DU projects, members, and tasks</p>
      </div>
      <Button v-if="auth.isAdminOrHR" label="Create Project" icon="pi pi-plus" @click="openCreate" />
    </div>

    <div class="content-card project-table-card">
      <DataTable
        :value="projects"
        :loading="loading"
        :paginator="true"
        :rows="rows"
        :totalRecords="totalRecords"
        :lazy="true"
        tableStyle="min-width: 980px"
        stripedRows
        @page="onPage"
      >
        <template #empty>
          No Project has been created yet. Please create a new Project.
        </template>
        <Column field="name" header="Project Name">
          <template #body="{ data }">
            <router-link :to="`/projects/${data.id}`" class="table-link">{{ data.name }}</router-link>
          </template>
        </Column>
        <Column field="status" header="Status" style="width:150px">
          <template #body="{ data }">
            <Tag :value="data.statusLabel" :severity="getStatusSeverity(data.status)" />
          </template>
        </Column>
        <Column field="startTime" header="Start">
          <template #body="{ data }">{{ fmtDate(data.startTime) }}</template>
        </Column>
        <Column field="endTime" header="End">
          <template #body="{ data }">{{ fmtDate(data.endTime) }}</template>
        </Column>
        <Column field="memberCount" header="Members" style="width:110px" />
        <Column field="taskCount" header="Tasks" style="width:100px" />
        <Column v-if="auth.isAdminOrHR" header="Actions" style="width:130px">
          <template #body="{ data }">
            <div class="table-actions">
              <Button icon="pi pi-pencil" text rounded severity="info" v-tooltip.top="'Edit project'" @click="openEdit(data)" />
              <Button icon="pi pi-trash" text rounded severity="danger" v-tooltip.top="'Archive project'" @click="confirmArchive(data)" />
            </div>
          </template>
        </Column>
      </DataTable>
    </div>

    <Dialog v-model:visible="dialogVisible" :header="editing ? 'Edit Project' : 'Create Project'" modal :style="{ width: '520px' }">
      <div class="project-form">
        <div class="form-field">
          <label class="required">Project Name</label>
          <InputText v-model="form.name" fluid />
        </div>
        <div class="form-field">
          <label class="required">Status</label>
          <Select v-model="form.status" :options="statusOptions" option-label="label" option-value="value" fluid />
        </div>
        <div class="form-field">
          <label class="required">Start Time</label>
          <DatePicker v-model="formStartDate" showTime hourFormat="24" :manualInput="false" fluid />
        </div>
        <div class="form-field">
          <label class="required">End Time</label>
          <DatePicker v-model="formEndDate" showTime hourFormat="24" :manualInput="false" fluid />
        </div>
      </div>
      <template #footer>
        <Button label="Cancel" text @click="dialogVisible = false" />
        <Button :label="editing ? 'Update' : 'Create'" icon="pi pi-check" :loading="saving" :disabled="saving || !canSubmit" @click="save" />
      </template>
    </Dialog>

    <Dialog v-model:visible="deleteConfirmDialog" header="Archive Project" modal :style="{ width: '420px' }">
      <p style="margin:0;">
        Archive {{ deleteTarget?.name }}?
      </p>
      <template #footer>
        <Button label="Cancel" text @click="deleteConfirmDialog = false" />
        <Button label="Archive" icon="pi pi-trash" severity="danger" @click="archiveProject" />
      </template>
    </Dialog>
  </div>
</template>

<style scoped>
.project-table-card {
  overflow-x: auto;
  -webkit-overflow-scrolling: touch;
}

.project-form {
  display: flex;
  flex-direction: column;
  gap: var(--space-4);
}

.table-actions {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}

.table-link {
  color: var(--theme-blue);
  font-weight: 500;
}

.table-link:hover {
  text-decoration: underline;
}
</style>
