<script setup lang="ts">
import { ref, onMounted, onUnmounted, computed, watch } from 'vue'
import { useAuthStore } from '@/stores/auth'
import { eventsApi } from '@/api/events'
import { membersApi } from '@/api/members'
import { luckyDrawApi } from '@/api/luckyDraw'
import type {
  EventResponse,
  LuckyDrawParticipantResponse,
  LuckyDrawPrizeResponse,
  LuckyDrawSessionResponse,
  LuckyDrawWinnerResponse,
} from '@/types'
import { wsService } from '@/services/websocket'
import { getApiErrorDetail } from '@/utils/apiError'
import { toMemberDisplayOption, type MemberDisplayOption } from '@/utils/memberDisplay'
import DataTable from 'primevue/datatable'
import Column from 'primevue/column'
import Button from 'primevue/button'
import Dialog from 'primevue/dialog'
import InputText from 'primevue/inputtext'
import InputNumber from 'primevue/inputnumber'
import Select from 'primevue/select'
import Tag from 'primevue/tag'
import MultiSelect from 'primevue/multiselect'
import { useToast } from 'primevue/usetoast'
import FortuneWheel from 'vue-fortune-wheel'
import 'vue-fortune-wheel/style.css'

const auth = useAuthStore()
const toast = useToast()
const events = ref<EventResponse[]>([])
const selectedEvent = ref<number | null>(null)
const sessions = ref<LuckyDrawSessionResponse[]>([])
const selectedSession = ref<number | null>(null)
const prizes = ref<LuckyDrawPrizeResponse[]>([])
const selectedPrize = ref<number | null>(null)
const winners = ref<LuckyDrawWinnerResponse[]>([])
const participants = ref<LuckyDrawParticipantResponse[]>([])
const sessionWinners = ref<LuckyDrawWinnerResponse[]>([])
const memberOptions = ref<MemberDisplayOption[]>([])
const loading = ref(false)
const winnersLoading = ref(false)

const sessDialog = ref(false)
const sessName = ref('')
const prizeDialog = ref(false)
const prizeForm = ref({ prizeName: '', quantity: 1 })
const participantDialog = ref(false)
const winnersDialog = ref(false)
const selectedPrizeName = ref('')
const selectedParticipantIds = ref<number[]>([])
const minPointFilter = ref<number | null>(null)
const maxPointFilter = ref<number | null>(null)

const wheelDialog = ref(false)
const wheelWinnerUserId = ref<number>(0)
const wheelVerify = ref(true)
const winnerAnnouncementDialog = ref(false)
const latestDrawWinner = ref<LuckyDrawWinnerResponse | null>(null)
const wheelParticipants = ref<
  Array<{
    id: number
    name: string
    value: string
    bgColor: string
    color: string
    weight: number
  }>
>([])

const selectedSessionDetail = computed(() =>
  sessions.value.find((session) => session.id === selectedSession.value) || null,
)

const palette = ['#f87171', '#fb923c', '#fbbf24', '#a3e635', '#34d399', '#22d3ee', '#818cf8', '#c084fc', '#f472b6']
const sessionWinnerUserIds = computed(() => new Set(sessionWinners.value.map((winner) => winner.userId)))
const eligibleWheelParticipants = computed(() =>
  participants.value.filter((participant) => !sessionWinnerUserIds.value.has(participant.userId)),
)
function mapWheelParticipants(drawParticipants: LuckyDrawParticipantResponse[]) {
  return drawParticipants.map((p, i) => ({
    id: p.userId,
    name: p.fullName.split(' ')[0],
    value: p.fullName,
    bgColor: palette[i % palette.length],
    color: '#ffffff',
    weight: 1,
  }))
}

const membersInPointRange = computed(() => {
  const min = minPointFilter.value
  const max = maxPointFilter.value
  return memberOptions.value.filter((member) => {
    if (member.inactive) return false
    const points = member.totalPoints ?? 0
    if (min != null && points < min) return false
    if (max != null && points > max) return false
    return true
  })
})

const canvasOptions = {
  btnWidth: 140,
  borderColor: '#584b43',
  borderWidth: 6,
  lineHeight: 30,
}

let sub: any = null

onMounted(async () => {
  try {
    const eventRes = await eventsApi.getAll({ size: 100 })
    events.value = eventRes.data.content
  } catch {}

  if (auth.isAdminOrHR) {
    try {
      const memberRes = await membersApi.search({ size: 500 })
      memberOptions.value = memberRes.data.content.map(toMemberDisplayOption)
    } catch {}
  }

  sub = wsService.subscribe('/topic/lucky-draw', (message) => {
    const newWinner = JSON.parse(message.body) as LuckyDrawWinnerResponse
    if (winnersDialog.value && selectedPrize.value === newWinner.prizeId) {
      void loadWinners()
    }
    if (selectedSession.value && prizes.value.some((p) => p.id === newWinner.prizeId)) {
      upsertSessionWinner(newWinner)
      void loadPrizes()
    }
    if (!wheelDialog.value) {
      toast.add({ severity: 'success', summary: 'New Winner!', detail: `${newWinner.fullName} won ${newWinner.prizeName}!`, life: 5000 })
    }
  })
})

onUnmounted(() => {
  if (sub) sub.unsubscribe()
})

watch(selectedEvent, () => {
  void loadSessions()
})

function resetSessionSettings() {
  selectedSession.value = null
  selectedPrize.value = null
  selectedPrizeName.value = ''
  participants.value = []
  prizes.value = []
  winners.value = []
  sessionWinners.value = []
  winnersDialog.value = false
  wheelDialog.value = false
  winnerAnnouncementDialog.value = false
  latestDrawWinner.value = null
  wheelParticipants.value = []
}

async function loadSessions() {
  if (!selectedEvent.value) {
    sessions.value = []
    resetSessionSettings()
    return
  }
  loading.value = true
  try {
    const response = await luckyDrawApi.getSessionsByEvent(selectedEvent.value, { size: 50 })
    sessions.value = response.data.content
    if (selectedSession.value && !sessions.value.some((session) => session.id === selectedSession.value)) {
      resetSessionSettings()
    }
  } finally {
    loading.value = false
  }
}

async function loadPrizes() {
  if (!selectedSession.value) {
    prizes.value = []
    return
  }
  try {
    const response = await luckyDrawApi.getPrizesBySession(selectedSession.value, { size: 50 })
    prizes.value = response.data.content
  } catch {
    prizes.value = []
  }
}

async function loadParticipants() {
  if (!selectedSession.value) {
    participants.value = []
    return
  }
  try {
    const response = await luckyDrawApi.getParticipants(selectedSession.value)
    participants.value = response.data
  } catch {
    participants.value = []
  }
}

async function loadSessionWinners() {
  if (!selectedSession.value) {
    sessionWinners.value = []
    return
  }
  try {
    const response = await luckyDrawApi.getWinnersBySession(selectedSession.value)
    sessionWinners.value = response.data
  } catch {
    sessionWinners.value = []
  }
}

async function loadWinners() {
  if (!selectedPrize.value) {
    winners.value = []
    return
  }
  winnersLoading.value = true
  try {
    const response = await luckyDrawApi.getWinnersByPrize(selectedPrize.value, { size: 50 })
    winners.value = response.data.content
  } catch {
    winners.value = []
  } finally {
    winnersLoading.value = false
  }
}

async function syncDrawState() {
  await Promise.all([loadPrizes(), loadSessionWinners()])
}

function upsertSessionWinner(winner: LuckyDrawWinnerResponse) {
  if (sessionWinners.value.some((item) => item.id === winner.id)) {
    return
  }
  sessionWinners.value = [...sessionWinners.value, winner]
}

function incrementPrizeDrawnCount(prizeId: number) {
  const prize = prizes.value.find((item) => item.id === prizeId)
  if (!prize) return
  prize.drawnCount = Math.min(prize.drawnCount + 1, prize.quantity)
}

async function openSessionSettings(session: LuckyDrawSessionResponse) {
  selectedSession.value = session.id
  selectedPrize.value = null
  winners.value = []
  winnersDialog.value = false
  await Promise.all([loadParticipants(), loadPrizes(), loadSessionWinners()])
}

function backToSessionList() {
  resetSessionSettings()
}

function openPrizeDialog() {
  prizeForm.value = { prizeName: '', quantity: 1 }
  prizeDialog.value = true
}

async function createSession() {
  if (!selectedEvent.value) return
  try {
    const response = await luckyDrawApi.createSession({ eventId: selectedEvent.value, name: sessName.value })
    toast.add({ severity: 'success', summary: 'Session created', detail: 'Default prizes 1st/2nd/3rd were created.', life: 2500 })
    sessDialog.value = false
    sessName.value = ''
    await loadSessions()
    await openSessionSettings(response.data)
  } catch (e: any) {
    toast.add({ severity: 'error', summary: 'Error', detail: getApiErrorDetail(e), life: 3000 })
  }
}

async function createPrize() {
  if (!selectedSession.value) return
  try {
    await luckyDrawApi.createPrize({ sessionId: selectedSession.value, ...prizeForm.value })
    toast.add({ severity: 'success', summary: 'Prize added', life: 2000 })
    prizeDialog.value = false
    prizeForm.value = { prizeName: '', quantity: 1 }
    await loadPrizes()
  } catch (e: any) {
    toast.add({ severity: 'error', summary: 'Error', detail: getApiErrorDetail(e), life: 3000 })
  }
}

async function setupParticipants() {
  if (!selectedSession.value) return
  try {
    await luckyDrawApi.setupParticipants(selectedSession.value, selectedParticipantIds.value)
    toast.add({ severity: 'success', summary: 'Participants updated', life: 2500 })
    participantDialog.value = false
    await loadParticipants()

    const currentSession = sessions.value.find((session) => session.id === selectedSession.value)
    if (currentSession) {
      currentSession.participantCount = selectedParticipantIds.value.length
    }
  } catch (e: any) {
    toast.add({ severity: 'error', summary: 'Error', detail: getApiErrorDetail(e), life: 3000 })
  }
}

function hasRemainingDraw(prize: LuckyDrawPrizeResponse) {
  return prize.drawnCount < prize.quantity
}

async function openWinnersDialog(prize: LuckyDrawPrizeResponse) {
  selectedPrize.value = prize.id
  selectedPrizeName.value = prize.prizeName
  winnersDialog.value = true
  await loadWinners()
}

async function openWheelDialog(prize: LuckyDrawPrizeResponse) {
  await syncDrawState()
  const livePrize = prizes.value.find((item) => item.id === prize.id)
  if (!livePrize || !hasRemainingDraw(livePrize)) {
    return
  }
  if (!participants.value.length) {
    toast.add({ severity: 'error', summary: 'No participants', detail: 'Setup participants first.', life: 3000 })
    return
  }
  if (!eligibleWheelParticipants.value.length) {
    toast.add({ severity: 'warn', summary: 'No eligible participants', detail: 'All participants have already won a prize.', life: 3000 })
    return
  }
  selectedPrize.value = prize.id
  selectedPrizeName.value = prize.prizeName
  wheelVerify.value = true
  wheelWinnerUserId.value = 0
  latestDrawWinner.value = null
  winnerAnnouncementDialog.value = false
  wheelParticipants.value = mapWheelParticipants(eligibleWheelParticipants.value)
  wheelDialog.value = true
}

async function onCanvasRotateStart(rotate: Function) {
  if (!selectedPrize.value || !wheelParticipants.value.length) return
  try {
    const response = await luckyDrawApi.drawWinnerFromPool(selectedPrize.value)
    wheelWinnerUserId.value = response.data.userId
    latestDrawWinner.value = response.data

    // Ensure the drawn winner always exists in the rendered wheel snapshot.
    if (!wheelParticipants.value.some((item) => item.id === response.data.userId)) {
      wheelParticipants.value = [
        ...wheelParticipants.value,
        {
          id: response.data.userId,
          name: response.data.fullName.split(' ')[0],
          value: response.data.fullName,
          bgColor: palette[wheelParticipants.value.length % palette.length],
          color: '#ffffff',
          weight: 1,
        },
      ]
    }

    wheelVerify.value = false
    rotate()
  } catch (e: any) {
    toast.add({ severity: 'error', summary: 'Error', detail: getApiErrorDetail(e), life: 3000 })
    await syncDrawState()
    wheelDialog.value = false
  }
}

async function onRotateEnd(prize: any) {
  const winnerName = latestDrawWinner.value?.fullName || prize?.value || 'Unknown winner'
  const prizeName = latestDrawWinner.value?.prizeName || selectedPrizeName.value

  if (latestDrawWinner.value) {
    upsertSessionWinner(latestDrawWinner.value)
  }
  if (selectedPrize.value) {
    incrementPrizeDrawnCount(selectedPrize.value)
  }

  toast.add({
    severity: 'success',
    summary: 'Winner drawn!',
    detail: prizeName ? `${winnerName} won ${prizeName}!` : `${winnerName} has won!`,
    life: 5000,
  })
  wheelDialog.value = false
  winnerAnnouncementDialog.value = true
  wheelParticipants.value = []
  await Promise.all([loadPrizes(), loadParticipants(), loadWinners(), loadSessionWinners()])
}

async function onWheelDialogHide() {
  wheelParticipants.value = []
  if (!selectedSession.value) return
  await syncDrawState()
}

function openParticipantDialog() {
  selectedParticipantIds.value = participants.value.map((participant) => participant.userId)
  minPointFilter.value = null
  maxPointFilter.value = null
  participantDialog.value = true
}

function selectParticipantsByPointRange() {
  const min = minPointFilter.value
  const max = maxPointFilter.value
  if (min == null && max == null) {
    toast.add({ severity: 'warn', summary: 'Missing point range', detail: 'Please input min or max point first.', life: 2500 })
    return
  }

  if (min != null && max != null && min > max) {
    toast.add({ severity: 'warn', summary: 'Invalid range', detail: 'Min point must be less than or equal to max point.', life: 2500 })
    return
  }

  const ids = membersInPointRange.value.map((member) => member.id)
  if (!ids.length) {
    toast.add({ severity: 'info', summary: 'No members in range', life: 2500 })
    return
  }

  selectedParticipantIds.value = ids
  toast.add({ severity: 'success', summary: `Selected ${ids.length} member(s)`, life: 2200 })
}

function addParticipantsByPointRange() {
  const min = minPointFilter.value
  const max = maxPointFilter.value
  if (min == null && max == null) {
    toast.add({ severity: 'warn', summary: 'Missing point range', detail: 'Please input min or max point first.', life: 2500 })
    return
  }

  if (min != null && max != null && min > max) {
    toast.add({ severity: 'warn', summary: 'Invalid range', detail: 'Min point must be less than or equal to max point.', life: 2500 })
    return
  }

  const ids = membersInPointRange.value.map((member) => member.id)
  if (!ids.length) {
    toast.add({ severity: 'info', summary: 'No members in range', life: 2500 })
    return
  }

  selectedParticipantIds.value = Array.from(new Set([...selectedParticipantIds.value, ...ids]))
  toast.add({ severity: 'success', summary: `Added ${ids.length} member(s) by point range`, life: 2200 })
}
</script>

<template>
  <div class="page-container">
    <div class="page-header">
      <div>
        <h2>Lucky Draw</h2>
        <p class="page-subtitle">Manage lucky draw sessions, participants, prizes, and winners</p>
      </div>
    </div>

    <div class="content-card" style="margin-bottom:var(--space-6);">
      <div style="display:flex;gap:var(--space-4);align-items:flex-end;flex-wrap:wrap;">
        <div class="form-field" style="min-width:200px">
          <label>Event</label>
          <Select v-model="selectedEvent" :options="events" optionLabel="name" optionValue="id" placeholder="Select event" fluid />
        </div>
        <Button v-if="auth.isAdminOrHR && selectedEvent" label="New Session" icon="pi pi-plus" size="small" @click="sessDialog=true" />
      </div>
    </div>

    <div v-if="!selectedSession" class="content-card" style="margin-bottom:var(--space-6);">
      <h3 style="margin-bottom:var(--space-4);">Sessions</h3>
      <DataTable :value="sessions" :loading="loading" stripedRows dataKey="id">
        <template #empty>
          No Session has been created for this Event yet.
        </template>
        <Column field="name" header="Session">
          <template #body="{ data }">
            <Button text class="session-name-btn" :label="data.name" @click="openSessionSettings(data)" />
          </template>
        </Column>
        <Column field="participantCount" header="Participants" style="width:180px" />
        <Column header="Actions" style="width:180px">
          <template #body="{ data }">
            <Button label="Setup" icon="pi pi-cog" size="small" outlined @click="openSessionSettings(data)" />
          </template>
        </Column>
      </DataTable>
    </div>

    <div v-else class="content-card" style="margin-bottom:var(--space-6);">
      <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:var(--space-4);">
        <div>
          <h3 style="margin:0;">Session Settings</h3>
          <p class="page-subtitle" style="margin-top:4px;">{{ selectedSessionDetail?.name || 'Selected Session' }}</p>
        </div>
        <Button label="Back to Sessions" icon="pi pi-arrow-left" text @click="backToSessionList" />
      </div>

      <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:var(--space-3);">
        <h4>Participants ({{ participants.length }})</h4>
        <Button v-if="auth.isAdminOrHR" label="Setup Participants" icon="pi pi-users" size="small" outlined @click="openParticipantDialog" />
      </div>
      <DataTable
        :value="participants"
        stripedRows
        class="lucky-grid"
        :tableStyle="{ tableLayout: 'fixed' }"
        style="margin-bottom:var(--space-5);"
      >
        <template #empty>
          No Participant has been added to this Session yet. Please set up participants.
        </template>
        <Column field="userId" header="User ID" style="width:22%" />
        <Column field="fullName" header="Name" style="width:37%" />
        <Column field="email" header="Email" style="width:41%" />
      </DataTable>

      <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:var(--space-4);">
        <h4>Prizes</h4>
        <Button v-if="auth.isAdminOrHR" label="Add Prize" icon="pi pi-plus" size="small" @click="openPrizeDialog" />
      </div>
      <DataTable
        :value="prizes"
        stripedRows
        class="lucky-grid"
        :tableStyle="{ tableLayout: 'fixed' }"
      >
        <template #empty>
          No Prize has been created for this Session yet. Please add a Prize.
        </template>
        <Column field="prizeName" header="Prize" style="width:26%" />
        <Column field="quantity" header="Qty" style="width:12%" />
        <Column field="drawnCount" header="Drawn" style="width:12%" />
        <Column header="Remaining" style="width:14%">
          <template #body="{ data }">
            {{ Math.max(data.quantity - data.drawnCount, 0) }}
          </template>
        </Column>
        <Column header="Actions" style="width:36%">
          <template #body="{ data }">
            <div class="prize-actions">
              <Button label="View Winners" size="small" outlined @click="openWinnersDialog(data)" style="white-space: nowrap;" />
              <Button
                v-if="auth.isAdminOrHR && hasRemainingDraw(data)"
                label="Draw"
                size="small"
                severity="success"
                icon="pi pi-bolt"
                @click="openWheelDialog(data)"
                style="white-space: nowrap;"
              />
              <Tag v-else-if="auth.isAdminOrHR" value="Completed" severity="secondary" />
            </div>
          </template>
        </Column>
      </DataTable>
    </div>

    <Dialog v-model:visible="sessDialog" header="New Lucky Draw Session" modal :style="{width:'380px'}">
      <div class="form-field">
        <label class="required">Session Name</label>
        <InputText v-model="sessName" fluid />
      </div>
      <template #footer>
        <Button label="Cancel" text @click="sessDialog=false" />
        <Button label="Create" icon="pi pi-check" @click="createSession" />
      </template>
    </Dialog>

    <Dialog v-model:visible="prizeDialog" header="Add Prize" modal :style="{width:'380px'}">
      <div style="display:flex;flex-direction:column;gap:var(--space-4);">
        <div class="form-field"><label class="required">Prize Name</label><InputText v-model="prizeForm.prizeName" fluid /></div>
        <div class="form-field"><label class="required">Quantity</label><InputNumber v-model="prizeForm.quantity" :min="1" fluid /></div>
      </div>
      <template #footer>
        <Button label="Cancel" text @click="prizeDialog=false" />
        <Button label="Add" icon="pi pi-check" @click="createPrize" />
      </template>
    </Dialog>

    <Dialog v-model:visible="participantDialog" header="Setup Participants" modal :style="{width:'620px'}">
      <div style="display:grid;grid-template-columns:1fr 1fr;gap:var(--space-3);margin-bottom:var(--space-4);">
        <div class="form-field">
          <label>Min Point (A) <span class="optional-hint">(optional)</span></label>
          <InputNumber v-model="minPointFilter" :min="0" placeholder="e.g. 10" fluid />
        </div>
        <div class="form-field">
          <label>Max Point (B) <span class="optional-hint">(optional)</span></label>
          <InputNumber v-model="maxPointFilter" :min="0" placeholder="e.g. 100" fluid />
        </div>
      </div>
      <div style="display:flex;align-items:center;gap:var(--space-2);flex-wrap:wrap;margin-bottom:var(--space-4);">
        <Button label="Select by Point Range" icon="pi pi-filter" severity="secondary" outlined size="small" @click="selectParticipantsByPointRange" />
        <Button label="Add by Point Range" icon="pi pi-plus" severity="secondary" outlined size="small" @click="addParticipantsByPointRange" />
        <span style="font-size:12px;color:var(--theme-text-weak);">{{ membersInPointRange.length }} member(s) match current range</span>
      </div>
      <div class="form-field">
        <label class="required">Participants</label>
        <MultiSelect
          v-model="selectedParticipantIds"
          :options="memberOptions"
          optionLabel="displayName"
          optionValue="id"
          optionDisabled="disabled"
          filter
          display="chip"
          placeholder="Select members"
          fluid
        >
          <template #option="{ option }">
            <div style="display:flex;align-items:center;justify-content:space-between;gap:var(--space-2);width:100%;">
              <span>{{ option.displayName }}</span>
              <span style="font-size:12px;color:var(--theme-text-weak);">{{ option.totalPoints ?? 0 }} pts</span>
            </div>
          </template>
        </MultiSelect>
      </div>
      <template #footer>
        <Button label="Cancel" text @click="participantDialog=false" />
        <Button label="Save" icon="pi pi-check" @click="setupParticipants" />
      </template>
    </Dialog>

    <Dialog v-model:visible="winnersDialog" :header="`Winners - ${selectedPrizeName || 'Prize'}`" modal :style="{width:'520px'}">
      <DataTable :value="winners" :loading="winnersLoading" stripedRows>
        <template #empty>
          No winner has been drawn for this prize yet.
        </template>
        <Column field="fullName" header="Winner" />
        <Column field="prizeName" header="Prize" />
      </DataTable>
    </Dialog>

    <Dialog v-model:visible="wheelDialog" header="Spin to Win!" modal :style="{width:'600px'}" @hide="onWheelDialogHide">
      <div style="display:flex; justify-content:center; align-items:center; padding:var(--space-4);">
        <FortuneWheel
          v-if="wheelParticipants.length"
          style="width: 500px; max-width: 100%;"
          :useWeight="true"
          :verify="wheelVerify"
          :canvas="canvasOptions"
          :prizes="wheelParticipants"
          :prizeId="wheelWinnerUserId"
          @rotateStart="onCanvasRotateStart"
          @rotateEnd="onRotateEnd"
        />
        <div v-else>No eligible participants available.</div>
      </div>
    </Dialog>

    <Dialog
      v-model:visible="winnerAnnouncementDialog"
      header="Lucky Winner"
      modal
      :closable="false"
      :style="{ width: '420px' }"
    >
      <div style="text-align:center; padding:var(--space-3) 0;">
        <p style="margin:0 0 var(--space-2); font-size:14px; color:var(--theme-text-weak);">
          Congratulations!
        </p>
        <h3 style="margin:0 0 var(--space-2);">
          {{ latestDrawWinner?.fullName || 'Unknown winner' }}
        </h3>
        <p style="margin:0; font-size:15px;">
          won prize:
          <strong>{{ latestDrawWinner?.prizeName || selectedPrizeName }}</strong>
        </p>
      </div>
      <template #footer>
        <Button label="Confirm" icon="pi pi-check" autofocus @click="winnerAnnouncementDialog = false" />
      </template>
    </Dialog>
  </div>
</template>

<style scoped>
.lucky-grid :deep(.p-datatable-thead > tr > th),
.lucky-grid :deep(.p-datatable-tbody > tr > td) {
  vertical-align: middle;
}

.prize-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  flex-wrap: wrap;
}

.session-name-btn {
  padding: 0;
}
</style>


