import http from './http'
import type {
  EventRequest, EventResponse, EventAttendanceRequest,
  EventAttendeeResponse, Page, Pageable
} from '@/types'

export const eventsApi = {
  getAll(params?: Pageable) {
    return http.get<Page<EventResponse>>('/events', { params })
  },

  getById(id: number) {
    return http.get<EventResponse>(`/events/${id}`)
  },

  create(data: EventRequest) {
    return http.post<EventResponse>('/events', data)
  },

  update(id: number, data: EventRequest) {
    return http.put<EventResponse>(`/events/${id}`, data)
  },

  rsvp(eventId: number, data: EventAttendanceRequest) {
    return http.post<EventAttendeeResponse>(`/events/${eventId}/rsvp`, data)
  },

  checkIn(eventId: number, userId: number) {
    return http.post<EventAttendeeResponse>(`/events/${eventId}/check-in`, null, {
      params: { userId },
    })
  },

  getAttendees(eventId: number, params?: Pageable) {
    return http.get<Page<EventAttendeeResponse>>(`/events/${eventId}/attendees`, { params })
  },
}
