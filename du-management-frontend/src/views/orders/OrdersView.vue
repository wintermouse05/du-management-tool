<script setup lang="ts">
import { ref, onMounted, onUnmounted, watch } from 'vue'
import { useAuthStore } from '@/stores/auth'
import { useMenuScrapeStore } from '@/stores/menuScrape'
import { ordersApi } from '@/api/orders'
import type { MenuItemResponse, MenuScrapeItemResponse, OrderSessionResponse, OrderSessionSummaryResponse, RestaurantResponse, UserOrderResponse } from '@/types'
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
const menuScrapeStore = useMenuScrapeStore()
const toast = useToast()
const activeTab = ref('0')

// ==================== Restaurants ====================
const restaurants = ref<RestaurantResponse[]>([])
const selectedRestaurantId = ref<number | null>(null)
const menuItems = ref<MenuItemResponse[]>([])
const menuLoading = ref(false)

async function loadRestaurants() {
  try {
    const r = await ordersApi.getRestaurants()
    restaurants.value = r.data
  } catch (e: any) {
    toast.add({ severity: 'error', summary: 'Error', detail: e.response?.data?.message || 'Cannot load restaurants', life: 3000 })
  }
}

async function loadRestaurantMenu() {
  if (!selectedRestaurantId.value) return
  menuLoading.value = true
  try {
    const r = await ordersApi.getRestaurantMenu(selectedRestaurantId.value)
    menuItems.value = r.data
    toast.add({ severity: 'success', summary: `Menu refreshed (${r.data.length} items)`, life: 2000 })
  } catch (e: any) {
    toast.add({ severity: 'error', summary: 'Scrape failed', detail: e.response?.data?.message || e.message, life: 5000 })
  } finally {
    menuLoading.value = false
  }
}

async function deleteRestaurant(id: number) {
  try {
    await ordersApi.deleteRestaurant(id)
    toast.add({ severity: 'warn', summary: 'Restaurant deleted', life: 2000 })
    if (selectedRestaurantId.value === id) {
      selectedRestaurantId.value = null
      menuItems.value = []
    }
    loadRestaurants()
  } catch (e: any) {
    toast.add({ severity: 'error', summary: 'Error', detail: e.response?.data?.message || e.message, life: 3000 })
  }
}

// Watch restaurant dropdown selection — auto re-scrape
watch(selectedRestaurantId, (newVal) => {
  if (newVal) {
    loadRestaurantMenu()
  } else {
    menuItems.value = []
  }
})

// ==================== Menu Scrape & Save ====================
const scraping = ref(false)
const saving = ref(false)

async function scrapeMenu() {
  if (!menuScrapeStore.scrapeUrl.trim()) {
    toast.add({ severity: 'warn', summary: 'Please enter a URL', life: 2000 })
    return
  }
  scraping.value = true
  try {
    const r = await ordersApi.scrapeMenu({ url: menuScrapeStore.scrapeUrl.trim() })
    menuScrapeStore.setResults(menuScrapeStore.scrapeUrl.trim(), r.data)
    toast.add({ severity: 'success', summary: `Found ${r.data.length} item(s)`, life: 2000 })
  } catch (e: any) {
    toast.add({ severity: 'error', summary: 'Scrape failed', detail: e.response?.data?.message || e.message, life: 5000 })
  } finally { scraping.value = false }
}

async function saveAsRestaurant() {
  if (!menuScrapeStore.restaurantName.trim()) {
    toast.add({ severity: 'warn', summary: 'Please enter a restaurant name', life: 2000 })
    return
  }
  saving.value = true
  try {
    await ordersApi.saveRestaurant({
      name: menuScrapeStore.restaurantName.trim(),
      scrapeUrl: menuScrapeStore.scrapeUrl.trim()
    })
    toast.add({ severity: 'success', summary: 'Restaurant saved!', life: 2000 })
    menuScrapeStore.clear()
    loadRestaurants()
  } catch (e: any) {
    toast.add({ severity: 'error', summary: 'Error', detail: e.response?.data?.message || e.message, life: 3000 })
  } finally { saving.value = false }
}

// ==================== Sessions ====================
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

// ==================== Orders ====================
const orders = ref<UserOrderResponse[]>([])
const ordLoading = ref(false)
const selectedSession = ref<number|null>(null)
const summary = ref<OrderSessionSummaryResponse | null>(null)
const orderDialog = ref(false)
const orderForm = ref({ sessionId: 0, itemId: 0, quantity: 1, note: '' })
async function loadOrders() {
  if (!selectedSession.value) return
  ordLoading.value = true; try { const r = await ordersApi.getOrdersBySession(selectedSession.value, { size: 100 }); orders.value = r.data.content } finally { ordLoading.value = false }
}
async function loadSummary() {
  if (!selectedSession.value) {
    return
  }
  try {
    const r = await ordersApi.getSessionSummary(selectedSession.value)
    summary.value = r.data
  } catch (e: any) {
    toast.add({ severity:'error', summary:'Error', detail: e.response?.data?.message || 'Cannot load summary', life:3000 })
  }
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
  loadRestaurants()
  loadSessions()
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
    <div class="page-header"><div><h2>Orders</h2><p class="page-subtitle">Restaurants, order sessions, and food orders</p></div></div>
    <div class="content-card">
      <Tabs :value="activeTab">
        <TabList>
          <Tab value="0">Menu</Tab>
          <Tab value="1">Sessions</Tab>
          <Tab value="2">Orders</Tab>
        </TabList>
        <TabPanels>
          <!-- ==================== Menu Tab ==================== -->
          <TabPanel value="0">
            <!-- Restaurant Dropdown -->
            <div style="margin-bottom:var(--space-6);">
              <h3 style="margin-bottom:var(--space-3);">Select Restaurant</h3>
              <p style="color:var(--theme-text-weak);font-size:13px;margin-bottom:var(--space-3);">Choose a saved restaurant to load its latest menu. The menu will be re-scraped from the source.</p>
              <div style="display:flex;gap:var(--space-3);align-items:center;flex-wrap:wrap;">
                <Select
                  v-model="selectedRestaurantId"
                  :options="restaurants"
                  optionLabel="name"
                  optionValue="id"
                  placeholder="Select a restaurant..."
                  showClear
                  style="flex:1;min-width:280px;"
                />
                <Button
                  v-if="selectedRestaurantId && auth.isAdminOrHR"
                  icon="pi pi-trash"
                  severity="danger"
                  text
                  rounded
                  v-tooltip.top="'Delete Restaurant'"
                  @click="deleteRestaurant(selectedRestaurantId!)"
                />
              </div>
            </div>

            <!-- Menu Items Table (from selected restaurant) -->
            <div v-if="selectedRestaurantId">
              <h4 style="margin-bottom:var(--space-3);">Menu Items</h4>
              <DataTable :value="menuItems" :loading="menuLoading" stripedRows>
                <Column field="name" header="Name" />
                <Column field="price" header="Price"><template #body="{data}">{{ data.price.toLocaleString() }} ₫</template></Column>
                <Column field="description" header="Description" />
              </DataTable>
            </div>

            <div v-if="!selectedRestaurantId && restaurants.length > 0" style="text-align:center;padding:var(--space-8) 0;color:var(--theme-text-weak);">
              <i class="pi pi-shop" style="font-size:2.5rem;margin-bottom:var(--space-3);display:block;opacity:0.4;"></i>
              <p>Select a restaurant above to view its menu</p>
            </div>

            <div v-if="restaurants.length === 0 && menuScrapeStore.scrapedItems.length === 0" style="text-align:center;padding:var(--space-8) 0;color:var(--theme-text-weak);">
              <i class="pi pi-shop" style="font-size:2.5rem;margin-bottom:var(--space-3);display:block;opacity:0.4;"></i>
              <p>No restaurants saved yet. Use the scraper below to add one.</p>
            </div>

            <!-- Scrape & Save Section -->
            <div style="margin-top:var(--space-8); padding-top:var(--space-6); border-top: 1px solid var(--theme-divider);">
              <h3 style="margin-bottom:var(--space-2);">Scrape Menu from URL</h3>
              <p style="color:var(--theme-text-weak);font-size:13px;margin-bottom:var(--space-4);">Paste a GrabFood or ShopeeFood link to parse its menu items. After parsing, you can save it as a restaurant.</p>
              <div style="display:flex;gap:var(--space-3);align-items:center;flex-wrap:wrap;">
                <InputText v-model="menuScrapeStore.scrapeUrl" placeholder="https://food.grab.com/vn/vi/restaurant/..." style="flex:1;min-width:280px;" />
                <Button label="Parse Menu" icon="pi pi-search" :loading="scraping" @click="scrapeMenu" />
              </div>

              <!-- Scraped Results -->
              <div v-if="menuScrapeStore.scrapedItems.length > 0" style="margin-top:var(--space-5);">
                <h4 style="margin-bottom:var(--space-3);">Scraped Items ({{ menuScrapeStore.scrapedItems.length }})</h4>
                <DataTable :value="menuScrapeStore.scrapedItems" stripedRows>
                  <Column field="name" header="Name" />
                  <Column field="price" header="Price" />
                  <Column field="description" header="Description" />
                </DataTable>

                <!-- Save as Restaurant -->
                <div v-if="auth.isAdminOrHR" style="margin-top:var(--space-5); padding:var(--space-4); border:1px solid var(--theme-divider); border-radius:var(--radius-md); background:var(--theme-surface-hover);">
                  <h4 style="margin-bottom:var(--space-3);">Save as Restaurant</h4>
                  <div style="display:flex;gap:var(--space-3);align-items:center;flex-wrap:wrap;">
                    <InputText v-model="menuScrapeStore.restaurantName" placeholder="Restaurant name (e.g. Phúc Long Landmark 81)" style="flex:1;min-width:280px;" />
                    <Button
                      label="Save Restaurant"
                      icon="pi pi-save"
                      :loading="saving"
                      :disabled="!menuScrapeStore.restaurantName.trim()"
                      @click="saveAsRestaurant"
                    />
                  </div>
                </div>
              </div>
            </div>
          </TabPanel>

          <!-- ==================== Sessions Tab ==================== -->
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

          <!-- ==================== Orders Tab ==================== -->
          <TabPanel value="2">
            <div style="display:flex;gap:var(--space-3);margin-bottom:var(--space-4);align-items:center;">
              <Select v-model="selectedSession" :options="sessions" optionLabel="id" optionValue="id" placeholder="Select Session" style="width:200px" />
              <Button label="Load" icon="pi pi-search" size="small" outlined @click="loadOrders" />
              <Button label="View Summary" icon="pi pi-chart-bar" size="small" outlined @click="loadSummary" :disabled="!selectedSession" />
              <div style="flex:1"></div>
              <Button v-if="selectedSession" label="Place Order" icon="pi pi-plus" size="small" @click="orderDialog=true" />
            </div>
            <DataTable :value="orders" :loading="ordLoading" stripedRows>
              <Column field="fullName" header="User" /><Column field="itemName" header="Item" /><Column field="quantity" header="Qty" />
              <Column field="note" header="Note" /><Column field="paid" header="Paid"><template #body="{data}"><Tag :value="data.paid?'Yes':'No'" :severity="data.paid?'success':'warn'" /></template></Column>
              <Column v-if="auth.isAdminOrHR" header="" style="width:120px"><template #body="{data}"><Button v-if="!data.paid" label="Pay" size="small" @click="markPaid(data.id, true)" /></template></Column>
            </DataTable>
            <div v-if="summary" style="margin-top:var(--space-5);">
              <h4 style="margin-bottom:var(--space-3);">Session Summary</h4>
              <div style="display:flex;gap:var(--space-4);margin-bottom:var(--space-3);flex-wrap:wrap;">
                <Tag :value="`Lines: ${summary.totalOrderLines}`" severity="info" />
                <Tag :value="`Total Qty: ${summary.totalQuantity}`" severity="warn" />
                <Tag :value="`Grand Total: ${summary.grandTotal.toLocaleString()} ₫`" severity="success" />
              </div>
              <DataTable :value="summary.items" stripedRows>
                <Column field="itemName" header="Item" />
                <Column field="unitPrice" header="Unit Price"><template #body="{ data }">{{ data.unitPrice.toLocaleString() }} ₫</template></Column>
                <Column field="totalQuantity" header="Total Qty" />
                <Column field="totalAmount" header="Total Amount"><template #body="{ data }">{{ data.totalAmount.toLocaleString() }} ₫</template></Column>
              </DataTable>
            </div>
          </TabPanel>
        </TabPanels>
      </Tabs>
    </div>
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

<style scoped>
:deep(.p-select-label) {
  color: var(--theme-text-primary) !important;
}
</style>
