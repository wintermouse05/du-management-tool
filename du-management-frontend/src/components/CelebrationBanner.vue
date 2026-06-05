<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { vConfetti, type ConfettiOptions } from '@neoconfetti/vue'
import Button from 'primevue/button'
import { membersApi } from '@/api/members'
import { UserStatus, type MemberResponse } from '@/types'
import { formatMemberName } from '@/utils/memberDisplay'

type CelebrationType = 'birthday' | 'anniversary'

type Celebration = {
  key: string
  type: CelebrationType
  memberId: number
  name: string
  years?: number
}

const celebrations = ref<Celebration[]>([])
const dismissed = ref(false)
const confettiVisible = ref(false)
const stageWidth = ref(typeof window !== 'undefined' ? window.innerWidth : 1600)
let confettiHideTimer: number | undefined
let confettiRepeatTimer: number | undefined

const today = new Date()
const todayKey = [
  today.getFullYear(),
  String(today.getMonth() + 1).padStart(2, '0'),
  String(today.getDate()).padStart(2, '0'),
].join('-')
const dismissedStorageKey = `du-celebration-banner-dismissed-${todayKey}`

const visible = computed(() => !dismissed.value && celebrations.value.length > 0)
const birthdayCelebrations = computed(() => celebrations.value.filter(item => item.type === 'birthday'))
const anniversaryCelebrations = computed(() => celebrations.value.filter(item => item.type === 'anniversary'))
const birthdayNames = computed(() => birthdayCelebrations.value.map(item => item.name).join(', '))
const anniversaryText = computed(() => anniversaryCelebrations.value
  .map(item => `${item.name}${item.years ? ` (${item.years} ${item.years === 1 ? 'year' : 'years'})` : ''}`)
  .join(', '))

const headline = computed(() => {
  const birthdayCount = birthdayCelebrations.value.length
  const anniversaryCount = anniversaryCelebrations.value.length
  if (birthdayCount && anniversaryCount) return 'Birthdays and work anniversaries today'
  if (birthdayCount) return birthdayCount === 1 ? 'Birthday today' : 'Birthdays today'
  return anniversaryCount === 1 ? 'Work anniversary today' : 'Work anniversaries today'
})

const confettiOptions = computed<ConfettiOptions>(() => ({
  particleCount: Math.min(220, 90 + celebrations.value.length * 35),
  particleSize: 8,
  duration: 3200,
  force: 0.38,
  stageWidth: stageWidth.value,
  stageHeight: 420,
  colors: ['#1b61c9', '#12a67c', '#f5b70a', '#d94545', '#8b5cf6'],
}))

onMounted(() => {
  dismissed.value = sessionStorage.getItem(dismissedStorageKey) === 'true'
  window.addEventListener('resize', updateStageWidth)
  void loadCelebrations()
})

onUnmounted(() => {
  window.removeEventListener('resize', updateStageWidth)
  clearConfettiTimers()
})

async function loadCelebrations() {
  if (dismissed.value) {
    return
  }

  try {
    const response = await membersApi.search({
      page: 0,
      size: 1000,
      status: UserStatus.ACTIVE,
      sort: 'fullName,asc',
    })
    celebrations.value = response.data.content.flatMap(member => celebrationsForMember(member))
    triggerConfetti()
  } catch {
    celebrations.value = []
  }
}

function celebrationsForMember(member: MemberResponse): Celebration[] {
  const name = formatMemberName(member)
  const memberCelebrations: Celebration[] = []

  if (isTodayMonthDay(member.dob)) {
    memberCelebrations.push({
      key: `birthday-${member.id}`,
      type: 'birthday',
      memberId: member.id,
      name,
    })
  }

  if (isTodayMonthDay(member.joinDate)) {
    const years = yearsSince(member.joinDate)
    if (years > 0) {
      memberCelebrations.push({
        key: `anniversary-${member.id}`,
        type: 'anniversary',
        memberId: member.id,
        name,
        years,
      })
    }
  }

  return memberCelebrations
}

function isTodayMonthDay(value: string | null | undefined) {
  const parts = parseDateParts(value)
  if (!parts) return false
  return parts.month === today.getMonth() + 1 && parts.day === today.getDate()
}

function yearsSince(value: string | null | undefined) {
  const parts = parseDateParts(value)
  if (!parts) return 0
  return today.getFullYear() - parts.year
}

function parseDateParts(value: string | null | undefined) {
  if (!value) return null
  const match = /^(\d{4})-(\d{2})-(\d{2})/.exec(value)
  if (!match) return null
  return {
    year: Number(match[1]),
    month: Number(match[2]),
    day: Number(match[3]),
  }
}

function triggerConfetti() {
  if (!celebrations.value.length || dismissed.value) {
    return
  }
  clearConfettiTimers()
  confettiVisible.value = false
  window.requestAnimationFrame(() => {
    confettiVisible.value = true
    confettiHideTimer = window.setTimeout(() => {
      confettiVisible.value = false
      scheduleNextConfetti()
    }, confettiOptions.value.duration ?? 3200)
  })
}

function scheduleNextConfetti() {
  if (!visible.value) {
    return
  }
  confettiRepeatTimer = window.setTimeout(() => {
    triggerConfetti()
  }, 5000)
}

function clearConfettiTimers() {
  if (confettiHideTimer) {
    window.clearTimeout(confettiHideTimer)
    confettiHideTimer = undefined
  }
  if (confettiRepeatTimer) {
    window.clearTimeout(confettiRepeatTimer)
    confettiRepeatTimer = undefined
  }
}

function dismissBanner() {
  dismissed.value = true
  confettiVisible.value = false
  clearConfettiTimers()
  sessionStorage.setItem(dismissedStorageKey, 'true')
}

function updateStageWidth() {
  stageWidth.value = window.innerWidth
}
</script>

<template>
  <section v-if="visible" class="celebration-banner" aria-live="polite">
    <div v-if="confettiVisible" v-confetti="confettiOptions" class="celebration-confetti"></div>
    <div class="celebration-icon">
      <i class="pi pi-gift"></i>
    </div>
    <div class="celebration-copy">
      <p class="celebration-heading">{{ headline }}</p>
      <div class="celebration-details">
        <span v-if="birthdayCelebrations.length" class="celebration-detail">
          <i class="pi pi-star"></i>
          <span>{{ birthdayNames }}</span>
        </span>
        <span v-if="anniversaryCelebrations.length" class="celebration-detail">
          <i class="pi pi-calendar-plus"></i>
          <span>{{ anniversaryText }}</span>
        </span>
      </div>
    </div>
    <Button
      icon="pi pi-times"
      text
      rounded
      severity="secondary"
      class="celebration-dismiss"
      aria-label="Dismiss celebration banner"
      @click="dismissBanner"
    />
  </section>
</template>

<style scoped>
.celebration-banner {
  position: relative;
  isolation: isolate;
  display: flex;
  align-items: center;
  gap: var(--space-3);
  margin: var(--space-5) var(--space-8) 0;
  padding: 12px 14px;
  border: 1px solid color-mix(in srgb, var(--theme-blue) 18%, var(--theme-border));
  border-radius: var(--radius-card);
  background:
    linear-gradient(135deg, color-mix(in srgb, var(--theme-blue) 10%, transparent), transparent 38%),
    var(--theme-surface);
  box-shadow: var(--theme-shadow-soft);
  overflow: hidden;
}

.celebration-confetti {
  position: absolute;
  top: 16px;
  left: 50%;
  pointer-events: none;
  z-index: 2;
}

.celebration-icon {
  position: relative;
  z-index: 1;
  display: grid;
  place-items: center;
  flex-shrink: 0;
  width: 38px;
  height: 38px;
  border-radius: var(--radius-md);
  background: var(--theme-blue);
  color: white;
}

.celebration-copy {
  position: relative;
  z-index: 1;
  min-width: 0;
  flex: 1 1 auto;
}

.celebration-heading {
  margin: 0;
  color: var(--theme-text-primary);
  font-size: 14px;
  font-weight: 700;
}

.celebration-details {
  display: flex;
  flex-wrap: wrap;
  gap: 6px 14px;
  margin-top: 3px;
}

.celebration-detail {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  min-width: 0;
  color: var(--theme-text-secondary);
  font-size: 13px;
  line-height: 1.35;
}

.celebration-detail i {
  flex-shrink: 0;
  color: var(--theme-blue);
  font-size: 12px;
}

.celebration-detail span {
  overflow-wrap: anywhere;
}

.celebration-dismiss {
  position: relative;
  z-index: 1;
  flex-shrink: 0;
}

@media (max-width: 768px) {
  .celebration-banner {
    align-items: flex-start;
    margin: var(--space-4) var(--space-4) 0;
  }

  .celebration-details {
    flex-direction: column;
    gap: 4px;
  }
}
</style>
