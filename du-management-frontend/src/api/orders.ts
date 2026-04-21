import http from './http'
import type {
  MenuItemRequest, MenuItemResponse,
  OrderSessionRequest, OrderSessionResponse,
  UserOrderRequest, UserOrderResponse,
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

  // Sessions
  getSessions(params?: Pageable) {
    return http.get<Page<OrderSessionResponse>>('/orders/sessions', { params })
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
}
