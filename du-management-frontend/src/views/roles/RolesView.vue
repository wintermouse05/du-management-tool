<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { rolesApi } from '@/api/roles'
import type { RoleResponse } from '@/types'
import DataTable from 'primevue/datatable'
import Column from 'primevue/column'
import Button from 'primevue/button'
import Dialog from 'primevue/dialog'
import InputText from 'primevue/inputtext'
import { useToast } from 'primevue/usetoast'

const toast = useToast()
const roles = ref<RoleResponse[]>([])
const loading = ref(false)
const dialog = ref(false); const editing = ref(false); const editId = ref<number|null>(null)
const form = ref({ name: '', description: '' })

async function load() { loading.value = true; try { const r = await rolesApi.getAll({ size: 100 }); roles.value = r.data.content } finally { loading.value = false } }
function openCreate() { editing.value = false; editId.value = null; form.value = { name: '', description: '' }; dialog.value = true }
function openEdit(r: RoleResponse) { editing.value = true; editId.value = r.id; form.value = { name: r.name, description: r.description || '' }; dialog.value = true }

async function save() {
  try {
    if (editing.value && editId.value) await rolesApi.update(editId.value, form.value)
    else await rolesApi.create(form.value)
    toast.add({ severity:'success', summary:'Saved', life:2000 }); dialog.value = false; load()
  } catch (e: any) { toast.add({ severity:'error', summary:'Error', detail: e.response?.data?.message, life:3000 }) }
}

async function remove(id: number) {
  try { await rolesApi.delete(id); toast.add({ severity:'warn', summary:'Role deleted', life:2000 }); load()
  } catch (e: any) { toast.add({ severity:'error', summary:'Error', detail: e.response?.data?.message, life:3000 }) }
}

onMounted(load)
</script>

<template>
  <div class="page-container">
    <div class="page-header">
      <div><h2>Roles</h2><p class="page-subtitle">Manage system roles (Admin only)</p></div>
      <Button label="Add Role" icon="pi pi-plus" @click="openCreate" />
    </div>
    <div class="content-card">
      <DataTable :value="roles" :loading="loading" stripedRows>
        <Column field="name" header="Name" /><Column field="description" header="Description" />
        <Column header="Actions" style="width:140px">
          <template #body="{data}">
            <Button icon="pi pi-pencil" text rounded severity="info" @click="openEdit(data)" />
            <Button icon="pi pi-trash" text rounded severity="danger" @click="remove(data.id)" />
          </template>
        </Column>
      </DataTable>
    </div>
    <Dialog v-model:visible="dialog" :header="editing?'Edit Role':'Add Role'" modal :style="{width:'380px'}">
      <div style="display:flex;flex-direction:column;gap:var(--space-4);">
        <div class="form-field"><label>Name</label><InputText v-model="form.name" fluid /></div>
        <div class="form-field"><label>Description</label><InputText v-model="form.description" fluid /></div>
      </div>
      <template #footer><Button label="Cancel" text @click="dialog=false" /><Button :label="editing?'Update':'Create'" icon="pi pi-check" @click="save" /></template>
    </Dialog>
  </div>
</template>
