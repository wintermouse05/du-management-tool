import http from './http'
import type {
  SurveyRequest, SurveyResponse, SurveyCompletionRequest,
  SurveyProgressResponse, SurveyAssignmentUpdateRequest, Page, Pageable
} from '@/types'

export const surveysApi = {
  getAll(params?: Pageable) {
    return http.get<Page<SurveyResponse>>('/surveys', { params })
  },

  getById(id: number) {
    return http.get<SurveyResponse>(`/surveys/${id}`)
  },

  create(data: SurveyRequest) {
    return http.post<SurveyResponse>('/surveys', data)
  },

  update(id: number, data: SurveyRequest) {
    return http.put<SurveyResponse>(`/surveys/${id}`, data)
  },

  assign(surveyId: number, userId: number) {
    return http.post<SurveyProgressResponse>(`/surveys/${surveyId}/assign`, null, {
      params: { userId },
    })
  },

  replaceAssignments(surveyId: number, data: SurveyAssignmentUpdateRequest) {
    return http.put<SurveyProgressResponse>(`/surveys/${surveyId}/assignments`, data)
  },

  complete(surveyId: number, data: SurveyCompletionRequest) {
    return http.post<SurveyProgressResponse>(`/surveys/${surveyId}/complete`, data)
  },

  getProgress(surveyId: number) {
    return http.get<SurveyProgressResponse>(`/surveys/${surveyId}/progress`)
  },
}
