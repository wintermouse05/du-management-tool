import http from './http'
import type { GroupRequest, GroupResponse, GroupMemberResponse } from '@/types'

export const groupsApi = {
  getAll() {
    return http.get<GroupResponse[]>('/groups')
  },

  getById(id: number) {
    return http.get<GroupResponse>(`/groups/${id}`)
  },

  getMembers(id: number) {
    return http.get<GroupMemberResponse[]>(`/groups/${id}/members`)
  },

  create(data: GroupRequest) {
    return http.post<GroupResponse>('/groups', data)
  },

  update(id: number, data: GroupRequest) {
    return http.put<GroupResponse>(`/groups/${id}`, data)
  },

  delete(id: number) {
    return http.delete<void>(`/groups/${id}`)
  },

  addMember(groupId: number, userId: number) {
    return http.post<GroupResponse>(`/groups/${groupId}/members`, { userId })
  },

  removeMember(groupId: number, userId: number) {
    return http.delete<void>(`/groups/${groupId}/members/${userId}`)
  },
}
