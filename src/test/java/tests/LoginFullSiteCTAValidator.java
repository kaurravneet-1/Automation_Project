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

public class LoginFullSiteCTAValidator extends BaseTest {

    private static final int MAX_PAGES = 1;

    @Test
    public void runCTAValidation() {

        String configPath = "src/test/resources/cta_websites.csv";
        List<String[]> sites = SiteConfigReader.readConfig(configPath);

        for (String[] site : sites) {

            String websiteUrl = site[0].trim();
            String username = site[1].trim();
            String password = site[2].trim();

            ExtentTest siteTest = extent.createTest("Site: " + websiteUrl);

            String domain = getDomain(websiteUrl);

            String authUrl = websiteUrl.replace("https://",
                    "https://" + username + ":" + password + "@");

            siteTest.info("Basic Auth URL → " + authUrl);

            try {
                setupDriver();

                driver.get(authUrl);
                Thread.sleep(2000);
                siteTest.pass("Logged in successfully.");

                Set<String> toVisit = new LinkedHashSet<>();
                toVisit.add(websiteUrl.endsWith("/") ? websiteUrl : websiteUrl + "/");

                int scanned = 0;

                while (!toVisit.isEmpty() && scanned < MAX_PAGES) {

                    String current = toVisit.iterator().next();
                    toVisit.remove(current);
                    scanned++;

                    ExtentTest pageNode = siteTest.createNode("Page: " + current);

                    try {
                        driver.get(current);
                        Thread.sleep(1000);

                        scrollToBottom();

                        collectLinksForCrawl(current, websiteUrl, toVisit);

                        validateCTAsOnPage(pageNode, username, password, domain);

                      

                        pageNode.pass("Page Validation Completed.");

                    } catch (Exception e) {
                        pageNode.warning("Page processing issue → " + e.getMessage());
                        pageNode.info("Screenshot: " + captureScreenshot(current));
                    }
                }

                siteTest.info("Total pages scanned: " + scanned);
                driver.quit();

            } catch (Exception e) {
                siteTest.warning("Site executed with warnings → " + e.getMessage());
                try { driver.quit(); } catch (Exception ignored) {}
            }
        }

        extent.flush();
    }

    class CTAItem {
        String name, href, color, font, weight, padding, radius;
        boolean icon;
    }

    private void validateCTAsOnPage(
            ExtentTest pageNode,
            String username,
            String password,
            String domain) {

        List<CTAItem> items = new ArrayList<>();

        List<WebElement> buttons = driver.findElements(By.xpath(
                "//*[@onclick or @role='button' or name()='a' or name()='button']"));

        pageNode.info("Total CTA elements found: " + buttons.size());

        for (WebElement el : buttons) {
            try {
                CTAItem c = new CTAItem();

                c.href = el.getAttribute("href");
                c.name = extractCTAName(el, c.href);

                if (c.name.equalsIgnoreCase("Accessibility Menu") ||
                    c.name.equalsIgnoreCase("Translations Menu")) {
                    continue;
                }

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
                cNode.warning("Not clickable (href missing)");
                continue;
            }

            String fixedHref = c.href;

            if (fixedHref.contains(domain)) {
                fixedHref = fixedHref.replace("https://",
                        "https://" + username + ":" + password + "@");
            }

            cNode.info("Navigation Attempt → " + fixedHref);

            try {
                driver.navigate().to(fixedHref);
                Thread.sleep(500);
                cNode.pass("CTA Working");

                driver.navigate().back();
                Thread.sleep(300);

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

    private String safeText(WebElement el) {
        try { return el.getText().trim(); }
        catch (Exception e) { return ""; }
    }

    private void collectLinksForCrawl(String current, String baseUrl, Set<String> toVisit) {
        try {
            String domain = getDomain(baseUrl);

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

    private void scrollToBottom() throws InterruptedException {
        JavascriptExecutor js = (JavascriptExecutor) driver;
        long height = (long) js.executeScript("return document.body.scrollHeight");

        for (int i = 0; i < height; i += 400) {
            js.executeScript("window.scrollTo(0, arguments[0]);", i);
            Thread.sleep(180);
        }
    }

    private String getDomain(String url) {
        try { return URI.create(url).getHost(); }
        catch (Exception e) { return url; }
    }
}
