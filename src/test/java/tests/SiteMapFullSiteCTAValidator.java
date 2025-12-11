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


public class SiteMapFullSiteCTAValidator extends BaseTest {

    private static final int MAX_PAGES = 1;

    @Test
    public void runSitemapFullValidation() {

        String configPath = "src/test/resources/headerfooter_websites.csv";
        List<String[]> sites = SiteConfigReader.readConfig(configPath);

        for (String[] site : sites) {

            // CSV contains only sitemap URL
            String sitemapUrl = site[0].trim();

            // automatically extract base domain
            String baseUrl = "https://" + URI.create(sitemapUrl).getHost();

            ExtentTest siteTest = extent.createTest("🌐 Website Validation → " + sitemapUrl);

            try {

                // driver setup already handled by BaseTest
                driver.get(sitemapUrl);
                Thread.sleep(1500);

                // Extract URLs
                List<String> urls = extractSitemapLinks(baseUrl);
                siteTest.info("Total URLs found in sitemap: " + urls.size());

                // Limit pages processed
                urls = urls.subList(0, Math.min(MAX_PAGES, urls.size()));

                // Validate each sitemap URL
                for (String pageUrl : urls) {

                    ExtentTest pageNode = siteTest.createNode("📄 Page: " + pageUrl);

                    try {
                        driver.get(pageUrl);
                        Thread.sleep(1000);

                        scrollPage();

                        validateCTAs(pageNode);
                   

                        pageNode.pass("✔ Page Validation Completed");

                    } catch (Exception e) {
                        pageNode.warning("⚠ Page Validation Issue → " + e.getMessage());
                        pageNode.info("Screenshot: " + captureScreenshot(pageUrl));
                    }
                }

            } catch (Exception e) {
                siteTest.warning("⚠ Site execution warning → " + e.getMessage());
            }
        }

        extent.flush();
    }

    // ---------------------------------------------------------------------------
    // UNIVERSAL SITEMAP PARSER (XML + HTML)
    // ---------------------------------------------------------------------------

    private List<String> extractSitemapLinks(String baseUrl) {

        List<String> urls = new ArrayList<>();
        String domain = URI.create(baseUrl).getHost();

        // Detect XML <loc> tags
        List<WebElement> locTags = driver.findElements(By.tagName("loc"));
        if (!locTags.isEmpty()) {
            for (WebElement e : locTags) {
                String url = e.getText().trim();
                if (url.contains(domain)) urls.add(url);
            }
            return urls;
        }

        // HTML sitemap case
        List<WebElement> anchors = driver.findElements(By.xpath("//a[@href]"));
        for (WebElement a : anchors) {
            String href = a.getAttribute("href");
            if (href != null && href.contains(domain)) {
                urls.add(href);
            }
        }

        return urls;
    }

    // ---------------------------------------------------------------------------
    // CTA VALIDATION (ALL CTAs)
    // ---------------------------------------------------------------------------

    class CTAItem {
        String name, href, color, font, weight, padding, radius;
        boolean icon;
    }

    private void validateCTAs(ExtentTest pageNode) {

        List<CTAItem> items = new ArrayList<>();

        List<WebElement> buttons = driver.findElements(
                By.xpath("//*[@onclick or @role='button' or name()='a' or name()='button']")
        );

        pageNode.info("Total CTA elements found → " + buttons.size());

        for (WebElement el : buttons) {
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
                cNode.warning("⚠ Not clickable");
                continue;
            }

            try {
                driver.navigate().to(c.href);
                Thread.sleep(700);
                cNode.pass("✔ CTA Working");

                driver.navigate().back();
                Thread.sleep(350);

            } catch (Exception ex) {
                cNode.warning("⚠ CTA Navigation Issue → " + ex.getMessage());
            }
        }
    }

    private String extractCTAName(WebElement el, String href) {

        try { if (!el.getText().trim().isEmpty()) return el.getText().trim(); } catch (Exception ignored) {}

        String aria = el.getAttribute("aria-label");
        if (aria != null && !aria.trim().isEmpty()) return aria;

        String alt = el.getAttribute("alt");
        if (alt != null && !alt.trim().isEmpty()) return alt;

        String title = el.getAttribute("title");
        if (title != null && !title.trim().isEmpty()) return title;

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
        } catch (Exception e) {
            return false;
        }
    }

    // ---------------------------------------------------------------------------
    // UTILITIES
    // ---------------------------------------------------------------------------

    private String captureScreenshot(String url) {
        try {
            File src = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
            String dir = "reports/screenshots";
            new File(dir).mkdirs();
            String path = dir + "/" + System.currentTimeMillis() + ".png";
            FileHandler.copy(src, new File(path));
            return path;
        } catch (Exception e) {
            return "";
        }
    }

    private void scrollPage() throws InterruptedException {
        JavascriptExecutor js = (JavascriptExecutor) driver;
        long height = (long) js.executeScript("return document.body.scrollHeight");
        js.executeScript("window.scrollTo(0, arguments[0])", height);
        Thread.sleep(500);
    }

    
}
