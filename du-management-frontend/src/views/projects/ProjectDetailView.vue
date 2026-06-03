<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { membersApi } from '@/api/members'
import { projectsApi } from '@/api/projects'
import {
  ProjectRole,
  ProjectStatus,
  TaskStatus,
  UserStatus,
  type MemberResponse,
  type ProjectMemberRequest,
  type ProjectMemberResponse,
  type ProjectResponse,
  type ProjectTaskRequest,
  type ProjectTaskResponse,
} from '@/types'
import { formatLocalDateTime, parseApiDate, toLocalDateTime } from '@/utils/datetime'
import { getApiErrorDetail } from '@/utils/apiError'
import DataTable from 'primevue/datatable'
import Column from 'primevue/column'
import Button from 'primevue/button'
import Dialog from 'primevue/dialog'
import InputText from 'primevue/inputtext'
import DatePicker from 'primevue/datepicker'
import Select from 'primevue/select'
import MultiSelect from 'primevue/multiselect'
import Tag from 'primevue/tag'
import Textarea from 'primevue/textarea'
import { useToast } from 'primevue/usetoast'

type DetailTab = 'members' | 'tasks'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const toast = useToast()

const projectId = Number(route.params.id)
const project = ref<ProjectResponse | null>(null)
const members = ref<ProjectMemberResponse[]>([])
const tasks = ref<ProjectTaskResponse[]>([])
const loading = ref(false)
const membersLoading = ref(false)
const tasksLoading = ref(false)
const activeTab = ref<DetailTab>('members')

const memberDialog = ref(false)
const memberEditing = ref(false)
const editMemberUserId = ref<number | null>(null)
const availableUsers = ref<MemberResponse[]>([])
const usersLoading = ref(false)
const memberSaving = ref(false)
const memberForm = ref<ProjectMemberRequest>({
  userId: 0,
  projectRole: ProjectRole.BACKEND_DEVELOPER,
  participationStartTime: '',
  expectedEndTime: '',
})
const memberStartDate = ref<Date | null>(null)
const memberEndDate = ref<Date | null>(null)

const taskDialog = ref(false)
const taskEditing = ref(false)
const editTaskId = ref<number | null>(null)
const taskSaving = ref(false)
const taskForm = ref<ProjectTaskRequest>({
  name: '',
  description: '',
  status: TaskStatus.TODO,
  assigneeIds: [],
  startTime: '',
  deadline: '',
})
const taskStartDate = ref<Date | null>(null)
const taskDeadlineDate = ref<Date | null>(null)

const projectRoleOptions = [
  { label: 'Backend Developer', value: ProjectRole.BACKEND_DEVELOPER },
  { label: 'Business Analyst', value: ProjectRole.BUSINESS_ANALYST },
  { label: 'DevOps Engineer', value: ProjectRole.DEVOPS_ENGINEER },
  { label: 'Flutter Developer', value: ProjectRole.FLUTTER_DEVELOPER },
  { label: 'Frontend Developer', value: ProjectRole.FRONTEND_DEVELOPER },
  { label: 'Project Manager', value: ProjectRole.PROJECT_MANAGER },
  { label: 'QA Engineer', value: ProjectRole.QA_ENGINEER },
  { label: 'Quality Control', value: ProjectRole.QUALITY_CONTROL },
  { label: 'Team Lead', value: ProjectRole.TEAM_LEAD },
  { label: 'Tech Lead', value: ProjectRole.TECH_LEAD },
  { label: 'UI/UX Designer', value: ProjectRole.UI_UX_DESIGNER },
  { label: 'Xamarin Developer', value: ProjectRole.XAMARIN_DEVELOPER },
]

const taskStatusOptions = [
  { label: 'To Do', value: TaskStatus.TODO },
  { label: 'In Progress', value: TaskStatus.IN_PROGRESS },
  { label: 'Blocked', value: TaskStatus.BLOCKED },
  { label: 'Done', value: TaskStatus.DONE },
  { label: 'Cancelled', value: TaskStatus.CANCELLED },
]

const memberOptionsForAdd = computed(() => {
  const existingIds = new Set(members.value.map(member => member.userId))
  return availableUsers.value.filter(user => !existingIds.has(user.id))
})

const taskAssigneeOptions = computed(() => members.value.map(member => ({
  id: member.userId,
  fullName: member.fullName,
  username: member.username,
  email: member.email,
})))

const canSubmitMember = computed(() => {
  return memberForm.value.userId > 0
    && resolveFormDate(memberStartDate.value) !== null
    && resolveFormDate(memberEndDate.value) !== null
})

const canSubmitTask = computed(() => {
  return taskForm.value.name.trim().length > 0
    && taskForm.value.assigneeIds.length > 0
    && resolveFormDate(taskStartDate.value) !== null
    && resolveFormDate(taskDeadlineDate.value) !== null
})

async function loadAll() {
  if (!Number.isFinite(projectId) || projectId <= 0) {
    await router.push('/projects')
    return
  }
  loading.value = true
  try {
    await Promise.all([loadProject(), loadMembers(), loadTasks()])
  } finally {
    loading.value = false
  }
}

async function loadProject() {
  try {
    const response = await projectsApi.getById(projectId)
    project.value = response.data
  } catch (err: any) {
    toast.add({ severity: 'error', summary: 'Error', detail: getApiErrorDetail(err, 'Failed to load project'), life: 3500 })
    await router.push('/projects')
  }
}

async function loadMembers() {
  membersLoading.value = true
  try {
    const response = await projectsApi.getMembers(projectId)
    members.value = response.data
  } catch (err: any) {
    toast.add({ severity: 'error', summary: 'Error', detail: getApiErrorDetail(err, 'Failed to load project members'), life: 3500 })
  } finally {
    membersLoading.value = false
  }
}

async function loadTasks() {
  tasksLoading.value = true
  try {
    const response = await projectsApi.getTasks(projectId)
    tasks.value = response.data
  } catch (err: any) {
    toast.add({ severity: 'error', summary: 'Error', detail: getApiErrorDetail(err, 'Failed to load project tasks'), life: 3500 })
  } finally {
    tasksLoading.value = false
  }
}

async function fetchAvailableUsers() {
  usersLoading.value = true
  try {
    const response = await membersApi.search({ size: 1000, status: UserStatus.ACTIVE })
    availableUsers.value = response.data.content.filter(user => (user.username || '').toLowerCase() !== 'admin')
  } catch (err: any) {
    toast.add({ severity: 'error', summary: 'Error', detail: getApiErrorDetail(err, 'Failed to load members'), life: 3500 })
  } finally {
    usersLoading.value = false
  }
}

async function openAddMember() {
  memberEditing.value = false
  editMemberUserId.value = null
  memberForm.value = {
    userId: 0,
    projectRole: ProjectRole.BACKEND_DEVELOPER,
    participationStartTime: '',
    expectedEndTime: '',
  }
  memberStartDate.value = parseApiDate(project.value?.startTime || null)
  memberEndDate.value = parseApiDate(project.value?.endTime || null)
  memberDialog.value = true
  await fetchAvailableUsers()
}

async function openEditMember(member: ProjectMemberResponse) {
  memberEditing.value = true
  editMemberUserId.value = member.userId
  memberForm.value = {
    userId: member.userId,
    projectRole: member.projectRole,
    participationStartTime: member.participationStartTime,
    expectedEndTime: member.expectedEndTime,
  }
  memberStartDate.value = parseApiDate(member.participationStartTime)
  memberEndDate.value = parseApiDate(member.expectedEndTime)
  memberDialog.value = true
  await fetchAvailableUsers()
}

async function saveMember() {
  const start = resolveFormDate(memberStartDate.value)
  const end = resolveFormDate(memberEndDate.value)
  if (!memberForm.value.userId || !start || !end) {
    toast.add({ severity: 'warn', summary: 'Missing member details', detail: 'Please complete all required fields.', life: 3000 })
    return
  }
  if (start.getTime() > end.getTime()) {
    toast.add({ severity: 'warn', summary: 'Invalid date range', detail: 'Participation start must be before or equal to expected end.', life: 3000 })
    return
  }

  const payload: ProjectMemberRequest = {
    userId: memberForm.value.userId,
    projectRole: memberForm.value.projectRole,
    participationStartTime: toLocalDateTime(start),
    expectedEndTime: toLocalDateTime(end),
  }

  memberSaving.value = true
  try {
    if (memberEditing.value && editMemberUserId.value) {
      await projectsApi.updateMember(projectId, editMemberUserId.value, payload)
      toast.add({ severity: 'success', summary: 'Project member updated', life: 2500 })
    } else {
      await projectsApi.addMember(projectId, payload)
      toast.add({ severity: 'success', summary: 'Project member added', life: 2500 })
    }
    memberDialog.value = false
    await Promise.all([loadProject(), loadMembers()])
  } catch (err: any) {
    toast.add({ severity: 'error', summary: 'Error', detail: getApiErrorDetail(err, 'Failed to save project member'), life: 3500 })
  } finally {
    memberSaving.value = false
  }
}

async function removeMember(member: ProjectMemberResponse) {
  try {
    await projectsApi.removeMember(projectId, member.userId)
    toast.add({ severity: 'success', summary: 'Project member removed', life: 2500 })
    await Promise.all([loadProject(), loadMembers()])
  } catch (err: any) {
    toast.add({ severity: 'error', summary: 'Error', detail: getApiErrorDetail(err, 'Failed to remove project member'), life: 3500 })
  }
}

function openCreateTask() {
  taskEditing.value = false
  editTaskId.value = null
  taskForm.value = {
    name: '',
    description: '',
    status: TaskStatus.TODO,
    assigneeIds: [],
    startTime: '',
    deadline: '',
  }
  taskStartDate.value = parseApiDate(project.value?.startTime || null)
  taskDeadlineDate.value = parseApiDate(project.value?.endTime || null)
  taskDialog.value = true
}

function openEditTask(task: ProjectTaskResponse) {
  taskEditing.value = true
  editTaskId.value = task.id
  taskForm.value = {
    name: task.name,
    description: task.description || '',
    status: task.status,
    assigneeIds: task.assignees.map(assignee => assignee.id),
    startTime: task.startTime,
    deadline: task.deadline,
  }
  taskStartDate.value = parseApiDate(task.startTime)
  taskDeadlineDate.value = parseApiDate(task.deadline)
  taskDialog.value = true
}

async function saveTask() {
  const start = resolveFormDate(taskStartDate.value)
  const deadline = resolveFormDate(taskDeadlineDate.value)
  if (!taskForm.value.name.trim() || taskForm.value.assigneeIds.length === 0 || !start || !deadline) {
    toast.add({ severity: 'warn', summary: 'Missing task details', detail: 'Please complete all required fields.', life: 3000 })
    return
  }
  if (start.getTime() > deadline.getTime()) {
    toast.add({ severity: 'warn', summary: 'Invalid date range', detail: 'Task start must be before or equal to deadline.', life: 3000 })
    return
  }

  const payload: ProjectTaskRequest = {
    name: taskForm.value.name.trim(),
    description: taskForm.value.description?.trim() || null,
    status: taskForm.value.status,
    assigneeIds: taskForm.value.assigneeIds,
    startTime: toLocalDateTime(start),
    deadline: toLocalDateTime(deadline),
  }

  taskSaving.value = true
  try {
    if (taskEditing.value && editTaskId.value) {
      await projectsApi.updateTask(projectId, editTaskId.value, payload)
      toast.add({ severity: 'success', summary: 'Task updated', life: 2500 })
    } else {
      await projectsApi.createTask(projectId, payload)
      toast.add({ severity: 'success', summary: 'Task created', life: 2500 })
    }
    taskDialog.value = false
    await Promise.all([loadProject(), loadTasks()])
  } catch (err: any) {
    toast.add({ severity: 'error', summary: 'Error', detail: getApiErrorDetail(err, 'Failed to save task'), life: 3500 })
  } finally {
    taskSaving.value = false
  }
}

async function archiveTask(task: ProjectTaskResponse) {
  try {
    await projectsApi.deleteTask(projectId, task.id)
    toast.add({ severity: 'success', summary: 'Task archived', life: 2500 })
    await Promise.all([loadProject(), loadTasks()])
  } catch (err: any) {
    toast.add({ severity: 'error', summary: 'Error', detail: getApiErrorDetail(err, 'Failed to archive task'), life: 3500 })
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

function formatTaskAssignees(task: ProjectTaskResponse) {
  const names = (task.assignees || []).map(assignee => assignee.fullName)
  return names.length ? names.join(', ') : 'Unassigned'
}

function getProjectStatusSeverity(status: ProjectStatus) {
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

function getTaskStatusSeverity(status: TaskStatus) {
  switch (status) {
    case TaskStatus.IN_PROGRESS:
      return 'info'
    case TaskStatus.BLOCKED:
      return 'warn'
    case TaskStatus.DONE:
      return 'success'
    case TaskStatus.CANCELLED:
      return 'danger'
    default:
      return 'secondary'
  }
}

onMounted(loadAll)
</script>

<template>
  <div class="page-container">
    <div class="project-back-row">
      <router-link to="/projects" class="back-link"><i class="pi pi-angle-left"></i> Projects</router-link>
    </div>

    <div class="page-header project-header">
      <div>
        <h2>{{ project?.name || 'Project' }}</h2>
        <p class="page-subtitle">Members, roles, participation dates, and project tasks</p>
      </div>
      <Tag v-if="project" :value="project.statusLabel" :severity="getProjectStatusSeverity(project.status)" />
    </div>

    <div v-if="project" class="project-summary">
      <div>
        <span class="caption">Start</span>
        <strong>{{ fmtDate(project.startTime) }}</strong>
      </div>
      <div>
        <span class="caption">End</span>
        <strong>{{ fmtDate(project.endTime) }}</strong>
      </div>
      <div>
        <span class="caption">Members</span>
        <strong>{{ project.memberCount }}</strong>
      </div>
      <div>
        <span class="caption">Tasks</span>
        <strong>{{ project.taskCount }}</strong>
      </div>
    </div>

    <div class="project-tabs" role="tablist" aria-label="Project detail sections">
      <button type="button" :class="{ active: activeTab === 'members' }" @click="activeTab = 'members'">
        Members <span>{{ members.length }}</span>
      </button>
      <button type="button" :class="{ active: activeTab === 'tasks' }" @click="activeTab = 'tasks'">
        Tasks <span>{{ tasks.length }}</span>
      </button>
    </div>

    <div v-if="activeTab === 'members'" class="content-card project-table-card">
      <div class="section-toolbar">
        <h3>Members</h3>
        <Button v-if="auth.isAdminOrHR" label="Add Member" icon="pi pi-user-plus" @click="openAddMember" />
      </div>
      <DataTable :value="members" :loading="loading || membersLoading" tableStyle="min-width: 960px" stripedRows>
        <template #empty>
          No members have been added to this project yet.
        </template>
        <Column field="fullName" header="Member">
          <template #body="{ data }">
            <div class="member-cell">
              <strong>{{ data.fullName }}</strong>
              <span>@{{ data.username }}</span>
            </div>
          </template>
        </Column>
        <Column field="email" header="Email" />
        <Column field="projectRoleLabel" header="Project Role" />
        <Column field="participationStartTime" header="Start">
          <template #body="{ data }">{{ fmtDate(data.participationStartTime) }}</template>
        </Column>
        <Column field="expectedEndTime" header="Expected End">
          <template #body="{ data }">{{ fmtDate(data.expectedEndTime) }}</template>
        </Column>
        <Column v-if="auth.isAdminOrHR" header="Actions" style="width:120px">
          <template #body="{ data }">
            <div class="table-actions">
              <Button icon="pi pi-pencil" text rounded severity="info" v-tooltip.top="'Edit member role'" @click="openEditMember(data)" />
              <Button icon="pi pi-times" text rounded severity="danger" v-tooltip.top="'Remove member'" @click="removeMember(data)" />
            </div>
          </template>
        </Column>
      </DataTable>
    </div>

    <div v-if="activeTab === 'tasks'" class="content-card project-table-card">
      <div class="section-toolbar">
        <h3>Tasks</h3>
        <Button
          v-if="auth.isAdminOrHR"
          label="Create Task"
          icon="pi pi-plus"
          :disabled="members.length === 0"
          v-tooltip.top="members.length === 0 ? 'Add a project member before creating tasks' : 'Create task'"
          @click="openCreateTask"
        />
      </div>
      <DataTable :value="tasks" :loading="loading || tasksLoading" tableStyle="min-width: 980px" stripedRows>
        <template #empty>
          No tasks have been created for this project yet.
        </template>
        <Column field="name" header="Task Name" />
        <Column field="description" header="Description">
          <template #body="{ data }">{{ data.description || '—' }}</template>
        </Column>
        <Column field="status" header="Status" style="width:150px">
          <template #body="{ data }">
            <Tag :value="data.statusLabel" :severity="getTaskStatusSeverity(data.status)" />
          </template>
        </Column>
        <Column header="Assignees">
          <template #body="{ data }">{{ formatTaskAssignees(data) }}</template>
        </Column>
        <Column field="startTime" header="Start">
          <template #body="{ data }">{{ fmtDate(data.startTime) }}</template>
        </Column>
        <Column field="deadline" header="Deadline">
          <template #body="{ data }">{{ fmtDate(data.deadline) }}</template>
        </Column>
        <Column v-if="auth.isAdminOrHR" header="Actions" style="width:120px">
          <template #body="{ data }">
            <div class="table-actions">
              <Button icon="pi pi-pencil" text rounded severity="info" v-tooltip.top="'Edit task'" @click="openEditTask(data)" />
              <Button icon="pi pi-trash" text rounded severity="danger" v-tooltip.top="'Archive task'" @click="archiveTask(data)" />
            </div>
          </template>
        </Column>
      </DataTable>
    </div>

    <Dialog v-model:visible="memberDialog" :header="memberEditing ? 'Edit Project Member' : 'Add Project Member'" modal :style="{ width: '520px' }">
      <div class="detail-form">
        <div class="form-field">
          <label class="required">Member</label>
          <Select
            v-model="memberForm.userId"
            :options="memberEditing ? availableUsers : memberOptionsForAdd"
            option-label="fullName"
            option-value="id"
            placeholder="Select member"
            filter
            :filter-fields="['username', 'fullName', 'email']"
            :loading="usersLoading"
            :disabled="memberEditing"
            fluid
          >
            <template #option="{ option }">
              <div>{{ option.fullName }} <span class="select-hint">(@{{ option.username }})</span></div>
            </template>
          </Select>
        </div>
        <div class="form-field">
          <label class="required">Project Role</label>
          <Select v-model="memberForm.projectRole" :options="projectRoleOptions" option-label="label" option-value="value" fluid />
        </div>
        <div class="form-field">
          <label class="required">Participation Start</label>
          <DatePicker v-model="memberStartDate" showTime hourFormat="24" :manualInput="false" fluid />
        </div>
        <div class="form-field">
          <label class="required">Expected End</label>
          <DatePicker v-model="memberEndDate" showTime hourFormat="24" :manualInput="false" fluid />
        </div>
      </div>
      <template #footer>
        <Button label="Cancel" text @click="memberDialog = false" />
        <Button :label="memberEditing ? 'Update' : 'Add'" icon="pi pi-check" :loading="memberSaving" :disabled="memberSaving || !canSubmitMember" @click="saveMember" />
      </template>
    </Dialog>

    <Dialog v-model:visible="taskDialog" :header="taskEditing ? 'Edit Task' : 'Create Task'" modal :style="{ width: '520px' }">
      <div class="detail-form">
        <div class="form-field">
          <label class="required">Task Name</label>
          <InputText v-model="taskForm.name" fluid />
        </div>
        <div class="form-field">
          <label>Description</label>
          <Textarea v-model="taskForm.description" rows="3" fluid />
        </div>
        <div class="form-field">
          <label class="required">Status</label>
          <Select v-model="taskForm.status" :options="taskStatusOptions" option-label="label" option-value="value" fluid />
        </div>
        <div class="form-field">
          <label class="required">Assignees</label>
          <MultiSelect
            v-model="taskForm.assigneeIds"
            :options="taskAssigneeOptions"
            option-label="fullName"
            option-value="id"
            placeholder="Select project members"
            filter
            display="chip"
            :filter-fields="['username', 'fullName', 'email']"
            fluid
          >
            <template #option="{ option }">
              <div>{{ option.fullName }} <span class="select-hint">(@{{ option.username }})</span></div>
            </template>
          </MultiSelect>
        </div>
        <div class="form-field">
          <label class="required">Start</label>
          <DatePicker v-model="taskStartDate" showTime hourFormat="24" :manualInput="false" fluid />
        </div>
        <div class="form-field">
          <label class="required">Deadline</label>
          <DatePicker v-model="taskDeadlineDate" showTime hourFormat="24" :manualInput="false" fluid />
        </div>
      </div>
      <template #footer>
        <Button label="Cancel" text @click="taskDialog = false" />
        <Button :label="taskEditing ? 'Update' : 'Create'" icon="pi pi-check" :loading="taskSaving" :disabled="taskSaving || !canSubmitTask" @click="saveTask" />
      </template>
    </Dialog>
  </div>
</template>

<style scoped>
.project-back-row {
  margin-bottom: var(--space-4);
}

.back-link {
  align-items: center;
  color: var(--theme-text-secondary);
  display: inline-flex;
  font-size: 14px;
  gap: 4px;
}

.back-link:hover {
  color: var(--theme-blue);
}

.project-header {
  align-items: flex-start;
}

.project-summary {
  border-bottom: 1px solid var(--theme-divider);
  border-top: 1px solid var(--theme-divider);
  display: grid;
  gap: var(--space-4);
  grid-template-columns: repeat(4, minmax(0, 1fr));
  margin-bottom: var(--space-5);
  padding: var(--space-4) 0;
}

.project-summary > div {
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-width: 0;
}

.project-summary strong {
  font-size: 15px;
  overflow-wrap: anywhere;
}

.project-tabs {
  align-items: center;
  border-bottom: 1px solid var(--theme-divider);
  display: flex;
  gap: var(--space-2);
  margin-bottom: var(--space-5);
}

.project-tabs button {
  align-items: center;
  background: transparent;
  border: 0;
  border-bottom: 2px solid transparent;
  color: var(--theme-text-secondary);
  cursor: pointer;
  display: inline-flex;
  font: inherit;
  font-size: 14px;
  font-weight: 600;
  gap: 8px;
  padding: 10px 12px;
}

.project-tabs button.active {
  border-bottom-color: var(--theme-blue);
  color: var(--theme-blue);
}

.project-tabs span {
  background: var(--theme-surface-light);
  border-radius: 999px;
  color: var(--theme-text-weak);
  font-size: 12px;
  padding: 2px 8px;
}

.project-table-card {
  overflow-x: auto;
  -webkit-overflow-scrolling: touch;
}

.section-toolbar {
  align-items: center;
  display: flex;
  justify-content: space-between;
  margin-bottom: var(--space-4);
  gap: var(--space-3);
}

.section-toolbar h3 {
  font-size: 20px;
}

.member-cell {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.member-cell span,
.select-hint {
  color: var(--theme-text-weak);
  font-size: 12px;
}

.table-actions {
  display: inline-flex;
  gap: 4px;
}

.detail-form {
  display: flex;
  flex-direction: column;
  gap: var(--space-4);
}

@media (max-width: 720px) {
  .project-summary {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .section-toolbar {
    align-items: flex-start;
    flex-direction: column;
  }
}
</style>
