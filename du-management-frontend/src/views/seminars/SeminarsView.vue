<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useAuthStore } from '@/stores/auth'
import { seminarsApi } from '@/api/seminars'
import { membersApi } from '@/api/members'
import type { SeminarResponse, SeminarRequest, SeminarVoteResponse } from '@/types'
import { SeminarStatus, VoteType, UserStatus } from '@/types'
import DataTable from 'primevue/datatable'
import Column from 'primevue/column'
import Button from 'primevue/button'
import Dialog from 'primevue/dialog'
import InputText from 'primevue/inputtext'
import Textarea from 'primevue/textarea'
import Select from 'primevue/select'
import DatePicker from 'primevue/datepicker'
import Tag from 'primevue/tag'
import { useToast } from 'primevue/usetoast'

const auth = useAuthStore()
const toast = useToast()
const seminars = ref<SeminarResponse[]>([])
const total = ref(0); const loading = ref(false); const pg = ref(0); const rows = ref(10)
const dialogVisible = ref(false); const editing = ref(false); const editId = ref<number|null>(null)
const form = ref<SeminarRequest>({ title: '', description: '', status: SeminarStatus.PROPOSED })
const formDate = ref<Date|null>(null)
const speakerOptions = ref<Array<{ label: string; value: number }>>([])
const votesDialog = ref(false); const votes = ref<SeminarVoteResponse[]>([]); const voteSeminarId = ref(0)
const materialInput = ref<HTMLInputElement | null>(null)
const materialSeminarId = ref<number | null>(null)

async function load() {
  loading.value = true
  try { const r = await seminarsApi.getAll({ page: pg.value, size: rows.value }); seminars.value = r.data.content; total.value = r.data.totalElements }
  finally { loading.value = false }
}
function onPage(e: any) { pg.value = e.page; rows.value = e.rows; load() }
function openCreate() {
  editing.value = false
  editId.value = null
  form.value = { speakerId: null, title: '', description: '', status: SeminarStatus.PROPOSED }
  formDate.value = null
  dialogVisible.value = true
}

function openEdit(s: SeminarResponse) {
  editing.value = true
  editId.value = s.id
  form.value = { speakerId: s.speakerId, title: s.title, description: s.description || '', scheduledAt: s.scheduledAt, status: s.status }
  formDate.value = s.scheduledAt ? new Date(s.scheduledAt) : null

  if (s.speakerId && !speakerOptions.value.some(option => option.value === s.speakerId)) {
    speakerOptions.value.unshift({ label: s.speakerName || `User #${s.speakerId}`, value: s.speakerId })
  }

  dialogVisible.value = true
}

async function loadSpeakers() {
  try {
    const res = await membersApi.search({ page: 0, size: 200, status: UserStatus.ACTIVE })
    speakerOptions.value = res.data.content.map(member => ({
      label: `${member.fullName} (${member.username})`,
      value: member.id,
    }))
  } catch {
    speakerOptions.value = []
  }
}

async function save() {
  if (formDate.value) form.value.scheduledAt = formDate.value.toISOString()
  try {
    if (editing.value && editId.value) await seminarsApi.update(editId.value, form.value)
    else await seminarsApi.create(form.value)
    toast.add({ severity: 'success', summary: editing.value ? 'Updated' : 'Created', life: 3000 }); dialogVisible.value = false; load()
  } catch (err: any) { toast.add({ severity: 'error', summary: 'Error', detail: err.response?.data?.message, life: 4000 }) }
}

async function vote(id: number, voteType: VoteType) {
  if (!auth.userId) {
    toast.add({ severity: 'error', summary: 'Cannot vote', detail: 'Missing user identity. Please log in again.', life: 3000 })
    return
  }
  try { await seminarsApi.vote(id, { userId: auth.userId, voteType }); toast.add({ severity: 'success', summary: 'Vote recorded', life: 2000 }); load()
  } catch (err: any) { toast.add({ severity: 'error', summary: 'Error', detail: err.response?.data?.message, life: 3000 }) }
}

async function showVotes(id: number) {
  voteSeminarId.value = id
  try { const r = await seminarsApi.getVotes(id, { size: 100 }); votes.value = r.data.content } catch {}
  votesDialog.value = true
}

function openUploadMaterials(id: number) {
  materialSeminarId.value = id
  materialInput.value?.click()
}

async function handleMaterialSelected(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file || !materialSeminarId.value) {
    return
  }

  try {
    await seminarsApi.uploadMaterials(materialSeminarId.value, file)
    toast.add({ severity: 'success', summary: 'Materials uploaded', life: 2500 })
    load()
  } catch (err: any) {
    toast.add({ severity: 'error', summary: 'Upload failed', detail: err.response?.data?.message || 'Unable to upload materials', life: 3000 })
  } finally {
    input.value = ''
    materialSeminarId.value = null
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
    toast.add({ severity: 'error', summary: 'Download failed', detail: err.response?.data?.message || 'Unable to download materials', life: 3000 })
  }
}

function statusSeverity(s: SeminarStatus) { return s === SeminarStatus.DONE ? 'success' : s === SeminarStatus.SCHEDULED ? 'info' : s === SeminarStatus.APPROVED ? 'warn' : 'secondary' }
function fmtDate(d: string|null) { return d ? new Date(d).toLocaleDateString('en-US', { month:'short', day:'numeric', hour:'2-digit', minute:'2-digit' }) : '—' }
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
      <Button label="Propose Seminar" icon="pi pi-plus" @click="openCreate" />
    </div>
    <div class="content-card">
      <input
        ref="materialInput"
        type="file"
        style="display:none"
        @change="handleMaterialSelected"
      />
      <DataTable :value="seminars" :loading="loading" :paginator="true" :rows="rows" :totalRecords="total" :lazy="true" @page="onPage" stripedRows>
        <Column field="title" header="Title" />
        <Column field="speakerName" header="Speaker"><template #body="{data}">{{ data.speakerName || '—' }}</template></Column>
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
            <span v-else>—</span>
          </template>
        </Column>
        <Column field="status" header="Status"><template #body="{data}"><Tag :value="data.status" :severity="statusSeverity(data.status)" /></template></Column>
        <Column header="Actions" style="width:260px">
          <template #body="{data}">
            <div style="display:flex;gap:4px;">
              <Button icon="pi pi-thumbs-up" text rounded severity="success" @click="vote(data.id, VoteType.UPVOTE)" v-tooltip="'Upvote'" />
              <Button icon="pi pi-thumbs-down" text rounded severity="danger" @click="vote(data.id, VoteType.DOWNVOTE)" v-tooltip="'Downvote'" />
              <Button icon="pi pi-eye" text rounded @click="showVotes(data.id)" v-tooltip="'View votes'" />
              <Button v-if="auth.isAdminOrHR" icon="pi pi-upload" text rounded severity="secondary" @click="openUploadMaterials(data.id)" v-tooltip="'Upload materials'" />
              <Button v-if="auth.isAdminOrHR" icon="pi pi-pencil" text rounded severity="info" @click="openEdit(data)" />
            </div>
          </template>
        </Column>
      </DataTable>
    </div>
    <Dialog v-model:visible="dialogVisible" :header="editing ? 'Edit Seminar' : 'Propose Seminar'" modal :style="{width:'520px'}">
      <div style="display:flex;flex-direction:column;gap:var(--space-4);">
        <div class="form-field">
          <label>Speaker</label>
          <Select
            v-model="form.speakerId"
            :options="speakerOptions"
            optionLabel="label"
            optionValue="value"
            placeholder="Select speaker"
            showClear
            fluid
          />
        </div>
        <div class="form-field"><label>Title</label><InputText v-model="form.title" fluid /></div>
        <div class="form-field"><label>Description</label><Textarea v-model="form.description" rows="3" fluid /></div>
        <div class="form-field"><label>Scheduled At</label><DatePicker v-model="formDate" showTime hourFormat="24" fluid /></div>
        <div v-if="editing" class="form-field"><label>Status</label><Select v-model="form.status" :options="statusOpts" optionLabel="label" optionValue="value" fluid /></div>
      </div>
      <template #footer><Button label="Cancel" text @click="dialogVisible=false" /><Button :label="editing?'Update':'Create'" icon="pi pi-check" @click="save" /></template>
    </Dialog>
    <Dialog v-model:visible="votesDialog" header="Votes" modal :style="{width:'400px'}">
      <DataTable :value="votes" stripedRows>
        <Column field="userId" header="User ID" />
        <Column field="voteType" header="Vote"><template #body="{data}"><Tag :value="data.voteType" :severity="data.voteType==='UPVOTE'?'success':'danger'" /></template></Column>
      </DataTable>
    </Dialog>
  </div>
</template>
