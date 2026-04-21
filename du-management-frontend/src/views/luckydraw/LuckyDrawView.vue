<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue'
import { useAuthStore } from '@/stores/auth'
import { eventsApi } from '@/api/events'
import { luckyDrawApi } from '@/api/luckyDraw'
import type { EventResponse, LuckyDrawSessionResponse, LuckyDrawPrizeResponse, LuckyDrawWinnerResponse } from '@/types'
import { wsService } from '@/services/websocket'
import DataTable from 'primevue/datatable'
import Column from 'primevue/column'
import Button from 'primevue/button'
import Dialog from 'primevue/dialog'
import InputText from 'primevue/inputtext'
import InputNumber from 'primevue/inputnumber'
import Select from 'primevue/select'
import Tag from 'primevue/tag'
import { useToast } from 'primevue/usetoast'

const auth = useAuthStore()
const toast = useToast()
const events = ref<EventResponse[]>([])
const selectedEvent = ref<number|null>(null)
const sessions = ref<LuckyDrawSessionResponse[]>([])
const selectedSession = ref<number|null>(null)
const prizes = ref<LuckyDrawPrizeResponse[]>([])
const selectedPrize = ref<number|null>(null)
const winners = ref<LuckyDrawWinnerResponse[]>([])
const loading = ref(false)

const sessDialog = ref(false); const sessName = ref('')
const prizeDialog = ref(false); const prizeForm = ref({ prizeName: '', quantity: 1 })
const drawDialog = ref(false); const drawUserId = ref<number>(0)

let sub: any = null

onMounted(async () => {
  try { const r = await eventsApi.getAll({ size: 100 }); events.value = r.data.content } catch {}
  sub = wsService.subscribe('/topic/lucky-draw', (message) => {
    const newWinner = JSON.parse(message.body) as LuckyDrawWinnerResponse
    toast.add({ severity: 'success', summary: '🎉 New Winner!', detail: `${newWinner.fullName} won ${newWinner.prizeName}!`, life: 5000 })
    if (selectedPrize.value === newWinner.prizeId) {
      loadWinners()
    }
  })
})

onUnmounted(() => {
  if (sub) sub.unsubscribe()
})

async function loadSessions() {
  if (!selectedEvent.value) return; sessions.value = []; prizes.value = []; winners.value = []
  loading.value = true; try { const r = await luckyDrawApi.getSessionsByEvent(selectedEvent.value, { size: 50 }); sessions.value = r.data.content } finally { loading.value = false }
}
async function loadPrizes() {
  if (!selectedSession.value) return; prizes.value = []; winners.value = []
  try { const r = await luckyDrawApi.getPrizesBySession(selectedSession.value, { size: 50 }); prizes.value = r.data.content } catch {}
}
async function loadWinners() {
  if (!selectedPrize.value) return
  try { const r = await luckyDrawApi.getWinnersByPrize(selectedPrize.value, { size: 50 }); winners.value = r.data.content } catch {}
}

async function createSession() {
  if (!selectedEvent.value) return
  try { await luckyDrawApi.createSession({ eventId: selectedEvent.value, name: sessName.value }); toast.add({ severity:'success', summary:'Session created', life:2000 }); sessDialog.value = false; loadSessions()
  } catch (e: any) { toast.add({ severity:'error', summary:'Error', detail: e.response?.data?.message, life:3000 }) }
}
async function createPrize() {
  if (!selectedSession.value) return
  try { await luckyDrawApi.createPrize({ sessionId: selectedSession.value, ...prizeForm.value }); toast.add({ severity:'success', summary:'Prize added', life:2000 }); prizeDialog.value = false; loadPrizes()
  } catch (e: any) { toast.add({ severity:'error', summary:'Error', detail: e.response?.data?.message, life:3000 }) }
}
async function drawWinner() {
  if (!selectedPrize.value) return
  try { await luckyDrawApi.drawWinner({ prizeId: selectedPrize.value, userId: drawUserId.value }); toast.add({ severity:'success', summary:'🎉 Winner drawn!', life:3000 }); drawDialog.value = false; loadWinners()
  } catch (e: any) { toast.add({ severity:'error', summary:'Error', detail: e.response?.data?.message, life:3000 }) }
}
</script>

<template>
  <div class="page-container">
    <div class="page-header"><div><h2>🎰 Lucky Draw</h2><p class="page-subtitle">Manage lucky draw sessions, prizes, and winners</p></div></div>
    <div class="content-card" style="margin-bottom:var(--space-6);">
      <div style="display:flex;gap:var(--space-4);align-items:flex-end;flex-wrap:wrap;">
        <div class="form-field" style="min-width:200px"><label>Event</label><Select v-model="selectedEvent" :options="events" optionLabel="name" optionValue="id" placeholder="Select event" @change="loadSessions" fluid /></div>
        <Button label="Load Sessions" icon="pi pi-search" outlined @click="loadSessions" />
        <Button v-if="auth.isAdminOrHR && selectedEvent" label="New Session" icon="pi pi-plus" size="small" @click="sessDialog=true" />
      </div>
    </div>

    <div v-if="sessions.length" class="content-card" style="margin-bottom:var(--space-6);">
      <h3 style="margin-bottom:var(--space-4);">Sessions</h3>
      <div style="display:flex;gap:var(--space-3);flex-wrap:wrap;margin-bottom:var(--space-4);">
        <Button v-for="s in sessions" :key="s.id" :label="s.name" :outlined="selectedSession !== s.id" size="small"
          @click="selectedSession = s.id; loadPrizes()" />
      </div>
      <div v-if="selectedSession">
        <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:var(--space-4);">
          <h4>Prizes</h4>
          <Button v-if="auth.isAdminOrHR" label="Add Prize" icon="pi pi-plus" size="small" @click="prizeDialog=true" />
        </div>
        <DataTable :value="prizes" stripedRows>
          <Column field="prizeName" header="Prize" /><Column field="quantity" header="Qty" />
          <Column header="">
            <template #body="{data}">
              <div style="display:flex;gap:4px;">
                <Button label="View Winners" size="small" outlined @click="selectedPrize=data.id;loadWinners()" />
                <Button v-if="auth.isAdminOrHR" label="Draw" size="small" severity="success" icon="pi pi-bolt" @click="selectedPrize=data.id;drawDialog=true" />
              </div>
            </template>
          </Column>
        </DataTable>
      </div>
    </div>

    <div v-if="winners.length" class="content-card">
      <h3 style="margin-bottom:var(--space-4);">🎉 Winners</h3>
      <DataTable :value="winners" stripedRows>
        <Column field="prizeName" header="Prize" /><Column field="fullName" header="Winner" />
      </DataTable>
    </div>

    <Dialog v-model:visible="sessDialog" header="New Lucky Draw Session" modal :style="{width:'380px'}">
      <div class="form-field"><label>Session Name</label><InputText v-model="sessName" fluid /></div>
      <template #footer><Button label="Cancel" text @click="sessDialog=false" /><Button label="Create" icon="pi pi-check" @click="createSession" /></template>
    </Dialog>
    <Dialog v-model:visible="prizeDialog" header="Add Prize" modal :style="{width:'380px'}">
      <div style="display:flex;flex-direction:column;gap:var(--space-4);">
        <div class="form-field"><label>Prize Name</label><InputText v-model="prizeForm.prizeName" fluid /></div>
        <div class="form-field"><label>Quantity</label><InputNumber v-model="prizeForm.quantity" :min="1" fluid /></div>
      </div>
      <template #footer><Button label="Cancel" text @click="prizeDialog=false" /><Button label="Add" icon="pi pi-check" @click="createPrize" /></template>
    </Dialog>
    <Dialog v-model:visible="drawDialog" header="Draw Winner" modal :style="{width:'360px'}">
      <div class="form-field"><label>User ID</label><InputNumber v-model="drawUserId" fluid /></div>
      <template #footer><Button label="Cancel" text @click="drawDialog=false" /><Button label="Draw!" icon="pi pi-bolt" severity="success" @click="drawWinner" /></template>
    </Dialog>
  </div>
</template>
