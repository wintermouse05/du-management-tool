<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { useAuthStore } from '@/stores/auth'
import { seminarsApi } from '@/api/seminars'
import { membersApi } from '@/api/members'
import type { SeminarResponse, SeminarRequest, SeminarVoteResponse, SeminarVoteSummaryResponse } from '@/types'
import { SeminarStatus, UserStatus, VoteType } from '@/types'
import { formatLocalDateTime, parseApiDate, toLocalDateTime } from '@/utils/datetime'
import { getApiErrorDetail } from '@/utils/apiError'
import { formatMemberName } from '@/utils/memberDisplay'
import DataTable from 'primevue/datatable'
import Column from 'primevue/column'
import Button from 'primevue/button'
import Dialog from 'primevue/dialog'
import Checkbox from 'primevue/checkbox'
import InputText from 'primevue/inputtext'
import Textarea from 'primevue/textarea'
import Select from 'primevue/select'
import DatePicker from 'primevue/datepicker'
import Tag from 'primevue/tag'
import { useToast } from 'primevue/usetoast'

const auth = useAuthStore()
const toast = useToast()

const seminars = ref<SeminarResponse[]>([])
const total = ref(0)
const loading = ref(false)
const pg = ref(0)
const rows = ref(10)

const dialogVisible = ref(false)
const editing = ref(false)
const editId = ref<number | null>(null)
const form = ref<SeminarRequest>({ title: '', description: '', scheduledAt: '', status: SeminarStatus.PENDING })
const formDate = ref<Date | null>(null)

const speakerOptions = ref<Array<{ label: string; value: number; disabled: boolean }>>([])

const votesDialog = ref(false)
const votes = ref<SeminarVoteResponse[]>([])
const voteSummary = ref<SeminarVoteSummaryResponse>({ upvotes: 0, downvotes: 0 })
const voteSeminarId = ref(0)

const materialFile = ref<File | null>(null)
const materialFileName = ref('')
const materialFileInputKey = ref(0)
const currentDialogSeminar = ref<SeminarResponse | null>(null)

const selectedSeminarIds = ref<number[]>([])
const selectedSeminars = computed(() => seminars.value.filter(item => selectedSeminarIds.value.includes(item.id)))
const approving = ref(false)

const selectableSeminarIds = computed(() =>
  seminars.value.filter(canSelectSeminar).map(item => item.id),
)

const selectedSelectableCount = computed(() =>
  selectableSeminarIds.value.filter(id => selectedSeminarIds.value.includes(id)).length,
)

const allSelectableSelected = computed(() =>
  selectableSeminarIds.value.length > 0 && selectedSelectableCount.value === selectableSeminarIds.value.length,
)

const someSelectableSelected = computed(() =>
  selectedSelectableCount.value > 0 && !allSelectableSelected.value,
)

async function load() {
  loading.value = true
  try {
    const r = await seminarsApi.getAll({ page: pg.value, size: rows.value })
    seminars.value = [...r.data.content].sort((left, right) => {
      const statusDiff = seminarStatusRank(displayStatus(left)) - seminarStatusRank(displayStatus(right))
      if (statusDiff !== 0) {
        return statusDiff
      }

      const timeDiff = seminarTimeOrder(left) - seminarTimeOrder(right)
      if (timeDiff !== 0) {
        return timeDiff
      }

      return right.id - left.id
    })
    total.value = r.data.totalElements
    const pageIds = new Set(seminars.value.map(item => item.id))
    selectedSeminarIds.value = selectedSeminarIds.value.filter(id => pageIds.has(id))
  } finally {
    loading.value = false
  }
}

function onPage(e: any) {
  pg.value = e.page
  rows.value = e.rows
  load()
}

function openCreate() {
  editing.value = false
  editId.value = null
  currentDialogSeminar.value = null
  form.value = { speakerId: null, title: '', description: '', scheduledAt: '', status: SeminarStatus.PENDING }
  formDate.value = null
  resetMaterialSelection()
  dialogVisible.value = true
}

function openEdit(s: SeminarResponse) {
  editing.value = true
  editId.value = s.id
  currentDialogSeminar.value = s
  form.value = {
    speakerId: s.speakerId,
    title: s.title,
    description: s.description || '',
    scheduledAt: s.scheduledAt || '',
    status: s.status,
  }
  formDate.value = parseApiDate(s.scheduledAt)
  resetMaterialSelection()

  if (s.speakerId && !speakerOptions.value.some(option => option.value === s.speakerId)) {
    speakerOptions.value.unshift({
      label: s.speakerName || `User #${s.speakerId}`,
      value: s.speakerId,
      disabled: s.speakerName?.endsWith(' (inactive)') || false,
    })
  }

  dialogVisible.value = true
}

async function loadSpeakers() {
  try {
    const res = await membersApi.search({ page: 0, size: 200 })
    speakerOptions.value = res.data.content.map(member => ({
      label: `${formatMemberName(member)} (${member.username})`,
      value: member.id,
      disabled: member.status === UserStatus.INACTIVE,
    }))
  } catch {
    speakerOptions.value = []
  }
}

async function save() {
  if (!formDate.value) {
    toast.add({
      severity: 'warn',
      summary: 'Schedule required',
      detail: 'Please choose date and time for the seminar.',
      life: 3000,
    })
    return
  }

  form.value.scheduledAt = toLocalDateTime(formDate.value)
  try {
    let saved: SeminarResponse
    if (editing.value && editId.value) {
      const response = await seminarsApi.update(editId.value, form.value)
      saved = response.data
    } else {
      const response = await seminarsApi.create(form.value)
      saved = response.data
    }

    if (materialFile.value) {
      await seminarsApi.uploadMaterials(saved.id, materialFile.value)
    }

    toast.add({
      severity: 'success',
      summary: editing.value ? 'Updated' : 'Created',
      detail: materialFile.value ? 'Seminar and materials saved successfully' : undefined,
      life: 3000,
    })
    resetMaterialSelection()
    currentDialogSeminar.value = null
    dialogVisible.value = false
    await load()
  } catch (err: any) {
    toast.add({ severity: 'error', summary: 'Error', detail: getApiErrorDetail(err), life: 4000 })
  }
}

function isExpiredSeminar(seminar: SeminarResponse) {
  return !!seminar.scheduledAt && new Date(seminar.scheduledAt).getTime() < Date.now()
}

function voteDisplay(voteType: VoteType) {
  return voteType === VoteType.UPVOTE ? 'Like' : 'Dislike'
}

function voteSeverity(voteType: VoteType) {
  return voteType === VoteType.UPVOTE ? 'success' : 'danger'
}

async function vote(id: number, voteType: VoteType) {
  if (!auth.userId) {
    toast.add({ severity: 'error', summary: 'Cannot vote', detail: 'Missing user identity. Please log in again.', life: 3000 })
    return
  }

  try {
    await seminarsApi.vote(id, { userId: auth.userId, voteType })
    const target = seminars.value.find(item => item.id === id)
    if (target) {
      target.currentUserVote = voteType
    }
    if (votesDialog.value && voteSeminarId.value === id) {
      await refreshVotes(id)
    }
    toast.add({ severity: 'success', summary: 'Vote recorded', life: 2000 })
  } catch (err: any) {
    toast.add({ severity: 'error', summary: 'Error', detail: getApiErrorDetail(err), life: 3000 })
  }
}

async function showVotes(id: number) {
  voteSeminarId.value = id
  await refreshVotes(id)
  votesDialog.value = true
}

function handleDialogMaterialSelected(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) {
    materialFile.value = null
    materialFileName.value = ''
    return
  }

  const maxFileSizeBytes = 10 * 1024 * 1024
  if (file.size > maxFileSizeBytes) {
    toast.add({
      severity: 'error',
      summary: 'Upload failed',
      detail: 'File is larger than 10MB limit',
      life: 3000,
    })
    materialFile.value = null
    materialFileName.value = ''
    materialFileInputKey.value += 1
    return
  }

  materialFile.value = file
  materialFileName.value = file.name
}

function resetMaterialSelection() {
  materialFile.value = null
  materialFileName.value = ''
  materialFileInputKey.value += 1
}

function canSelectSeminar(seminar: SeminarResponse) {
  const status = displayStatus(seminar)
  return status !== SeminarStatus.APPROVED && status !== SeminarStatus.DONE
}

function isSeminarSelected(seminarId: number) {
  return selectedSeminarIds.value.includes(seminarId)
}

function toggleSeminarSelection(seminarId: number, checked: boolean) {
  if (checked) {
    if (!selectedSeminarIds.value.includes(seminarId)) {
      selectedSeminarIds.value = [...selectedSeminarIds.value, seminarId]
    }
    return
  }
  selectedSeminarIds.value = selectedSeminarIds.value.filter(id => id !== seminarId)
}

function toggleSelectAll(checked: boolean) {
  if (checked) {
    const merged = new Set([...selectedSeminarIds.value, ...selectableSeminarIds.value])
    selectedSeminarIds.value = Array.from(merged)
    return
  }
  const selectable = new Set(selectableSeminarIds.value)
  selectedSeminarIds.value = selectedSeminarIds.value.filter(id => !selectable.has(id))
}

async function approveSelected() {
  if (!selectedSeminarIds.value.length) {
    return
  }

  const approvableIds = selectedSeminarIds.value.filter(id =>
    seminars.value.some(item => item.id === id && canSelectSeminar(item)),
  )
  if (!approvableIds.length) {
    toast.add({ severity: 'warn', summary: 'Nothing to approve', detail: 'Only PENDING seminars can be approved.', life: 3000 })
    selectedSeminarIds.value = []
    return
  }

  approving.value = true
  try {
    const res = await seminarsApi.approveMany(approvableIds)
    toast.add({
      severity: 'success',
      summary: 'Approved',
      detail: `${res.data.updated} seminar(s) approved`,
      life: 3000,
    })
    selectedSeminarIds.value = []
    await load()
  } catch (err: any) {
    toast.add({ severity: 'error', summary: 'Approve failed', detail: getApiErrorDetail(err, 'Unable to approve seminars'), life: 4000 })
  } finally {
    approving.value = false
  }
}

async function refreshVotes(seminarId: number) {
  try {
    const [votesResponse, summaryResponse] = await Promise.all([
      seminarsApi.getVotes(seminarId, { size: 100 }),
      seminarsApi.getVoteSummary(seminarId),
    ])
    votes.value = votesResponse.data.content
    voteSummary.value = summaryResponse.data
  } catch {
    votes.value = []
    voteSummary.value = { upvotes: 0, downvotes: 0 }
  }
}

function extractFilename(contentDisposition?: string): string | null {
  if (!contentDisposition) {
    return null
  }

  const utf8Match = contentDisposition.match(/filename\*=UTF-8''([^;]+)/i)
  if (utf8Match?.[1]) {
    try {
      return decodeURIComponent(utf8Match[1].replace(/"/g, '').trim())
    } catch {
      return utf8Match[1].replace(/"/g, '').trim()
    }
  }

  const plainMatch = contentDisposition.match(/filename="?([^";]+)"?/i)
  return plainMatch?.[1]?.trim() || null
}

async function downloadMaterials(seminar: SeminarResponse) {
  try {
    const res = await seminarsApi.downloadMaterials(seminar.id)
    const disposition = res.headers['content-disposition'] as string | undefined
    const filename = extractFilename(disposition) || `seminar-${seminar.id}-materials`

    const blobUrl = URL.createObjectURL(res.data)
    const link = document.createElement('a')
    link.href = blobUrl
    link.download = filename
    link.click()
    URL.revokeObjectURL(blobUrl)
  } catch (err: any) {
    toast.add({ severity: 'error', summary: 'Download failed', detail: getApiErrorDetail(err, 'Unable to download materials'), life: 3000 })
  }
}

function statusSeverity(s: SeminarStatus) {
  return s === SeminarStatus.DONE ? 'success' : s === SeminarStatus.APPROVED ? 'warn' : 'secondary'
}

function displayStatus(seminar: SeminarResponse) {
  return isExpiredSeminar(seminar) ? SeminarStatus.DONE : seminar.status
}

function seminarStatusRank(status: SeminarStatus) {
  if (status === SeminarStatus.PENDING) return 0
  if (status === SeminarStatus.APPROVED) return 1
  return 2
}

function seminarTimeOrder(seminar: SeminarResponse) {
  if (!seminar.scheduledAt) {
    return Number.MAX_SAFE_INTEGER
  }
  const timestamp = new Date(seminar.scheduledAt).getTime()
  return Number.isNaN(timestamp) ? Number.MAX_SAFE_INTEGER : timestamp
}

function fmtDate(d: string | null) {
  return d ? formatLocalDateTime(d, 'en-US', { month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit' }) : '-'
}

const statusOpts = Object.values(SeminarStatus).map(v => ({ label: v, value: v }))

onMounted(() => {
  load()
  loadSpeakers()
})
</script>

<template>
  <div class="page-container">
    <div class="page-header">
      <div><h2>Seminars</h2><p class="page-subtitle">Propose topics, vote, and schedule seminars</p></div>
      <div style="display:flex;gap:8px;">
        <Button label="Propose Seminar" icon="pi pi-plus" @click="openCreate" />
        <Button
          v-if="auth.isAdminOrHR && selectedSeminars.length > 0"
          label="Approve"
          icon="pi pi-check"
          severity="success"
          :loading="approving"
          @click="approveSelected"
        />
      </div>
    </div>
    <div class="content-card">
      <DataTable
        :value="seminars"
        dataKey="id"
        :loading="loading"
        :paginator="true"
        :rows="rows"
        :totalRecords="total"
        :lazy="true"
        @page="onPage"
        stripedRows
      >
        <template #empty>
          No Seminar has been created yet. Please create a new Seminar.
        </template>
        <Column v-if="auth.isAdminOrHR" headerStyle="width: 3rem">
          <template #header>
            <Checkbox
              v-if="selectableSeminarIds.length > 0"
              :modelValue="allSelectableSelected"
              :indeterminate="someSelectableSelected"
              :binary="true"
              @update:modelValue="toggleSelectAll(Boolean($event))"
            />
          </template>
          <template #body="{ data }">
            <Checkbox
              v-if="canSelectSeminar(data)"
              :modelValue="isSeminarSelected(data.id)"
              :binary="true"
              @update:modelValue="toggleSeminarSelection(data.id, Boolean($event))"
            />
          </template>
        </Column>
        <Column field="title" header="Title" />
        <Column field="speakerName" header="Speaker"><template #body="{data}">{{ data.speakerName || '-' }}</template></Column>
        <Column field="scheduledAt" header="Schedule"><template #body="{data}">{{ fmtDate(data.scheduledAt) }}</template></Column>
        <Column field="materialsUrl" header="Materials">
          <template #body="{ data }">
            <Button
              v-if="data.materialsUrl"
              label="Download"
              icon="pi pi-download"
              text
              @click="downloadMaterials(data)"
            />
            <span v-else>-</span>
          </template>
        </Column>
        <Column field="status" header="Status">
          <template #body="{data}">
            <Tag :value="displayStatus(data)" :severity="statusSeverity(displayStatus(data))" />
          </template>
        </Column>
        <Column header="Actions" style="width:280px">
          <template #body="{data}">
            <div style="display:flex;gap:4px;">
              <Button
                icon="pi pi-thumbs-up"
                rounded
                severity="success"
                :text="data.currentUserVote !== VoteType.UPVOTE"
                :outlined="data.currentUserVote !== VoteType.UPVOTE"
                @click="vote(data.id, VoteType.UPVOTE)"
                v-tooltip="'Like'"
              />
              <Button
                icon="pi pi-thumbs-down"
                rounded
                severity="danger"
                :text="data.currentUserVote !== VoteType.DOWNVOTE"
                :outlined="data.currentUserVote !== VoteType.DOWNVOTE"
                @click="vote(data.id, VoteType.DOWNVOTE)"
                v-tooltip="'Dislike'"
              />
              <Button icon="pi pi-eye" text rounded @click="showVotes(data.id)" v-tooltip="'View votes'" />
              <Button v-if="auth.isAdminOrHR" icon="pi pi-pencil" text rounded severity="info" @click="openEdit(data)" />
            </div>
          </template>
        </Column>
      </DataTable>
    </div>
    <Dialog v-model:visible="dialogVisible" :header="editing ? 'Edit Seminar' : 'Propose Seminar'" modal :style="{width:'520px'}">
      <div style="display:flex;flex-direction:column;gap:var(--space-4);">
        <div class="form-field">
          <label>Speaker <span class="optional-hint">(optional)</span></label>
          <Select
            v-model="form.speakerId"
            :options="speakerOptions"
            optionLabel="label"
            optionValue="value"
            optionDisabled="disabled"
            placeholder="Select speaker"
            showClear
            fluid
          />
        </div>
        <div class="form-field"><label class="required">Title</label><InputText v-model="form.title" fluid /></div>
        <div class="form-field"><label>Description <span class="optional-hint">(optional)</span></label><Textarea v-model="form.description" rows="3" fluid /></div>
        <div class="form-field"><label class="required">Scheduled At</label><DatePicker v-model="formDate" showTime hourFormat="24" fluid /></div>
        <div class="form-field">
          <label>Materials File <span class="optional-hint">(optional, max 10MB)</span></label>
          <input :key="materialFileInputKey" type="file" @change="handleDialogMaterialSelected" />
          <small v-if="materialFileName">{{ materialFileName }}</small>
          <small v-else-if="editing && currentDialogSeminar?.materialsUrl">Current materials already uploaded</small>
        </div>
        <div v-if="editing && currentDialogSeminar?.materialsUrl" class="form-field">
          <Button label="Download Current Materials" icon="pi pi-download" text @click="downloadMaterials(currentDialogSeminar)" />
        </div>
        <div v-if="editing" class="form-field"><label>Status <span class="optional-hint">(optional)</span></label><Select v-model="form.status" :options="statusOpts" optionLabel="label" optionValue="value" fluid /></div>
      </div>
      <template #footer><Button label="Cancel" text @click="dialogVisible=false" /><Button :label="editing?'Update':'Create'" icon="pi pi-check" @click="save" /></template>
    </Dialog>
    <Dialog v-model:visible="votesDialog" header="Votes" modal :style="{width:'440px'}">
      <div style="display:flex;gap:8px;margin-bottom:12px;">
        <Tag :value="`Like: ${voteSummary.upvotes}`" severity="success" />
        <Tag :value="`Dislike: ${voteSummary.downvotes}`" severity="danger" />
      </div>
      <DataTable :value="votes" stripedRows>
        <template #empty>
          No vote found for this Seminar yet.
        </template>
        <Column field="fullName" header="User" />
        <Column field="voteType" header="Vote">
          <template #body="{data}">
            <Tag :value="voteDisplay(data.voteType)" :severity="voteSeverity(data.voteType)" />
          </template>
        </Column>
      </DataTable>
    </Dialog>
  </div>
</template>


