<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { eventsApi } from '@/api/events'
import type { EventResponse, EventAttendeeResponse } from '@/types'
import { RsvpStatus } from '@/types'
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

async function loadEvent() {
  try {
    const r = await eventsApi.getById(eventId)
    event.value = r.data
  } catch (err: any) {
    toast.add({ severity: 'error', summary: 'Failed to load event', detail: err.response?.data?.message || 'Unable to load event details.', life: 3000 })
  }
}
async function loadAttendees() {
  loading.value = true
  try { const r = await eventsApi.getAttendees(eventId, { size: 50 }); attendees.value = r.data.content; totalAttendees.value = r.data.totalElements }
  catch (err: any) {
    toast.add({ severity: 'error', summary: 'Failed to load attendees', detail: err.response?.data?.message || 'Unable to load attendee list.', life: 3000 })
  }
  finally { loading.value = false }
}

async function handleCheckIn(userId: number) {
  try { await eventsApi.checkIn(eventId, userId); toast.add({ severity: 'success', summary: 'Checked in!', life: 2000 }); loadAttendees()
  } catch (err: any) { toast.add({ severity: 'error', summary: 'Error', detail: err.response?.data?.message, life: 3000 }) }
}

function rsvpSeverity(s: RsvpStatus) { return s === RsvpStatus.YES ? 'success' : s === RsvpStatus.NO ? 'danger' : 'warn' }
function fmtDate(d: string) { return d ? new Date(d).toLocaleDateString('en-US', { year:'numeric', month:'long', day:'numeric', hour:'2-digit', minute:'2-digit' }) : '' }

onMounted(() => {
  if (!Number.isFinite(eventId) || eventId <= 0) {
    toast.add({ severity: 'error', summary: 'Invalid event', detail: 'The event ID in the URL is invalid.', life: 3000 })
    router.replace('/events')
    return
  }
  loadEvent(); loadAttendees()
})
</script>

<template>
  <div class="page-container">
    <div class="page-header">
      <div>
        <h2>{{ event?.name || 'Event Detail' }}</h2>
        <p class="page-subtitle" v-if="event">{{ fmtDate(event.eventDate) }} · {{ event.location || 'No location' }}</p>
      </div>
      <router-link to="/events"><Button label="Back to Events" icon="pi pi-arrow-left" outlined /></router-link>
    </div>

    <div class="content-card" v-if="event" style="margin-bottom:var(--space-6);">
      <div class="event-info-grid">
        <div><div class="caption">Event Name</div><div style="font-weight:600;margin-top:4px;">{{ event.name }}</div></div>
        <div><div class="caption">Date</div><div style="font-weight:600;margin-top:4px;">{{ fmtDate(event.eventDate) }}</div></div>
        <div><div class="caption">Location</div><div style="font-weight:600;margin-top:4px;">{{ event.location || '—' }}</div></div>
      </div>
    </div>

    <div class="content-card">
      <h3 style="margin-bottom:var(--space-4);">Attendees ({{ totalAttendees }})</h3>
      <DataTable :value="attendees" :loading="loading" stripedRows>
        <Column field="fullName" header="Name" />
        <Column field="rsvpStatus" header="RSVP">
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
  </div>
</template>

<style scoped>
.event-info-grid {
  display: grid;
  grid-template-columns: 1fr 1fr 1fr;
  gap: var(--space-6);
}

@media (max-width: 768px) {
  .event-info-grid {
    grid-template-columns: 1fr;
    gap: var(--space-4);
  }
}
</style>
