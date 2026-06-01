<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { storeToRefs } from 'pinia'
import { useAuthStore } from '@/stores/auth'
import { useBookmarksStore } from '@/stores/bookmarks'
import type { BookmarkRequest, BookmarkResponse } from '@/types'
import { formatLocalDateTime } from '@/utils/datetime'
import { getApiErrorDetail } from '@/utils/apiError'
import DataTable from 'primevue/datatable'
import Column from 'primevue/column'
import Button from 'primevue/button'
import Dialog from 'primevue/dialog'
import InputText from 'primevue/inputtext'
import Textarea from 'primevue/textarea'
import Checkbox from 'primevue/checkbox'
import Tag from 'primevue/tag'
import { useToast } from 'primevue/usetoast'

const auth = useAuthStore()
const bookmarksStore = useBookmarksStore()
const toast = useToast()
const { bookmarks, loading } = storeToRefs(bookmarksStore)

const dialogVisible = ref(false)
const editing = ref(false)
const editId = ref<number | null>(null)
const form = ref<BookmarkRequest>({
  title: '',
  url: '',
  description: '',
  category: '',
  pinned: false,
})

const deleteConfirmDialog = ref(false)
const deleteTargetId = ref<number | null>(null)

async function load() {
  try {
    await bookmarksStore.loadBookmarks({ force: true })
  } catch (err: any) {
    toast.add({ severity: 'error', summary: 'Error', detail: getApiErrorDetail(err, 'Failed to load bookmarks'), life: 3000 })
  }
}

function openCreate() {
  editing.value = false
  editId.value = null
  form.value = { title: '', url: '', description: '', category: '', pinned: false }
  dialogVisible.value = true
}

function openEdit(bookmark: BookmarkResponse) {
  editing.value = true
  editId.value = bookmark.id
  form.value = {
    title: bookmark.title,
    url: bookmark.url,
    description: bookmark.description || '',
    category: bookmark.category || '',
    pinned: bookmark.pinned,
  }
  dialogVisible.value = true
}

function canManageBookmark(bookmark: BookmarkResponse) {
  if (auth.isAdminOrHR) {
    return true
  }

  const currentUsername = auth.username?.trim().toLowerCase() || ''
  if (!currentUsername) {
    return false
  }

  return (bookmark.createdBy || '').trim().toLowerCase() === currentUsername
}

async function save() {
  if (!form.value.url?.startsWith('http://') && !form.value.url?.startsWith('https://')) {
    toast.add({ severity: 'warn', summary: 'Invalid URL', detail: 'URL must start with http:// or https://', life: 2500 })
    return
  }

  try {
    if (editing.value && editId.value) {
      await bookmarksStore.updateBookmark(editId.value, form.value)
      toast.add({ severity: 'success', summary: 'Bookmark updated', life: 2200 })
    } else {
      await bookmarksStore.createBookmark(form.value)
      toast.add({ severity: 'success', summary: 'Bookmark created', life: 2200 })
    }
    dialogVisible.value = false
  } catch (err: any) {
    toast.add({ severity: 'error', summary: 'Error', detail: getApiErrorDetail(err, 'Failed to save bookmark'), life: 3000 })
  }
}

function confirmDelete(id: number) {
  deleteTargetId.value = id
  deleteConfirmDialog.value = true
}

async function doDelete() {
  if (!deleteTargetId.value) {
    return
  }
  try {
    await bookmarksStore.deleteBookmark(deleteTargetId.value)
    toast.add({ severity: 'success', summary: 'Bookmark deleted', life: 2200 })
    deleteConfirmDialog.value = false
    deleteTargetId.value = null
  } catch (err: any) {
    toast.add({ severity: 'error', summary: 'Error', detail: getApiErrorDetail(err, 'Failed to delete bookmark'), life: 3000 })
  }
}

function fmtUpdatedAt(value: string | null) {
  return value ? formatLocalDateTime(value) : '-'
}

function fmtUpdatedBy(value: string | null) {
  return value?.trim() ? value : 'system'
}

onMounted(load)
</script>

<template>
  <div class="page-container">
    <div class="page-header">
      <div>
        <h2>Bookmarks</h2>
        <p class="page-subtitle">Shared links used frequently by the team</p>
      </div>
      <Button v-if="auth.isAuthenticated" label="Add Bookmark" icon="pi pi-plus" @click="openCreate" />
    </div>

    <div class="content-card">
      <DataTable :value="bookmarks" :loading="loading" stripedRows>
        <template #empty>
          No Bookmark has been created yet. Please create a new Bookmark.
        </template>
        <Column field="title" header="Title" />
        <Column field="category" header="Category" style="width:160px">
          <template #body="{ data }">
            <span>{{ data.category || '-' }}</span>
          </template>
        </Column>
        <Column field="url" header="Link" style="width:180px">
          <template #body="{ data }">
            <a :href="data.url" target="_blank" rel="noopener noreferrer" style="color:var(--theme-blue);">
              Open <i class="pi pi-external-link" style="font-size:11px"></i>
            </a>
          </template>
        </Column>
        <Column field="description" header="Description">
          <template #body="{ data }">
            <span>{{ data.description || '-' }}</span>
          </template>
        </Column>
        <Column header="Pinned" style="width:110px">
          <template #body="{ data }">
            <Tag v-if="data.pinned" value="Pinned" severity="info" />
            <span v-else>-</span>
          </template>
        </Column>
        <Column header="Updated" style="width:190px">
          <template #body="{ data }">
            <div style="display:flex;flex-direction:column;">
              <span>{{ fmtUpdatedAt(data.updatedAt) }}</span>
              <span class="caption">by {{ fmtUpdatedBy(data.updatedBy) }}</span>
            </div>
          </template>
        </Column>
        <Column header="Actions" style="width:170px">
          <template #body="{ data }">
            <div style="display:flex;gap:4px;">
              <Button v-if="canManageBookmark(data)" icon="pi pi-pencil" text rounded severity="info" @click="openEdit(data)" />
              <Button v-if="canManageBookmark(data)" icon="pi pi-trash" text rounded severity="danger" @click="confirmDelete(data.id)" />
            </div>
          </template>
        </Column>
      </DataTable>
    </div>

    <Dialog v-model:visible="dialogVisible" :header="editing ? 'Edit Bookmark' : 'Create Bookmark'" modal :style="{ width: '500px' }">
      <div style="display:flex;flex-direction:column;gap:var(--space-4);">
        <div class="form-field">
          <label class="required">Title</label>
          <InputText v-model="form.title" placeholder="e.g. Team wiki" fluid />
        </div>
        <div class="form-field">
          <label class="required">Link (URL)</label>
          <InputText v-model="form.url" placeholder="https://..." fluid />
        </div>
        <div class="form-field">
          <label>Category <span class="optional-hint">(optional)</span></label>
          <InputText v-model="form.category" placeholder="e.g. Internal, Tracking, Docs" fluid />
        </div>
        <div class="form-field">
          <label>Description <span class="optional-hint">(optional)</span></label>
          <Textarea v-model="form.description" rows="3" placeholder="Optional note for the team" fluid />
        </div>
        <div class="form-field" style="display:flex;flex-direction:row;align-items:center;gap:var(--space-3);">
          <Checkbox v-model="form.pinned" :binary="true" input-id="bookmarkPinned" />
          <label for="bookmarkPinned">Pin this link to top <span class="optional-hint">(optional)</span></label>
        </div>
      </div>
      <template #footer>
        <Button label="Cancel" text @click="dialogVisible = false" />
        <Button :label="editing ? 'Update' : 'Create'" icon="pi pi-check" @click="save" />
      </template>
    </Dialog>

    <Dialog v-model:visible="deleteConfirmDialog" header="Delete Bookmark" modal :style="{ width: '360px' }">
      <p>Are you sure you want to delete this bookmark?</p>
      <template #footer>
        <Button label="Cancel" text @click="deleteConfirmDialog = false" />
        <Button label="Delete" severity="danger" icon="pi pi-trash" @click="doDelete" />
      </template>
    </Dialog>
  </div>
</template>


