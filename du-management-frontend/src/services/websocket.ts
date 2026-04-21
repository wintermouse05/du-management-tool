import { Client, type IMessage } from '@stomp/stompjs'
import SockJS from 'sockjs-client'

class WebSocketService {
  private client: Client | null = null

  public connect(token: string, onConnectCallback?: () => void) {
    if (this.client && this.client.active) {
      return
    }

    this.client = new Client({
      // We use SockJS to fallback and handle cross-origin websocket endpoints easier
      webSocketFactory: () => new SockJS('http://localhost:8080/ws'),
      connectHeaders: {
        Authorization: `Bearer ${token}`
      },
      debug: function (str) {
        // console.log(str)
      },
      reconnectDelay: 5000,
      heartbeatIncoming: 4000,
      heartbeatOutgoing: 4000
    })

    this.client.onConnect = (frame) => {
      console.log('Connected to WebSocket STOMP via SockJS')
      if (onConnectCallback) {
        onConnectCallback()
      }
    }

    this.client.onStompError = (frame) => {
      console.error('Broker reported error: ' + frame.headers['message'])
      console.error('Additional details: ' + frame.body)
    }

    this.client.activate()
  }

  public disconnect() {
    if (this.client) {
      this.client.deactivate()
      this.client = null
    }
  }

  public subscribe(destination: string, callback: (message: IMessage) => void) {
    if (!this.client || !this.client.connected) {
      console.warn('Cannot subscribe, WebSocket is not connected yet. Topic: ' + destination)
      // Ideally we should queue subscriptions until connected, but for simplicity we rely on components waiting
      return null
    }
    return this.client.subscribe(destination, callback)
  }
}

export const wsService = new WebSocketService()
