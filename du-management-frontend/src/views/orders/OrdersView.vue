<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue'
import { useAuthStore } from '@/stores/auth'
import { ordersApi } from '@/api/orders'
import type { MenuItemResponse, OrderSessionResponse, UserOrderResponse } from '@/types'
import { OrderSessionStatus } from '@/types'
import { wsService } from '@/services/websocket'
import DataTable from 'primevue/datatable'
import Column from 'primevue/column'
import Button from 'primevue/button'
import Dialog from 'primevue/dialog'
import InputText from 'primevue/inputtext'
import InputNumber from 'primevue/inputnumber'
import Select from 'primevue/select'
import DatePicker from 'primevue/datepicker'
import Tag from 'primevue/tag'
import Tabs from 'primevue/tabs'
import TabList from 'primevue/tablist'
import Tab from 'primevue/tab'
import TabPanels from 'primevue/tabpanels'
import TabPanel from 'primevue/tabpanel'
import { useToast } from 'primevue/usetoast'

const auth = useAuthStore()
const toast = useToast()
const activeTab = ref('0')

// Menu Items
const menuItems = ref<MenuItemResponse[]>([])
const menuLoading = ref(false)
const menuDialog = ref(false)
const menuForm = ref({ name: '', price: 0 })
async function loadMenu() { menuLoading.value = true; try { const r = await ordersApi.getMenuItems({ size: 100 }); menuItems.value = r.data.content } finally { menuLoading.value = false } }
async function createMenuItem() {
  try { await ordersApi.createMenuItem(menuForm.value); toast.add({ severity:'success', summary:'Menu item added', life:2000 }); menuDialog.value = false; loadMenu()
  } catch (e: any) { toast.add({ severity:'error', summary:'Error', detail: e.response?.data?.message, life:3000 }) }
}

// Sessions
const sessions = ref<OrderSessionResponse[]>([])
const sessLoading = ref(false)
const sessDialog = ref(false)
const sessDeadline = ref<Date|null>(null)
async function loadSessions() { sessLoading.value = true; try { const r = await ordersApi.getSessions({ size: 50 }); sessions.value = r.data.content } finally { sessLoading.value = false } }
async function createSession() {
  if (!sessDeadline.value) return
  try { await ordersApi.createSession({ deadline: sessDeadline.value.toISOString() }); toast.add({ severity:'success', summary:'Session created', life:2000 }); sessDialog.value = false; loadSessions()
  } catch (e: any) { toast.add({ severity:'error', summary:'Error', detail: e.response?.data?.message, life:3000 }) }
}
async function updateStatus(id: number, status: OrderSessionStatus) {
  try { await ordersApi.updateSessionStatus(id, status); toast.add({ severity:'success', summary:'Status updated', life:2000 }); loadSessions()
  } catch (e: any) { toast.add({ severity:'error', summary:'Error', detail: e.response?.data?.message, life:3000 }) }
}

// Orders
const orders = ref<UserOrderResponse[]>([])
const ordLoading = ref(false)
const selectedSession = ref<number|null>(null)
const orderDialog = ref(false)
const orderForm = ref({ sessionId: 0, itemId: 0, quantity: 1, note: '' })
async function loadOrders() {
  if (!selectedSession.value) return
  ordLoading.value = true; try { const r = await ordersApi.getOrdersBySession(selectedSession.value, { size: 100 }); orders.value = r.data.content } finally { ordLoading.value = false }
}
async function placeOrder() {
  if (!auth.userId) {
    toast.add({ severity:'error', summary:'Error', detail:'Missing user identity. Please log in again.', life:3000 })
    return
  }
  orderForm.value.sessionId = selectedSession.value || 0
  try { await ordersApi.placeOrder({ ...orderForm.value, userId: auth.userId }); toast.add({ severity:'success', summary:'Order placed', life:2000 }); orderDialog.value = false; loadOrders()
  } catch (e: any) { toast.add({ severity:'error', summary:'Error', detail: e.response?.data?.message, life:3000 }) }
}
async function markPaid(id: number, paid: boolean) {
  try { await ordersApi.markPaid(id, paid); toast.add({ severity:'success', summary: paid?'Marked paid':'Marked unpaid', life:2000 }); loadOrders()
  } catch (e: any) { toast.add({ severity:'error', summary:'Error', detail: e.response?.data?.message, life:3000 }) }
}

function statusSev(s: OrderSessionStatus) { return s === OrderSessionStatus.OPEN ? 'success' : s === OrderSessionStatus.CLOSED ? 'secondary' : 'danger' }
function fmtDate(d: string) { return d ? new Date(d).toLocaleDateString('en-US', { month:'short', day:'numeric', hour:'2-digit', minute:'2-digit' }) : '' }

let sub: any = null

onMounted(() => { 
  loadMenu(); 
  loadSessions();
  sub = wsService.subscribe('/topic/orders', () => {
    loadSessions()
    if (selectedSession.value) {
      loadOrders()
    }
  })
})

onUnmounted(() => {
  if (sub) sub.unsubscribe()
})
</script>

<template>
  <div class="page-container">
    <div class="page-header"><div><h2>Orders</h2><p class="page-subtitle">Menu items, order sessions, and food orders</p></div></div>
    <div class="content-card">
      <Tabs :value="activeTab">
        <TabList>
          <Tab value="0">Menu Items</Tab>
          <Tab value="1">Sessions</Tab>
          <Tab value="2">Orders</Tab>
        </TabList>
        <TabPanels>
          <TabPanel value="0">
            <div style="display:flex;justify-content:flex-end;margin-bottom:var(--space-4);"><Button v-if="auth.isAdminOrHR" label="Add Item" icon="pi pi-plus" size="small" @click="menuDialog=true" /></div>
            <DataTable :value="menuItems" :loading="menuLoading" stripedRows>
              <Column field="name" header="Name" /><Column field="price" header="Price"><template #body="{data}">{{ data.price.toLocaleString() }} ₫</template></Column>
            </DataTable>
          </TabPanel>
          <TabPanel value="1">
            <div style="display:flex;justify-content:flex-end;margin-bottom:var(--space-4);"><Button v-if="auth.isAdminOrHR" label="New Session" icon="pi pi-plus" size="small" @click="sessDialog=true" /></div>
            <DataTable :value="sessions" :loading="sessLoading" stripedRows>
              <Column field="id" header="ID" /><Column field="status" header="Status"><template #body="{data}"><Tag :value="data.status" :severity="statusSev(data.status)" /></template></Column>
              <Column field="deadline" header="Deadline"><template #body="{data}">{{ fmtDate(data.deadline) }}</template></Column>
              <Column v-if="auth.isAdminOrHR" header="Actions" style="width:200px">
                <template #body="{data}">
                  <Button v-if="data.status==='OPEN'" label="Close" size="small" severity="secondary" @click="updateStatus(data.id, OrderSessionStatus.CLOSED)" style="margin-right:4px" />
                  <Button v-if="data.status!=='OPEN'" label="Reopen" size="small" @click="updateStatus(data.id, OrderSessionStatus.OPEN)" />
                </template>
              </Column>
            </DataTable>
          </TabPanel>
          <TabPanel value="2">
            <div style="display:flex;gap:var(--space-3);margin-bottom:var(--space-4);align-items:center;">
              <Select v-model="selectedSession" :options="sessions" optionLabel="id" optionValue="id" placeholder="Select Session" style="width:200px" />
              <Button label="Load" icon="pi pi-search" size="small" outlined @click="loadOrders" />
              <div style="flex:1"></div>
              <Button v-if="selectedSession" label="Place Order" icon="pi pi-plus" size="small" @click="orderDialog=true" />
            </div>
            <DataTable :value="orders" :loading="ordLoading" stripedRows>
              <Column field="fullName" header="User" /><Column field="itemName" header="Item" /><Column field="quantity" header="Qty" />
              <Column field="note" header="Note" /><Column field="paid" header="Paid"><template #body="{data}"><Tag :value="data.paid?'Yes':'No'" :severity="data.paid?'success':'warn'" /></template></Column>
              <Column v-if="auth.isAdminOrHR" header="" style="width:120px"><template #body="{data}"><Button v-if="!data.paid" label="Pay" size="small" @click="markPaid(data.id, true)" /></template></Column>
            </DataTable>
          </TabPanel>
        </TabPanels>
      </Tabs>
    </div>
    <!-- Menu Item Dialog -->
    <Dialog v-model:visible="menuDialog" header="Add Menu Item" modal :style="{width:'380px'}">
      <div style="display:flex;flex-direction:column;gap:var(--space-4);">
        <div class="form-field"><label>Name</label><InputText v-model="menuForm.name" fluid /></div>
        <div class="form-field"><label>Price</label><InputNumber v-model="menuForm.price" fluid /></div>
      </div>
      <template #footer><Button label="Cancel" text @click="menuDialog=false" /><Button label="Add" icon="pi pi-check" @click="createMenuItem" /></template>
    </Dialog>
    <!-- Session Dialog -->
    <Dialog v-model:visible="sessDialog" header="New Order Session" modal :style="{width:'380px'}">
      <div class="form-field"><label>Deadline</label><DatePicker v-model="sessDeadline" showTime hourFormat="24" fluid /></div>
      <template #footer><Button label="Cancel" text @click="sessDialog=false" /><Button label="Create" icon="pi pi-check" @click="createSession" /></template>
    </Dialog>
    <!-- Order Dialog -->
    <Dialog v-model:visible="orderDialog" header="Place Order" modal :style="{width:'420px'}">
      <div style="display:flex;flex-direction:column;gap:var(--space-4);">
        <div class="form-field"><label>Menu Item</label><Select v-model="orderForm.itemId" :options="menuItems" optionLabel="name" optionValue="id" fluid /></div>
        <div class="form-field"><label>Quantity</label><InputNumber v-model="orderForm.quantity" :min="1" fluid /></div>
        <div class="form-field"><label>Note</label><InputText v-model="orderForm.note" fluid /></div>
      </div>
      <template #footer><Button label="Cancel" text @click="orderDialog=false" /><Button label="Order" icon="pi pi-check" @click="placeOrder" /></template>
    </Dialog>
  </div>
</template>
