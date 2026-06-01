import http from './http'
import type {
  MenuItemResponse,
  MenuScrapeRequest, MenuScrapeResponse,
  OrderSessionRequest, OrderSessionResponse,
  RestaurantRequest, RestaurantResponse,
  UserOrderRequest, UserOrderBulkRequest, UserOrderResponse, UserOrderUpdateRequest,
  OrderSessionSummaryResponse,
  Page, Pageable
} from '@/types'
import type { OrderSessionStatus } from '@/types'

const MENU_SCRAPE_TIMEOUT_MS = 120000

export const ordersApi = {
  // Restaurants
  getRestaurants() {
    return http.get<RestaurantResponse[]>('/orders/restaurants')
  },

  saveRestaurant(data: RestaurantRequest) {
    return http.post<RestaurantResponse>('/orders/restaurants', data, { timeout: MENU_SCRAPE_TIMEOUT_MS })
  },

  deleteRestaurant(id: number) {
    return http.delete<void>(`/orders/restaurants/${id}`)
  },

  getRestaurantMenu(id: number) {
    return http.get<MenuItemResponse[]>(`/orders/restaurants/${id}/menu`, { timeout: MENU_SCRAPE_TIMEOUT_MS })
  },

  // Scrape (preview)
  scrapeMenu(data: MenuScrapeRequest) {
    return http.post<MenuScrapeResponse>('/orders/scrape-menu', data, { timeout: MENU_SCRAPE_TIMEOUT_MS })
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

  updateSessionStatus(sessionId: number, status: OrderSessionStatus, deadline?: string) {
    return http.patch<OrderSessionResponse>('/orders/sessions/status', null, {
      params: { sessionId, status, deadline },
    })
  },

  // User Orders
  placeOrder(data: UserOrderRequest) {
    return http.post<UserOrderResponse>('/orders/user-orders', data)
  },

  placeOrderBulk(data: UserOrderBulkRequest) {
    return http.post<UserOrderResponse[]>('/orders/user-orders/bulk', data)
  },

  updateOrder(orderId: number, data: UserOrderUpdateRequest) {
    return http.patch<UserOrderResponse>(`/orders/user-orders/${orderId}`, data)
  },

  cancelOrder(orderId: number) {
    return http.delete<void>(`/orders/user-orders/${orderId}`)
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
