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
  const restaurantName = ref('')

  function setResults(url: string, items: MenuScrapeItemResponse[], parsedRestaurantName?: string | null) {
    scrapeUrl.value = url
    scrapedItems.value = items
    if (parsedRestaurantName?.trim()) {
      restaurantName.value = parsedRestaurantName.trim()
    }
  }

  function clear() {
    scrapeUrl.value = ''
    scrapedItems.value = []
    restaurantName.value = ''
  }

  return { scrapeUrl, scrapedItems, restaurantName, setResults, clear }
})
