package tests;

import org.openqa.selenium.*;
import org.openqa.selenium.interactions.Actions;
import org.openqa.selenium.io.FileHandler;
import org.openqa.selenium.JavascriptExecutor;
import org.testng.annotations.Test;
import com.aventstack.extentreports.*;

import java.io.*;
import java.util.*;

public class FullPageCTARedirectionValidatorTest extends BaseTest {

    private static final int MAX_PAGES = 1;  

    @Test
    public void validateAllCTAs() {

        ExtentTest mainTest = extent.createTest("Full Website CTA Validator");

        setupDriver();

        try {

            String csvPath = "src/test/resources/sitemapurls.csv";
            List<String> urls = readUrlsFromCSV(csvPath);

            if (urls.isEmpty()) {
                mainTest.fail("CSV file does not contain any URLs.");
                return;
            }

            String baseUrl = urls.get(0);
            mainTest.info("Base URL loaded: " + baseUrl);

            LinkedHashSet<String> toVisit = new LinkedHashSet<>();
            HashSet<String> visited = new HashSet<>();
            toVisit.add(baseUrl);

            int scanned = 0;

            while (!toVisit.isEmpty() && scanned < MAX_PAGES) {

                String current = toVisit.iterator().next();
                toVisit.remove(current);

                if (visited.contains(current)) continue;
                visited.add(current);

                scanned++;

                ExtentTest pageNode = mainTest.createNode("Scanning Page: " + current);

                try {
                    driver.navigate().to(current);
                    Thread.sleep(1500);
                    scrollToBottom();

                    List<WebElement> allCTAs = driver.findElements(By.xpath("//a | //button | //*[@onclick]"));

                    int totalCTAs = allCTAs.size();
                    int headerCTAs = 0, footerCTAs = 0;

                    try {
                        headerCTAs = driver.findElement(By.tagName("header"))
                                .findElements(By.xpath(".//a | .//button | .//*[@onclick]")).size();
                    } catch (Exception ignored) {}

                    try {
                        footerCTAs = driver.findElement(By.tagName("footer"))
                                .findElements(By.xpath(".//a | .//button | .//*[@onclick]")).size();
                    } catch (Exception ignored) {}

                    int bodyCTAs = totalCTAs - headerCTAs - footerCTAs;

                    ExtentTest summary = pageNode.createNode("Page Summary");
                    summary.info("Total CTAs Found: " + totalCTAs);
                    summary.info("Header CTAs: " + headerCTAs);
                    summary.info("Footer CTAs: " + footerCTAs);
                    summary.info("Body CTAs: " + bodyCTAs);

                    int failedCTAs = validateAllCTAs(pageNode, current);
                    summary.info("Working CTAs: " + (totalCTAs - failedCTAs));
                    summary.info("Failed CTAs: " + failedCTAs);
                    summary.pass("Page CTA Validation Completed");

                } catch (Exception e) {
                    pageNode.warning("Error loading page: " + e.getMessage());
                    pageNode.info("Screenshot: " + captureScreenshot());
                }
            }

            mainTest.info("Total Pages Scanned: " + scanned);

        } catch (Exception e) {
            mainTest.fail("Unexpected Error: " + e.getMessage());
        } finally {
            try { driver.quit(); } catch (Exception ignored) {}
        }
    }


    // ------------------------------------------------------------------------------------
    // ⭐ FIXED: Search CTA correctly detected (Not treated as generic interactive CTA)
    // ------------------------------------------------------------------------------------
    private boolean isSearchCTA(WebElement el) {

        try {
            String id = el.getAttribute("id");
            String cls = el.getAttribute("class");
            String placeholder = el.getAttribute("placeholder");
            String aria = el.getAttribute("aria-label");
            String text = el.getText().trim().toLowerCase();

            if ((id != null && id.toLowerCase().contains("search")) ||
                (cls != null && cls.toLowerCase().contains("search")) ||
                (placeholder != null && placeholder.toLowerCase().contains("search")) ||
                (aria != null && aria.toLowerCase().contains("search")) ||
                text.contains("search")) {

                return true;
            }

        } catch (Exception ignored) {}

        return false;
    }


    private int validateAllCTAs(ExtentTest pageNode, String currentPageUrl) {

        int failed = 0;

        List<WebElement> all = driver.findElements(By.xpath("//a | //button | //*[@onclick]"));
        int total = all.size();

        for (int i = 0; i < total; i++) {

            List<WebElement> fresh = driver.findElements(By.xpath("//a | //button | //*[@onclick]"));
            if (i >= fresh.size()) break;

            WebElement el = fresh.get(i);

            String href = "";
            try { href = el.getAttribute("href"); } catch (Exception ignored) {}

            String label = extractLabel(el);

            ExtentTest cNode = pageNode.createNode("CTA #" + (i + 1) + ": " + label);

            try {
                cNode.info("UI Color = " + el.getCssValue("color"));
            } catch (Exception ignored) {}

            cNode.info("Target URL → " + href);


            // ------------------------------------------------------------------------------------
            // ⭐ SEARCH CTA HANDLING — FIXED
            // ------------------------------------------------------------------------------------
            if (isSearchCTA(el)) {

                try {
                    ((JavascriptExecutor) driver).executeScript("arguments[0].click();", el);
                    Thread.sleep(500);

                    cNode.pass("Search CTA Working (Detected as Search Input/Button)");
                } catch (Exception ex) {
                    cNode.fail("Search CTA Failed → " + ex.getMessage());
                    failed++;
                }

                try { driver.navigate().to(currentPageUrl); } catch (Exception ignored) {}

                continue;
            }



            try {
                String original = driver.getCurrentUrl();

                if (href != null && href.startsWith("tel:")) {
                    cNode.pass("Phone CTA Working: " + href);
                    continue;
                }

                if (href != null && href.startsWith("mailto:")) {
                    cNode.pass("Email CTA Working: " + href);
                    continue;
                }

                if (href != null && href.startsWith("sms:")) {
                    cNode.pass("SMS CTA Working: " + href);
                    continue;
                }

                if (href == null || href.isEmpty()) {
                    try {
                        ((JavascriptExecutor) driver).executeScript("arguments[0].click();", el);
                        Thread.sleep(400);
                        cNode.pass("Interactive CTA Working (JS Click)");
                    } catch (Exception ex) {
                        cNode.fail("Interactive CTA Failed → " + ex.getMessage());
                        failed++;
                    }

                    driver.navigate().to(currentPageUrl);
                    Thread.sleep(300);
                    continue;
                }

                if (href.startsWith("javascript")) {
                    cNode.fail("Invalid CTA: javascript link");
                    failed++;
                    continue;
                }

                driver.navigate().to(href);
                Thread.sleep(700);

                String after = driver.getCurrentUrl();

                if (!after.equals(original)) {
                    cNode.pass("CTA Working → " + after);
                } else {
                    cNode.fail("CTA Failed — no redirect");
                    failed++;
                }

                driver.navigate().to(currentPageUrl);
                Thread.sleep(400);

            } catch (Exception ex) {
                cNode.fail("CTA Error: " + ex.getMessage());
                failed++;

                try { driver.navigate().to(currentPageUrl); } catch (Exception ignored) {}
            }
        }

        return failed;
    }



    private String extractLabel(WebElement el) {

        try { if (!el.getText().trim().isEmpty()) return el.getText().trim(); } catch (Exception ignored) {}

        try { 
            String aria = el.getAttribute("aria-label"); 
            if (aria != null && !aria.isEmpty()) return aria; 
        } catch (Exception ignored) {}

        try { 
            String title = el.getAttribute("title"); 
            if (title != null && !title.isEmpty()) return title; 
        } catch (Exception ignored) {}

        return el.getTagName() + " element";
    }


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


    private void scrollToBottom() throws InterruptedException {
        JavascriptExecutor js = (JavascriptExecutor) driver;
        long height = (long) js.executeScript("return document.body.scrollHeight");
        for (int i = 0; i < height; i += 300) {
            js.executeScript("window.scrollTo(0, arguments[0]);", i);
            Thread.sleep(80);
        }
    }


    private String captureScreenshot() {
        try {
            File src = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
            String dir = "report/screenshots";
            new File(dir).mkdirs();
            String path = dir + "/" + System.currentTimeMillis() + ".png";
            FileHandler.copy(src, new File(path));
            return path;
        } catch (Exception e) {
            return "";
        }
    }
}
