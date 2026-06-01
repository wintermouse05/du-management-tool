import http from './http'
import type {
  AccountPasswordChangeRequest,
  AccountProfileUpdateRequest,
  AccountResponse,
} from '@/types'

export const accountApi = {
  getAccount() {
    return http.get<AccountResponse>('/account')
  },

  updateProfile(data: AccountProfileUpdateRequest) {
    return http.put<AccountResponse>('/account/profile', data)
  },

  changePassword(data: AccountPasswordChangeRequest) {
    return http.put<void>('/account/password', data)
  },
}
