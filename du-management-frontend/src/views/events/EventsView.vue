<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { useAuthStore } from '@/stores/auth'
import { eventsApi } from '@/api/events'
import type { EventResponse, EventRequest } from '@/types'
import { RsvpStatus } from '@/types'
import { formatLocalDateTime, parseApiDate, toLocalDateTime } from '@/utils/datetime'
import { getApiErrorDetail } from '@/utils/apiError'
import DataTable from 'primevue/datatable'
import Column from 'primevue/column'
import Button from 'primevue/button'
import Dialog from 'primevue/dialog'
import InputText from 'primevue/inputtext'
import Textarea from 'primevue/textarea'
import DatePicker from 'primevue/datepicker'
import { useToast } from 'primevue/usetoast'

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
const form = ref<EventRequest>({ name: '', eventDate: '', location: '', description: '' })
const formDate = ref<Date | null>(null)
const saving = ref(false)
const attendanceDialogVisible = ref(false)
const attendanceEvent = ref<EventResponse | null>(null)
const attendanceStatus = ref<RsvpStatus>(RsvpStatus.YES)
const myAttendanceByEvent = ref<Record<number, RsvpStatus>>({})
const notifyingEventId = ref<number | null>(null)

async function load() {
  loading.value = true
  try { const r = await eventsApi.getAll({ page: pg.value, size: rows.value }); events.value = r.data.content; totalRecords.value = r.data.totalElements }
  finally { loading.value = false }
}

function onPage(e: any) { pg.value = e.page; rows.value = e.rows; load() }
function openCreate() { editing.value = false; editId.value = null; form.value = { name: '', eventDate: '', location: '', description: '' }; formDate.value = null; dialogVisible.value = true }
function openEdit(ev: EventResponse) { editing.value = true; editId.value = ev.id; form.value = { name: ev.name, eventDate: ev.eventDate, location: ev.location || '', description: ev.description || '' }; formDate.value = parseApiDate(ev.eventDate); dialogVisible.value = true }

function resolveFormDate(value: unknown): Date | null {
  if (value instanceof Date) {
    return Number.isNaN(value.getTime()) ? null : value
  }
  if (typeof value === 'string') {
    return parseApiDate(value)
  }
  return null
}

const canSubmitEventForm = computed(() => {
  return form.value.name.trim().length > 0 && resolveFormDate(formDate.value) !== null
})

async function save() {
  const trimmedName = form.value.name.trim()
  if (!trimmedName) {
    toast.add({ severity: 'warn', summary: 'Missing event name', detail: 'Please enter an event name.', life: 3000 })
    return
  }
  const selectedDate = resolveFormDate(formDate.value)
  if (!selectedDate) {
    toast.add({ severity: 'warn', summary: 'Missing date & time', detail: 'Please select a valid date and time.', life: 3000 })
    return
  }
  const payload: EventRequest = {
    name: trimmedName,
    eventDate: toLocalDateTime(selectedDate),
    location: form.value.location?.trim() || '',
    description: form.value.description?.trim() || '',
  }
  saving.value = true
  try {
    if (editing.value && editId.value) await eventsApi.update(editId.value, payload)
    else await eventsApi.create(payload)
    toast.add({ severity: 'success', summary: editing.value ? 'Event updated' : 'Event created', life: 3000 })
    dialogVisible.value = false; load()
  } catch (err: any) { toast.add({ severity: 'error', summary: 'Error', detail: getApiErrorDetail(err, 'Failed'), life: 4000 }) }
  finally { saving.value = false }
}

async function loadMyAttendances() {
  if (!auth.userId) {
    myAttendanceByEvent.value = {}
    return
  }
  try {
    const response = await eventsApi.getMyAttendances()
    const attendanceMap: Record<number, RsvpStatus> = {}
    for (const attendee of response.data) {
      attendanceMap[attendee.eventId] = attendee.rsvpStatus
    }
    myAttendanceByEvent.value = attendanceMap
  } catch {
    myAttendanceByEvent.value = {}
  }
}

function isEventExpired(eventDate: string) {
  return new Date(eventDate).getTime() < Date.now()
}

function openAttendanceDialog(event: EventResponse) {
  if (isEventExpired(event.eventDate)) {
    return
  }
  attendanceEvent.value = event
  attendanceStatus.value = myAttendanceByEvent.value[event.id] || RsvpStatus.YES
  attendanceDialogVisible.value = true
}

async function confirmAttendance() {
  if (!auth.userId) {
    toast.add({ severity: 'error', summary: 'Cannot Confirm Attendance', detail: 'Missing user identity. Please log in again.', life: 3000 })
    return
  }
  if (!attendanceEvent.value) {
    return
  }
  try {
    await eventsApi.rsvp(attendanceEvent.value.id, { userId: auth.userId, rsvpStatus: attendanceStatus.value })
    myAttendanceByEvent.value[attendanceEvent.value.id] = attendanceStatus.value
    toast.add({ severity: 'success', summary: `Attendance ${attendanceStatus.value}`, life: 2000 })
    attendanceDialogVisible.value = false
  } catch (err: any) {
    toast.add({ severity: 'error', summary: 'Error', detail: getApiErrorDetail(err, 'Attendance confirmation failed'), life: 3000 })
  }
}

async function sendEventReminder(event: EventResponse) {
  if (!auth.isAdmin) {
    return
  }
  notifyingEventId.value = event.id
  try {
    const response = await eventsApi.triggerReminder(event.id)
    toast.add({ severity: 'success', summary: 'Notification sent', detail: response.data.message, life: 3000 })
  } catch (err: any) {
    toast.add({ severity: 'error', summary: 'Error', detail: getApiErrorDetail(err, 'Failed to send event notification'), life: 4000 })
  } finally {
    notifyingEventId.value = null
  }
}

function canEditEvent(event: EventResponse) {
  return auth.isAdmin || (!!auth.username && event.creatorUsername === auth.username)
}

function getLocationUrl(location?: string | null) {
  if (!location) {
    return null
  }
  const trimmed = location.trim()
  if (!trimmed) {
    return null
  }
  return /^https?:\/\/\S+$/i.test(trimmed) ? trimmed : null
}

function formatDate(d: string) { return formatLocalDateTime(d) }

onMounted(async () => {
  await load()
  await loadMyAttendances()
})
</script>

<template>
  <div class="page-container">
    <div class="page-header">
      <div><h2>Events</h2><p class="page-subtitle">Manage DU events and activities</p></div>
      <Button v-if="auth.isAuthenticated" label="Create Event" icon="pi pi-plus" @click="openCreate" />
    </div>
    <div class="content-card">
      <DataTable :value="events" :loading="loading" :paginator="true" :rows="rows" :totalRecords="totalRecords" :lazy="true" @page="onPage" stripedRows>
        <template #empty>
          No Event has been created yet. Please create a new Event.
        </template>
        <Column field="name" header="Event Name">
          <template #body="{ data }"><router-link :to="`/events/${data.id}`" class="table-link">{{ data.name }}</router-link></template>
        </Column>
        <Column field="eventDate" header="Date"><template #body="{ data }">{{ formatDate(data.eventDate) }}</template></Column>
        <Column field="creator" header="Creator" />
        <Column field="location" header="Location">
          <template #body="{ data }">
            <a
              v-if="getLocationUrl(data.location)"
              :href="getLocationUrl(data.location) || '#'"
              target="_blank"
              rel="noopener noreferrer"
              class="table-link"
            >
              Open Location <i class="pi pi-external-link" style="font-size:11px"></i>
            </a>
            <span v-else>{{ data.location || '-' }}</span>
          </template>
        </Column>
        <Column header="Attendance" style="width: 170px">
          <template #body="{ data }">
            <span v-if="isEventExpired(data.eventDate)" class="expired-label">Expired</span>
            <Button
              v-else
              :label="myAttendanceByEvent[data.id] ? 'Re-Confirm' : 'Confirm'"
              size="small"
              severity="success"
              outlined
              @click="openAttendanceDialog(data)"
            />
          </template>
        </Column>
        <Column header="Actions" style="width: 220px">
          <template #body="{ data }">
            <div style="display:flex;align-items:center;gap:8px;">
              <Button v-if="canEditEvent(data)" icon="pi pi-pencil" text rounded severity="info" @click="openEdit(data)" />
              <Button
                v-if="auth.isAdmin"
                label="Notify"
                icon="pi pi-send"
                size="small"
                severity="warn"
                outlined
                :loading="notifyingEventId === data.id"
                :disabled="notifyingEventId !== null && notifyingEventId !== data.id"
                @click="sendEventReminder(data)"
              />
            </div>
          </template>
        </Column>
      </DataTable>
    </div>
    <Dialog v-model:visible="attendanceDialogVisible" header="Confirm Attendance" modal :style="{ width: '420px' }">
      <div style="display:flex;flex-direction:column;gap:var(--space-4);">
        <div class="page-subtitle" style="margin:0;">
          {{ attendanceEvent ? `Event: ${attendanceEvent.name}` : '' }}
        </div>
        <div style="display:flex;gap:8px;flex-wrap:wrap;">
          <Button
            label="Yes"
            size="small"
            :severity="attendanceStatus === RsvpStatus.YES ? 'success' : 'secondary'"
            :outlined="attendanceStatus !== RsvpStatus.YES"
            @click="attendanceStatus = RsvpStatus.YES"
          />
          <Button
            label="Maybe"
            size="small"
            :severity="attendanceStatus === RsvpStatus.MAYBE ? 'warn' : 'secondary'"
            :outlined="attendanceStatus !== RsvpStatus.MAYBE"
            @click="attendanceStatus = RsvpStatus.MAYBE"
          />
          <Button
            label="No"
            size="small"
            :severity="attendanceStatus === RsvpStatus.NO ? 'danger' : 'secondary'"
            :outlined="attendanceStatus !== RsvpStatus.NO"
            @click="attendanceStatus = RsvpStatus.NO"
          />
        </div>
      </div>
      <template #footer>
        <Button label="Cancel" text @click="attendanceDialogVisible = false" />
        <Button label="Confirm" icon="pi pi-check" @click="confirmAttendance" />
      </template>
    </Dialog>
    <Dialog v-model:visible="dialogVisible" :header="editing ? 'Edit Event' : 'Create Event'" modal :style="{ width: '480px' }">
      <div style="display:flex;flex-direction:column;gap:var(--space-4);">
        <div class="form-field"><label class="required">Event Name</label><InputText v-model="form.name" fluid /></div>
        <div class="form-field"><label class="required">Date &amp; Time</label><DatePicker v-model="formDate" showTime hourFormat="24" :manualInput="false" fluid /></div>
        <div class="form-field"><label>Location <span class="optional-hint">(optional)</span></label><InputText v-model="form.location" fluid /></div>
        <div class="form-field"><label>Description <span class="optional-hint">(optional)</span></label><Textarea v-model="form.description" rows="3" fluid /></div>
      </div>
      <template #footer><Button label="Cancel" text @click="dialogVisible = false" /><Button :label="editing ? 'Update' : 'Create'" icon="pi pi-check" :loading="saving" :disabled="saving || !canSubmitEventForm" @click="save" /></template>
    </Dialog>
  </div>
</template>

<style scoped>
.expired-label {
  display: inline-flex;
  align-items: center;
  border-radius: 999px;
  background: #1f2937;
  color: #ffffff;
  font-size: 12px;
  font-weight: 600;
  padding: 4px 10px;
}
.table-link { color: var(--theme-blue); font-weight: 500; }
.table-link:hover { text-decoration: underline; }
</style>


