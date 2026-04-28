import http from './http'
import type {
  MenuItemRequest, MenuItemResponse,
  MenuScrapeRequest, MenuScrapeItemResponse,
  OrderSessionRequest, OrderSessionResponse,
  UserOrderRequest, UserOrderResponse,
  OrderSessionSummaryResponse,
  Page, Pageable
} from '@/types'
import type { OrderSessionStatus } from '@/types'

export const ordersApi = {
  // Menu Items
  getMenuItems(params?: Pageable) {
    return http.get<Page<MenuItemResponse>>('/orders/menu-items', { params })
  },

  createMenuItem(data: MenuItemRequest) {
    return http.post<MenuItemResponse>('/orders/menu-items', data)
  },

  updateMenuItem(id: number, data: MenuItemRequest) {
    return http.put<MenuItemResponse>(`/orders/menu-items/${id}`, data)
  },

  deleteMenuItem(id: number) {
    return http.delete<void>(`/orders/menu-items/${id}`)
  },

  // Sessions
  getSessions(params?: Pageable) {
    return http.get<Page<OrderSessionResponse>>('/orders/sessions', { params })
  },

  getSessionSummary(sessionId: number) {
    return http.get<OrderSessionSummaryResponse>(`/orders/sessions/${sessionId}/summary`)
  },

  createSession(data: OrderSessionRequest) {
    return http.post<OrderSessionResponse>('/orders/sessions', data)
  },

  updateSessionStatus(sessionId: number, status: OrderSessionStatus) {
    return http.patch<OrderSessionResponse>('/orders/sessions/status', null, {
      params: { sessionId, status },
    })
  },

  // User Orders
  placeOrder(data: UserOrderRequest) {
    return http.post<UserOrderResponse>('/orders/user-orders', data)
  },

  getOrdersBySession(sessionId: number, params?: Pageable) {
    return http.get<Page<UserOrderResponse>>('/orders/user-orders', {
      params: { sessionId, ...params },
    })
  },

  markPaid(orderId: number, paid: boolean) {
    return http.patch<UserOrderResponse>('/orders/user-orders/paid', null, {
      params: { orderId, paid },
    })
  },

  scrapeMenu(data: MenuScrapeRequest) {
    return http.post<MenuScrapeItemResponse[]>('/orders/scrape-menu', data)
  },
}
