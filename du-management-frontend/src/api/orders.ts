import http from './http'
import type {
  MenuItemResponse,
  MenuScrapeRequest, MenuScrapeItemResponse,
  OrderSessionRequest, OrderSessionResponse,
  RestaurantRequest, RestaurantResponse,
  UserOrderRequest, UserOrderResponse,
  OrderSessionSummaryResponse,
  Page, Pageable
} from '@/types'
import type { OrderSessionStatus } from '@/types'

export const ordersApi = {
  // Restaurants
  getRestaurants() {
    return http.get<RestaurantResponse[]>('/orders/restaurants')
  },

  saveRestaurant(data: RestaurantRequest) {
    return http.post<RestaurantResponse>('/orders/restaurants', data)
  },

  deleteRestaurant(id: number) {
    return http.delete<void>(`/orders/restaurants/${id}`)
  },

  getRestaurantMenu(id: number) {
    return http.get<MenuItemResponse[]>(`/orders/restaurants/${id}/menu`)
  },

  // Scrape (preview)
  scrapeMenu(data: MenuScrapeRequest) {
    return http.post<MenuScrapeItemResponse[]>('/orders/scrape-menu', data)
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
}
