import http from './http'
import type { BookmarkRequest, BookmarkResponse } from '@/types'

export const bookmarksApi = {
  getAll() {
    return http.get<BookmarkResponse[]>('/bookmarks')
  },

  getById(id: number) {
    return http.get<BookmarkResponse>(`/bookmarks/${id}`)
  },

  create(data: BookmarkRequest) {
    return http.post<BookmarkResponse>('/bookmarks', data)
  },

  update(id: number, data: BookmarkRequest) {
    return http.put<BookmarkResponse>(`/bookmarks/${id}`, data)
  },

  delete(id: number) {
    return http.delete<void>(`/bookmarks/${id}`)
  },
}
