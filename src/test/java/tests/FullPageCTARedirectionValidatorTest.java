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
    public void validateAllCTAs() throws Exception {

        ExtentTest mainTest = extent.createTest("Full Website CTA Validator");

        
        String csvPath = "src/test/resources/sitemapurls.csv";
        List<String> urls = readUrlsFromCSV(csvPath);

        if (urls.isEmpty()) {
            mainTest.fail("CSV file does not contain any URLs.");
            return;
        }

        String baseUrl = urls.get(0);
        mainTest.info("Base URL loaded: " + baseUrl);

        Set<String> toVisit = new LinkedHashSet<>();
        Set<String> visited = new HashSet<>();

        toVisit.add(baseUrl);

        int scanned = 0;

        while (!toVisit.isEmpty() && scanned < MAX_PAGES) {

            String current = toVisit.iterator().next();
            toVisit.remove(current);

            if (visited.contains(current)) continue;
            visited.add(current);

            scanned++;

           
            ExtentTest pageNode = mainTest;

            try {
                driver.get(current);
                Thread.sleep(1500);
                scrollToBottom();

                
                List<WebElement> allCTAsBefore =
                        driver.findElements(By.xpath("//a | //button | //*[@onclick]"));

                int totalCTAs = allCTAsBefore.size();
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

              
                ExtentTest summaryNode = pageNode.createNode("Page Summary");
                summaryNode.info("Page Loaded: " + current);
                summaryNode.info("Total CTAs Found: " + totalCTAs);
                summaryNode.info("Header CTAs: " + headerCTAs);
                summaryNode.info("Footer CTAs: " + footerCTAs);
                summaryNode.info("Body CTAs: " + bodyCTAs);

                
                int failedCTAs = validateAllCTAs(pageNode, current);
                int workingCTAs = totalCTAs - failedCTAs;

                summaryNode.info("Working CTAs: " + workingCTAs);
                summaryNode.info("Failed CTAs: " + failedCTAs);

                summaryNode.pass("Page CTA Validation Completed");

            } catch (Exception e) {
                pageNode.warning("Error loading page: " + e.getMessage());
                pageNode.info("Screenshot: " + captureScreenshot());
            }
        }

        mainTest.info("Total Pages Scanned: " + scanned);
        extent.flush();
    }

    
    private int validateAllCTAs(ExtentTest pageNode, String currentPageUrl) throws ElementClickInterceptedException {

        int failed = 0;

        List<WebElement> all = driver.findElements(By.xpath("//a | //button | //*[@onclick]"));
        int total = all.size();

        for (int i = 0; i < total; i++) {

            List<WebElement> freshList =
                    driver.findElements(By.xpath("//a | //button | //*[@onclick]"));
            if (i >= freshList.size()) break;

            WebElement el = freshList.get(i);

            String href = "";
            try { href = el.getAttribute("href"); } catch (Exception ignored) {}

            String label = extractLabel(el);

            ExtentTest cNode = pageNode.createNode("CTA #" + (i + 1) + ": " + label);

            
            try {
                cNode.info("UI → color=" + el.getCssValue("color") +
                           ", font=" + el.getCssValue("font-size"));
            } catch (Exception ignored) {
               
            }
            cNode.info("Target → " + href);

            try {
                String originalUrl = driver.getCurrentUrl();

                
                if (href != null && href.startsWith("tel:")) {
                    cNode.pass("Phone CTA Working → " + href);
                    continue;
                }

                
                if (href != null && href.startsWith("sms:")) {
                    cNode.pass("SMS CTA Working → " + href);
                    continue;
                }

                
                if (href != null && href.startsWith("mailto:")) {
                    cNode.pass("Email CTA Working → " + href);
                    continue;
                }

              
                if (href == null || href.trim().isEmpty()) {

                    boolean clickedOk = false;
                    String afterClickUrl = originalUrl;

                    try {
                        try {
                            ((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView(true);", el);
                            Thread.sleep(120);
                            el.click();
                        } catch (WebDriverException clickEx) {
                            try {
                                ((JavascriptExecutor) driver).executeScript("arguments[0].click();", el);
                            } catch (Exception jsClickEx) {
                                throw clickEx; 
                            }
                        }

                        Thread.sleep(650);

                        try { afterClickUrl = driver.getCurrentUrl(); } catch (Exception ignored) {}

                        if (!afterClickUrl.equals(originalUrl)) {
                            cNode.info("Redirected URL → " + afterClickUrl);
                            cNode.pass("Interactive CTA Working");
                            try {
                                driver.navigate().to(currentPageUrl);
                                Thread.sleep(500);
                            } catch (Exception ignored) {}
                            clickedOk = true;
                        } else {
                            cNode.pass("Interactive CTA Working (JS click handled; no href)");
                            clickedOk = true;

                            try {
                                Actions actions = new Actions(driver);
                                actions.sendKeys(Keys.ESCAPE).perform();
                                Thread.sleep(200);
                            } catch (Exception ignored) {}
                        }

                    } catch (Exception clickFailure) {
                        cNode.fail("Interactive CTA Failed — click error: " + clickFailure.getMessage());
                        failed++;
                        try {
                            driver.navigate().to(currentPageUrl);
                            Thread.sleep(400);
                        } catch (Exception ignored) {}
                        continue;
                    }

                    if (clickedOk) {
                        continue;
                    } else {
                        cNode.fail("CTA Failed — Empty href and not interactive");
                        failed++;
                        continue;
                    }
                }

                if (href.startsWith("javascript")) {
                    cNode.fail("CTA Failed — javascript link");
                    failed++;
                    continue;
                }

                if (href.equalsIgnoreCase(currentPageUrl)) {
                    cNode.pass("CTA Working — Same Page Reload");
                    continue;
                }

                try {
                    driver.navigate().to(href);
                    Thread.sleep(900);
                } catch (Exception navEx) {
                    try {
                        ((JavascriptExecutor) driver).executeScript("window.location.href = arguments[0];", href);
                        Thread.sleep(900);
                    } catch (Exception ignored) {}
                }

                String newUrl = "";
                try { newUrl = driver.getCurrentUrl(); } catch (Exception ignored) {}

                if (href.contains("#") && newUrl.equals(originalUrl)) {
                    cNode.pass("Anchor CTA Working (same page navigation)");
                } else if (!newUrl.equals(originalUrl)) {
                    cNode.pass("CTA Working → " + newUrl);
                } else {
                    cNode.fail("CTA Failed — No Redirect");
                    failed++;
                }

                try {
                    driver.navigate().to(currentPageUrl);
                    Thread.sleep(400);
                } catch (Exception ignored) {}

            } catch (Exception ex) {
                cNode.fail("CTA Exception → " + ex.getMessage());
                failed++;
                try {
                    driver.navigate().to(currentPageUrl);
                    Thread.sleep(400);
                } catch (Exception ignored) {}
            }
        }

        return failed;
    }

    private String extractLabel(WebElement el) {

        try {
            String t = el.getText();
            if (t != null && !t.trim().isEmpty()) return t.trim();
        } catch (Exception ignored) {}

        try {
            String aria = el.getAttribute("aria-label");
            if (aria != null && !aria.trim().isEmpty()) return aria.trim();
        } catch (Exception ignored) {}

        try {
            String title = el.getAttribute("title");
            if (title != null && !title.trim().isEmpty()) return title.trim();
        } catch (Exception ignored) {}

        try {
            JavascriptExecutor js = (JavascriptExecutor) driver;
            String inner = (String) js.executeScript(
                    "return arguments[0].innerText || arguments[0].textContent;", el);
            if (inner != null && !inner.trim().isEmpty()) return inner.trim();
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
        for (int i = 0; i < height; i += 400) {
            js.executeScript("window.scrollTo(0, arguments[0]);", i);
            Thread.sleep(120);
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
