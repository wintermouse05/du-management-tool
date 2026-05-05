import { defineStore } from 'pinia'
import { ref, watch, computed } from 'vue'

export type ThemeMode = 'light' | 'dark' | 'auto'

export const useThemeStore = defineStore('theme', () => {
  const mode = ref<ThemeMode>((localStorage.getItem('theme-mode') as ThemeMode) || 'auto')

  // When mode is 'auto', set data-theme="auto" and let CSS @media handle it.
  // When mode is 'light' or 'dark', set data-theme explicitly.
  function applyMode(m: ThemeMode) {
    document.documentElement.setAttribute('data-theme', m)
  }

  // isDark is used for the icon display only — for 'auto' we check the media query
  const isDark = computed(() => {
    if (mode.value === 'auto') {
      return window.matchMedia('(prefers-color-scheme: dark)').matches
    }
    return mode.value === 'dark'
  })

  // Apply on init
  applyMode(mode.value)

  watch(mode, (newMode) => {
    localStorage.setItem('theme-mode', newMode)
    applyMode(newMode)
  })

  function toggle() {
    if (mode.value === 'light') mode.value = 'dark'
    else if (mode.value === 'dark') mode.value = 'auto'
    else mode.value = 'light'
  }

  return { mode, isDark, toggle }
})
