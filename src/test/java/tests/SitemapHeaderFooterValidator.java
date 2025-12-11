package tests;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.*;

import org.apache.commons.io.FileUtils;
import org.openqa.selenium.*;
import org.testng.Assert;
import org.testng.annotations.Test;

import com.aventstack.extentreports.*;
import com.aventstack.extentreports.reporter.ExtentSparkReporter;

public class SitemapHeaderFooterValidator extends BaseTest {

    private static final int MAX_PAGES = 1000;
    private static final double FUZZY_THRESHOLD = 0.80;

    private static int totalPages = 0, passCount = 0, failCount = 0, warningCount = 0;

    @Test
    public void verifySitemapLinksHeaderFooter() {
        String csvPath = System.getProperty("user.dir") + "/src/test/resources/client_brief04.csv";
        List<ExcelReader.ClientData> clients = ExcelReader.getClientData(csvPath);

        ExtentSparkReporter spark = new ExtentSparkReporter(
                "reports/Sitemap_HeaderFooter_Report_" + getDateTime() + ".html");
        extent.attachReporter(spark);

        for (ExcelReader.ClientData client : clients) {
            ExtentTest siteTest = extent.createTest("Website: " + client.website);

            int sitePages = 0, sitePass = 0, siteFail = 0, siteWarn = 0;

            try {
                setupDriver();

                String sitemapUrl = client.website + (client.website.endsWith("/") ? "" : "/") + "sitemap/";
                driver.get(sitemapUrl);
                Thread.sleep(2000);
                scrollToBottom();

                Set<String> pageLinks = collectInternalLinks(client.website);

                siteTest.info("Total links found in sitemap: " + pageLinks.size());

                if (pageLinks.isEmpty()) {
                    siteTest.warning("No links found in sitemap, fallback to homepage.");
                    warningCount++;
                    siteWarn++;
                    pageLinks.add(client.website);
                }

                int checked = 0;
                for (String pageUrl : pageLinks) {

                    if (checked++ >= MAX_PAGES) break;
                    totalPages++;
                    sitePages++;

                    ExtentTest pageNode = siteTest.createNode("🔗 Page: " + pageUrl);

                    try {
                        driver.get(pageUrl);
                        scrollToBottom();

                        String fullText = getFullSearchableText();

                        boolean namePass = checkCompanyNameAndLogo(client, fullText, pageNode, pageUrl);
                        boolean phonePass = checkPhoneNumbers(client, fullText, pageNode, pageUrl);
                        boolean addressPass = checkAddresses(client, fullText, pageNode, pageUrl);
                        boolean hoursPass = checkHours(client, fullText, pageNode, pageUrl);

                        boolean shouldFail = false;

                        if (!client.companyName.isEmpty() && !namePass) shouldFail = true;
                        if (!client.phone.isEmpty() && !phonePass) shouldFail = true;
                        if (!client.address.isEmpty() && !addressPass) shouldFail = true;
                        if (!client.hours.isEmpty() && !hoursPass) shouldFail = true;

                        if (!shouldFail) {
                            passCount++;
                            sitePass++;
                            pageNode.pass("Page validation passed.");
                        } else {
                            failCount++;
                            siteFail++;
                            pageNode.fail("Page validation failed.");
                        }

                    } catch (Exception ex) {
                        failCount++;
                        siteFail++;
                        pageNode.fail("Error handling page: " + ex.getMessage());
                    }
                }

                
                siteTest.info(
                        "Summary for site:\n" +
                        "Pages Scanned: " + sitePages +
                        " | Passed: " + sitePass +
                        " | Failed: " + siteFail +
                        " | Warnings: " + siteWarn
                );

                driver.quit();

            } catch (Exception e) {
                siteTest.fail("Error reading sitemap: " + e.getMessage());
                driver.quit();
            }
        }

        ExtentTest summary = extent.createTest("FINAL RUN SUMMARY");
        summary.info("Total Pages Scanned: " + totalPages);
        summary.info("Total Passed: " + passCount);
        summary.info("Total Failed: " + failCount);
        summary.info("Total Warnings: " + warningCount);

        if (failCount > 0) summary.fail("Some pages failed.");
        else if (warningCount > 0) summary.warning("Some warnings exist.");
        else summary.pass("All pages passed successfully!");

        extent.flush();

        Assert.assertTrue(failCount == 0, "Some pages failed — check report.");
    }

    private String normalize(String text) {
        if (text == null) return "";
        String t = text.replaceAll("(?i)<br\\s*/?>", " ");
        t = t.replaceAll("<[^>]+>", " ");
        t = t.replaceAll("[^a-zA-Z0-9 ]", " ");
        t = t.toLowerCase();
        t = t.replaceAll("\\s+", " ").trim();
        return t;
    }

    private String getSectionText(String xpath) {
        try {
            WebElement sec = driver.findElement(By.xpath(xpath));
            return normalize(sec.getText());
        } catch (Exception e) {
            return "";
        }
    }

    private String getFullSearchableText() {
        return normalize(
                getSectionText("//header") + " " +
                getSectionText("//footer") + " " +
                driver.getPageSource()
        );
    }

    private boolean fuzzyMatch(String a, String b) {
        a = normalize(a);
        b = normalize(b);

        int dist = levenshtein(a, b);
        int max = Math.max(a.length(), b.length());

        double similarity = 1.0 - ((double) dist / max);
        return similarity >= FUZZY_THRESHOLD;
    }

    private int levenshtein(String a, String b) {
        int[][] dp = new int[a.length() + 1][b.length() + 1];

        for (int i = 0; i <= a.length(); i++) dp[i][0] = i;
        for (int j = 0; j <= b.length(); j++) dp[0][j] = j;

        for (int i = 1; i <= a.length(); i++) {
            for (int j = 1; j <= b.length(); j++) {
                int cost = (a.charAt(i - 1) == b.charAt(j - 1)) ? 0 : 1;
                dp[i][j] = Math.min(
                        dp[i - 1][j] + 1,
                        Math.min(dp[i][j - 1] + 1, dp[i - 1][j - 1] + cost)
                );
            }
        }
        return dp[a.length()][b.length()];
    }

    

    private boolean checkCompanyNameAndLogo(ExcelReader.ClientData client, String fullText,
                                            ExtentTest test, String url) {
        boolean found = false;

        try {
            if (client.companyName.isEmpty()) return true;

            String normalizedCompany = normalize(client.companyName);

            // Try logo alt text
            for (WebElement logo : driver.findElements(By.tagName("img"))) {
                try {
                    String alt = logo.getAttribute("alt");
                    if (alt != null && !alt.trim().isEmpty()) {
                        if (normalize(alt).contains(normalizedCompany)) {
                            found = true;
                            test.pass("Company logo found (alt=" + alt + ")");
                            break;
                        }
                    }
                } catch (StaleElementReferenceException ignored) {}
            }

            
            if (!found && fullText.contains(normalizedCompany)) {
                found = true;
                test.pass("Company name found in text.");
            }

            
            if (!found && fuzzyMatch(fullText, normalizedCompany)) {
                found = true;
                test.pass("Company name fuzzy matched.");
            }

            if (!found) {
                test.fail("Company name/logo NOT found.",
                        MediaEntityBuilder.createScreenCaptureFromPath(captureScreenshot(url)).build());
            }

        } catch (Exception e) {
            test.warning("Error checking company name/logo: " + e.getMessage());
        }

        return found;
    }

    

    private boolean checkPhoneNumbers(ExcelReader.ClientData client, String fullText,
                                      ExtentTest test, String url) throws IOException {
        if (client.phone.isEmpty()) return true;

        boolean foundAny = false;
        String digitsPage = fullText.replaceAll("[^0-9]", "");

        for (String ph : client.phone.split(";")) {
            String normalized = ph.replaceAll("[^0-9]", "");

            if (digitsPage.contains(normalized)) {
                foundAny = true;
                test.pass("Phone found: " + ph);
                continue;
            }

            // Check visible DOM text
            for (WebElement el : driver.findElements(By.xpath("//*"))) {
                try {
                    if (el.isDisplayed()) {
                        String visible = normalize(el.getText()).replaceAll("[^0-9]", "");
                        if (visible.contains(normalized)) {
                            foundAny = true;
                            test.pass("Phone visible on page: " + ph);
                            break;
                        }
                    }
                } catch (Exception ignored) {}
            }

            if (!foundAny) {
                test.fail("Phone NOT found: " + ph,
                        MediaEntityBuilder.createScreenCaptureFromPath(captureScreenshot(url)).build());
            }
        }

        return foundAny;
    }

    

    private boolean checkAddresses(ExcelReader.ClientData client, String fullText,
                                   ExtentTest test, String url) throws IOException {

        if (client.address.isEmpty()) return true;

        boolean foundAny = false;
        String normalizedPage = normalize(fullText);

        for (String addr : client.address.split(";")) {
            String normAddr = normalize(addr);

            boolean exact = normalizedPage.contains(normAddr);
            boolean fuzzy = fuzzyMatch(normalizedPage, normAddr);
            boolean partial = allWordsPresent(normalizedPage, normAddr);

            if (exact || fuzzy || partial) {
                foundAny = true;
                test.pass("Address found: " + addr);
            } else {
                test.warning("Address not found: " + addr);
            }
        }

        if (!foundAny) {
            test.fail("Address missing",
                    MediaEntityBuilder.createScreenCaptureFromPath(captureScreenshot(url)).build());
        }

        return foundAny;
    }

    private boolean allWordsPresent(String big, String small) {
        String[] words = small.split(" ");
        int found = 0;

        for (String w : words) {
            if (w.length() > 2 && big.contains(w)) found++;
        }

        return ((double) found / words.length) >= 0.60;
    }

   

    private boolean checkHours(ExcelReader.ClientData client, String fullText,
                               ExtentTest test, String url) throws IOException {

        if (client.hours.isEmpty()) return true;

        boolean found = false;

        String expected = normalize(client.hours).replaceAll("\\s+", "");
        String actual = normalize(fullText).replaceAll("\\s+", "");

        if (actual.contains(expected)) found = true;
        else if (actual.replaceAll("(am|pm)", "")
                .contains(expected.replaceAll("(am|pm)", "")))
            found = true;
        else if (fuzzyMatch(actual, expected)) found = true;

        if (found) {
            test.pass("Hours found: " + client.hours);
        } else {
            test.fail("Hours not found: " + client.hours,
                    MediaEntityBuilder.createScreenCaptureFromPath(captureScreenshot(url)).build());
        }

        return found;
    }

    

    private void scrollToBottom() throws InterruptedException {
        JavascriptExecutor js = (JavascriptExecutor) driver;
        long lastHeight = (long) js.executeScript("return document.body.scrollHeight");
        while (true) {
            js.executeScript("window.scrollTo(0, document.body.scrollHeight);");
            Thread.sleep(800);
            long newHeight = (long) js.executeScript("return document.body.scrollHeight");
            if (newHeight == lastHeight) break;
            lastHeight = newHeight;
        }
    }

    private Set<String> collectInternalLinks(String baseUrl) {
        Set<String> links = new LinkedHashSet<>();
        try {
            List<WebElement> aTags = driver.findElements(By.tagName("a"));
            String domain = getDomain(baseUrl);

            for (WebElement a : aTags) {
                String href = a.getAttribute("href");
                if (href != null && href.startsWith("http") && href.contains(domain) && !href.contains("mailto")) {
                    links.add(href.split("#")[0]);
                }
            }
        } catch (Exception ignored) {}
        return links;
    }

    private String getDomain(String url) {
        try {
            return java.net.URI.create(url).getHost();
        } catch (Exception e) {
            return url;
        }
    }

    private String captureScreenshot(String url) {
        try {
            File src = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
            String filename =
                    System.getProperty("user.dir") +
                    "/screenshots/" +
                    url.replaceAll("[^a-zA-Z0-9]", "_") +
                    "_" + getDateTime() + ".png";

            FileUtils.copyFile(src, new File(filename));
            return filename;

        } catch (IOException e) {
            return "";
        }
    }

  
    private String getDateTime() {
        return new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
    }
}
