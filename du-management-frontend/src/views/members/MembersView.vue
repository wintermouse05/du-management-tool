<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { membersApi } from '@/api/members'
import { rolesApi } from '@/api/roles'
import type { MemberResponse, MemberRequest, RoleResponse } from '@/types'
import { UserStatus } from '@/types'
import DataTable from 'primevue/datatable'
import Column from 'primevue/column'
import Button from 'primevue/button'
import Dialog from 'primevue/dialog'
import InputText from 'primevue/inputtext'
import Select from 'primevue/select'
import DatePicker from 'primevue/datepicker'
import Tag from 'primevue/tag'
import { useToast } from 'primevue/usetoast'

const toast = useToast()
const members = ref<MemberResponse[]>([])
const roles = ref<RoleResponse[]>([])
const totalRecords = ref(0)
const loading = ref(false)
const page = ref(0)
const rows = ref(10)
const dialogVisible = ref(false)
const editing = ref(false)
const editingId = ref<number | null>(null)
const form = ref<MemberRequest>({ roleId: 0, username: '', email: '', fullName: '', password: '' })

async function loadMembers() {
  loading.value = true
  try {
    const res = await membersApi.getAll({ page: page.value, size: rows.value })
    members.value = res.data.content; totalRecords.value = res.data.totalElements
  } finally { loading.value = false }
}

async function loadRoles() {
  try { const res = await rolesApi.getAll({ size: 100 }); roles.value = res.data.content } catch {}
}

function onPage(event: any) { page.value = event.page; rows.value = event.rows; loadMembers() }

function openCreate() {
  editing.value = false; editingId.value = null
  form.value = { roleId: roles.value[0]?.id || 0, username: '', email: '', fullName: '', password: '' }
  dialogVisible.value = true
}

function openEdit(m: MemberResponse) {
  editing.value = true; editingId.value = m.id
  form.value = { roleId: m.roleId, username: m.username, email: m.email, fullName: m.fullName, status: m.status }
  dialogVisible.value = true
}

async function save() {
  try {
    if (editing.value && editingId.value) { await membersApi.update(editingId.value, form.value) }
    else { await membersApi.create(form.value) }
    toast.add({ severity: 'success', summary: editing.value ? 'Member updated' : 'Member created', life: 3000 })
    dialogVisible.value = false; loadMembers()
  } catch (err: any) { toast.add({ severity: 'error', summary: 'Error', detail: err.response?.data?.message || 'Failed', life: 4000 }) }
}

async function deactivate(id: number) {
  try { await membersApi.deactivate(id); toast.add({ severity: 'warn', summary: 'Member deactivated', life: 3000 }); loadMembers()
  } catch (err: any) { toast.add({ severity: 'error', summary: 'Error', detail: err.response?.data?.message, life: 4000 }) }
}

function statusSeverity(s: UserStatus) { return s === UserStatus.ACTIVE ? 'success' : 'danger' }

onMounted(() => { loadMembers(); loadRoles() })
</script>

<template>
  <div class="page-container">
    <div class="page-header">
      <div><h2>Members</h2><p class="page-subtitle">Manage DU members and their roles</p></div>
      <Button label="Add Member" icon="pi pi-plus" @click="openCreate" />
    </div>
    <div class="content-card">
      <DataTable :value="members" :loading="loading" :paginator="true" :rows="rows" :totalRecords="totalRecords"
        :lazy="true" @page="onPage" :rowsPerPageOptions="[10,20,50]" stripedRows>
        <Column field="fullName" header="Name" />
        <Column field="username" header="Username" />
        <Column field="email" header="Email" />
        <Column field="roleName" header="Role">
          <template #body="{ data }"><Tag :value="data.roleName" :severity="data.roleName === 'ADMIN' ? 'danger' : data.roleName === 'HR' ? 'warn' : 'info'" /></template>
        </Column>
        <Column field="totalPoints" header="Points" />
        <Column field="status" header="Status">
          <template #body="{ data }"><Tag :value="data.status" :severity="statusSeverity(data.status)" /></template>
        </Column>
        <Column header="Actions" style="width: 160px">
          <template #body="{ data }">
            <div class="flex-end">
              <Button icon="pi pi-pencil" text rounded severity="info" @click="openEdit(data)" />
              <Button v-if="data.status === 'ACTIVE'" icon="pi pi-ban" text rounded severity="danger" @click="deactivate(data.id)" />
            </div>
          </template>
        </Column>
      </DataTable>
    </div>

    <Dialog v-model:visible="dialogVisible" :header="editing ? 'Edit Member' : 'Add Member'" modal :style="{ width: '520px' }">
      <div class="form-grid">
        <div class="form-field"><label>Role</label>
          <Select v-model="form.roleId" :options="roles" optionLabel="name" optionValue="id" placeholder="Select role" fluid />
        </div>
        <div class="form-field"><label>Username</label><InputText v-model="form.username" :disabled="editing" fluid /></div>
        <div class="form-field"><label>Email</label><InputText v-model="form.email" fluid /></div>
        <div class="form-field"><label>Full Name</label><InputText v-model="form.fullName" fluid /></div>
        <div v-if="!editing" class="form-field"><label>Password</label><InputText v-model="form.password" type="password" fluid /></div>
        <div v-if="editing" class="form-field"><label>Status</label>
          <Select v-model="form.status" :options="[{l:'Active',v:UserStatus.ACTIVE},{l:'Inactive',v:UserStatus.INACTIVE}]" optionLabel="l" optionValue="v" fluid />
        </div>
      </div>
      <template #footer><Button label="Cancel" text @click="dialogVisible = false" /><Button :label="editing ? 'Update' : 'Create'" icon="pi pi-check" @click="save" /></template>
    </Dialog>
  </div>
</template>
