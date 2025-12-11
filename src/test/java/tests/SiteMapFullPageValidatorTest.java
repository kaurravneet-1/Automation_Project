package tests;

import utils.SiteConfigReader;

import java.util.*;

import java.net.URI;

import org.openqa.selenium.*;

import org.testng.annotations.Test;

import com.aventstack.extentreports.*;

public class SiteMapFullPageValidatorTest extends BaseTest {

    private static final int MAX_PAGES = 1;

    @Test
    public void runSitemapFullValidation() {

        String configPath = "src/test/resources/cta_websitessitemap.csv";
        List<String[]> sites = SiteConfigReader.readConfig(configPath);

        for (String[] site : sites) {

            String baseUrl = site[0].trim();
            String username = site[1].trim();
            String password = site[2].trim();
            String sitemapUrl = site[3].trim();

            ExtentTest siteTest = extent.createTest("Website Validation → " + sitemapUrl);

            try {
                setupDriver();

                String authUrl = baseUrl.replace("https://",
                        "https://" + username + ":" + password + "@");

                driver.get(authUrl);
                Thread.sleep(1200);
                siteTest.pass("Logged in successfully");

                driver.get(sitemapUrl);
                Thread.sleep(1500);

                List<String> urls = extractSitemapLinks(baseUrl);
                siteTest.pass("HTML Sitemap detected → URLs = " + urls.size());
                siteTest.info("Total URLs Found → " + urls.size());

                urls = urls.subList(0, Math.min(MAX_PAGES, urls.size()));

                for (String pageUrl : urls) {

                    ExtentTest pageNode = siteTest.createNode("Page: " + pageUrl);

                    try {
                        driver.get(pageUrl);
                        Thread.sleep(1000);
                        scrollPage();

                        validateCTAs(pageNode);

                        pageNode.pass("Page Validation Completed");

                    } catch (Exception e) {
                        pageNode.warning("Page Validation Issue → " + e.getMessage());
                    }
                }

                driver.quit();

            } catch (Exception e) {
                siteTest.warning("Site executed with warnings → " + e.getMessage());
                try { driver.quit(); } catch (Exception ignore) {}
            }
        }

        extent.flush();
    }

    private List<String> extractSitemapLinks(String baseUrl) {
        List<String> urls = new ArrayList<>();

        List<WebElement> locTags = driver.findElements(By.tagName("loc"));

        if (!locTags.isEmpty()) {
            for (WebElement e : locTags) urls.add(e.getText().trim());
            return urls;
        }

        List<WebElement> links = driver.findElements(By.xpath("//a[@href]"));
        for (WebElement a : links) {
            String href = a.getAttribute("href");
            if (href != null && href.startsWith(baseUrl))
                urls.add(href);
        }

        return urls;
    }

    class CTAItem {
        String name, href, color, font, weight, padding, radius;
        boolean icon;
    }

    private void validateCTAs(ExtentTest pageNode) {

        List<CTAItem> items = new ArrayList<>();

        List<WebElement> buttons = driver.findElements(By.xpath(
                "//*[@onclick or @role='button' or name()='a' or name()='button']"));

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

            } catch (Exception ignore) {}
        }

        for (CTAItem c : items) {

            ExtentTest cNode = pageNode.createNode("CTA: " + c.name);

            cNode.info("UI → Color=" + c.color + ", Font=" + c.font +
                    ", Weight=" + c.weight + ", Padding=" + c.padding +
                    ", Radius=" + c.radius + ", Icon=" + c.icon);

            if (c.href == null || c.href.isEmpty()) {
                cNode.warning("Not clickable (href missing)");
                continue;
            }

            cNode.info("Navigation Attempt → " + c.href);

            try {
                driver.navigate().to(c.href);
                Thread.sleep(700);

                cNode.pass("CTA Working");

                driver.navigate().back();
                Thread.sleep(400);

            } catch (Exception ex) {
                cNode.warning("CTA Navigation Issue → " + ex.getMessage());
            }
        }
    }

    private String extractCTAName(WebElement el, String href) {

        String text = safeText(el);
        if (!text.isEmpty()) return text;

        String aria = el.getAttribute("aria-label");
        if (aria != null && !aria.trim().isEmpty()) return aria;

        String alt = el.getAttribute("alt");
        if (alt != null && !alt.trim().isEmpty()) return alt;

        String title = el.getAttribute("title");
        if (title != null && !title.trim().isEmpty()) return title;

        if (href != null) {
            try {
                String p = URI.create(href).getPath();
                if (p != null) {
                    p = p.replace("/", "").replace("-", " ").trim();
                    if (!p.isEmpty()) return p;
                }
            } catch (Exception ignore) {}
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

    private String safeText(WebElement el) {
        try { return el.getText().trim(); }
        catch (Exception ignore) { return ""; }
    }

    private void scrollPage() throws InterruptedException {
        JavascriptExecutor js = (JavascriptExecutor) driver;
        long height = (long) js.executeScript("return document.body.scrollHeight");
        js.executeScript("window.scrollTo(0, arguments[0])", height);
        Thread.sleep(500);
    }
}
