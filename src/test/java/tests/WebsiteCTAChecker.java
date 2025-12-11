package tests;

import utils.SiteConfigReader;

import java.io.File;
import java.net.URI;
import java.util.*;

import org.openqa.selenium.*;
import org.openqa.selenium.io.FileHandler;
import org.openqa.selenium.JavascriptExecutor;
import org.testng.annotations.Test;

import com.aventstack.extentreports.*;


public class WebsiteCTAChecker extends BaseTest {

    private static final int MAX_PAGES = 1;   // ⭐ LIMIT PAGES INSIDE WEBSITE

    @Test
    public void runFullSiteValidation() {

        String configPath = "src/test/resources/websites.csv";   // ONLY website URLs
        List<String[]> sites = SiteConfigReader.readConfig(configPath);

   
        for (String[] site : sites) {

            String websiteUrl = site[0].trim();

            ExtentTest siteTest = extent.createTest("🌐 Website: " + websiteUrl);

            String domain = getDomain(websiteUrl);

            try {
                driver.get(websiteUrl);
                Thread.sleep(1200);

                siteTest.pass("✔ Homepage Loaded");

                // ⭐ PAGE CRAWLING SETUP
                Set<String> toVisit = new LinkedHashSet<>();
                Set<String> visited = new HashSet<>();

                toVisit.add(websiteUrl.endsWith("/") ? websiteUrl : websiteUrl + "/");

                int scanned = 0;

                while (!toVisit.isEmpty() && scanned < MAX_PAGES) {

                    String current = toVisit.iterator().next();
                    toVisit.remove(current);

                    if (visited.contains(current)) continue;
                    visited.add(current);

                    scanned++;

                    ExtentTest pageNode = siteTest.createNode("📄 Page: " + current);

                    try {
                        driver.get(current);
                        Thread.sleep(900);

                        scrollToBottom();

                        collectInternalLinks(current, websiteUrl, domain, toVisit);

                        validateCTAs(pageNode);

                  

                        pageNode.pass("✔ Page Validation Completed");

                    } catch (Exception e) {
                        pageNode.warning("⚠ Error processing page → " + e.getMessage());
                        pageNode.info("Screenshot: " + captureScreenshot(current));
                    }
                }

                siteTest.info("Total pages scanned: " + scanned);

            } catch (Exception e) {
                siteTest.warning("⚠ Website executed with warnings → " + e.getMessage());
            }
        }

        extent.flush();
    }

    // --------------------------------------------------------------------
    // Collect internal page links for crawling
    // --------------------------------------------------------------------
    private void collectInternalLinks(String current, String baseUrl, String domain, Set<String> toVisit) {
        try {
            for (WebElement a : driver.findElements(By.tagName("a"))) {
                String href = a.getAttribute("href");
                if (href == null) continue;

                if (href.startsWith("/")) {
                    URI base = URI.create(baseUrl);
                    href = base.getScheme() + "://" + base.getHost() + href;
                }

                if (href.contains(domain)) {
                    toVisit.add(href);
                }
            }
        } catch (Exception ignored) {}
    }

    // --------------------------------------------------------------------
    // CTA VALIDATION
    // --------------------------------------------------------------------
    class CTAItem {
        String name, href, color, font, weight, padding, radius;
        boolean icon;
    }

    private void validateCTAs(ExtentTest pageNode) {

        List<CTAItem> items = new ArrayList<>();

        List<WebElement> elements = driver.findElements(By.xpath(
                "//*[@onclick or @role='button' or self::a or self::button]"
        ));

        pageNode.info("Total CTA elements found: " + elements.size());

        for (WebElement el : elements) {
            try {
                CTAItem c = new CTAItem();

                c.href = el.getAttribute("href");
                c.name = extractCTAName(el, c.href);

                if (c.name.equalsIgnoreCase("Accessibility Menu") ||
                    c.name.equalsIgnoreCase("Translations Menu"))
                    continue;

                c.color = el.getCssValue("color");
                c.font = el.getCssValue("font-size");
                c.weight = el.getCssValue("font-weight");
                c.padding = el.getCssValue("padding");
                c.radius = el.getCssValue("border-radius");
                c.icon = hasIcon(el);

                items.add(c);

            } catch (Exception ignored) {}
        }

        for (CTAItem c : items) {

            ExtentTest cNode = pageNode.createNode("CTA: " + c.name);

            cNode.info("UI → Color=" + c.color +
                    ", Font=" + c.font +
                    ", Weight=" + c.weight +
                    ", Padding=" + c.padding +
                    ", Radius=" + c.radius +
                    ", Icon=" + c.icon);

            if (c.href == null || c.href.isEmpty()) {
                cNode.warning("⚠ Not clickable (href missing)");
                continue;
            }

            try {
                driver.navigate().to(c.href);
                Thread.sleep(600);

                cNode.pass("✔ CTA Working");

                driver.navigate().back();
                Thread.sleep(300);

            } catch (Exception ex) {
                cNode.warning("⚠ CTA Navigation Issue → " + ex.getMessage());
            }
        }
    }

   
    // --------------------------------------------------------------------
    // UTILITY METHODS
    // --------------------------------------------------------------------
    private String extractCTAName(WebElement el, String href) {

        try { if (!el.getText().trim().isEmpty()) return el.getText().trim(); }
        catch (Exception ignored) {}

        String aria = el.getAttribute("aria-label");
        if (aria != null && !aria.isEmpty()) return aria;

        String alt = el.getAttribute("alt");
        if (alt != null && !alt.isEmpty()) return alt;

        String title = el.getAttribute("title");
        if (title != null && !title.isEmpty()) return title;

        if (href != null) {
            try {
                String p = URI.create(href).getPath();
                if (p != null) return p.replace("/", "").replace("-", " ").trim();
            } catch (Exception ignored) {}
        }

        return "Unnamed CTA";
    }

    private boolean hasIcon(WebElement el) {
        try {
            return el.findElements(By.tagName("svg")).size() > 0 ||
                   el.findElements(By.tagName("img")).size() > 0 ||
                   el.getAttribute("class").contains("fa-") ||
                   el.getAttribute("class").contains("icon");
        } catch (Exception e) { return false; }
    }

    private void scrollToBottom() throws InterruptedException {
        JavascriptExecutor js = (JavascriptExecutor) driver;
        long height = (long) js.executeScript("return document.body.scrollHeight");
        for (int i = 0; i < height; i += 400) {
            js.executeScript("window.scrollTo(0, arguments[0]);", i);
            Thread.sleep(150);
        }
    }

    private String captureScreenshot(String url) {
        try {
            File src = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
            String dir = "reports/screenshots";
            new File(dir).mkdirs();
            String path = dir + "/" + System.currentTimeMillis() + ".png";
            FileHandler.copy(src, new File(path));
            return path;
        } catch (Exception e) { return ""; }
    }

    private String getDomain(String url) {
        try { return URI.create(url).getHost(); }
        catch (Exception e) { return url; }
    }

}
