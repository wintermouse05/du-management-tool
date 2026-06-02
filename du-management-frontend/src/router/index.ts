import { createRouter, createWebHistory } from 'vue-router'
import type { RouteRecordRaw } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const routes: RouteRecordRaw[] = [
  // ── Auth (no layout) ──────────────────────────────
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/auth/LoginView.vue'),
    meta: { requiresAuth: false },
  },
  {
    path: '/register',
    name: 'Register',
    component: () => import('@/views/auth/RegisterView.vue'),
    meta: { requiresAuth: false },
  },
  {
    path: '/forgot-password',
    name: 'ForgotPassword',
    component: () => import('@/views/auth/ForgotPasswordView.vue'),
    meta: { requiresAuth: false },
  },
  {
    path: '/reset-password/:token',
    name: 'ResetPassword',
    component: () => import('@/views/auth/ResetPasswordView.vue'),
    meta: { requiresAuth: false },
    props: true,
  },

  // ── App (AppLayout) ───────────────────────────────
  {
    path: '/',
    component: () => import('@/layouts/AppLayout.vue'),
    meta: { requiresAuth: true },
    children: [
      {
        path: '',
        name: 'Dashboard',
        component: () => import('@/views/DashboardView.vue'),
      },
      {
        path: 'members',
        name: 'Members',
        component: () => import('@/views/members/MembersView.vue'),
      },
      {
        path: 'projects',
        name: 'Projects',
        component: () => import('@/views/projects/ProjectsView.vue'),
      },
      {
        path: 'projects/:id',
        name: 'ProjectDetail',
        component: () => import('@/views/projects/ProjectDetailView.vue'),
        props: true,
      },
      {
        path: 'events',
        name: 'Events',
        component: () => import('@/views/events/EventsView.vue'),
      },
      {
        path: 'events/:id',
        name: 'EventDetail',
        component: () => import('@/views/events/EventDetailView.vue'),
        props: true,
      },
      {
        path: 'seminars',
        name: 'Seminars',
        component: () => import('@/views/seminars/SeminarsView.vue'),
      },
      {
        path: 'orders',
        name: 'Orders',
        component: () => import('@/views/orders/OrdersView.vue'),
      },
      {
        path: 'bookmarks',
        name: 'Bookmarks',
        component: () => import('@/views/bookmarks/BookmarksView.vue'),
      },
      {
        path: 'account',
        name: 'Account',
        component: () => import('@/views/account/AccountView.vue'),
      },
      {
        path: 'surveys',
        name: 'Surveys',
        component: () => import('@/views/surveys/SurveysView.vue'),
      },
      {
        path: 'gamification',
        name: 'Gamification',
        component: () => import('@/views/gamification/GamificationView.vue'),
      },
      {
        path: 'leaderboard',
        name: 'Leaderboard',
        component: () => import('@/views/gamification/LeaderboardView.vue'),
      },
      {
        path: 'late-records',
        name: 'LateRecords',
        component: () => import('@/views/late/LateRecordsView.vue'),
        meta: { roles: ['ADMIN', 'HR'] },
      },
      {
        path: 'lucky-draw',
        name: 'LuckyDraw',
        component: () => import('@/views/luckydraw/LuckyDrawView.vue'),
      },
      {
        path: 'groups',
        name: 'Groups',
        component: () => import('@/views/groups/GroupsView.vue'),
        meta: { roles: ['ADMIN', 'HR'] },
      },
      {
        path: 'roles',
        name: 'Roles',
        component: () => import('@/views/roles/RolesView.vue'),
        meta: { roles: ['ADMIN'] },
      },
      {
        path: 'notifications',
        name: 'Notifications',
        component: () => import('@/views/notifications/NotificationsView.vue'),
        meta: { roles: ['ADMIN'] },
      },
      {
        path: 'notification-schedules',
        name: 'NotificationSchedules',
        component: () => import('@/views/notifications/NotificationSchedulesView.vue'),
        meta: { roles: ['ADMIN'] },
      },
      {
        path: 'logs',
        name: 'SystemLogs',
        component: () => import('@/views/system/SystemLogsView.vue'),
        meta: { roles: ['ADMIN'] },
      },
    ],
  },

  // ── Catch-all ─────────────────────────────────────
  {
    path: '/:pathMatch(.*)*',
    redirect: '/',
  },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
})

// Navigation guard
router.beforeEach((to, _from, next) => {
  const auth = useAuthStore()

  // Redirect authenticated users away from login/register
  if (!to.meta.requiresAuth && to.meta.requiresAuth !== undefined && auth.isAuthenticated) {
    return next('/')
  }

  // Check authentication
  if (to.meta.requiresAuth !== false && !auth.isAuthenticated) {
    return next('/login')
  }

  // Check role-based access
  const requiredRoles = to.meta.roles as string[] | undefined
  if (requiredRoles && requiredRoles.length > 0) {
    if (!requiredRoles.includes(auth.role)) {
      return next('/')
    }
  }

  next()
})

export default router
