import http from './http'
import type {
  SurveyRequest, SurveyResponse, SurveyCompletionRequest,
  SurveyProgressResponse, Page, Pageable
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

  complete(surveyId: number, data: SurveyCompletionRequest) {
    return http.post<SurveyProgressResponse>(`/surveys/${surveyId}/complete`, data)
  },

  getProgress(surveyId: number) {
    return http.get<SurveyProgressResponse>(`/surveys/${surveyId}/progress`)
  },
}
