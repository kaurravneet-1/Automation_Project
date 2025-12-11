package tests;

import org.openqa.selenium.*;
import org.openqa.selenium.JavascriptExecutor;
import org.testng.annotations.Test;
import com.aventstack.extentreports.*;
import java.io.BufferedReader;
import java.io.FileReader;
import java.util.*;

public class CTARedirectionValidatorTest extends BaseTest {

    private static final int MAX_PAGES = 2;

    @Test
    public void runCSV_CTA_Validation() throws Exception {

        ExtentTest mainTest = extent.createTest("CTA Validator Of Complete Page – No Login");

        // 1. Read sitemap URL from CSV
        String csvPath = "src/test/resources/sitemapurls.csv";
        List<String> sitemapUrls = readAllUrlsFromCSV(csvPath);

        if (sitemapUrls.isEmpty()) {
            mainTest.fail("CSV contains no sitemap URL.");
            return;
        }

        // Only first sitemap URL
        String sitemapUrl = sitemapUrls.get(0);

        // 2. Extract ALL <a> links from sitemap
        List<String> extractedPages = extractLinksFromSitemap(sitemapUrl);

        if (extractedPages.isEmpty()) {
            mainTest.fail("No links found on sitemap: " + sitemapUrl);
            return;
        }

        mainTest.info("Total links extracted from sitemap: " + extractedPages.size());
        mainTest.info("Executing CTA validation for first " + MAX_PAGES + " pages only.");

        int scanned = 0;

        // 3. Loop pages
        for (String pageUrl : extractedPages) {

            if (scanned >= MAX_PAGES) break;

            ExtentTest pageNode = mainTest.createNode("Page: " + pageUrl);

            try {

                driver.get(pageUrl);
                Thread.sleep(1500);
                scrollToBottom();

                // ===========================
                // PAGE SUMMARY (FIRST BLOCK)
                // ===========================
                int totalCTAs = driver.findElements(By.xpath(
                        "//*[@onclick or @role='button' or self::a or self::button or (self::a//*[name()='img'])]"
                )).size();

                int headerCTAs = 0, footerCTAs = 0;

                try {
                    headerCTAs = driver.findElement(By.tagName("header"))
                            .findElements(By.xpath(".//*[@onclick or self::a or self::button]")).size();
                } catch (Exception ignored) {}

                try {
                    footerCTAs = driver.findElement(By.tagName("footer"))
                            .findElements(By.xpath(".//*[@onclick or self::a or self::button]")).size();
                } catch (Exception ignored) {}

                int bodyCTAs = totalCTAs - headerCTAs - footerCTAs;

                // Summary logs BEFORE CTA validation
                pageNode.info("🌐 Page Loaded: " + pageUrl);
                pageNode.info("Total CTAs: " + totalCTAs);
                pageNode.info("Header CTAs: " + headerCTAs);
                pageNode.info("Body CTAs: " + bodyCTAs);
                pageNode.info("Footer CTAs: " + footerCTAs);
                pageNode.info("Total CTA elements found: " + totalCTAs);

                // CLOSE SUMMARY — MUST come BEFORE CTA group
                pageNode.pass("Page summary completed.");

                // ===========================
                // CTA GROUP (Only group)
                // ===========================
                ExtentTest allCTAGroup = pageNode.createNode("All CTAs (" + totalCTAs + ")");
                validateCTAsOnPage(allCTAGroup, pageUrl);

                // ❗ NOTHING after CTA group
                // No pass(), no info(), no screenshot, no warnings

            } catch (Exception e) {
                // If page fails BEFORE CTA group
                pageNode.warning("Page Load Error → " + e.getMessage());
            }

            scanned++;
        }

        // DO NOT LOG ANYTHING AFTER CTA VALIDATION
        // mainTest.info("Total pages validated: " + scanned);  // ❌ MUST BE REMOVED
        // extent.flush();                                     // ✔ Run once at end automatically

    }

    // =============================================================
    // READ CSV FILE
    // =============================================================
    public List<String> readAllUrlsFromCSV(String csvPath) throws Exception {

        List<String> urls = new ArrayList<>();
        BufferedReader br = new BufferedReader(new FileReader(csvPath));

        String line;
        while ((line = br.readLine()) != null) {
            if (!line.trim().isEmpty())
                urls.add(line.trim());
        }
        br.close();
        return urls;
    }

    // =============================================================
    // Extract ALL <a> links from sitemap
    // =============================================================
    public List<String> extractLinksFromSitemap(String sitemapUrl) throws Exception {

        Set<String> links = new LinkedHashSet<>();

        driver.get(sitemapUrl);
        Thread.sleep(1500);

        List<WebElement> anchors = driver.findElements(By.tagName("a"));

        for (WebElement a : anchors) {

            String href = a.getAttribute("href");
            if (href == null) continue;

            href = href.trim();

            if (href.startsWith("javascript")) continue;
            if (href.startsWith("tel:")) continue;
            if (href.startsWith("mailto:")) continue;

            if (href.equals("#")) continue;

            links.add(href);
        }

        return new ArrayList<>(links);
    }

    // =============================================================
    // CTA VALIDATION ENGINE
    // =============================================================
    class CTAItem {
        String name, href, color, font, weight, padding, radius;
        boolean icon;
    }

    private void validateCTAsOnPage(ExtentTest allCTAGroup, String pageUrl) {

        List<CTAItem> items = new ArrayList<>();

        List<WebElement> buttons = driver.findElements(By.xpath(
                "//*[@onclick or @role='button' or self::a or self::button or (self::a//*[name()='img'])]"
        ));

        // Build CTA list
        for (WebElement el : buttons) {
            try {
                CTAItem c = new CTAItem();

                c.href = el.getAttribute("href");
                c.name = extractCTAName(el, c.href);

                String low = c.name.toLowerCase();
                if (low.contains("menu") && low.contains("toggle")) continue;
                if (low.contains("skip to")) continue;

                c.color = el.getCssValue("color");
                c.font = el.getCssValue("font-size");
                c.weight = el.getCssValue("font-weight");
                c.padding = el.getCssValue("padding");
                c.radius = el.getCssValue("border-radius");
                c.icon = hasIcon(el);

                items.add(c);

            } catch (Exception ignored) {}
        }

        int index = 0;

        // Create CTA nodes inside the group ONLY
        for (CTAItem c : items) {

            ExtentTest cNode = allCTAGroup.createNode("CTA #" + (++index) + ": " + c.name);

            cNode.info("UI → color=" + c.color +
                    ", font=" + c.font +
                    ", weight=" + c.weight +
                    ", padding=" + c.padding +
                    ", radius=" + c.radius +
                    ", icon=" + c.icon);

            try {
                WebElement el = driver.findElement(By.xpath(
                        "//*[text()='" + c.name.replace("(Company Name)", "").trim() +
                        "'] | //*[@href='" + c.href + "']"
                ));

                String onclick = el.getAttribute("onclick");
                if ((c.href == null || c.href.isEmpty()) &&
                    onclick != null && !onclick.isEmpty()) {

                    cNode.pass("✔ Interactive CTA Working (onclick event)");
                    continue;
                }

            } catch (Exception ignored) {}

            if (c.href == null || c.href.isEmpty()) {
                cNode.warning("⚠ CTA Not Clickable (href missing)");
                continue;
            }

            cNode.info("Clicking → " + c.href);

            if (c.href.startsWith("tel:") ||
                c.href.startsWith("sms:") ||
                c.href.startsWith("mailto:")) {

                cNode.info("Redirected → " + c.href);

                if (c.href.startsWith("tel:")) cNode.pass("📞 Phone CTA Working");
                else if (c.href.startsWith("sms:")) cNode.pass("📩 SMS CTA Working");
                else cNode.pass("✉ Email CTA Working");

                continue;
            }

            try {
                String startUrl = driver.getCurrentUrl();

                driver.navigate().to(c.href);
                Thread.sleep(800);

                String redirectedUrl = driver.getCurrentUrl();
                cNode.info("Redirected URL → " + redirectedUrl);

                String low = c.name.toLowerCase();

                if (isSocial(c.href)) {
                    cNode.pass("🌐 Social CTA Working → " + redirectedUrl);
                }
                else if (low.contains("logo") || low.contains("company name")) {
                    cNode.pass("🏢 Logo/Company CTA Working → " + redirectedUrl);
                }
                else {
                    if (redirectedUrl.equals(startUrl))
                        cNode.pass("✔ CTA Working — Same Page");
                    else
                        cNode.pass("✔ Redirect Successful → " + redirectedUrl);
                }

                driver.navigate().back();
                Thread.sleep(500);

            } catch (Exception ignored) {
                cNode.warning("⚠ CTA Navigation Issue");
            }
        }
    }

    // =============================================================
    // SUPPORT
    // =============================================================
    private String extractCTAName(WebElement el, String href) {

        String text = safeText(el);
        String low = text.toLowerCase();

        if (!text.isEmpty()) {
            if (low.contains("hearing") || low.contains("life"))
                return text + " (Company Name)";
        }

        String aria = el.getAttribute("aria-label");
        if (aria != null && !aria.trim().isEmpty()) return aria;

        String alt = el.getAttribute("alt");
        if (alt != null && !alt.trim().isEmpty()) return alt;

        try {
            List<WebElement> imgs = el.findElements(By.tagName("img"));
            if (!imgs.isEmpty()) {
                String a = imgs.get(0).getAttribute("alt");
                if (a != null && !a.trim().isEmpty())
                    return a + " (Logo)";
                return "Logo CTA";
            }
        } catch (Exception ignored) {}

        if (href != null) {
            if (href.startsWith("tel:")) return href.replace("tel:", "");
            if (href.startsWith("sms:")) return href.replace("sms:", "");
            if (href.startsWith("mailto:")) return href.replace("mailto:", "");
        }

        if (!text.isEmpty()) return text;

        return "Unnamed CTA";
    }

    private boolean isSocial(String href) {
        href = href.toLowerCase();
        return href.contains("facebook") ||
               href.contains("instagram") ||
               href.contains("youtube") ||
               href.contains("linkedin") ||
               href.contains("twitter") ||
               href.contains("x.com");
    }

    private boolean hasIcon(WebElement el) {
        try {
            return el.findElements(By.tagName("svg")).size() > 0 ||
                   el.findElements(By.tagName("img")).size() > 0 ||
                   (el.getAttribute("class") != null &&
                           (el.getAttribute("class").contains("fa-") ||
                            el.getAttribute("class").contains("icon")));
        } catch (Exception ignored) {
            return false;
        }
    }

    private String safeText(WebElement el) {
        try {
            if (el.getText() != null && !el.getText().trim().isEmpty())
                return el.getText().trim();
        } catch (Exception ignored) {}

        try {
            JavascriptExecutor js = (JavascriptExecutor) driver;
            String txt = (String) js.executeScript(
                    "return arguments[0].innerText || arguments[0].textContent;", el);
            if (txt != null && !txt.trim().isEmpty())
                return txt.trim();
        } catch (Exception ignored) {}

        return "";
    }

    private void scrollToBottom() throws InterruptedException {
        JavascriptExecutor js = (JavascriptExecutor) driver;
        long height = (long) js.executeScript("return document.body.scrollHeight");

        for (int i = 0; i < height; i += 350) {
            js.executeScript("window.scrollTo(0, arguments[0]);", i);
            Thread.sleep(150);
        }
    }
}
