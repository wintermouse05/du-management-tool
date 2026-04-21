import http from './http'
import type {
  NotificationJobResponse,
  NotificationJobToggleRequest,
  NotificationTemplateRequest,
  NotificationTemplateResponse,
  NotificationInboxResponse,
  NotificationUnreadCountResponse,
  Page,
  Pageable,
} from '@/types'

export const notificationsApi = {
  getJobs() {
    return http.get<NotificationJobResponse[]>('/notifications/jobs')
  },

  setJobEnabled(code: string, payload: NotificationJobToggleRequest) {
    return http.patch<NotificationJobResponse>(`/notifications/jobs/${code}/enabled`, payload)
  },

  getTemplates() {
    return http.get<NotificationTemplateResponse[]>('/notifications/templates')
  },

  createTemplate(payload: NotificationTemplateRequest) {
    return http.post<NotificationTemplateResponse>('/notifications/templates', payload)
  },

  updateTemplate(code: string, payload: NotificationTemplateRequest) {
    return http.put<NotificationTemplateResponse>(`/notifications/templates/${code}`, payload)
  },

  deleteTemplate(code: string) {
    return http.delete<void>(`/notifications/templates/${code}`)
  },

  getMyNotifications(params?: Pageable) {
    return http.get<Page<NotificationInboxResponse>>('/notifications/me', { params })
  },

  getMyUnreadCount() {
    return http.get<NotificationUnreadCountResponse>('/notifications/me/unread-count')
  },

  markAsRead(notificationId: number) {
    return http.patch<NotificationInboxResponse>(`/notifications/me/${notificationId}/read`)
  },

  markAllAsRead() {
    return http.patch<NotificationUnreadCountResponse>('/notifications/me/read-all')
  },

  triggerSurveyReminder(surveyId: number) {
    return http.post<{ message: string }>('/notifications/survey-reminder', null, {
      params: { surveyId },
    })
  },
}
