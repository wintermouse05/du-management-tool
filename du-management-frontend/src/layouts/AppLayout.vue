<script setup lang="ts">
import { ref, computed, watch, onMounted, onUnmounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { storeToRefs } from 'pinia'
import { useAuthStore } from '@/stores/auth'
import { useBookmarksStore } from '@/stores/bookmarks'
import { useThemeStore } from '@/stores/theme'
import { wsService } from '@/services/websocket'
import { useToast } from 'primevue/usetoast'
import Button from 'primevue/button'
import NotificationBell from '@/components/NotificationBell.vue'
import CelebrationBanner from '@/components/CelebrationBanner.vue'

const router = useRouter()
const route = useRoute()
const auth = useAuthStore()
const bookmarksStore = useBookmarksStore()
const theme = useThemeStore()
const toast = useToast()
const sidebarOpen = ref(false)
const desktopSidebarVisible = ref(true)
const mobileBreakpoint = 1024
const isMobileViewport = ref(typeof window !== 'undefined' ? window.innerWidth <= mobileBreakpoint : false)
const { pinnedHeaderBookmarks } = storeToRefs(bookmarksStore)

function updateViewportState() {
  isMobileViewport.value = window.innerWidth <= mobileBreakpoint
}

function connectWebSocketIfAuthenticated() {
  if (auth.token) {
    wsService.connect(auth.token, () => {
      wsService.subscribe('/user/queue/notifications', handleIncomingNotification)
    })
  }
}

async function loadHeaderBookmarks() {
  if (!auth.token) return

  try {
    await bookmarksStore.loadBookmarks()
  } catch {
    // Header shortcuts are optional; keep any existing links if a refresh fails.
  }
}

onMounted(() => {
  updateViewportState()
  window.addEventListener('resize', updateViewportState)
  connectWebSocketIfAuthenticated()
  expandActiveNavSection()
  void loadHeaderBookmarks()
})

onUnmounted(() => {
  window.removeEventListener('resize', updateViewportState)
  wsService.disconnect()
})

function handleIncomingNotification(message: { body: string }) {
  const data = JSON.parse(message.body)
  const detail = data?.message ?? 'You have a new notification.'
  toast.add({ severity: 'info', summary: 'New Notification', detail, life: 5000 })
  window.dispatchEvent(new CustomEvent('du-notification-received', { detail: data }))
}

interface NavItem {
  label: string
  icon: string
  to: string
  roles?: string[]
}

interface NavSection {
  key: string
  label?: string
  icon?: string
  items: NavItem[]
}

const expandedNavSections = ref<Record<string, boolean>>({
  activities: true,
  settings: true,
})
const collapsibleNavSectionKeys = new Set(['activities', 'settings'])

function canAccessNavItem(item: NavItem): boolean {
  if (!item.roles) return true
  return item.roles.includes(auth.role)
}

const navSections = computed<NavSection[]>(() => {
  const sections: NavSection[] = [
    {
      key: 'primary',
      items: [
        { label: 'Dashboard', icon: 'pi pi-home', to: '/' },
        { label: 'Members', icon: 'pi pi-users', to: '/members' },
        { label: 'Projects', icon: 'pi pi-briefcase', to: '/projects' },
        { label: 'Leaderboard', icon: 'pi pi-trophy', to: '/leaderboard' },
        { label: 'Bookmarks', icon: 'pi pi-bookmark', to: '/bookmarks' },
        { label: 'Late Records', icon: 'pi pi-clock', to: '/late-records', roles: ['ADMIN', 'HR'] },
      ],
    },
    {
      key: 'activities',
      label: 'Activities',
      icon: 'pi pi-calendar',
      items: [
        { label: 'Events', icon: 'pi pi-calendar', to: '/events' },
        { label: 'Seminars', icon: 'pi pi-microphone', to: '/seminars' },
        { label: 'Orders', icon: 'pi pi-shopping-cart', to: '/orders' },
        { label: 'Gamification', icon: 'pi pi-star', to: '/gamification' },
        { label: 'Surveys', icon: 'pi pi-check-square', to: '/surveys' },
        { label: 'Lucky Draw', icon: 'pi pi-gift', to: '/lucky-draw' },
      ],
    },
    {
      key: 'settings',
      label: 'Settings',
      icon: 'pi pi-cog',
      items: [
        { label: 'Account', icon: 'pi pi-user', to: '/account' },
        { label: 'Notifications', icon: 'pi pi-bell', to: '/notifications', roles: ['ADMIN'] },
        { label: 'Notification Schedules', icon: 'pi pi-clock', to: '/notification-schedules', roles: ['ADMIN'] },
        { label: 'System Logs', icon: 'pi pi-list-check', to: '/logs', roles: ['ADMIN'] },
        { label: 'Roles', icon: 'pi pi-shield', to: '/roles', roles: ['ADMIN'] },
      ],
    },
  ]

  return sections
    .map(section => ({
      ...section,
      items: section.items.filter(canAccessNavItem),
    }))
    .filter(section => section.items.length > 0)
})

function isActive(path: string): boolean {
  if (path === '/') return route.path === '/'
  return route.path.startsWith(path)
}

function isCollapsibleSection(section: NavSection): boolean {
  return collapsibleNavSectionKeys.has(section.key)
}

function isSectionActive(section: NavSection): boolean {
  return section.items.some(item => isActive(item.to))
}

function isSectionExpanded(section: NavSection): boolean {
  return !isCollapsibleSection(section) || expandedNavSections.value[section.key] !== false
}

function toggleNavSection(sectionKey: string) {
  expandedNavSections.value = {
    ...expandedNavSections.value,
    [sectionKey]: !expandedNavSections.value[sectionKey],
  }
}

function expandActiveNavSection() {
  const nextExpandedSections = { ...expandedNavSections.value }

  navSections.value.forEach(section => {
    if (isCollapsibleSection(section) && isSectionActive(section)) {
      nextExpandedSections[section.key] = true
    }
  })

  expandedNavSections.value = nextExpandedSections
}

async function handleLogout() {
  await auth.logout()
  router.push('/login')
}

// Close sidebar on route change (mobile)
watch(() => route.path, () => {
  sidebarOpen.value = false
  expandActiveNavSection()
})
watch(() => auth.token, (newToken, oldToken) => {
  if (newToken === oldToken) return

  wsService.disconnect()
  if (newToken) {
    connectWebSocketIfAuthenticated()
    void loadHeaderBookmarks()
  } else {
    bookmarksStore.clearBookmarks()
  }
})

const roleColor = computed(() => {
  switch (auth.role) {
    case 'ADMIN': return '#dc2626'
    case 'HR': return '#b45309'
    default: return '#1b61c9'
  }
})

const themeModeIcon = computed(() => {
  if (theme.mode === 'light') return 'pi pi-sun'
  if (theme.mode === 'dark') return 'pi pi-moon'
  return 'pi pi-desktop'
})

const themeModeTitle = computed(() => {
  if (theme.mode === 'light') return 'Switch to Dark Mode'
  if (theme.mode === 'dark') return 'Switch to Auto Mode'
  return 'Switch to Light Mode'
})

const desktopSidebarToggleIcon = computed(() => (
  desktopSidebarVisible.value ? 'pi pi-angle-left' : 'pi pi-angle-right'
))

const desktopSidebarToggleTitle = computed(() => (
  desktopSidebarVisible.value ? 'Hide navigation' : 'Show navigation'
))

const userInitial = computed(() => {
  const source = (auth.displayName || auth.username || '').trim()
  return (source.charAt(0) || 'U').toUpperCase()
})
</script>

<template>
  <div class="app-layout" :class="{ 'sidebar-hidden': !desktopSidebarVisible }">
    <!-- Mobile overlay -->
    <div class="sidebar-overlay" :class="{ visible: sidebarOpen }" @click="sidebarOpen = false"></div>

    <!-- Mobile top bar -->
    <header class="mobile-topbar">
      <Button icon="pi pi-bars" text rounded @click="sidebarOpen = !sidebarOpen" class="hamburger-btn" />
      <div class="mobile-logo">
        <div class="logo-icon-sm"><i class="pi pi-th-large"></i></div>
        <span>DU Manager</span>
      </div>
      <div class="mobile-topbar-actions">
        <NotificationBell v-if="auth.token && isMobileViewport" inline />
        <button class="theme-toggle-btn" @click="theme.toggle()" :title="themeModeTitle">
          <i :class="themeModeIcon"></i>
        </button>
        <Button icon="pi pi-sign-out" text rounded severity="secondary" @click="handleLogout" />
      </div>
    </header>

    <!-- Sidebar -->
    <aside class="sidebar" :class="{ open: sidebarOpen }">
      <div class="sidebar-header">
        <div class="logo">
          <div class="logo-icon"><i class="pi pi-th-large"></i></div>
          <span class="logo-text">DU Manager</span>
        </div>
        <Button icon="pi pi-times" text rounded class="sidebar-close" @click="sidebarOpen = false" />
      </div>

      <nav class="sidebar-nav">
        <div v-for="section in navSections" :key="section.key" class="nav-section">
          <button
            v-if="isCollapsibleSection(section)"
            type="button"
            class="nav-section-toggle"
            :class="{ active: isSectionActive(section), expanded: isSectionExpanded(section) }"
            :aria-expanded="isSectionExpanded(section)"
            @click="toggleNavSection(section.key)"
          >
            <span class="nav-section-toggle-main">
              <i :class="section.icon" class="nav-section-toggle-icon"></i>
              <span>{{ section.label }}</span>
            </span>
            <i class="pi pi-chevron-down nav-section-chevron"></i>
          </button>
          <div v-else-if="section.label" class="nav-section-label">{{ section.label }}</div>
          <div v-if="isSectionExpanded(section)" class="nav-section-items" :class="{ nested: isCollapsibleSection(section) }">
            <router-link
              v-for="item in section.items"
              :key="item.to"
              :to="item.to"
              class="nav-item"
              :class="{ active: isActive(item.to) }"
            >
              <i :class="item.icon" class="nav-icon"></i>
              <span class="nav-label">{{ item.label }}</span>
            </router-link>
          </div>
        </div>
      </nav>

      <div class="sidebar-footer">
        <div class="user-info">
          <div class="user-avatar">{{ userInitial }}</div>
          <div class="user-details">
            <span class="user-name">{{ auth.displayName }}</span>
            <span class="user-role" :style="{ color: roleColor }">{{ auth.role }}</span>
          </div>
        </div>
        <div style="display:flex;align-items:center;gap:6px;flex-shrink:0;">
          <button class="theme-toggle-btn" @click="theme.toggle()" :title="themeModeTitle">
            <i :class="themeModeIcon"></i>
          </button>
          <Button icon="pi pi-sign-out" severity="secondary" text rounded aria-label="Logout"
            @click="handleLogout" class="logout-btn" />
        </div>
      </div>
    </aside>

    <!-- Main Content -->
    <main class="main-content">
      <header v-if="auth.token && !isMobileViewport" class="desktop-topbar">
        <div class="desktop-header-left">
          <Button
            :icon="desktopSidebarToggleIcon"
            text
            rounded
            severity="secondary"
            class="desktop-nav-toggle"
            :aria-label="desktopSidebarToggleTitle"
            :title="desktopSidebarToggleTitle"
            @click="desktopSidebarVisible = !desktopSidebarVisible"
          />
          <div v-if="pinnedHeaderBookmarks.length" class="header-bookmarks">
            <a
              v-for="bookmark in pinnedHeaderBookmarks"
              :key="bookmark.id"
              :href="bookmark.url"
              class="header-bookmark-link"
              :title="bookmark.title"
              target="_blank"
              rel="noopener noreferrer"
            >
              <i class="pi pi-bookmark"></i>
              <span class="header-bookmark-title">{{ bookmark.title }}</span>
            </a>
          </div>
        </div>
        <div class="desktop-header-actions">
          <NotificationBell inline />
        </div>
      </header>

      <CelebrationBanner v-if="auth.token" />

      <router-view v-slot="{ Component }">
        <transition name="fade" mode="out-in">
          <component :is="Component" />
        </transition>
      </router-view>
    </main>
  </div>
</template>

<style scoped>
.app-layout {
  display: flex;
  min-height: 100vh;
  transition: background var(--transition-normal), color var(--transition-normal);
}

/* ── Desktop Top Bar ──────────────────────────────── */
.desktop-topbar {
  position: sticky;
  top: 0;
  z-index: 80;
  height: var(--topbar-height);
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-3);
  padding: 0 var(--space-8);
  background: var(--theme-surface-light);
  border-bottom: 1px solid var(--theme-border);
}

.desktop-nav-toggle {
  flex-shrink: 0;
}

.desktop-header-left {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  min-width: 0;
  flex: 1 1 auto;
}

.desktop-header-actions {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  flex-shrink: 0;
}

.header-bookmarks {
  display: flex;
  align-items: center;
  gap: 6px;
  flex: 1 1 auto;
  min-width: 0;
  max-width: 620px;
  overflow: hidden;
}

.header-bookmark-link {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  height: 38px;
  max-width: 140px;
  padding: 0 10px;
  border: 1px solid var(--theme-border);
  border-radius: var(--radius-md);
  background: var(--theme-surface);
  color: var(--theme-text-secondary);
  font-size: 13px;
  font-weight: 600;
  text-decoration: none;
  box-shadow: var(--theme-shadow-soft);
  transition: background var(--transition-fast), color var(--transition-fast), border-color var(--transition-fast);
}

.header-bookmark-link:hover {
  border-color: var(--theme-blue);
  background: var(--theme-bg-hover);
  color: var(--theme-blue);
}

.header-bookmark-link i {
  flex-shrink: 0;
  font-size: 13px;
}

.header-bookmark-title {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* ── Mobile Top Bar ───────────────────────────────── */
.mobile-topbar {
  display: none;
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  height: var(--topbar-height);
  background: var(--theme-surface);
  border-bottom: 1px solid var(--theme-border);
  z-index: 90;
  align-items: center;
  justify-content: space-between;
  padding: 0 var(--space-4);
}

.mobile-topbar-actions {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  flex-shrink: 0;
}

.mobile-logo {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  font-size: 16px;
  font-weight: 700;
  color: var(--theme-text-primary);
}

.logo-icon-sm {
  width: 30px;
  height: 30px;
  background: var(--theme-blue);
  border-radius: var(--radius-md);
  display: flex;
  align-items: center;
  justify-content: center;
  color: white;
  font-size: 13px;
}

/* ── Sidebar Overlay (mobile) ─────────────────────── */
.sidebar-overlay {
  display: none;
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.4);
  z-index: 99;
  opacity: 0;
  transition: opacity var(--transition-normal);
  pointer-events: none;
}

.sidebar-overlay.visible {
  opacity: 1;
  pointer-events: auto;
}

.sidebar-close {
  display: none;
}

/* ── Sidebar ──────────────────────────────────────── */
.sidebar {
  width: var(--sidebar-width);
  background: var(--theme-surface);
  border-right: 1px solid var(--theme-border);
  display: flex;
  flex-direction: column;
  position: fixed;
  top: 0;
  left: 0;
  bottom: 0;
  z-index: 100;
  overflow-y: auto;
  transition: transform var(--transition-normal);
}

.sidebar-header {
  padding: var(--space-6);
  border-bottom: 1px solid var(--theme-divider);
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.logo {
  display: flex;
  align-items: center;
  gap: var(--space-3);
}

.logo-icon {
  width: 36px;
  height: 36px;
  background: var(--theme-blue);
  border-radius: var(--radius-md);
  display: flex;
  align-items: center;
  justify-content: center;
  color: white;
  font-size: 16px;
}

.logo-text {
  font-size: 18px;
  font-weight: 700;
  color: var(--theme-text-primary);
  letter-spacing: -0.3px;
}

/* ── Nav ──────────────────────────────────────────── */
.sidebar-nav {
  flex: 1;
  padding: var(--space-3);
  display: flex;
  flex-direction: column;
  gap: var(--space-3);
  overflow-y: auto;
}

.nav-section {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.nav-section-label {
  padding: 8px 14px 4px;
  color: var(--theme-text-weak);
  font-size: 11px;
  font-weight: 700;
  text-transform: uppercase;
}

.nav-section-toggle {
  width: 100%;
  min-height: 42px;
  border: 0;
  border-radius: var(--radius-md);
  background: transparent;
  color: var(--theme-text-secondary);
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-2);
  padding: 10px 14px;
  font-family: inherit;
  font-size: 14px;
  font-weight: 500;
  line-height: normal;
  letter-spacing: 0.08px;
  cursor: pointer;
  transition: background var(--transition-fast), color var(--transition-fast);
}

.nav-section-toggle:hover {
  background: var(--theme-bg-hover);
  color: var(--theme-text-primary);
}

.nav-section-toggle.active {
  background: var(--theme-blue-light);
  color: var(--theme-blue);
}

.nav-section-toggle-main {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  min-width: 0;
}

.nav-section-toggle-icon {
  font-size: 16px;
  width: 20px;
  text-align: center;
  color: var(--theme-text-weak);
  transition: color var(--transition-fast);
}

.nav-section-toggle.active .nav-section-toggle-icon,
.nav-section-toggle.active .nav-section-chevron {
  color: var(--theme-blue);
}

.nav-section-chevron {
  flex-shrink: 0;
  color: var(--theme-text-weak);
  font-size: 12px;
  transition: transform var(--transition-fast), color var(--transition-fast);
}

.nav-section-toggle.expanded .nav-section-chevron {
  transform: rotate(180deg);
}

.nav-section-items {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.nav-section-items.nested {
  padding-left: 10px;
}

.nav-item {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  padding: 10px 14px;
  border-radius: var(--radius-md);
  color: var(--theme-text-secondary);
  font-size: 14px;
  font-weight: 500;
  letter-spacing: 0.08px;
  text-decoration: none;
  transition: all var(--transition-fast);
  cursor: pointer;
}

.nav-item:hover { background: var(--theme-bg-hover); color: var(--theme-text-primary); }
.nav-item.active { background: var(--theme-blue-light); color: var(--theme-blue); }
.nav-item.active .nav-icon { color: var(--theme-blue); }

.nav-icon {
  font-size: 16px;
  width: 20px;
  text-align: center;
  color: var(--theme-text-weak);
  transition: color var(--transition-fast);
}

.nav-label { white-space: nowrap; }

/* ── Footer ───────────────────────────────────────── */
.sidebar-footer {
  padding: var(--space-4);
  border-top: 1px solid var(--theme-divider);
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-2);
}

.user-info { display: flex; align-items: center; gap: var(--space-3); min-width: 0; }

.user-avatar {
  width: 34px; height: 34px; border-radius: var(--radius-full);
  background: var(--theme-blue); color: white;
  display: flex; align-items: center; justify-content: center;
  font-size: 14px; font-weight: 600; flex-shrink: 0;
}

.user-details { display: flex; flex-direction: column; min-width: 0; }
.user-name { font-size: 13px; font-weight: 600; color: var(--theme-text-primary); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.user-role { font-size: 11px; font-weight: 700; text-transform: uppercase; letter-spacing: 0.5px; }
.logout-btn { flex-shrink: 0; }

/* ── Main ─────────────────────────────────────────── */
.main-content {
  flex: 1;
  margin-left: var(--sidebar-width);
  min-height: 100vh;
  background: var(--theme-surface-light);
  transition: margin-left var(--transition-normal), background var(--transition-normal);
}

/* ============================================================
   Responsive: Tablet (≤ 1024px)
   ============================================================ */
@media (min-width: 1025px) {
  .app-layout.sidebar-hidden .sidebar {
    transform: translateX(-100%);
  }

  .app-layout.sidebar-hidden .main-content {
    margin-left: 0;
  }
}

@media (max-width: 1024px) {
  .main-content {
    margin-left: 0;
    padding-top: var(--topbar-height);
  }

  .mobile-topbar {
    display: flex;
  }

  .sidebar {
    transform: translateX(-100%);
  }

  .sidebar.open {
    transform: translateX(0);
    box-shadow: var(--theme-shadow-elevated);
  }

  .sidebar-overlay {
    display: block;
  }

  .sidebar-close {
    display: inline-flex;
  }
}
</style>
