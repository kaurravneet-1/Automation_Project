package tests;

import java.io.File;
//import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.*;
//import java.util.regex.*;

import org.apache.commons.io.FileUtils;
import org.openqa.selenium.*;
import org.openqa.selenium.OutputType;

import org.testng.annotations.Test;

import com.aventstack.extentreports.ExtentTest;
import com.aventstack.extentreports.MediaEntityBuilder;
import com.aventstack.extentreports.Status;

public class HeaderFooterChecker extends BaseTest {

    private static final int MAX_PAGES = 500;
    private static final double FUZZY_THRESHOLD = 0.80;

    @Test
    public void verifyHeaderFooterForAllWebsites() {

        String csvPath = System.getProperty("user.dir") + "/src/test/resources/client_brief.csv";
        List<ExcelReader.ClientData> clients = ExcelReader.getClientData(csvPath);

        for (ExcelReader.ClientData client : clients) {

            ExtentTest test = extent.createTest("Checking site: " + client.website);

            // SUMMARY COUNTERS
            int totalPages = 0;
            int totalPassed = 0;
            int totalFailed = 0;
            int totalWarnings = 0;

            try {
                setupDriver();
                driver.get(client.website);
                Thread.sleep(2000);

                Set<String> linksToCheck = collectInternalLinks(client.website);
                if (linksToCheck.isEmpty()) linksToCheck.add(client.website);

                int count = 0;

                for (String url : linksToCheck) {
                    if (count >= MAX_PAGES) break;
                    count++;

                    totalPages++;

                    driver.get(url);
                    Thread.sleep(1500);

                    ExtentTest pageTest = test.createNode("Page: " + url);

                    // Each method updates pass/fail counters
                    totalPassed += checkCompanyNameAndLogo(client, pageTest, url);
                    totalPassed += checkPhoneNumbers(client, pageTest, url);
                    totalPassed += checkAddresses(client, pageTest, url);
                    totalPassed += checkHours(client, pageTest, url);

                    // Count warnings and failures from logs
                    List<Status> logs = pageTest.getModel().getLogContext().getAll().stream()
                            .map(l -> l.getStatus())
                            .toList();

                    for (Status s : logs) {
                        if (s == Status.FAIL) totalFailed++;
                        if (s == Status.WARNING) totalWarnings++;
                    }
                }

                // ADD SUMMARY AT THE TOP
                ExtentTest summary = test.createNode("SUMMARY");

                summary.info("Total Pages Scanned: " + totalPages);
                summary.info("Total Passed Checks: " + totalPassed);
                summary.info("Total Failed Checks: " + totalFailed);
                summary.info("Total Warnings: " + totalWarnings);

                if (totalFailed > 0) {
                    summary.fail("❌ Website has failed checks.");
                } else if (totalWarnings > 0) {
                    summary.warning("⚠ Website has warnings.");
                } else {
                    summary.pass("✔ All checks passed successfully!");
                }

                driver.quit();

            } catch (Exception e) {
                test.fail("Error checking site: " + client.website + " → " + e.getMessage());
                driver.quit();
            }
        }
    }

    // ---------------- NORMALIZATION ----------------

    private String normalize(String text) {
        if (text == null) return "";

        return text
            .toLowerCase()
            .replaceAll("<br>", " ")
            .replaceAll("<br/>", " ")
            .replaceAll("<[^>]+>", " ")
            .replaceAll("[^a-z0-9 ]", " ")
            .replaceAll("\\s+", " ")
            .trim();
    }

    private String getSectionText(String xpath) {
        try {
            WebElement section = driver.findElement(By.xpath(xpath));
            return normalize(section.getText());
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

    // ---------------- FUZZY MATCH ----------------

    private boolean fuzzyMatch(String a, String b) {
        a = normalize(a);
        b = normalize(b);

        int distance = levenshtein(a, b);
        int max = Math.max(a.length(), b.length());
        double similarity = 1.0 - (double) distance / max;

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

    // ---------------- COMPANY NAME + LOGO ----------------

    private int checkCompanyNameAndLogo(ExcelReader.ClientData client, ExtentTest pageTest, String url) {

        int passCount = 0;

        try {
            String company = normalize(client.companyName);
            String fullText = getFullSearchableText();

            boolean logoFound = false;
            boolean nameFound = fullText.contains(company) || fuzzyMatch(fullText, company);

            for (WebElement logo : driver.findElements(By.tagName("img"))) {
                String alt = normalize(logo.getAttribute("alt"));

                if (alt.contains(company) || fuzzyMatch(alt, company)) {
                    logoFound = true;
                    pageTest.pass(getTimestamp() + " | Company logo found (alt=" + alt + ")");
                    passCount++;
                }
            }

            if (nameFound && !logoFound) {
                pageTest.pass(getTimestamp() + " | Company name found in header/footer/page");
                passCount++;
            }

            if (!nameFound && !logoFound) {
                String ss = captureScreenshot(url);
                pageTest.fail(getTimestamp() + " | Company name/logo NOT found: " + client.companyName,
                        MediaEntityBuilder.createScreenCaptureFromPath(ss).build());
            }

        } catch (Exception e) {
            pageTest.warning("Error checking company name: " + e.getMessage());
        }

        return passCount;
    }

    // ---------------- PHONE CHECK ----------------

    private int checkPhoneNumbers(ExcelReader.ClientData client, ExtentTest pageTest, String url) {

        int passCount = 0;

        if (client.phone.isEmpty()) return 0;

        String fullTextNums = getFullSearchableText().replaceAll("[^0-9]", "");

        for (String phone : client.phone.split(";")) {

            String cleaned = phone.replaceAll("[^0-9]", "");

            if (fullTextNums.contains(cleaned)) {
                pageTest.pass(getTimestamp() + " | Phone found: " + phone);
                passCount++;
            } else {
                String ss = captureScreenshot(url);
                pageTest.warning(getTimestamp() + " | Phone NOT found: " + phone);
                pageTest.info("Screenshot: " + ss);
            }
        }
        return passCount;
    }

    // ---------------- ADDRESS CHECK ----------------

    private int checkAddresses(ExcelReader.ClientData client, ExtentTest pageTest, String url) {

        int passCount = 0;

        if (client.address.isEmpty()) return 0;

        String fullText = getFullSearchableText();

        for (String addr : client.address.split(";")) {

            String normalized = normalize(addr);

            boolean exact = fullText.contains(normalized);
            boolean fuzzy = fuzzyMatch(fullText, normalized);
            boolean partial = fullText.contains(normalized.split(" ")[0]);

            if (exact || fuzzy || partial) {
                pageTest.pass(getTimestamp() + " | Address found: " + addr);
                passCount++;
            } else {
                String ss = captureScreenshot(url);
                pageTest.warning(getTimestamp() + " | Address NOT found: " + addr);
                pageTest.info("Screenshot: " + ss);
            }
        }
        return passCount;
    }

    // ---------------- HOURS CHECK ----------------

    private int checkHours(ExcelReader.ClientData client, ExtentTest pageTest, String url) {

        int passCount = 0;

        if (client.hours.isEmpty()) return 0;

        String fullText = getFullSearchableText();

        for (String hrs : client.hours.split(";")) {

            String hours = normalize(hrs);

            if (fullText.contains(hours) || fuzzyMatch(fullText, hours)) {
                pageTest.pass(getTimestamp() + " | Hours found: " + hrs);
                passCount++;
            } else {
                String ss = captureScreenshot(url);
                pageTest.warning(getTimestamp() + " | Hours NOT found: " + hrs);
                pageTest.info("Screenshot: " + ss);
            }
        }
        return passCount;
    }

    // ---------------- LINKS ----------------

    private Set<String> collectInternalLinks(String baseUrl) {
        Set<String> links = new HashSet<>();
        try {
            String domain = getDomain(baseUrl);

            for (WebElement a : driver.findElements(By.tagName("a"))) {
                String href = a.getAttribute("href");
                if (href != null && href.startsWith("http") && href.contains(domain)) {
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

    // ---------------- SCREENSHOT ----------------

    private String captureScreenshot(String url) {
        try {
            File src = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
            String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
            String safe = url.replaceAll("[^a-zA-Z0-9]", "_");

            String filename = System.getProperty("user.dir") + "/screenshots/" + safe + "_" + timestamp + ".png";

            File dest = new File(filename);
            FileUtils.copyFile(src, dest);

            return filename;

        } catch (Exception e) {
            return "";
        }
    }

    private String getTimestamp() {
        return new SimpleDateFormat("hh:mm:ss a").format(new Date());
    }
}
