import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import { bookmarksApi } from '@/api/bookmarks'
import type { BookmarkRequest, BookmarkResponse } from '@/types'

function bookmarkTimestamp(bookmark: BookmarkResponse): number {
  if (!bookmark.updatedAt) return 0
  const parsed = new Date(bookmark.updatedAt).getTime()
  return Number.isNaN(parsed) ? 0 : parsed
}

function sortBookmarks(items: BookmarkResponse[]): BookmarkResponse[] {
  return [...items].sort((a, b) => {
    if (a.pinned !== b.pinned) return Number(b.pinned) - Number(a.pinned)
    return a.title.localeCompare(b.title)
  })
}

function sortPinnedBookmarksByRecency(items: BookmarkResponse[]): BookmarkResponse[] {
  return [...items].sort((a, b) => {
    const timestampDiff = bookmarkTimestamp(b) - bookmarkTimestamp(a)
    if (timestampDiff !== 0) return timestampDiff
    return b.id - a.id
  })
}

export const useBookmarksStore = defineStore('bookmarks', () => {
  const bookmarks = ref<BookmarkResponse[]>([])
  const loading = ref(false)
  const loaded = ref(false)

  const pinnedHeaderBookmarks = computed(() =>
    sortPinnedBookmarksByRecency(bookmarks.value.filter(bookmark => bookmark.pinned)).slice(0, 5)
  )

  function setBookmarks(items: BookmarkResponse[]) {
    bookmarks.value = sortBookmarks(items)
    loaded.value = true
  }

  function upsertBookmark(bookmark: BookmarkResponse) {
    setBookmarks([
      ...bookmarks.value.filter(item => item.id !== bookmark.id),
      bookmark,
    ])
  }

  async function loadBookmarks(options: { force?: boolean } = {}) {
    if (loaded.value && !options.force) {
      return bookmarks.value
    }

    loading.value = true
    try {
      const response = await bookmarksApi.getAll()
      setBookmarks(response.data)
      return bookmarks.value
    } finally {
      loading.value = false
    }
  }

  async function createBookmark(data: BookmarkRequest) {
    const response = await bookmarksApi.create(data)
    upsertBookmark(response.data)
    return response.data
  }

  async function updateBookmark(id: number, data: BookmarkRequest) {
    const response = await bookmarksApi.update(id, data)
    upsertBookmark(response.data)
    return response.data
  }

  async function deleteBookmark(id: number) {
    await bookmarksApi.delete(id)
    bookmarks.value = bookmarks.value.filter(item => item.id !== id)
  }

  function clearBookmarks() {
    bookmarks.value = []
    loaded.value = false
  }

  return {
    bookmarks,
    loading,
    loaded,
    pinnedHeaderBookmarks,
    loadBookmarks,
    createBookmark,
    updateBookmark,
    deleteBookmark,
    clearBookmarks,
  }
})
