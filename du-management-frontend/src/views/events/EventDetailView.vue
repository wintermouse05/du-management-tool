<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { eventsApi } from '@/api/events'
import type { EventResponse, EventAttendeeResponse } from '@/types'
import { RsvpStatus } from '@/types'
import { getApiErrorDetail } from '@/utils/apiError'
import DataTable from 'primevue/datatable'
import Column from 'primevue/column'
import Button from 'primevue/button'
import Tag from 'primevue/tag'
import { useToast } from 'primevue/usetoast'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const toast = useToast()
const eventId = Number(route.params.id)
const event = ref<EventResponse | null>(null)
const attendees = ref<EventAttendeeResponse[]>([])
const totalAttendees = ref(0)
const loading = ref(false)
const notifying = ref(false)

async function loadEvent() {
  try {
    const response = await eventsApi.getById(eventId)
    event.value = response.data
  } catch (error: any) {
    toast.add({ severity: 'error', summary: 'Failed to load event', detail: getApiErrorDetail(error, 'Unable to load event details.'), life: 3000 })
  }
}

async function loadAttendees() {
  loading.value = true
  try {
    const response = await eventsApi.getAttendees(eventId, { size: 50 })
    attendees.value = response.data.content
    totalAttendees.value = response.data.totalElements
  } catch (error: any) {
    toast.add({ severity: 'error', summary: 'Failed to load attendees', detail: getApiErrorDetail(error, 'Unable to load attendee list.'), life: 3000 })
  } finally {
    loading.value = false
  }
}

async function handleCheckIn(userId: number) {
  try {
    await eventsApi.checkIn(eventId, userId)
    toast.add({ severity: 'success', summary: 'Checked in!', life: 2000 })
    loadAttendees()
  } catch (error: any) {
    toast.add({ severity: 'error', summary: 'Error', detail: getApiErrorDetail(error), life: 3000 })
  }
}

async function handleRsvp(status: RsvpStatus) {
  if (!auth.userId) {
    toast.add({ severity: 'error', summary: 'Cannot Confirm Attendance', detail: 'Missing user identity. Please log in again.', life: 3000 })
    return
  }

  try {
    await eventsApi.rsvp(eventId, { userId: auth.userId, rsvpStatus: status })
    toast.add({ severity: 'success', summary: `Attendance ${status}`, life: 2000 })
    if (auth.isAdminOrHR) {
      loadAttendees()
    }
  } catch (error: any) {
    toast.add({ severity: 'error', summary: 'Error', detail: getApiErrorDetail(error, 'Attendance update failed'), life: 3000 })
  }
}

async function sendEventReminder() {
  if (!auth.isAdmin || !event.value) {
    return
  }
  notifying.value = true
  try {
    const response = await eventsApi.triggerReminder(event.value.id)
    toast.add({ severity: 'success', summary: 'Notification sent', detail: response.data.message, life: 3000 })
  } catch (error: any) {
    toast.add({ severity: 'error', summary: 'Error', detail: getApiErrorDetail(error, 'Failed to send event notification'), life: 3000 })
  } finally {
    notifying.value = false
  }
}

function rsvpSeverity(status: RsvpStatus) {
  return status === RsvpStatus.YES ? 'success' : status === RsvpStatus.NO ? 'danger' : 'warn'
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

function formatDate(value: string) {
  return value ? new Date(value).toLocaleDateString('en-US', { year: 'numeric', month: 'long', day: 'numeric', hour: '2-digit', minute: '2-digit' }) : ''
}

onMounted(() => {
  if (!Number.isFinite(eventId) || eventId <= 0) {
    toast.add({ severity: 'error', summary: 'Invalid event', detail: 'The event ID in the URL is invalid.', life: 3000 })
    router.replace('/events')
    return
  }

  loadEvent()
  if (auth.isAdminOrHR) {
    loadAttendees()
  }
})
</script>

<template>
  <div class="page-container">
    <div class="page-header">
      <div>
        <h2>{{ event?.name || 'Event Detail' }}</h2>
        <p class="page-subtitle" v-if="event">
          <span>{{ formatDate(event.eventDate) }}</span>
          <span> | </span>
          <a
            v-if="getLocationUrl(event.location)"
            :href="getLocationUrl(event.location) || '#'"
            target="_blank"
            rel="noopener noreferrer"
            class="event-location-link"
          >
            Open Location <i class="pi pi-external-link" style="font-size:11px"></i>
          </a>
          <span v-else>{{ event.location || 'No location' }}</span>
          <span> | by {{ event.creator }}</span>
        </p>
      </div>
      <div style="display:flex;gap:8px;align-items:center;">
        <Button
          v-if="auth.isAdmin && event"
          label="Notify"
          icon="pi pi-send"
          severity="warn"
          outlined
          :loading="notifying"
          @click="sendEventReminder"
        />
        <router-link to="/events"><Button label="Back to Events" icon="pi pi-arrow-left" outlined /></router-link>
      </div>
    </div>

    <div class="content-card" v-if="event" style="margin-bottom:var(--space-6);">
      <div class="event-info-grid">
        <div><div class="caption">Event Name</div><div style="font-weight:600;margin-top:4px;">{{ event.name }}</div></div>
        <div><div class="caption">Date</div><div style="font-weight:600;margin-top:4px;">{{ formatDate(event.eventDate) }}</div></div>
        <div><div class="caption">Creator</div><div style="font-weight:600;margin-top:4px;">{{ event.creator }}</div></div>
        <div>
          <div class="caption">Location</div>
          <div style="font-weight:600;margin-top:4px;">
            <a
              v-if="getLocationUrl(event.location)"
              :href="getLocationUrl(event.location) || '#'"
              target="_blank"
              rel="noopener noreferrer"
              class="event-location-link"
            >
              {{ event.location }}
            </a>
            <span v-else>{{ event.location || '-' }}</span>
          </div>
        </div>
      </div>
      <div v-if="event.description" style="margin-top:var(--space-4);">
        <div class="caption">Description</div>
        <div style="margin-top:4px;white-space:pre-wrap;line-height:1.5;">{{ event.description }}</div>
      </div>
      <div style="display:flex;gap:8px;margin-top:var(--space-4);">
        <Button label="Yes" size="small" severity="success" @click="handleRsvp(RsvpStatus.YES)" />
        <Button label="Maybe" size="small" severity="warn" @click="handleRsvp(RsvpStatus.MAYBE)" />
        <Button label="No" size="small" severity="secondary" @click="handleRsvp(RsvpStatus.NO)" />
      </div>
    </div>

    <div class="content-card" v-if="auth.isAdminOrHR">
      <h3 style="margin-bottom:var(--space-4);">Attendees ({{ totalAttendees }})</h3>
      <DataTable :value="attendees" :loading="loading" stripedRows>
        <template #empty>
          No attendee yet for this Event.
        </template>
        <Column field="fullName" header="Name" />
        <Column field="rsvpStatus" header="Attendance">
          <template #body="{ data }"><Tag :value="data.rsvpStatus" :severity="rsvpSeverity(data.rsvpStatus)" /></template>
        </Column>
        <Column field="checkedIn" header="Checked In">
          <template #body="{ data }"><Tag :value="data.checkedIn ? 'Yes' : 'No'" :severity="data.checkedIn ? 'success' : 'secondary'" /></template>
        </Column>
        <Column v-if="auth.isAdminOrHR" header="Actions" style="width:120px">
          <template #body="{ data }">
            <Button v-if="!data.checkedIn" label="Check In" size="small" @click="handleCheckIn(data.userId)" />
          </template>
        </Column>
      </DataTable>
    </div>
    <div v-else class="content-card">
      <p class="page-subtitle" style="margin:0;">You can confirm attendance above. Attendee list is visible to Admin/HR only.</p>
    </div>
  </div>
</template>

<style scoped>
.event-info-grid {
  display: grid;
  grid-template-columns: 1fr 1fr 1fr;
  gap: var(--space-6);
}

.event-location-link {
  color: var(--theme-blue);
}

.event-location-link:hover {
  text-decoration: underline;
}

@media (max-width: 768px) {
  .event-info-grid {
    grid-template-columns: 1fr;
    gap: var(--space-4);
  }
}
</style>


