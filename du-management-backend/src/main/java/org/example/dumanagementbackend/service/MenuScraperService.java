package org.example.dumanagementbackend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.options.WaitUntilState;
import org.example.dumanagementbackend.dto.order.MenuScrapeItemResponse;
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
    private final ObjectMapper objectMapper = new ObjectMapper();

    public List<MenuScrapeItemResponse> scrape(String url) {
        boolean isShopeeFood = url.contains("shopeefood.vn");

        String renderedHtml;
        try (Playwright playwright = Playwright.create()) {
            Browser browser = playwright.chromium().launch(new BrowserType.LaunchOptions()
                    .setHeadless(true));
            BrowserContext context = browser.newContext(new Browser.NewContextOptions()
                    .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"));
            Page page = context.newPage();

            LOGGER.info("Scraping URL: {} (type: {})", url, isShopeeFood ? "ShopeeFood" : "GrabFood");
            page.navigate(url, new Page.NavigateOptions().setWaitUntil(WaitUntilState.NETWORKIDLE));
            renderedHtml = page.content();
            browser.close();
        } catch (Exception e) {
            throw new BadRequestException("Failed to load URL: " + e.getMessage());
        }

        if (isShopeeFood) {
            return parseShopeeFoodMenu(renderedHtml);
        }
        return parseGrabFoodMenu(renderedHtml);
    }

    // ==================== ShopeeFood Parser (HTML-based) ====================

    private List<MenuScrapeItemResponse> parseShopeeFoodMenu(String html) {
        Document doc = Jsoup.parse(html);
        List<MenuScrapeItemResponse> items = new ArrayList<>();

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
        return items;
    }

    // ==================== GrabFood Parser (JSON-LD based) ====================

    private List<MenuScrapeItemResponse> parseGrabFoodMenu(String html) {
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

        try {
            JsonNode rootNode = objectMapper.readTree(trimmedJson);

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
            throw new BadRequestException("Failed to parse GrabFood menu data: " + e.getMessage());
        }

        LOGGER.info("Extracted {} items from GrabFood", items.size());
        return items;
    }
}
