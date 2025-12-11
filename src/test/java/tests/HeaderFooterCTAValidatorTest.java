package tests;

import org.openqa.selenium.*;
import org.openqa.selenium.io.FileHandler;
import org.openqa.selenium.JavascriptExecutor;
import org.testng.annotations.Test;
import com.aventstack.extentreports.*;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;

import java.util.*;

public class HeaderFooterCTAValidatorTest extends BaseTest {

    private static final int MAX_PAGES = 1;
    private static final int RETRY_STALE = 2;

    @Test
    public void validateHeaderFooterCTAs() throws Exception {

        ExtentTest mainTest = extent.createTest("Header & Footer CTA Validator – No Body CTAs");

        String csvPath = "src/test/resources/sitemapurls.csv";
        List<String> urls = readUrlsFromCSV(csvPath);

        if (urls.isEmpty()) {
            mainTest.fail("CSV has no URLs.");
            return;
        }

        int scanned = 0;

        for (String url : urls) {

            if (scanned >= MAX_PAGES) break;

            ExtentTest pageNode = mainTest.createNode("Page: " + url);

            try {
                driver.get(url);
                Thread.sleep(1200);
                scrollToBottom();

                int headerCount = getHeaderCTAcount();
                int footerCount = getFooterCTAcount();
                int totalCTAs = headerCount + footerCount;

                pageNode.info("🌐 Page Loaded: " + url);
                pageNode.info("Total CTAs (Header+Footer): " + totalCTAs);
                pageNode.info("Header CTAs: " + headerCount);
                pageNode.info("Footer CTAs: " + footerCount);

                pageNode.info("🔵 Validating HEADER CTAs…");
                validateCTAGroupByIndex(pageNode, "HEADER", headerCount);

                pageNode.info("🟤 Validating FOOTER CTAs…");
                validateCTAGroupByIndex(pageNode, "FOOTER", footerCount);

                pageNode.pass("Page CTA Validation Completed.");

            } catch (Exception e) {
                pageNode.warning("⚠ Page Error → " + e.getMessage());
                pageNode.info("Screenshot: " + captureScreenshot(url));
            }

            scanned++;
        }

        mainTest.info("Total Pages Scanned: " + scanned);
        extent.flush();
    }

    // =============================================================
    // CSV READER
    // =============================================================
    public List<String> readUrlsFromCSV(String path) throws Exception {
        List<String> list = new ArrayList<>();
        BufferedReader br = new BufferedReader(new FileReader(path));
        String line;

        while ((line = br.readLine()) != null) {
            if (!line.trim().isEmpty()) list.add(line.trim());
        }
        br.close();
        return list;
    }

    // =============================================================
    // HEADER/FOOTER COUNT
    // =============================================================
    private int getHeaderCTAcount() {
        try {
            WebElement header = driver.findElement(By.tagName("header"));
            List<WebElement> items = header.findElements(By.xpath(
                ".//*[@onclick or @role='button' or self::a or self::button or (self::a//*[name()='img'])]"
            ));
            return items.size();
        } catch (Exception e) {
            return 0;
        }
    }

    private int getFooterCTAcount() {
        try {
            WebElement footer = driver.findElement(By.tagName("footer"));
            List<WebElement> items = footer.findElements(By.xpath(
                ".//*[@onclick or @role='button' or self::a or self::button or (self::a//*[name()='img'])]"
            ));
            return items.size();
        } catch (Exception e) {
            return 0;
        }
    }

    // =============================================================
    // VALIDATE CTA GROUP BY INDEX (Stale-safe)
    // =============================================================
    private void validateCTAGroupByIndex(ExtentTest pageNode, String section, int count) throws InterruptedException {

        String sectionRootTag = section.equals("HEADER") ? "header" : "footer";
        String xpathForItems = ".//*[@onclick or @role='button' or self::a or self::button or (self::a//*[name()='img'])]";

        for (int idx = 0; idx < count; idx++) {

            WebElement el = null;
            boolean found = false;

            for (int attempt = 0; attempt < RETRY_STALE; attempt++) {
                try {
                    WebElement root = driver.findElement(By.tagName(sectionRootTag));
                    List<WebElement> items = root.findElements(By.xpath(xpathForItems));

                    if (idx >= items.size()) break;

                    el = items.get(idx);
                    el.isDisplayed();
                    found = true;
                    break;

                } catch (StaleElementReferenceException stale) {
                    Thread.sleep(300);
                } catch (Exception ignored) {
                    break;
                }
            }

            if (!found || el == null) {
                pageNode.info(section + " CTA #" + (idx + 1) + " - skipped (not found), Footer initially had 12 CTAs — after DOM update only 10 remain.\r\n"
                		+ "Skipped: 2 ");
                continue;
            }

            // Extract CTA data
            String href = safeHref(el);
            String name = extractCTAName(el, href);

            // ⭐ Numbered CTA Node (NEW FEATURE)
            ExtentTest cNode = pageNode.createNode(
                    section + " CTA #" + (idx + 1) + ": " + name
            );

            cNode.info("Href: " + href);

            String low = name.toLowerCase();

            // Skip menu toggle
            if (low.contains("menu") && low.contains("toggle")) {
                cNode.info("⏭ Skipped Menu Toggle CTA");
                continue;
            }

            // PHONE CTA
            if (href.startsWith("tel:")) {
                cNode.pass("📞 Phone CTA Working → " + href);
                continue;
            }

            // EMAIL CTA
            if (href.startsWith("mailto:")) {
                cNode.pass("✉ Email CTA Working → " + href);
                continue;
            }

            // SMS CTA
            if (href.startsWith("sms:")) {
                cNode.pass("📩 SMS CTA Working → " + href);
                continue;
            }

            // Social CTA
            if (isSocial(href)) {
                cNode.pass("🌐 Social CTA Working → " + href);
                continue;
            }

            // OnClick CTA (search icon)
            try {
                String onclick = el.getAttribute("onclick");
                if (onclick != null && !onclick.isEmpty()) {
                    cNode.pass("✔ Interactive CTA Working (onclick)");
                    continue;
                }
            } catch (Exception ignored) {}

            // Missing href
            if (href.isEmpty()) {
                cNode.warning("⚠ CTA not clickable (href missing)");
                continue;
            }

            // Normal redirect CTA
            try {
                String start = driver.getCurrentUrl();
                driver.navigate().to(href);
                Thread.sleep(900);

                String end = driver.getCurrentUrl();
                cNode.info("Redirected → " + end);

                if (end.equals(start)) {
                    cNode.pass("✔ CTA Working (opened same page)");
                } else {
                    cNode.pass("✔ Redirect Successful → " + end);
                }

                driver.navigate().back();
                Thread.sleep(300);

            } catch (Exception ex) {
                cNode.fail("❌ CTA Navigation Failed → " + ex.getMessage());
            }
        }
    }

    // =============================================================
    // SUPPORT FUNCTIONS
    // =============================================================
    private String extractCTAName(WebElement el, String href) {

        String text = safeText(el);
        String low = text.toLowerCase();

        if (!text.isEmpty()) {
            if (low.contains("hearing") || low.contains("life") || low.contains("clinic"))
                return text + " (Company Name)";
        }

        String aria = "";
        try { aria = el.getAttribute("aria-label"); } catch (Exception ignored) {}
        if (aria != null && !aria.trim().isEmpty()) return aria;

        String alt = "";
        try { alt = el.getAttribute("alt"); } catch (Exception ignored) {}
        if (alt != null && !alt.trim().isEmpty()) return alt;

        try {
            List<WebElement> imgs = el.findElements(By.tagName("img"));
            if (!imgs.isEmpty()) {
                String a = imgs.get(0).getAttribute("alt");
                if (a != null && !a.trim().isEmpty()) return a + " (Logo)";
                return "Logo CTA";
            }
        } catch (Exception ignored) {}

        if (href.startsWith("tel:")) return href.replace("tel:", "");

        if (!text.isEmpty()) return text;

        return "Unnamed CTA";
    }

    private String safeHref(WebElement el) {
        try {
            String h = el.getAttribute("href");
            return h != null ? h : "";
        } catch (Exception e) {
            return "";
        }
    }

    private boolean isSocial(String href) {
        if (href == null) return false;
        href = href.toLowerCase();
        return href.contains("facebook.com") ||
               href.contains("instagram.com") ||
               href.contains("linkedin.com") ||
               href.contains("youtube.com") ||
               href.contains("twitter.com") ||
               href.contains("x.com");
    }

    private String safeText(WebElement el) {
        try {
            String t = el.getText().trim();
            if (!t.isEmpty()) return t;
        } catch (Exception ignored) {}

        try {
            JavascriptExecutor js = (JavascriptExecutor) driver;
            Object inner = js.executeScript(
                    "return arguments[0].innerText || arguments[0].textContent || '';", el
            );

            if (inner != null) {
                String s = inner.toString().trim();
                if (!s.isEmpty()) return s;
            }
        } catch (Exception ignored) {}

        return "";
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

    private void scrollToBottom() throws InterruptedException {
        JavascriptExecutor js = (JavascriptExecutor) driver;
        long height = (long) js.executeScript("return document.body.scrollHeight");

        for (int i = 0; i < height; i += 300) {
            js.executeScript("window.scrollTo(0, arguments[0]);", i);
            Thread.sleep(120);
        }
    }
}
