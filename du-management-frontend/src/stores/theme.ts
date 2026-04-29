import { defineStore } from 'pinia'
import { ref, watch } from 'vue'

export const useThemeStore = defineStore('theme', () => {
  const isDark = ref(localStorage.getItem('theme') === 'dark')

  function applyTheme(dark: boolean) {
    document.documentElement.setAttribute('data-theme', dark ? 'dark' : 'light')
  }

  // Apply on init
  applyTheme(isDark.value)

  watch(isDark, (dark) => {
    localStorage.setItem('theme', dark ? 'dark' : 'light')
    applyTheme(dark)
  })

  function toggle() {
    isDark.value = !isDark.value
  }

  return { isDark, toggle }
})
