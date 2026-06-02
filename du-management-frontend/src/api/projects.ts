import http from './http'
import type {
  Page,
  Pageable,
  ProjectMemberRequest,
  ProjectMemberResponse,
  ProjectAvailabilitySummaryResponse,
  ProjectRequest,
  ProjectResponse,
  ProjectTaskRequest,
  ProjectTaskResponse,
} from '@/types'

export const projectsApi = {
  getAll(params?: Pageable) {
    return http.get<Page<ProjectResponse>>('/projects', { params })
  },

  getById(id: number) {
    return http.get<ProjectResponse>(`/projects/${id}`)
  },

  getAvailabilitySummary() {
    return http.get<ProjectAvailabilitySummaryResponse>('/projects/availability-summary')
  },

  create(data: ProjectRequest) {
    return http.post<ProjectResponse>('/projects', data)
  },

  update(id: number, data: ProjectRequest) {
    return http.put<ProjectResponse>(`/projects/${id}`, data)
  },

  delete(id: number) {
    return http.delete<void>(`/projects/${id}`)
  },

  getMembers(projectId: number) {
    return http.get<ProjectMemberResponse[]>(`/projects/${projectId}/members`)
  },

  addMember(projectId: number, data: ProjectMemberRequest) {
    return http.post<ProjectMemberResponse>(`/projects/${projectId}/members`, data)
  },

  updateMember(projectId: number, userId: number, data: ProjectMemberRequest) {
    return http.put<ProjectMemberResponse>(`/projects/${projectId}/members/${userId}`, data)
  },

  removeMember(projectId: number, userId: number) {
    return http.delete<void>(`/projects/${projectId}/members/${userId}`)
  },

  getTasks(projectId: number) {
    return http.get<ProjectTaskResponse[]>(`/projects/${projectId}/tasks`)
  },

  createTask(projectId: number, data: ProjectTaskRequest) {
    return http.post<ProjectTaskResponse>(`/projects/${projectId}/tasks`, data)
  },

  updateTask(projectId: number, taskId: number, data: ProjectTaskRequest) {
    return http.put<ProjectTaskResponse>(`/projects/${projectId}/tasks/${taskId}`, data)
  },

  deleteTask(projectId: number, taskId: number) {
    return http.delete<void>(`/projects/${projectId}/tasks/${taskId}`)
  },
}
