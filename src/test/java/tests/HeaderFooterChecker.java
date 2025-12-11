package tests;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.*;

import org.apache.commons.io.FileUtils;
import org.openqa.selenium.*;
import org.openqa.selenium.OutputType;
import org.testng.annotations.Test;

import com.aventstack.extentreports.ExtentTest;
import com.aventstack.extentreports.MediaEntityBuilder;


public class HeaderFooterChecker extends BaseTest {

    private static final int MAX_PAGES = 500;
    private static final double FUZZY_THRESHOLD = 0.80;

    @Test
    public void verifyHeaderFooterForAllWebsites() {

        String csvPath = System.getProperty("user.dir") + "/src/test/resources/client_brief.csv";
        List<ExcelReader.ClientData> clients = ExcelReader.getClientData(csvPath);

        for (ExcelReader.ClientData client : clients) {

            ExtentTest test = extent.createTest("Checking site: " + client.website);

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

                   
                    totalPassed += checkCompanyNameAndLogo(client, pageTest, url);
                    totalPassed += checkPhoneNumbers(client, pageTest, url);
                    totalPassed += checkAddresses(client, pageTest, url);
                    totalPassed += checkHours(client, pageTest, url);

                    // FAIL and WARN counters are updated inside methods via returns
                    // Example for WARNING: return 100
                    // Example for FAIL: return -1
                }

              
                ExtentTest summary = test.createNode("SUMMARY");
                summary.info("Total Pages Scanned: " + totalPages);
                summary.info("Total Passed Checks: " + totalPassed);
                summary.info("Total Failed Checks: " + totalFailed);
                summary.info("Total Warnings: " + totalWarnings);

                if (totalFailed > 0) {
                    summary.fail(" Website has failed checks.");
                } else if (totalWarnings > 0) {
                    summary.warning(" Website has warnings.");
                } else {
                    summary.pass(" All checks passed successfully!");
                }

                driver.quit();

            } catch (Exception e) {
                test.fail("Error checking site: " + client.website + " → " + e.getMessage());
                driver.quit();
            }
        }
    }

   

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

  

    public int checkCompanyNameAndLogo(ExcelReader.ClientData client, ExtentTest pageTest, String url) {

        try {
            String company = normalize(client.companyName);
            String fullText = getFullSearchableText();

          //  boolean logoFound;
            boolean nameFound = fullText.contains(company) || fuzzyMatch(fullText, company);

            for (WebElement logo : driver.findElements(By.tagName("img"))) {
                String alt = normalize(logo.getAttribute("alt"));

                if (alt.contains(company) || fuzzyMatch(alt, company)) {
               //     logoFound = true;
                    pageTest.pass("Company logo found: " + alt);
                    return 1;
                }
            }

            if (nameFound) {
                pageTest.pass("Company name found in text: " + client.companyName);
                return 1;
            }

            String ss = captureScreenshot(url);
            pageTest.fail("Company name/logo NOT found: " + client.companyName,
                    MediaEntityBuilder.createScreenCaptureFromPath(ss).build());
            return -1;

        } catch (Exception e) {
            pageTest.warning("Error checking company name: " + e.getMessage());
            return 100;
        }
    }



    private int checkPhoneNumbers(ExcelReader.ClientData client, ExtentTest pageTest, String url) {

        if (client.phone.isEmpty()) return 0;

        String fullTextNums = getFullSearchableText().replaceAll("[^0-9]", "");

        for (String phone : client.phone.split(";")) {
            String cleaned = phone.replaceAll("[^0-9]", "");

            if (fullTextNums.contains(cleaned)) {
                pageTest.pass("Phone found: " + phone);
                return 1;
            } else {
               // String ss = captureScreenshot(url);
                pageTest.warning("Phone NOT found: " + phone);
                return 100;
            }
        }
        return 0;
    }

   

    private int checkAddresses(ExcelReader.ClientData client, ExtentTest pageTest, String url) {

        if (client.address.isEmpty()) return 0;

        String fullText = getFullSearchableText();

        for (String addr : client.address.split(";")) {

            String normalized = normalize(addr);

            boolean exact = fullText.contains(normalized);
            boolean fuzzy = fuzzyMatch(fullText, normalized);

            if (exact || fuzzy) {
                pageTest.pass("Address found: " + addr);
                return 1;
            } else {
                pageTest.warning("Address NOT found: " + addr);
                return 100;
            }
        }
        return 0;
    }


    private int checkHours(ExcelReader.ClientData client, ExtentTest pageTest, String url) {

        if (client.hours.isEmpty()) return 0;

        String fullText = getFullSearchableText();

        for (String hrs : client.hours.split(";")) {

            String hours = normalize(hrs);

            if (fullText.contains(hours) || fuzzyMatch(fullText, hours)) {
                pageTest.pass("Hours found: " + hrs);
                return 1;
            } else {
                pageTest.warning("Hours NOT found: " + hrs);
                return 100;
            }
        }
        return 0;
    }

 

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

   
}

