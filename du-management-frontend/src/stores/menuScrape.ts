import { defineStore } from 'pinia'
import { ref } from 'vue'
import type { MenuScrapeItemResponse } from '@/types'

/**
 * Pinia store to persist scraped menu data across route navigations.
 * Data lives in memory (app-level) — survives route changes but clears on page refresh.
 */
export const useMenuScrapeStore = defineStore('menuScrape', () => {
  const scrapeUrl = ref('')
  const scrapedItems = ref<MenuScrapeItemResponse[]>([])

  function setResults(url: string, items: MenuScrapeItemResponse[]) {
    scrapeUrl.value = url
    scrapedItems.value = items
  }

  function clear() {
    scrapeUrl.value = ''
    scrapedItems.value = []
  }

  return { scrapeUrl, scrapedItems, setResults, clear }
})
