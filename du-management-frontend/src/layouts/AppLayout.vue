<script setup lang="ts">
import { ref, computed, watch, onMounted, onUnmounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { useThemeStore } from '@/stores/theme'
import { wsService } from '@/services/websocket'
import { useToast } from 'primevue/usetoast'
import Button from 'primevue/button'
import NotificationBell from '@/components/NotificationBell.vue'

const router = useRouter()
const route = useRoute()
const auth = useAuthStore()
const theme = useThemeStore()
const toast = useToast()
const sidebarOpen = ref(false)

onMounted(() => {
  if (auth.token) {
    wsService.connect(auth.token, () => {
      wsService.subscribe('/user/queue/notifications', handleIncomingNotification)
    })
  }
})

onUnmounted(() => {
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

const navItems = computed<NavItem[]>(() => {
  const items: NavItem[] = [
    { label: 'Dashboard', icon: 'pi pi-home', to: '/' },
    { label: 'Members', icon: 'pi pi-users', to: '/members', roles: ['ADMIN', 'HR'] },
    { label: 'Events', icon: 'pi pi-calendar', to: '/events' },
    { label: 'Seminars', icon: 'pi pi-microphone', to: '/seminars' },
    { label: 'Orders', icon: 'pi pi-shopping-cart', to: '/orders' },
    { label: 'Surveys', icon: 'pi pi-check-square', to: '/surveys' },
    { label: 'Leaderboard', icon: 'pi pi-trophy', to: '/leaderboard' },
    { label: 'Gamification', icon: 'pi pi-star', to: '/gamification' },
    { label: 'Late Records', icon: 'pi pi-clock', to: '/late-records', roles: ['ADMIN', 'HR'] },
    { label: 'Lucky Draw', icon: 'pi pi-gift', to: '/lucky-draw' },
    { label: 'Roles', icon: 'pi pi-shield', to: '/roles', roles: ['ADMIN'] },
    { label: 'Notifications', icon: 'pi pi-bell', to: '/notifications', roles: ['ADMIN'] },
  ]
  return items.filter(item => {
    if (!item.roles) return true
    return item.roles.includes(auth.role)
  })
})

function isActive(path: string): boolean {
  if (path === '/') return route.path === '/'
  return route.path.startsWith(path)
}

async function handleLogout() {
  await auth.logout()
  router.push('/login')
}

// Close sidebar on route change (mobile)
watch(() => route.path, () => { sidebarOpen.value = false })

const roleColor = computed(() => {
  switch (auth.role) {
    case 'ADMIN': return '#dc2626'
    case 'HR': return '#b45309'
    default: return '#1b61c9'
  }
})
</script>

<template>
  <div class="app-layout">
    <NotificationBell v-if="auth.token" />

    <!-- Mobile overlay -->
    <div class="sidebar-overlay" :class="{ visible: sidebarOpen }" @click="sidebarOpen = false"></div>

    <!-- Mobile top bar -->
    <header class="mobile-topbar">
      <Button icon="pi pi-bars" text rounded @click="sidebarOpen = !sidebarOpen" class="hamburger-btn" />
      <div class="mobile-logo">
        <div class="logo-icon-sm"><i class="pi pi-th-large"></i></div>
        <span>DU Manager</span>
      </div>
      <div style="display:flex;align-items:center;gap:8px;">
        <button class="theme-toggle-btn" @click="theme.toggle()" :title="theme.isDark ? 'Switch to Light Mode' : 'Switch to Dark Mode'">
          <i :class="theme.isDark ? 'pi pi-sun' : 'pi pi-moon'"></i>
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
        <router-link
          v-for="item in navItems"
          :key="item.to"
          :to="item.to"
          class="nav-item"
          :class="{ active: isActive(item.to) }"
        >
          <i :class="item.icon" class="nav-icon"></i>
          <span class="nav-label">{{ item.label }}</span>
        </router-link>
      </nav>

      <div class="sidebar-footer">
        <div class="user-info">
          <div class="user-avatar">{{ auth.username.charAt(0).toUpperCase() }}</div>
          <div class="user-details">
            <span class="user-name">{{ auth.username }}</span>
            <span class="user-role" :style="{ color: roleColor }">{{ auth.role }}</span>
          </div>
        </div>
        <div style="display:flex;align-items:center;gap:6px;flex-shrink:0;">
          <button class="theme-toggle-btn" @click="theme.toggle()" :title="theme.isDark ? 'Switch to Light Mode' : 'Switch to Dark Mode'">
            <i :class="theme.isDark ? 'pi pi-sun' : 'pi pi-moon'"></i>
          </button>
          <Button icon="pi pi-sign-out" severity="secondary" text rounded aria-label="Logout"
            @click="handleLogout" class="logout-btn" />
        </div>
      </div>
    </aside>

    <!-- Main Content -->
    <main class="main-content">
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
  gap: 2px;
  overflow-y: auto;
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
}

/* ============================================================
   Responsive: Tablet (≤ 1024px)
   ============================================================ */
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
