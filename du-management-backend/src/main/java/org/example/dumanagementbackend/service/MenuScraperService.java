package org.example.dumanagementbackend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.TimeoutError;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.WaitUntilState;
import org.example.dumanagementbackend.dto.order.MenuScrapeItemResponse;
import org.example.dumanagementbackend.dto.order.MenuScrapeResponse;
import org.example.dumanagementbackend.exception.BadRequestException;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class MenuScraperService {

    private static final Logger LOGGER = LoggerFactory.getLogger(MenuScraperService.class);
    private static final double PAGE_LOAD_TIMEOUT_MS = 90_000D;
    private static final double NETWORK_IDLE_GRACE_MS = 10_000D;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public MenuScrapeResponse scrape(String url) {
        boolean isShopeeFood = url.contains("shopeefood.vn");

        String renderedHtml;
        try (Playwright playwright = Playwright.create()) {
            Browser browser = playwright.chromium().launch(new BrowserType.LaunchOptions()
                    .setHeadless(true));
            BrowserContext context = browser.newContext(new Browser.NewContextOptions()
                    .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"));
            Page page = context.newPage();
            page.setDefaultTimeout(PAGE_LOAD_TIMEOUT_MS);
            page.setDefaultNavigationTimeout(PAGE_LOAD_TIMEOUT_MS);

            LOGGER.info("Scraping URL: {} (type: {})", url, isShopeeFood ? "ShopeeFood" : "GrabFood");
            page.navigate(url, new Page.NavigateOptions()
                    .setWaitUntil(WaitUntilState.DOMCONTENTLOADED)
                    .setTimeout(PAGE_LOAD_TIMEOUT_MS));
            waitForNetworkToSettle(page, url);
            renderedHtml = page.content();
            browser.close();
        } catch (Exception e) {
            LOGGER.error("Failed to load menu URL: {}", url, e);
            throw new BadRequestException("Failed to load URL. Please verify the link and try again.");
        }

        if (isShopeeFood) {
            return parseShopeeFoodMenu(renderedHtml);
        }
        return parseGrabFoodMenu(renderedHtml);
    }

    private void waitForNetworkToSettle(Page page, String url) {
        try {
            page.waitForLoadState(LoadState.NETWORKIDLE, new Page.WaitForLoadStateOptions()
                    .setTimeout(NETWORK_IDLE_GRACE_MS));
        } catch (TimeoutError e) {
            LOGGER.debug("Page kept network activity while scraping {}; continuing with current DOM.", url);
        }
    }

    // ==================== ShopeeFood Parser (HTML-based) ====================

    private MenuScrapeResponse parseShopeeFoodMenu(String html) {
        Document doc = Jsoup.parse(html);
        List<MenuScrapeItemResponse> items = new ArrayList<>();
        String restaurantName = extractRestaurantName(doc, null);

        Elements itemElements = doc.select(".menu-restaurant-detail .item-restaurant-row");

        for (Element row : itemElements) {
            String name = row.select(".item-restaurant-name").text();
            String price = row.select(".current-price").text().replaceAll("[^0-9]", "");
            String description = row.select(".item-restaurant-desc").text();

            if (!name.isEmpty()) {
                items.add(new MenuScrapeItemResponse(name, price, description));
            }
        }

        if (items.isEmpty()) {
            throw new BadRequestException("No menu items could be extracted from ShopeeFood. The page structure may have changed.");
        }

        LOGGER.info("Extracted {} items from ShopeeFood", items.size());
        return new MenuScrapeResponse(restaurantName, items);
    }

    // ==================== GrabFood Parser (JSON-LD based) ====================

    private MenuScrapeResponse parseGrabFoodMenu(String html) {
        Document doc = Jsoup.parse(html);
        Elements scripts = doc.select("script");
        String jsonString = null;

        for (Element script : scripts) {
            if (script.html().contains("\"@type\":\"MenuItem\"")) {
                jsonString = script.html();
                break;
            }
        }

        if (jsonString == null) {
            throw new BadRequestException("No menu data found on the page. The URL may not be a supported GrabFood page.");
        }

        String trimmedJson = jsonString.trim();
        if (!trimmedJson.startsWith("{") && !trimmedJson.startsWith("[")) {
            int objectStart = trimmedJson.indexOf('{');
            int arrayStart = trimmedJson.indexOf('[');
            int startIndex;
            if (objectStart == -1) {
                startIndex = arrayStart;
            } else if (arrayStart == -1) {
                startIndex = objectStart;
            } else {
                startIndex = Math.min(objectStart, arrayStart);
            }
            if (startIndex != -1) {
                trimmedJson = trimmedJson.substring(startIndex);
            }
        }

        List<MenuScrapeItemResponse> items = new ArrayList<>();
        String restaurantName = extractRestaurantName(doc, null);

        try {
            JsonNode rootNode = objectMapper.readTree(trimmedJson);
            restaurantName = firstNonBlank(rootNode.path("name").asText(null), restaurantName);

            JsonNode sectionsNode = rootNode.path("hasMenu").path("hasMenuSection");
            if (sectionsNode.isArray()) {
                for (JsonNode sectionNode : sectionsNode) {
                    JsonNode itemsNode = sectionNode.path("hasMenuItem");
                    if (!itemsNode.isArray()) {
                        continue;
                    }
                    for (JsonNode itemNode : itemsNode) {
                        if (!"MenuItem".equals(itemNode.path("@type").asText())) {
                            continue;
                        }
                        String name = itemNode.path("name").asText("N/A");
                        String description = itemNode.path("description").asText("");
                        JsonNode offersNode = itemNode.path("offers");
                        String price = offersNode.path("price").asText("N/A");
                        items.add(new MenuScrapeItemResponse(name, price, description));
                    }
                }
            } else if (rootNode.isArray()) {
                for (JsonNode itemNode : rootNode) {
                    if (!"MenuItem".equals(itemNode.path("@type").asText())) {
                        continue;
                    }
                    String name = itemNode.path("name").asText("N/A");
                    String description = itemNode.path("description").asText("");
                    JsonNode offersNode = itemNode.path("offers");
                    String price = offersNode.path("price").asText("N/A");
                    items.add(new MenuScrapeItemResponse(name, price, description));
                }
            }

            if (items.isEmpty()) {
                throw new BadRequestException("No menu items could be extracted from GrabFood. The page structure may have changed.");
            }
        } catch (BadRequestException e) {
            throw e;
        } catch (Exception e) {
            LOGGER.error("Failed to parse GrabFood menu data", e);
            throw new BadRequestException("Failed to parse GrabFood menu data. Please try again later.");
        }

        LOGGER.info("Extracted {} items from GrabFood", items.size());
        return new MenuScrapeResponse(restaurantName, items);
    }

    private String extractRestaurantName(Document doc, String fallback) {
        String name = firstNonBlank(
                doc.selectFirst("meta[property=og:title]") != null
                        ? doc.selectFirst("meta[property=og:title]").attr("content")
                        : null,
                doc.selectFirst("meta[name=twitter:title]") != null
                        ? doc.selectFirst("meta[name=twitter:title]").attr("content")
                        : null,
                doc.selectFirst("h1") != null ? doc.selectFirst("h1").text() : null,
                doc.title(),
                fallback
        );
        return cleanRestaurantName(name);
    }

    private String cleanRestaurantName(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        String cleaned = name.trim()
                .replaceAll("\\s+", " ")
                .replaceFirst("(?i)\\s*[-|\\u2013\\u2014]\\s*(GrabFood|ShopeeFood).*$", "")
                .replaceFirst("(?i)\\s*[-|\\u2013\\u2014]\\s*Food Delivery.*$", "")
                .replaceFirst("(?i)^Order\\s+", "")
                .trim();
        return cleaned.isBlank() ? null : cleaned;
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }
}
