<script setup lang="ts">
import { computed, ref, watch, onMounted } from 'vue'
import { useAuthStore } from '@/stores/auth'
import { membersApi } from '@/api/members'
import { rolesApi } from '@/api/roles'
import type { MemberResponse, MemberRequest, MemberSkillRequest, RoleResponse } from '@/types'
import { MemberSkillType, UserStatus } from '@/types'
import DataTable from 'primevue/datatable'
import Column from 'primevue/column'
import Button from 'primevue/button'
import Dialog from 'primevue/dialog'
import InputText from 'primevue/inputtext'
import Select from 'primevue/select'
import DatePicker from 'primevue/datepicker'
import Tag from 'primevue/tag'
import { useToast } from 'primevue/usetoast'
import { parseApiDate, toLocalDate } from '@/utils/datetime'
import { getApiErrorDetail } from '@/utils/apiError'
import { formatMemberName } from '@/utils/memberDisplay'

const auth = useAuthStore()
const toast = useToast()
const PASSWORD_POLICY_MESSAGE = 'Password must be 8-128 characters and include uppercase, lowercase, number, and special character.'
const PASSWORD_POLICY_REGEX = /^(?=\S{8,128}$)(?=.*[a-z])(?=.*[A-Z])(?=.*\d)(?=.*[!"#$%&'()*+,\-./:;<=>?@[\\\]^_`{|}~]).*$/
const skillOptions = [
  { label: 'Backend Developer', value: MemberSkillType.BACKEND_DEVELOPER },
  { label: 'Business Analyst', value: MemberSkillType.BUSINESS_ANALYST },
  { label: 'DevOps Engineer', value: MemberSkillType.DEVOPS_ENGINEER },
  { label: 'Flutter Developer', value: MemberSkillType.FLUTTER_DEVELOPER },
  { label: 'Frontend Developer', value: MemberSkillType.FRONTEND_DEVELOPER },
  { label: 'Project Manager', value: MemberSkillType.PROJECT_MANAGER },
  { label: 'QA Engineer', value: MemberSkillType.QA_ENGINEER },
  { label: 'Quality Control', value: MemberSkillType.QUALITY_CONTROL },
  { label: 'Team Lead', value: MemberSkillType.TEAM_LEAD },
  { label: 'Tech Lead', value: MemberSkillType.TECH_LEAD },
  { label: 'UI/UX Designer', value: MemberSkillType.UI_UX_DESIGNER },
  { label: 'Xamarin Developer', value: MemberSkillType.XAMARIN_DEVELOPER },
]
const skillLevelOptions = [
  { label: '1 - Beginner', value: 1 },
  { label: '2 - Familiar', value: 2 },
  { label: '3 - Proficient', value: 3 },
  { label: '4 - Advanced', value: 4 },
  { label: '5 - Expert', value: 5 },
]
const members = ref<MemberResponse[]>([])
const roles = ref<RoleResponse[]>([])
const totalRecords = ref(0)
const loading = ref(false)
const page = ref(1)
const rows = ref(10)
const searchQuery = ref('')
type MemberStatusFilter = UserStatus | 'ALL'
const statusFilter = ref<MemberStatusFilter>('ALL')
const statusFilterOptions = computed<Array<{ label: string; value: MemberStatusFilter }>>(() => {
  const options: Array<{ label: string; value: MemberStatusFilter }> = [
    { label: 'All', value: 'ALL' },
    { label: 'Active', value: UserStatus.ACTIVE },
  ]

  if (auth.isAdmin) {
    options.push({ label: 'Inactive', value: UserStatus.INACTIVE })
  }

  return options
})
const dialogVisible = ref(false)
const editing = ref(false)
const editingId = ref<number | null>(null)
const deactivateConfirmDialog = ref(false)
const deactivateTarget = ref<MemberResponse | null>(null)
const deactivating = ref(false)
const form = ref<MemberRequest>({ roleId: 0, username: '', email: '', fullName: '', password: '', dob: null, joinDate: null, skills: [] })
const formDob = ref<Date | null>(null)
const formJoinDate = ref<Date | null>(null)
const importInput = ref<HTMLInputElement | null>(null)

// Debounce timer for instant search
let debounceTimer: ReturnType<typeof setTimeout> | null = null

async function loadMembers() {
  loading.value = true
  try {
    const resolvedStatus = statusFilter.value === 'ALL' ? undefined : statusFilter.value
    const res = await membersApi.search({
      page: page.value,
      size: rows.value,
      q: searchQuery.value.trim() || undefined,
      status: resolvedStatus,
    })
    members.value = res.data.content; totalRecords.value = res.data.totalElements
  } finally { loading.value = false }
}

async function loadRoles() {
  try { const res = await rolesApi.getAll({ size: 100 }); roles.value = res.data.content } catch {}
}

function onPage(event: any) { page.value = event.page + 1; rows.value = event.rows; loadMembers() }

// Instant search: debounced watch on searchQuery
// Triggers when query is empty (reset) or >= 2 characters
watch(searchQuery, (newVal) => {
  if (debounceTimer) clearTimeout(debounceTimer)
  const trimmed = newVal.trim()
  // Only search when empty (show all) or >= 2 chars to avoid overly broad queries
  if (trimmed.length === 0 || trimmed.length >= 2) {
    debounceTimer = setTimeout(() => {
      page.value = 1
      loadMembers()
    }, 300)
  }
})

// Instant filter: status change triggers immediate fetch
watch(statusFilter, () => {
  page.value = 1
  loadMembers()
})

watch(() => auth.isAdmin, (isAdmin) => {
  if (isAdmin) {
    return
  }

  if (statusFilter.value === UserStatus.INACTIVE) {
    statusFilter.value = 'ALL'
    return
  }

  page.value = 1
  loadMembers()
})

function clearFilters() {
  searchQuery.value = ''
  statusFilter.value = 'ALL'
  // The watchers will handle loading automatically
}

function openCreate() {
  editing.value = false; editingId.value = null
  form.value = { roleId: roles.value[0]?.id || 0, username: '', email: '', fullName: '', password: '', dob: null, joinDate: null, skills: [] }
  formDob.value = null
  formJoinDate.value = null
  dialogVisible.value = true
}

function openEdit(m: MemberResponse) {
  editing.value = true; editingId.value = m.id
  form.value = {
    roleId: m.roleId,
    username: m.username,
    email: m.email,
    fullName: m.fullName,
    status: m.status,
    dob: m.dob,
    joinDate: m.joinDate,
    skills: cloneSkills(m.skills),
  }
  formDob.value = parseApiDate(m.dob)
  formJoinDate.value = parseApiDate(m.joinDate)
  dialogVisible.value = true
}

function cloneSkills(skills?: MemberResponse['skills']): MemberSkillRequest[] {
  return (skills ?? []).map((skill) => ({ skill: skill.skill, level: skill.level }))
}

function selectedSkillValues() {
  return new Set((form.value.skills ?? []).map((skill) => skill.skill))
}

function availableSkillOptions(index: number) {
  const currentSkill = form.value.skills?.[index]?.skill
  const selectedSkills = selectedSkillValues()
  return skillOptions.filter((option) => option.value === currentSkill || !selectedSkills.has(option.value))
}

function addSkill() {
  if (!form.value.skills) {
    form.value.skills = []
  }
  const selectedSkills = selectedSkillValues()
  const nextSkill = skillOptions.find((option) => !selectedSkills.has(option.value))
  if (!nextSkill) {
    return
  }
  form.value.skills.push({ skill: nextSkill.value, level: 3 })
}

function removeSkill(index: number) {
  form.value.skills?.splice(index, 1)
}

function skillLevelTag(level: number) {
  return `L${level}`
}

async function save() {
  if (formDob.value) form.value.dob = toLocalDate(formDob.value)
  else form.value.dob = null
  if (formJoinDate.value) form.value.joinDate = toLocalDate(formJoinDate.value)
  else form.value.joinDate = null
  form.value.skills = (form.value.skills ?? []).filter((skill) => skill.skill && skill.level)
  if (!editing.value) {
    const password = form.value.password ?? ''
    if (!PASSWORD_POLICY_REGEX.test(password)) {
      toast.add({ severity: 'error', summary: 'Error', detail: PASSWORD_POLICY_MESSAGE, life: 4000 })
      return
    }
  }
  try {
    if (editing.value && editingId.value) { await membersApi.update(editingId.value, form.value) }
    else { await membersApi.create(form.value) }
    toast.add({ severity: 'success', summary: editing.value ? 'Member updated' : 'Member created', life: 3000 })
    dialogVisible.value = false; loadMembers()
  } catch (err: any) { toast.add({ severity: 'error', summary: 'Error', detail: getApiErrorDetail(err, 'Failed'), life: 4000 }) }
}

function openDeactivateConfirm(member: MemberResponse) {
  deactivateTarget.value = member
  deactivateConfirmDialog.value = true
}

async function confirmDeactivate() {
  if (!deactivateTarget.value) return
  deactivating.value = true
  try {
    await membersApi.deactivate(deactivateTarget.value.id)
    toast.add({ severity: 'warn', summary: 'Member deactivated', life: 3000 })
    deactivateConfirmDialog.value = false
    deactivateTarget.value = null
    loadMembers()
  } catch (err: any) {
    toast.add({ severity: 'error', summary: 'Error', detail: getApiErrorDetail(err), life: 4000 })
  } finally {
    deactivating.value = false
  }
}

function triggerImportPicker() {
  importInput.value?.click()
}

async function handleImport(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) {
    return
  }

  try {
    const res = await membersApi.importFile(file)
    toast.add({ severity: 'success', summary: 'Import completed', detail: `Imported ${res.data.imported} members`, life: 3000 })
    loadMembers()
  } catch (err: any) {
    toast.add({ severity: 'error', summary: 'Import failed', detail: getApiErrorDetail(err, 'Failed to import file'), life: 4000 })
  } finally {
    input.value = ''
  }
}

async function exportCsv() {
  try {
    const resolvedStatus = statusFilter.value === 'ALL' ? undefined : statusFilter.value
    const res = await membersApi.exportCsv({
      q: searchQuery.value.trim() || undefined,
      status: resolvedStatus,
    })
    const blob = new Blob([res.data], { type: 'text/csv;charset=utf-8' })
    const link = document.createElement('a')
    link.href = URL.createObjectURL(blob)
    link.download = 'members.csv'
    link.click()
    URL.revokeObjectURL(link.href)
  } catch (err: any) {
    toast.add({ severity: 'error', summary: 'Export failed', detail: getApiErrorDetail(err, 'Unable to export members'), life: 4000 })
  }
}

function statusSeverity(s: UserStatus) { return s === UserStatus.ACTIVE ? 'success' : 'danger' }

function formatTenure(months: number | null) {
  if (months == null) {
    return '-'
  }
  if (months <= 12) {
    return `${months} ${months === 1 ? 'month' : 'months'}`
  }

  const years = Math.floor(months / 12)
  const remainingMonths = months % 12
  const parts = [`${years} ${years === 1 ? 'year' : 'years'}`]
  if (remainingMonths > 0) {
    parts.push(`${remainingMonths} ${remainingMonths === 1 ? 'month' : 'months'}`)
  }
  return parts.join(' ')
}

onMounted(() => { loadMembers(); loadRoles() })
</script>

<template>
  <div class="page-container">
    <div class="page-header">
      <div><h2>Members</h2><p class="page-subtitle">Manage DU members and their roles</p></div>
      <div v-if="auth.isAdminOrHR" style="display:flex;gap:8px;">
        <Button label="Import" icon="pi pi-upload" severity="secondary" outlined @click="triggerImportPicker" />
        <Button label="Export" icon="pi pi-download" severity="secondary" outlined @click="exportCsv" />
        <Button label="Add Member" icon="pi pi-plus" @click="openCreate" />
      </div>
    </div>
    <input
      ref="importInput"
      type="file"
      accept=".csv,.xlsx"
      style="display:none"
      @change="handleImport"
    />
    <div class="content-card">
      <div style="display:flex;gap:8px;align-items:end;margin-bottom:var(--space-4);flex-wrap:wrap;">
        <div class="form-field" style="min-width:260px;margin:0;">
          <label>Search</label>
          <InputText v-model="searchQuery" placeholder="Name (min 2 chars)" fluid />
        </div>
        <div class="form-field" style="min-width:180px;margin:0;">
          <label>Status</label>
          <Select
            v-model="statusFilter"
            :options="statusFilterOptions"
            optionLabel="label"
            optionValue="value"
            fluid
          />
        </div>
        <Button label="Clear" icon="pi pi-times" text @click="clearFilters" />
      </div>
      <DataTable :value="members" :loading="loading" :paginator="true" :rows="rows" :first="(page - 1) * rows" :totalRecords="totalRecords"
        :lazy="true" @page="onPage" :rowsPerPageOptions="[10,20,50]" stripedRows>
        <template #empty>
          No Member found. Please adjust filters or create a new Member.
        </template>
        <Column field="fullName" header="Name">
          <template #body="{ data }">{{ formatMemberName(data) }}</template>
        </Column>
        <Column field="roleName" header="Role">
          <template #body="{ data }"><Tag :value="data.roleName" :severity="data.roleName === 'ADMIN' ? 'danger' : data.roleName === 'HR' ? 'warn' : 'info'" /></template>
        </Column>
        <Column header="Skills" style="min-width: 240px">
          <template #body="{ data }">
            <div v-if="data.skills?.length" class="member-skill-tags">
              <Tag
                v-for="skill in data.skills.slice(0, 3)"
                :key="skill.skill"
                :value="`${skill.skillLabel} ${skillLevelTag(skill.level)}`"
                severity="info"
              />
              <Tag v-if="data.skills.length > 3" :value="`+${data.skills.length - 3}`" severity="secondary" />
            </div>
            <span v-else class="caption">-</span>
          </template>
        </Column>
        <Column field="tenureMonths" header="Tenure">
          <template #body="{ data }">{{ formatTenure(data.tenureMonths) }}</template>
        </Column>
        <Column field="totalPoints" header="Points" />
        <Column field="status" header="Status">
          <template #body="{ data }"><Tag :value="data.status" :severity="statusSeverity(data.status)" /></template>
        </Column>
        <Column v-if="auth.isAdminOrHR" header="Actions" style="width: 200px">
          <template #body="{ data }">
            <div class="flex-end">
              <Button icon="pi pi-pencil" text rounded severity="info" @click="openEdit(data)" />
              <Button v-if="data.status === 'ACTIVE'" icon="pi pi-ban" text rounded severity="danger" @click="openDeactivateConfirm(data)" />
            </div>
          </template>
        </Column>
      </DataTable>
    </div>

    <Dialog v-model:visible="dialogVisible" :header="editing ? 'Edit Member' : 'Add Member'" modal :style="{ width: '720px', maxWidth: '95vw' }">
      <div class="form-grid">
        <div class="form-field"><label class="required">Role</label>
          <Select v-model="form.roleId" :options="roles" optionLabel="name" optionValue="id" placeholder="Select role" fluid />
        </div>
        <div class="form-field"><label class="required">Username</label><InputText v-model="form.username" :disabled="editing" fluid /></div>
        <div class="form-field"><label class="required">Email</label><InputText v-model="form.email" fluid /></div>
        <div class="form-field"><label class="required">Full Name</label><InputText v-model="form.fullName" fluid /></div>
        <div v-if="!editing" class="form-field"><label class="required">Password</label><InputText v-model="form.password" type="password" placeholder="8-128 chars, upper/lower/number/special" fluid /></div>
        <div class="form-field"><label>Date of Birth <span class="optional-hint">(optional)</span></label><DatePicker v-model="formDob" fluid /></div>
        <div class="form-field"><label>Join Date <span class="optional-hint">(optional)</span></label><DatePicker v-model="formJoinDate" fluid /></div>
        <div v-if="editing" class="form-field"><label>Status <span class="optional-hint">(optional)</span></label>
          <Select v-model="form.status" :options="[{l:'Active',v:UserStatus.ACTIVE},{l:'Inactive',v:UserStatus.INACTIVE}]" optionLabel="l" optionValue="v" fluid />
        </div>
        <div class="form-field form-field-full">
          <div class="member-skill-editor-header">
            <label>Skills <span class="optional-hint">(optional)</span></label>
            <Button
              icon="pi pi-plus"
              text
              rounded
              size="small"
              v-tooltip="'Add skill'"
              :disabled="(form.skills?.length || 0) >= skillOptions.length"
              @click="addSkill"
            />
          </div>
          <div v-if="form.skills?.length" class="member-skill-editor-list">
            <div v-for="(memberSkill, index) in form.skills" :key="index" class="member-skill-editor-row">
              <Select
                v-model="memberSkill.skill"
                :options="availableSkillOptions(index)"
                optionLabel="label"
                optionValue="value"
                placeholder="Skill"
                fluid
              />
              <Select
                v-model="memberSkill.level"
                :options="skillLevelOptions"
                optionLabel="label"
                optionValue="value"
                placeholder="Level"
                fluid
              />
              <Button
                icon="pi pi-trash"
                text
                rounded
                severity="danger"
                v-tooltip="'Remove skill'"
                @click="removeSkill(index)"
              />
            </div>
          </div>
          <span v-else class="caption">No skills added</span>
        </div>
      </div>
      <template #footer><Button label="Cancel" text @click="dialogVisible = false" /><Button :label="editing ? 'Update' : 'Create'" icon="pi pi-check" @click="save" /></template>
    </Dialog>

    <Dialog v-model:visible="deactivateConfirmDialog" header="Deactivate Member" modal :style="{ width: '380px' }">
      <p style="margin:0;">
        Deactivate {{ deactivateTarget?.fullName || 'this member' }} and hide them from normal member lists?
      </p>
      <template #footer>
        <Button label="Cancel" text @click="deactivateConfirmDialog = false" />
        <Button label="Deactivate" severity="danger" icon="pi pi-ban" :loading="deactivating" @click="confirmDeactivate" />
      </template>
    </Dialog>

  </div>
</template>

<style scoped>
.member-skill-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  max-width: 320px;
}

.member-skill-editor-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.member-skill-editor-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.member-skill-editor-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(150px, 180px) 42px;
  gap: 8px;
  align-items: center;
}

@media (max-width: 640px) {
  .member-skill-editor-row {
    grid-template-columns: 1fr;
  }
}
</style>
