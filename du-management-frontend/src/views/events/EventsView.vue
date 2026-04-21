<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { eventsApi } from '@/api/events'
import type { EventResponse, EventRequest } from '@/types'
import { RsvpStatus } from '@/types'
import DataTable from 'primevue/datatable'
import Column from 'primevue/column'
import Button from 'primevue/button'
import Dialog from 'primevue/dialog'
import InputText from 'primevue/inputtext'
import DatePicker from 'primevue/datepicker'
import { useToast } from 'primevue/usetoast'

const router = useRouter()
const auth = useAuthStore()
const toast = useToast()
const events = ref<EventResponse[]>([])
const totalRecords = ref(0)
const loading = ref(false)
const pg = ref(0)
const rows = ref(10)
const dialogVisible = ref(false)
const editing = ref(false)
const editId = ref<number | null>(null)
const form = ref<EventRequest>({ name: '', eventDate: '', location: '' })
const formDate = ref<Date | null>(null)

async function load() {
  loading.value = true
  try { const r = await eventsApi.getAll({ page: pg.value, size: rows.value }); events.value = r.data.content; totalRecords.value = r.data.totalElements }
  finally { loading.value = false }
}

function onPage(e: any) { pg.value = e.page; rows.value = e.rows; load() }
function openCreate() { editing.value = false; editId.value = null; form.value = { name: '', eventDate: '', location: '' }; formDate.value = null; dialogVisible.value = true }
function openEdit(ev: EventResponse) { editing.value = true; editId.value = ev.id; form.value = { name: ev.name, eventDate: ev.eventDate, location: ev.location || '' }; formDate.value = ev.eventDate ? new Date(ev.eventDate) : null; dialogVisible.value = true }

async function save() {
  if (formDate.value) form.value.eventDate = formDate.value.toISOString()
  try {
    if (editing.value && editId.value) await eventsApi.update(editId.value, form.value)
    else await eventsApi.create(form.value)
    toast.add({ severity: 'success', summary: editing.value ? 'Event updated' : 'Event created', life: 3000 })
    dialogVisible.value = false; load()
  } catch (err: any) { toast.add({ severity: 'error', summary: 'Error', detail: err.response?.data?.message || 'Failed', life: 4000 }) }
}

async function rsvp(eventId: number, status: RsvpStatus) {
  if (!auth.userId) {
    toast.add({ severity: 'error', summary: 'Cannot RSVP', detail: 'Missing user identity. Please log in again.', life: 3000 })
    return
  }
  try {
    await eventsApi.rsvp(eventId, { userId: auth.userId, rsvpStatus: status })
    toast.add({ severity: 'success', summary: `RSVP ${status}`, life: 2000 })
  } catch (err: any) {
    toast.add({ severity: 'error', summary: 'Error', detail: err.response?.data?.message || 'RSVP failed', life: 3000 })
  }
}

function formatDate(d: string) { return d ? new Date(d).toLocaleDateString('en-US', { year: 'numeric', month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit' }) : '' }

onMounted(load)
</script>

<template>
  <div class="page-container">
    <div class="page-header">
      <div><h2>Events</h2><p class="page-subtitle">Manage DU events and activities</p></div>
      <Button v-if="auth.isAdminOrHR" label="Create Event" icon="pi pi-plus" @click="openCreate" />
    </div>
    <div class="content-card">
      <DataTable :value="events" :loading="loading" :paginator="true" :rows="rows" :totalRecords="totalRecords" :lazy="true" @page="onPage" stripedRows>
        <Column field="name" header="Event Name">
          <template #body="{ data }"><router-link :to="`/events/${data.id}`" class="table-link">{{ data.name }}</router-link></template>
        </Column>
        <Column field="eventDate" header="Date"><template #body="{ data }">{{ formatDate(data.eventDate) }}</template></Column>
        <Column field="location" header="Location" />
        <Column header="RSVP" style="width: 190px">
          <template #body="{ data }">
            <div style="display:flex;gap:4px;">
              <Button label="Yes" size="small" severity="success" text @click="rsvp(data.id, RsvpStatus.YES)" />
              <Button label="Maybe" size="small" severity="warn" text @click="rsvp(data.id, RsvpStatus.MAYBE)" />
              <Button label="No" size="small" severity="secondary" text @click="rsvp(data.id, RsvpStatus.NO)" />
            </div>
          </template>
        </Column>
        <Column v-if="auth.isAdminOrHR" header="Actions" style="width: 100px">
          <template #body="{ data }"><Button icon="pi pi-pencil" text rounded severity="info" @click="openEdit(data)" /></template>
        </Column>
      </DataTable>
    </div>
    <Dialog v-model:visible="dialogVisible" :header="editing ? 'Edit Event' : 'Create Event'" modal :style="{ width: '480px' }">
      <div style="display:flex;flex-direction:column;gap:var(--space-4);">
        <div class="form-field"><label>Event Name</label><InputText v-model="form.name" fluid /></div>
        <div class="form-field"><label>Date & Time</label><DatePicker v-model="formDate" showTime hourFormat="24" fluid /></div>
        <div class="form-field"><label>Location</label><InputText v-model="form.location" fluid /></div>
      </div>
      <template #footer><Button label="Cancel" text @click="dialogVisible = false" /><Button :label="editing ? 'Update' : 'Create'" icon="pi pi-check" @click="save" /></template>
    </Dialog>
  </div>
</template>

<style scoped>
.table-link { color: var(--theme-blue); font-weight: 500; }
.table-link:hover { text-decoration: underline; }
</style>
