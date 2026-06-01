<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { useAuthStore } from '@/stores/auth'
import { useMenuScrapeStore } from '@/stores/menuScrape'
import { membersApi } from '@/api/members'
import { ordersApi } from '@/api/orders'
import { wsService } from '@/services/websocket'
import { getApiErrorDetail } from '@/utils/apiError'
import {
  formatLocalDateTime,
  toLocalDateTime,
} from '@/utils/datetime'
import type {
  MemberResponse,
  MenuItemResponse,
  OrderSessionResponse,
  OrderSessionSummaryResponse,
  RestaurantResponse,
  UserOrderResponse,
} from '@/types'
import { OrderSessionStatus, UserStatus } from '@/types'
import DataTable from 'primevue/datatable'
import Column from 'primevue/column'
import Button from 'primevue/button'
import Dialog from 'primevue/dialog'
import InputText from 'primevue/inputtext'
import InputNumber from 'primevue/inputnumber'
import Checkbox from 'primevue/checkbox'
import Select from 'primevue/select'
import MultiSelect from 'primevue/multiselect'
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
const suppressWatcherRestaurantId = ref<number | null>(null)
let latestMenuRequestSeq = 0
const restaurantMenuRows = ref(10)
const restaurantMenuFirst = ref(0)
const scrapedMenuRows = ref(10)
const scrapedMenuFirst = ref(0)
const deleteRestaurantDialog = ref(false)
const deleteRestaurantTarget = ref<RestaurantResponse | null>(null)
const deletingRestaurant = ref(false)

const selectedRestaurantRecord = computed(
  () => restaurants.value.find((restaurant) => restaurant.id === selectedRestaurantId.value) ?? null,
)

async function loadRestaurants() {
  try {
    const response = await ordersApi.getRestaurants()
    restaurants.value = response.data
  } catch (error: any) {
    toast.add({ severity: 'error', summary: 'Error', detail: getApiErrorDetail(error, 'Cannot load restaurants'), life: 3000 })
  }
}

async function loadRestaurantMenuById(restaurantId: number, silent = false) {
  const requestSeq = ++latestMenuRequestSeq
  menuLoading.value = true
  try {
    const response = await ordersApi.getRestaurantMenu(restaurantId)
    if (requestSeq !== latestMenuRequestSeq || selectedRestaurantId.value !== restaurantId) {
      return
    }
    menuItems.value = response.data
    restaurantMenuFirst.value = 0
    if (!silent) {
      toast.add({ severity: 'success', summary: `Menu refreshed (${response.data.length} items)`, life: 2000 })
    }
  } catch (error: any) {
    if (requestSeq !== latestMenuRequestSeq) {
      return
    }
    menuItems.value = []
    if (!silent) {
      toast.add({ severity: 'error', summary: 'Load failed', detail: getApiErrorDetail(error, 'Unable to load menu data.'), life: 5000 })
    }
  } finally {
    if (requestSeq === latestMenuRequestSeq) {
      menuLoading.value = false
    }
  }
}

function confirmDeleteRestaurant() {
  if (!selectedRestaurantRecord.value) {
    return
  }
  deleteRestaurantTarget.value = selectedRestaurantRecord.value
  deleteRestaurantDialog.value = true
}

async function deleteRestaurant() {
  if (!deleteRestaurantTarget.value) {
    return
  }

  const restaurantId = deleteRestaurantTarget.value.id
  deletingRestaurant.value = true
  try {
    await ordersApi.deleteRestaurant(restaurantId)
    toast.add({ severity: 'warn', summary: 'Restaurant archived', life: 2000 })
    if (selectedRestaurantId.value === restaurantId) {
      selectedRestaurantId.value = null
      menuItems.value = []
    }
    deleteRestaurantDialog.value = false
    deleteRestaurantTarget.value = null
    await loadRestaurants()
  } catch (error: any) {
    toast.add({ severity: 'error', summary: 'Error', detail: getApiErrorDetail(error), life: 3000 })
  } finally {
    deletingRestaurant.value = false
  }
}

watch(selectedRestaurantId, async (restaurantId) => {
  if (restaurantId && suppressWatcherRestaurantId.value === restaurantId) {
    suppressWatcherRestaurantId.value = null
    return
  }
  if (!restaurantId) {
    menuItems.value = []
    restaurantMenuFirst.value = 0
    latestMenuRequestSeq += 1
    return
  }
  await loadRestaurantMenuById(restaurantId)
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
    const response = await ordersApi.scrapeMenu({ url: menuScrapeStore.scrapeUrl.trim() })
    menuScrapeStore.setResults(menuScrapeStore.scrapeUrl.trim(), response.data.items, response.data.restaurantName)
    scrapedMenuFirst.value = 0
    toast.add({ severity: 'success', summary: `Found ${response.data.items.length} item(s)`, life: 2000 })
  } catch (error: any) {
    toast.add({ severity: 'error', summary: 'Scrape failed', detail: getApiErrorDetail(error, 'Unable to scrape menu data.'), life: 5000 })
  } finally {
    scraping.value = false
  }
}

async function saveAsRestaurant() {
  if (!menuScrapeStore.scrapeUrl.trim()) {
    toast.add({ severity: 'warn', summary: 'Please enter a URL', life: 2000 })
    return
  }
  if (!menuScrapeStore.restaurantName.trim()) {
    toast.add({ severity: 'warn', summary: 'Please enter a restaurant name', life: 2000 })
    return
  }

  saving.value = true
  try {
    await ordersApi.saveRestaurant({
      name: menuScrapeStore.restaurantName.trim(),
      scrapeUrl: menuScrapeStore.scrapeUrl.trim(),
    })
    toast.add({ severity: 'success', summary: 'Restaurant saved!', life: 2000 })
    menuScrapeStore.clear()
    scrapedMenuFirst.value = 0
    await loadRestaurants()
  } catch (error: any) {
    toast.add({ severity: 'error', summary: 'Error', detail: getApiErrorDetail(error), life: 3000 })
  } finally {
    saving.value = false
  }
}

function onRestaurantMenuPage(event: any) {
  restaurantMenuFirst.value = event.first
  restaurantMenuRows.value = event.rows
}

function onScrapedMenuPage(event: any) {
  scrapedMenuFirst.value = event.first
  scrapedMenuRows.value = event.rows
}

// ==================== Sessions ====================
const sessions = ref<OrderSessionResponse[]>([])
const sessLoading = ref(false)
const sessDialog = ref(false)
const sessName = ref('')
const sessRestaurantId = ref<number | null>(null)
const sessDeadline = ref<Date | null>(null)
const reopenDialog = ref(false)
const reopenSessionId = ref<number | null>(null)
const reopenDeadline = ref<Date | null>(null)

const sessionOptions = computed(() =>
  sessions.value.map((session) => ({
    label: `${session.name} (${session.status})`,
    value: session.id,
  })),
)

async function loadSessions() {
  sessLoading.value = true
  try {
    const response = await ordersApi.getSessions({ size: 100, sort: 'createdAt,desc' })
    sessions.value = response.data.content
  } finally {
    sessLoading.value = false
  }
}

function resetSessionForm() {
  sessName.value = ''
  sessRestaurantId.value = null
  sessDeadline.value = null
}

async function createSession() {
  if (!sessName.value.trim() || !sessRestaurantId.value || !sessDeadline.value) {
    toast.add({ severity: 'warn', summary: 'Please complete all session fields', life: 2500 })
    return
  }

  try {
    await ordersApi.createSession({
      name: sessName.value.trim(),
      restaurantId: sessRestaurantId.value,
      deadline: toLocalDateTime(sessDeadline.value),
    })
    toast.add({ severity: 'success', summary: 'Session created', life: 2000 })
    sessDialog.value = false
    resetSessionForm()
    await loadSessions()
  } catch (error: any) {
    toast.add({ severity: 'error', summary: 'Error', detail: getApiErrorDetail(error), life: 3000 })
  }
}

async function updateStatus(id: number, status: OrderSessionStatus, deadline?: Date | null) {
  try {
    await ordersApi.updateSessionStatus(id, status, deadline ? toLocalDateTime(deadline) : undefined)
    toast.add({ severity: 'success', summary: 'Status updated', life: 2000 })
    await loadSessions()
  } catch (error: any) {
    toast.add({ severity: 'error', summary: 'Error', detail: getApiErrorDetail(error), life: 3000 })
  }
}

function resetReopenForm() {
  reopenSessionId.value = null
  reopenDeadline.value = null
}

function requestReopen(session: OrderSessionResponse) {
  if (!isDeadlineExpired(session.deadline)) {
    void updateStatus(session.id, OrderSessionStatus.OPEN)
    return
  }
  reopenSessionId.value = session.id
  reopenDeadline.value = null
  reopenDialog.value = true
}

async function confirmReopen() {
  if (!reopenSessionId.value) {
    return
  }
  if (!reopenDeadline.value) {
    toast.add({ severity: 'warn', summary: 'Please select a new deadline', life: 2500 })
    return
  }
  await updateStatus(reopenSessionId.value, OrderSessionStatus.OPEN, reopenDeadline.value)
  reopenDialog.value = false
  resetReopenForm()
}

function statusSev(status: OrderSessionStatus) {
  return status === OrderSessionStatus.OPEN ? 'success' : status === OrderSessionStatus.CLOSED ? 'secondary' : 'danger'
}

function isDeadlineExpired(deadline: string | null | undefined) {
  if (!deadline) {
    return false
  }
  return new Date(deadline).getTime() < Date.now()
}

// ==================== Orders ====================
const members = ref<MemberResponse[]>([])
const orders = ref<UserOrderResponse[]>([])
const ordLoading = ref(false)
const selectedSession = ref<number | null>(null)
const summary = ref<OrderSessionSummaryResponse | null>(null)
const orderDialog = ref(false)
const editOrderDialog = ref(false)
const editingOrder = ref<UserOrderResponse | null>(null)
const deleteOrderDialog = ref(false)
const deleteOrderTarget = ref<UserOrderResponse | null>(null)
const deletingOrder = ref(false)
const orderForm = ref({
  userIds: [] as number[],
  itemId: 0,
  quantity: 1,
  note: '',
})
const editOrderForm = ref({
  itemId: 0,
  quantity: 1,
  note: '',
})

type SizeOption = 'S' | 'M' | 'L'
type IceOption = 'Đá riêng' | '50%' | '100%'
type SugarOption = '0%' | '25%' | '50%' | '100%'
type SingleChoiceField = 'size' | 'ice' | 'sugar'

const sizeOptions: SizeOption[] = ['S', 'M', 'L']
const iceOptions: IceOption[] = ['Đá riêng', '50%', '100%']
const sugarOptions: SugarOption[] = ['0%', '25%', '50%', '100%']

const orderSelections = ref<{
  size: SizeOption | null
  ice: IceOption | null
  sugar: SugarOption | null
}>({
  size: null,
  ice: null,
  sugar: null,
})

const selectedSessionRecord = computed(
  () => sessions.value.find((session) => session.id === selectedSession.value) ?? null,
)

const canPlaceOrder = computed(
  () => selectedSessionRecord.value?.status === OrderSessionStatus.OPEN
    && !!selectedSessionRecord.value?.restaurantId
    && !isDeadlineExpired(selectedSessionRecord.value.deadline),
)

const orderedUserIds = computed(() => new Set(orders.value.map((order) => order.userId)))

const availableMembersForOrder = computed(() =>
  members.value.filter((member) => !orderedUserIds.value.has(member.id)),
)

function resetOrderForm() {
  orderForm.value = {
    userIds: auth.userId && !orderedUserIds.value.has(auth.userId) ? [auth.userId] : [],
    itemId: menuItems.value[0]?.id || 0,
    quantity: 1,
    note: '',
  }
  orderSelections.value = {
    size: null,
    ice: null,
    sugar: null,
  }
}

function updateSingleChoice(field: SingleChoiceField, value: string, checked: boolean) {
  const target = orderSelections.value as Record<SingleChoiceField, string | null>
  if (checked) {
    target[field] = value
    return
  }
  if (target[field] === value) {
    target[field] = null
  }
}

function buildOrderNote(rawNote: string): string | undefined {
  const summaryParts: string[] = []
  if (orderSelections.value.size) {
    summaryParts.push(`Size: ${orderSelections.value.size}`)
  }
  if (orderSelections.value.ice) {
    summaryParts.push(`Đá: ${orderSelections.value.ice}`)
  }
  if (orderSelections.value.sugar) {
    summaryParts.push(`Đường: ${orderSelections.value.sugar}`)
  }

  const note = rawNote.trim()
  if (note) {
    summaryParts.push(`Note: ${note}`)
  }

  const merged = summaryParts.join(' | ')
  return merged || undefined
}

function canManageOrderActions(order: UserOrderResponse) {
  const session = sessions.value.find((item) => item.id === order.sessionId)
  return order.canManage
    && order.sessionStatus === OrderSessionStatus.OPEN
    && (!session || !isDeadlineExpired(session.deadline))
}

async function loadMembers() {
  try {
    const response = await membersApi.search({ page: 0, size: 500, status: UserStatus.ACTIVE })
    members.value = response.data.content
  } catch {
    members.value = []
  }
}

async function loadOrders() {
  if (!selectedSession.value) {
    orders.value = []
    return
  }

  ordLoading.value = true
  try {
    const response = await ordersApi.getOrdersBySession(selectedSession.value, { size: 100, sort: 'createdAt,desc' })
    orders.value = response.data.content
  } finally {
    ordLoading.value = false
  }
}

async function loadSummary() {
  if (!selectedSession.value) {
    summary.value = null
    return
  }

  try {
    const response = await ordersApi.getSessionSummary(selectedSession.value)
    if (selectedSession.value === response.data.sessionId) {
      summary.value = response.data
    }
  } catch (error: any) {
    summary.value = null
    toast.add({ severity: 'error', summary: 'Error', detail: getApiErrorDetail(error, 'Cannot load summary'), life: 3000 })
  }
}

async function syncSelectedSessionData() {
  summary.value = null
  orders.value = []

  const session = selectedSessionRecord.value
  if (!session) {
    menuItems.value = []
    latestMenuRequestSeq += 1
    return
  }

  if (session.restaurantId) {
    if (selectedRestaurantId.value !== session.restaurantId) {
      suppressWatcherRestaurantId.value = session.restaurantId
      selectedRestaurantId.value = session.restaurantId
    }
    menuItems.value = []
    restaurantMenuFirst.value = 0
    await loadRestaurantMenuById(session.restaurantId, true)
  } else {
    menuItems.value = []
    latestMenuRequestSeq += 1
  }

  await Promise.all([loadOrders(), loadSummary()])
  resetOrderForm()
}

async function placeOrder() {
  if (!selectedSession.value) {
    toast.add({ severity: 'warn', summary: 'Please select a session first', life: 2500 })
    return
  }
  if (!orderForm.value.userIds.length || !orderForm.value.itemId) {
    toast.add({ severity: 'warn', summary: 'Please choose users and menu item', life: 2500 })
    return
  }
  if (orderForm.value.userIds.some((userId) => orderedUserIds.value.has(userId))) {
    toast.add({ severity: 'warn', summary: 'Each user can only have one order in this session', life: 2500 })
    return
  }

  try {
    await ordersApi.placeOrderBulk({
      sessionId: selectedSession.value,
      userIds: orderForm.value.userIds,
      itemId: orderForm.value.itemId,
      quantity: orderForm.value.quantity,
      note: buildOrderNote(orderForm.value.note),
    })
    toast.add({ severity: 'success', summary: `Orders placed for ${orderForm.value.userIds.length} user(s)`, life: 2200 })
    orderDialog.value = false
    await Promise.all([loadOrders(), loadSummary()])
  } catch (error: any) {
    toast.add({ severity: 'error', summary: 'Error', detail: getApiErrorDetail(error), life: 3000 })
  }
}

function openEditOrder(order: UserOrderResponse) {
  if (!canManageOrderActions(order)) {
    return
  }
  editingOrder.value = order
  editOrderForm.value = {
    itemId: order.itemId,
    quantity: order.quantity,
    note: order.note || '',
  }
  editOrderDialog.value = true
}

async function updateOrder() {
  if (!editingOrder.value || !editOrderForm.value.itemId) {
    toast.add({ severity: 'warn', summary: 'Please choose a menu item', life: 2500 })
    return
  }

  try {
    await ordersApi.updateOrder(editingOrder.value.id, {
      itemId: editOrderForm.value.itemId,
      quantity: editOrderForm.value.quantity,
      note: editOrderForm.value.note.trim() || undefined,
    })
    toast.add({ severity: 'success', summary: 'Order updated', life: 2000 })
    editOrderDialog.value = false
    editingOrder.value = null
    await Promise.all([loadOrders(), loadSummary()])
  } catch (error: any) {
    toast.add({ severity: 'error', summary: 'Error', detail: getApiErrorDetail(error), life: 3000 })
  }
}

function confirmDeleteOrder(order: UserOrderResponse) {
  if (!canManageOrderActions(order)) {
    return
  }
  deleteOrderTarget.value = order
  deleteOrderDialog.value = true
}

async function deleteOrder() {
  if (!deleteOrderTarget.value) {
    return
  }

  deletingOrder.value = true
  try {
    await ordersApi.cancelOrder(deleteOrderTarget.value.id)
    toast.add({ severity: 'warn', summary: 'Order deleted', life: 2000 })
    deleteOrderDialog.value = false
    deleteOrderTarget.value = null
    await Promise.all([loadOrders(), loadSummary()])
  } catch (error: any) {
    toast.add({ severity: 'error', summary: 'Error', detail: getApiErrorDetail(error), life: 3000 })
  } finally {
    deletingOrder.value = false
  }
}

async function markPaid(id: number, paid: boolean) {
  try {
    await ordersApi.markPaid(id, paid)
    toast.add({ severity: 'success', summary: paid ? 'Marked paid' : 'Marked unpaid', life: 2000 })
    await Promise.all([loadOrders(), loadSummary()])
  } catch (error: any) {
    toast.add({ severity: 'error', summary: 'Error', detail: getApiErrorDetail(error), life: 3000 })
  }
}

function formatCurrency(value: number) {
  return `${Number(value || 0).toLocaleString('en-US')} VND`
}

function renderOrderedUser(order: UserOrderResponse) {
  return order.orderedByFullName ? `${order.fullName} (by ${order.orderedByFullName})` : order.fullName
}

watch(selectedSession, async () => {
  await syncSelectedSessionData()
})

watch(orderDialog, (visible) => {
  if (visible) {
    resetOrderForm()
  }
})

watch(editOrderDialog, (visible) => {
  if (!visible) {
    editingOrder.value = null
  }
})

watch(sessDialog, (visible) => {
  if (!visible) {
    resetSessionForm()
  }
})

watch(reopenDialog, (visible) => {
  if (!visible) {
    resetReopenForm()
  }
})

let sub: any = null

onMounted(async () => {
  await Promise.all([loadRestaurants(), loadSessions(), loadMembers()])
  resetOrderForm()

  sub = wsService.subscribe('/topic/orders', async () => {
    await loadSessions()
    if (selectedSession.value) {
      await Promise.all([loadOrders(), loadSummary()])
    }
  })
})

onUnmounted(() => {
  if (sub) {
    sub.unsubscribe()
  }
})
</script>

<template>
  <div class="page-container">
    <div class="page-header">
      <div>
        <h2>Orders</h2>
        <p class="page-subtitle">Restaurants, order sessions, and food orders</p>
      </div>
    </div>

    <div class="content-card">
      <Tabs :value="activeTab">
        <TabList>
          <Tab value="0">Menu</Tab>
          <Tab value="1">Sessions</Tab>
          <Tab value="2">Orders</Tab>
        </TabList>

        <TabPanels>
          <TabPanel value="0">
            <div style="margin-bottom:var(--space-6);">
              <h3 style="margin-bottom:var(--space-3);">Select Restaurant</h3>
              <p style="color:var(--theme-text-weak);font-size:13px;margin-bottom:var(--space-3);">Choose a saved restaurant to load its current menu from the system.</p>
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
                  v-tooltip.top="'Archive Restaurant'"
                  @click="confirmDeleteRestaurant"
                />
              </div>
            </div>

            <div v-if="selectedRestaurantId">
              <h4 style="margin-bottom:var(--space-3);">Menu Items</h4>
              <DataTable
                :value="menuItems"
                :loading="menuLoading"
                :paginator="true"
                :rows="restaurantMenuRows"
                :first="restaurantMenuFirst"
                :rowsPerPageOptions="[10,20,50]"
                @page="onRestaurantMenuPage"
                stripedRows
                class="menu-grid"
                :tableStyle="{ tableLayout: 'fixed' }"
              >
                <template #empty>
                  No menu item found for this restaurant.
                </template>
                <Column field="name" header="Name" style="width:34%" />
                <Column field="price" header="Price" style="width:14%">
                  <template #body="{ data }">
                    <span class="menu-price">{{ formatCurrency(data.price) }}</span>
                  </template>
                </Column>
                <Column field="description" header="Description" style="width:52%" />
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

            <div style="margin-top:var(--space-8); padding-top:var(--space-6); border-top: 1px solid var(--theme-divider);">
              <h3 style="margin-bottom:var(--space-2);">Scrape Menu from URL</h3>
              <p style="color:var(--theme-text-weak);font-size:13px;margin-bottom:var(--space-4);">Paste a GrabFood or ShopeeFood link to parse its menu items. After parsing, you can save it as a restaurant.</p>

              <div style="margin-bottom:var(--space-4); padding:var(--space-4); border:1px solid var(--theme-divider); border-radius:var(--radius-md); background:var(--theme-surface-hover);">
                <h4 style="margin-bottom:var(--space-3);">Save as Restaurant</h4>
                <div style="display:flex;flex-direction:column;gap:var(--space-3);">
                  <div style="display:flex;gap:var(--space-3);align-items:center;flex-wrap:wrap;">
                    <InputText v-model="menuScrapeStore.scrapeUrl" placeholder="https://food.grab.com/vn/vi/restaurant/..." style="flex:1;min-width:280px;" />
                    <Button label="Parse Menu" icon="pi pi-search" class="scrape-action-btn" :loading="scraping" @click="scrapeMenu" />
                  </div>
                  <div style="display:flex;gap:var(--space-3);align-items:center;flex-wrap:wrap;">
                    <InputText v-model="menuScrapeStore.restaurantName" placeholder="Restaurant name" style="flex:1;min-width:280px;" />
                    <Button
                      label="Save Restaurant"
                      icon="pi pi-save"
                      class="scrape-action-btn"
                      :loading="saving"
                      :disabled="!menuScrapeStore.restaurantName.trim() || !menuScrapeStore.scrapeUrl.trim()"
                      @click="saveAsRestaurant"
                    />
                  </div>
                </div>
              </div>

              <div v-if="menuScrapeStore.scrapedItems.length > 0" style="margin-top:var(--space-5);">
                <h4 style="margin-bottom:var(--space-3);">Scraped Items ({{ menuScrapeStore.scrapedItems.length }})</h4>
                <DataTable
                  :value="menuScrapeStore.scrapedItems"
                  :paginator="true"
                  :rows="scrapedMenuRows"
                  :first="scrapedMenuFirst"
                  :rowsPerPageOptions="[10,20,50]"
                  @page="onScrapedMenuPage"
                  stripedRows
                  class="menu-grid"
                  :tableStyle="{ tableLayout: 'fixed' }"
                >
                  <Column field="name" header="Name" style="width:34%" />
                  <Column field="price" header="Price" style="width:14%">
                    <template #body="{ data }">
                      <span class="menu-price">{{ data.price }}</span>
                    </template>
                  </Column>
                  <Column field="description" header="Description" style="width:52%" />
                </DataTable>
              </div>
            </div>
          </TabPanel>

          <TabPanel value="1">
            <div style="display:flex;justify-content:flex-end;margin-bottom:var(--space-4);">
              <Button label="New Session" icon="pi pi-plus" size="small" @click="sessDialog = true" />
            </div>

            <DataTable :value="sessions" :loading="sessLoading" stripedRows>
              <template #empty>
                No Session Order has been created yet. Please create a Session Order.
              </template>
              <Column field="name" header="Session Name" />
              <Column field="restaurantName" header="Restaurant" />
              <Column field="creatorName" header="Created By" />
              <Column field="deadline" header="Deadline">
                <template #body="{ data }">
                  <span :class="['deadline-pill', isDeadlineExpired(data.deadline) ? 'deadline-expired' : 'deadline-active']">
                    {{ formatLocalDateTime(data.deadline, 'en-US', { month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit' }) }}
                  </span>
                </template>
              </Column>
              <Column field="status" header="Status">
                <template #body="{ data }">
                  <Tag :value="data.status" :severity="statusSev(data.status)" />
                </template>
              </Column>
              <Column header="Actions" style="width:200px">
                <template #body="{ data }">
                  <div v-if="data.canManage" style="display:flex;gap:4px;">
                    <Button v-if="data.status === OrderSessionStatus.OPEN" label="Close" size="small" severity="secondary" @click="updateStatus(data.id, OrderSessionStatus.CLOSED)" />
                    <Button v-else label="Reopen" size="small" @click="requestReopen(data)" />
                  </div>
                </template>
              </Column>
            </DataTable>
          </TabPanel>

          <TabPanel value="2">
            <div style="display:flex;gap:var(--space-3);margin-bottom:var(--space-4);align-items:center;flex-wrap:wrap;">
              <Select
                v-model="selectedSession"
                :options="sessionOptions"
                optionLabel="label"
                optionValue="value"
                placeholder="Select Session"
                style="width:320px"
              />
              <div v-if="selectedSessionRecord" style="display:flex;gap:var(--space-2);align-items:center;flex-wrap:wrap;">
                <Tag :value="selectedSessionRecord.restaurantName || 'No restaurant'" severity="info" />
                <span :class="['deadline-pill', isDeadlineExpired(selectedSessionRecord.deadline) ? 'deadline-expired' : 'deadline-active']">
                  Deadline:
                  {{ formatLocalDateTime(selectedSessionRecord.deadline, 'en-US', { month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit' }) }}
                </span>
              </div>
              <div style="flex:1"></div>
              <Button
                v-if="canPlaceOrder"
                label="Place Order"
                icon="pi pi-plus"
                size="small"
                @click="orderDialog = true"
              />
            </div>

            <div v-if="selectedSessionRecord && selectedSessionRecord.status !== OrderSessionStatus.OPEN" style="margin-bottom:var(--space-4);color:var(--theme-text-weak);font-size:13px;">
              This session is closed, so new orders are hidden.
            </div>

            <DataTable :value="orders" :loading="ordLoading" stripedRows>
              <template #empty>
                {{ selectedSession ? 'No Order has been placed for this Session yet.' : 'Please select a Session to view Orders.' }}
              </template>
              <Column header="User">
                <template #body="{ data }">{{ renderOrderedUser(data) }}</template>
              </Column>
              <Column field="sessionName" header="Session" />
              <Column field="itemName" header="Item" />
              <Column field="itemPrice" header="Price">
                <template #body="{ data }">{{ formatCurrency(data.itemPrice) }}</template>
              </Column>
              <Column field="quantity" header="Qty" />
              <Column field="note" header="Note" />
              <Column field="paid" header="Paid">
                <template #body="{ data }">
                  <Tag :value="data.paid ? 'Yes' : 'No'" :severity="data.paid ? 'success' : 'warn'" />
                </template>
              </Column>
              <Column header="" style="width:220px">
                <template #body="{ data }">
                  <div class="order-actions">
                    <Button
                      v-if="canManageOrderActions(data)"
                      icon="pi pi-pencil"
                      text
                      rounded
                      severity="info"
                      size="small"
                      aria-label="Edit order"
                      @click="openEditOrder(data)"
                    />
                    <Button
                      v-if="canManageOrderActions(data)"
                      icon="pi pi-trash"
                      text
                      rounded
                      severity="danger"
                      size="small"
                      aria-label="Delete order"
                      @click="confirmDeleteOrder(data)"
                    />
                    <Button v-if="auth.isAdmin && !data.paid" label="Pay" size="small" @click="markPaid(data.id, true)" />
                    <Button v-else-if="auth.isAdmin && data.paid" label="Unpay" size="small" severity="secondary" @click="markPaid(data.id, false)" />
                  </div>
                </template>
              </Column>
            </DataTable>

            <div v-if="summary && selectedSessionRecord" style="margin-top:var(--space-5);">
              <h4 style="margin-bottom:var(--space-3);">Session Summary - {{ selectedSessionRecord.name }}</h4>
              <div style="display:flex;gap:var(--space-4);margin-bottom:var(--space-3);flex-wrap:wrap;">
                <Tag :value="`Lines: ${summary.totalOrderLines}`" severity="info" />
                <Tag :value="`Total Qty: ${summary.totalQuantity}`" severity="warn" />
                <Tag :value="`Grand Total: ${formatCurrency(summary.grandTotal)}`" severity="success" />
              </div>
              <DataTable :value="summary.items" stripedRows>
                <template #empty>
                  No summary item found for this Session yet.
                </template>
                <Column field="itemName" header="Item" />
                <Column field="unitPrice" header="Unit Price">
                  <template #body="{ data }">{{ formatCurrency(data.unitPrice) }}</template>
                </Column>
                <Column field="totalQuantity" header="Total Qty" />
                <Column field="totalAmount" header="Total Amount">
                  <template #body="{ data }">{{ formatCurrency(data.totalAmount) }}</template>
                </Column>
              </DataTable>
            </div>
          </TabPanel>
        </TabPanels>
      </Tabs>
    </div>

    <Dialog v-model:visible="sessDialog" header="New Order Session" modal :style="{ width: '420px' }">
      <div style="display:flex;flex-direction:column;gap:var(--space-4);">
        <div class="form-field">
          <label class="required">Session Name</label>
          <InputText v-model="sessName" placeholder="Lunch team order" fluid />
        </div>
        <div class="form-field">
          <label class="required">Restaurant</label>
          <Select
            v-model="sessRestaurantId"
            :options="restaurants"
            optionLabel="name"
            optionValue="id"
            placeholder="Select restaurant"
            fluid
          />
        </div>
        <div class="form-field">
          <label class="required">Deadline</label>
          <DatePicker v-model="sessDeadline" showTime hourFormat="24" fluid />
        </div>
      </div>
      <template #footer>
        <Button label="Cancel" text @click="sessDialog = false" />
        <Button label="Create" icon="pi pi-check" @click="createSession" />
      </template>
    </Dialog>

    <Dialog v-model:visible="reopenDialog" header="Reopen Session" modal :style="{ width: '420px' }">
      <div style="display:flex;flex-direction:column;gap:var(--space-3);">
        <p style="margin:0;color:var(--theme-text-weak);font-size:13px;">
          This session is past its deadline. Please choose a new deadline to reopen it.
        </p>
        <div class="form-field">
          <label class="required">New Deadline</label>
          <DatePicker v-model="reopenDeadline" showTime hourFormat="24" fluid />
        </div>
      </div>
      <template #footer>
        <Button label="Cancel" text @click="reopenDialog = false" />
        <Button label="Reopen" icon="pi pi-check" @click="confirmReopen" />
      </template>
    </Dialog>

    <Dialog v-model:visible="orderDialog" header="Place Order" modal :style="{ width: '460px' }">
      <div style="display:flex;flex-direction:column;gap:var(--space-4);">
        <div class="form-field">
          <label class="required">Order For Users</label>
          <MultiSelect
            v-model="orderForm.userIds"
            :options="availableMembersForOrder"
            optionLabel="fullName"
            optionValue="id"
            placeholder="Select one or more members"
            filter
            :filter-fields="['username', 'fullName', 'email']"
            emptyMessage="No available members"
            display="chip"
            fluid
          >
            <template #option="{ option }">
              <div>{{ option.fullName }} <span style="color:var(--theme-text-weak);font-size:12px;">(@{{ option.username }})</span></div>
            </template>
          </MultiSelect>
        </div>
        <div class="form-field">
          <label class="required">Menu Item</label>
          <Select
            v-model="orderForm.itemId"
            :options="menuItems"
            optionLabel="name"
            optionValue="id"
            :loading="menuLoading"
            :emptyMessage="menuLoading ? 'Loading, please wait' : 'No available options'"
            fluid
          >
            <template #option="{ option }">
              <div style="display:flex;justify-content:space-between;gap:var(--space-3);">
                <span>{{ option.name }}</span>
                <span style="color:var(--theme-text-weak);">{{ formatCurrency(option.price) }}</span>
              </div>
            </template>
          </Select>
        </div>
        <div class="form-field">
          <label class="required">Quantity</label>
          <InputNumber v-model="orderForm.quantity" :min="1" fluid />
        </div>
        <div class="form-field">
          <label>Size <span class="optional-hint">(optional)</span></label>
          <div class="order-option-group">
            <div v-for="size in sizeOptions" :key="`size-${size}`" class="order-option-row">
              <Checkbox
                :inputId="`order-size-${size}`"
                binary
                :modelValue="orderSelections.size === size"
                @update:modelValue="(checked: boolean) => updateSingleChoice('size', size, checked)"
              />
              <label :for="`order-size-${size}`">{{ size }}</label>
            </div>
          </div>
        </div>
        <div class="form-field">
          <label>Lượng đá <span class="optional-hint">(optional)</span></label>
          <div class="order-option-group">
            <div v-for="ice in iceOptions" :key="`ice-${ice}`" class="order-option-row">
              <Checkbox
                :inputId="`order-ice-${ice}`"
                binary
                :modelValue="orderSelections.ice === ice"
                @update:modelValue="(checked: boolean) => updateSingleChoice('ice', ice, checked)"
              />
              <label :for="`order-ice-${ice}`">{{ ice }}</label>
            </div>
          </div>
        </div>
        <div class="form-field">
          <label>Lượng đường <span class="optional-hint">(optional)</span></label>
          <div class="order-option-group">
            <div v-for="sugar in sugarOptions" :key="`sugar-${sugar}`" class="order-option-row">
              <Checkbox
                :inputId="`order-sugar-${sugar}`"
                binary
                :modelValue="orderSelections.sugar === sugar"
                @update:modelValue="(checked: boolean) => updateSingleChoice('sugar', sugar, checked)"
              />
              <label :for="`order-sugar-${sugar}`">{{ sugar }}</label>
            </div>
          </div>
        </div>
        <div class="form-field">
          <label>Note <span class="optional-hint">(optional)</span></label>
          <InputText v-model="orderForm.note" fluid />
        </div>
      </div>
      <template #footer>
        <Button label="Cancel" text @click="orderDialog = false" />
        <Button label="Order" icon="pi pi-check" @click="placeOrder" />
      </template>
    </Dialog>

    <Dialog v-model:visible="editOrderDialog" header="Edit Order" modal :style="{ width: '420px' }">
      <div style="display:flex;flex-direction:column;gap:var(--space-4);">
        <div class="form-field">
          <label class="required">Menu Item</label>
          <Select
            v-model="editOrderForm.itemId"
            :options="menuItems"
            optionLabel="name"
            optionValue="id"
            :loading="menuLoading"
            :emptyMessage="menuLoading ? 'Loading, please wait' : 'No available options'"
            fluid
          >
            <template #option="{ option }">
              <div style="display:flex;justify-content:space-between;gap:var(--space-3);">
                <span>{{ option.name }}</span>
                <span style="color:var(--theme-text-weak);">{{ formatCurrency(option.price) }}</span>
              </div>
            </template>
          </Select>
        </div>
        <div class="form-field">
          <label class="required">Quantity</label>
          <InputNumber v-model="editOrderForm.quantity" :min="1" fluid />
        </div>
        <div class="form-field">
          <label>Note <span class="optional-hint">(optional)</span></label>
          <InputText v-model="editOrderForm.note" fluid />
        </div>
      </div>
      <template #footer>
        <Button label="Cancel" text @click="editOrderDialog = false" />
        <Button label="Save" icon="pi pi-check" @click="updateOrder" />
      </template>
    </Dialog>

    <Dialog v-model:visible="deleteOrderDialog" header="Delete Order" modal :style="{ width: '380px' }">
      <p style="margin:0;">
        Are you sure you want to delete {{ deleteOrderTarget?.itemName || 'this order' }} for
        {{ deleteOrderTarget?.fullName || 'this user' }}? This action cannot be undone.
      </p>
      <template #footer>
        <Button label="Cancel" text @click="deleteOrderDialog = false" />
        <Button label="Delete" severity="danger" icon="pi pi-trash" :loading="deletingOrder" @click="deleteOrder" />
      </template>
    </Dialog>

    <Dialog v-model:visible="deleteRestaurantDialog" header="Archive Restaurant" modal :style="{ width: '380px' }">
      <p style="margin:0;">
        Archive {{ deleteRestaurantTarget?.name || 'this restaurant' }} and hide it from restaurant and menu lists?
      </p>
      <template #footer>
        <Button label="Cancel" text @click="deleteRestaurantDialog = false" />
        <Button label="Archive" severity="danger" icon="pi pi-trash" :loading="deletingRestaurant" @click="deleteRestaurant" />
      </template>
    </Dialog>
  </div>
</template>

<style scoped>
:deep(.p-select-label) {
  color: var(--theme-text-primary) !important;
}

.order-option-group {
  display: flex;
  gap: var(--space-3);
  flex-wrap: wrap;
}

.order-option-row {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.order-actions {
  display: flex;
  align-items: center;
  gap: var(--space-1);
  min-height: 2rem;
}

.menu-grid :deep(.p-datatable-thead > tr > th),
.menu-grid :deep(.p-datatable-tbody > tr > td) {
  vertical-align: top;
}

.menu-price {
  white-space: nowrap;
}

.deadline-pill {
  display: inline-flex;
  align-items: center;
  border-radius: 9999px;
  padding: 4px 10px;
  color: #ffffff;
  font-size: 12px;
  font-weight: 600;
  white-space: nowrap;
}

.deadline-active {
  background: #16a34a;
}

.deadline-expired {
  background: #111111;
}

.scrape-action-btn {
  width: 180px;
  justify-content: center;
}
</style>
