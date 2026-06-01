<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { groupsApi } from '@/api/groups'
import { membersApi } from '@/api/members'
import type { GroupResponse, GroupRequest, GroupMemberResponse, MemberResponse } from '@/types'
import { getApiErrorDetail } from '@/utils/apiError'
import DataTable from 'primevue/datatable'
import Column from 'primevue/column'
import Button from 'primevue/button'
import Dialog from 'primevue/dialog'
import InputText from 'primevue/inputtext'
import Textarea from 'primevue/textarea'
import Checkbox from 'primevue/checkbox'
import Select from 'primevue/select'
import Tag from 'primevue/tag'
import { useToast } from 'primevue/usetoast'

const toast = useToast()

const groups = ref<GroupResponse[]>([])
const loading = ref(false)

const dialogVisible = ref(false)
const editing = ref(false)
const editId = ref<number | null>(null)
const form = ref<GroupRequest>({ name: '', description: '', allGroup: false })

const membersDialog = ref(false)
const selectedGroup = ref<GroupResponse | null>(null)
const members = ref<GroupMemberResponse[]>([])
const membersLoading = ref(false)

const addMemberDialog = ref(false)
const availableUsers = ref<MemberResponse[]>([])
const selectedUserId = ref<number | null>(null)

const deleteConfirmDialog = ref(false)
const deleteTargetId = ref<number | null>(null)

async function load() {
  loading.value = true
  try {
    const res = await groupsApi.getAll()
    groups.value = res.data
  } catch (err: any) {
    toast.add({ severity: 'error', summary: 'Error', detail: getApiErrorDetail(err, 'Failed to load groups'), life: 3000 })
  } finally {
    loading.value = false
  }
}

function openCreate() {
  editing.value = false
  editId.value = null
  form.value = { name: '', description: '', allGroup: false }
  dialogVisible.value = true
}

function openEdit(g: GroupResponse) {
  editing.value = true
  editId.value = g.id
  form.value = { name: g.name, description: g.description || '', allGroup: g.allGroup }
  dialogVisible.value = true
}

async function save() {
  try {
    if (editing.value && editId.value) {
      await groupsApi.update(editId.value, form.value)
      toast.add({ severity: 'success', summary: 'Group updated', life: 2000 })
    } else {
      await groupsApi.create(form.value)
      toast.add({ severity: 'success', summary: 'Group created', life: 2000 })
    }
    dialogVisible.value = false
    load()
  } catch (err: any) {
    toast.add({ severity: 'error', summary: 'Error', detail: getApiErrorDetail(err, 'Failed to save group'), life: 3000 })
  }
}

function confirmDelete(id: number) {
  deleteTargetId.value = id
  deleteConfirmDialog.value = true
}

async function doDelete() {
  if (!deleteTargetId.value) return
  try {
    await groupsApi.delete(deleteTargetId.value)
    toast.add({ severity: 'success', summary: 'Group archived', life: 2000 })
    deleteConfirmDialog.value = false
    load()
  } catch (err: any) {
    toast.add({ severity: 'error', summary: 'Error', detail: getApiErrorDetail(err), life: 3000 })
  }
}

async function openMembers(g: GroupResponse) {
  selectedGroup.value = g
  membersDialog.value = true
  membersLoading.value = true
  try {
    const res = await groupsApi.getMembers(g.id)
    members.value = res.data
  } catch (err: any) {
    toast.add({ severity: 'error', summary: 'Error', detail: getApiErrorDetail(err), life: 3000 })
  } finally {
    membersLoading.value = false
  }
}

async function openAddMember() {
  addMemberDialog.value = true
  selectedUserId.value = null
  try {
    const res = await membersApi.search({ size: 1000 })
    availableUsers.value = res.data.content.filter(u => u.status === 'ACTIVE')
  } catch {}
}

async function addMember() {
  if (!selectedUserId.value || !selectedGroup.value) return
  try {
    await groupsApi.addMember(selectedGroup.value.id, selectedUserId.value)
    toast.add({ severity: 'success', summary: 'Member added', life: 2000 })
    addMemberDialog.value = false
    openMembers(selectedGroup.value)
  } catch (err: any) {
    toast.add({ severity: 'error', summary: 'Error', detail: getApiErrorDetail(err), life: 3000 })
  }
}

async function removeMember(userId: number) {
  if (!selectedGroup.value) return
  try {
    await groupsApi.removeMember(selectedGroup.value.id, userId)
    toast.add({ severity: 'success', summary: 'Member removed', life: 2000 })
    openMembers(selectedGroup.value)
  } catch (err: any) {
    toast.add({ severity: 'error', summary: 'Error', detail: getApiErrorDetail(err), life: 3000 })
  }
}

onMounted(load)
</script>

<template>
  <div class="page-container">
    <div class="page-header">
      <div>
        <h2>Groups</h2>
        <p class="page-subtitle">Manage user groups for batch operations</p>
      </div>
      <Button label="Create Group" icon="pi pi-plus" @click="openCreate" />
    </div>

    <div class="content-card">
      <DataTable :value="groups" :loading="loading" stripedRows>
        <template #empty>
          No Group has been created yet. Please create a new Group.
        </template>
        <Column field="name" header="Name" />
        <Column field="description" header="Description">
          <template #body="{ data }">{{ data.description || '-' }}</template>
        </Column>
        <Column header="Type" style="width:120px">
          <template #body="{ data }">
            <Tag :value="data.allGroup ? 'All Users' : 'Custom'" :severity="data.allGroup ? 'info' : 'secondary'" />
          </template>
        </Column>
        <Column field="memberCount" header="Members" style="width:100px" />
        <Column header="Actions" style="width:200px">
          <template #body="{ data }">
            <div style="display:flex;gap:4px;">
              <Button icon="pi pi-users" text rounded v-tooltip="'Members'" @click="openMembers(data)" />
              <Button icon="pi pi-pencil" text rounded severity="info" @click="openEdit(data)" />
              <Button icon="pi pi-trash" text rounded severity="danger" @click="confirmDelete(data.id)" />
            </div>
          </template>
        </Column>
      </DataTable>
    </div>

    <Dialog v-model:visible="dialogVisible" :header="editing ? 'Edit Group' : 'Create Group'" modal :style="{ width: '480px' }">
      <div style="display:flex;flex-direction:column;gap:var(--space-4);">
        <div class="form-field">
          <label class="required">Name</label>
          <InputText v-model="form.name" placeholder="e.g. Engineering, All" fluid />
        </div>
        <div class="form-field">
          <label>Description <span class="optional-hint">(optional)</span></label>
          <Textarea v-model="form.description" rows="3" placeholder="Optional description" fluid />
        </div>
        <div class="form-field" style="display:flex;align-items:center;gap:var(--space-3);">
          <Checkbox v-model="form.allGroup" :binary="true" input-id="allGroup" />
          <label for="allGroup">All Users Group  Edynamically includes every active user</label>
        </div>
      </div>
      <template #footer>
        <Button label="Cancel" text @click="dialogVisible = false" />
        <Button :label="editing ? 'Update' : 'Create'" icon="pi pi-check" @click="save" />
      </template>
    </Dialog>

    <Dialog v-model:visible="membersDialog" header="Group Members" modal :style="{ width: '600px' }">
      <div style="display:flex;flex-direction:column;gap:var(--space-4);">
        <div style="display:flex;justify-content:space-between;align-items:center;">
          <span class="caption">{{ members.length }} member(s)</span>
          <Button
            v-if="selectedGroup && !selectedGroup.allGroup"
            label="Add Member"
            icon="pi pi-user-plus"
            size="small"
            @click="openAddMember"
          />
        </div>
        <DataTable :value="members" :loading="membersLoading" stripedRows>
          <template #empty>
            No members in this Group yet. Please add a member.
          </template>
          <Column field="username" header="Username" />
          <Column field="fullName" header="Full Name" />
          <Column field="email" header="Email" />
          <Column v-if="selectedGroup && !selectedGroup.allGroup" header="" style="width:60px">
            <template #body="{ data }">
              <Button icon="pi pi-times" text rounded severity="danger" size="small" @click="removeMember(data.id)" />
            </template>
          </Column>
        </DataTable>
      </div>
      <template #footer>
        <Button label="Close" text @click="membersDialog = false" />
      </template>
    </Dialog>

    <Dialog v-model:visible="addMemberDialog" header="Add Member" modal :style="{ width: '400px' }">
      <div class="form-field">
        <label class="required">User</label>
        <Select
          v-model="selectedUserId"
          :options="availableUsers"
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
      <template #footer>
        <Button label="Cancel" text @click="addMemberDialog = false" />
        <Button label="Add" icon="pi pi-check" @click="addMember" :disabled="!selectedUserId" />
      </template>
    </Dialog>

    <Dialog v-model:visible="deleteConfirmDialog" header="Archive Group" modal :style="{ width: '360px' }">
      <p>Archive this group and hide it from group lists?</p>
      <template #footer>
        <Button label="Cancel" text @click="deleteConfirmDialog = false" />
        <Button label="Archive" severity="danger" icon="pi pi-trash" @click="doDelete" />
      </template>
    </Dialog>
  </div>
</template>
