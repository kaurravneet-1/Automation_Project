package tests;

import utils.SiteConfigReader;

import com.aventstack.extentreports.ExtentTest;
import com.aventstack.extentreports.Status;

import org.openqa.selenium.*;
import org.openqa.selenium.io.FileHandler;
import org.testng.annotations.*;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.*;

public class MultiSitemapLoginTest extends BaseTest {

    List<String[]> websites;
    ExtentTest test;

    int totalUrls = 0;
    int loadedCount = 0;
    int redirectCount = 0;
    int failedCount = 0;
    int warningCount = 0;

    @BeforeClass
    public void initializeTest() {
        setupDriver();   // Start driver (headless)
        websites = SiteConfigReader.readConfig("src/test/resources/websites01.txt");
    }

    @Test
    public void validateLoginRequiredSitemap() throws Exception {

        for (String[] site : websites) {

            totalUrls = loadedCount = redirectCount = failedCount = warningCount = 0;

            String websiteUrl = site[0];
            String username = site[1];
            String password = site[2];
            String sitemapUrl = site[3];

            // Build authenticated URL (Basic Auth)
            String authUrl = websiteUrl.replace("https://",
                    "https://" + username + ":" + password + "@");

            // -----------------------------
            // Create report block
            // -----------------------------
            test = extent.createTest("Validate Sitemap (Login Required): " + websiteUrl);

            test.log(Status.INFO, "Opening website using Basic Auth: " + authUrl);
            logInfo("Opening website using Basic Auth: " + authUrl);

            driver.get(authUrl);
            Thread.sleep(2500);

            test.log(Status.PASS, "Logged in successfully (Basic Auth).");
            logPass("Logged in successfully (Basic Auth).");

            // Open sitemap
            test.log(Status.INFO, "Opening sitemap: " + sitemapUrl);
            logInfo("Opening sitemap: " + sitemapUrl);

            driver.get(sitemapUrl);
            Thread.sleep(2000);

            List<String> allUrls = collectSitemapUrls();
            totalUrls = allUrls.size();

            test.log(Status.INFO, "Found " + totalUrls + " URLs in sitemap.");
            logInfo("Found " + totalUrls + " URLs in sitemap.");

            // Validate each URL
            for (String url : allUrls) {
                validateUrl(url);
            }

          
           
            // -----------------------------
            int working = loadedCount;
            int broken  = redirectCount + failedCount;

            String summaryLine = "Summary → Total: " + totalUrls +
                                 " | Working: " + working +
                                 " | Broken: " + broken;

            test.log(Status.INFO, summaryLine);

            System.out.println("\n===== SUMMARY =====");
            System.out.println("Total URLs: " + totalUrls);
            System.out.println("Working: " + working);
            System.out.println("Broken: " + broken);
            System.out.println("===================\n");
        }
    }

    // --------------------------------------------------------------
    // COLLECT ALL LINKS FROM HTML SITEMAP
    // --------------------------------------------------------------
    public List<String> collectSitemapUrls() {

        List<WebElement> links = driver.findElements(By.xpath("//a[@href]"));
        List<String> urls = new ArrayList<>();

        for (WebElement el : links) {
            String href = el.getAttribute("href");
            if (href != null && href.startsWith("http")) {
                urls.add(href.trim());
            }
        }

        return urls;
    }

    // --------------------------------------------------------------
    // VALIDATE EACH URL + LOGGING + COUNTS + SCREENSHOTS
    // --------------------------------------------------------------
    public void validateUrl(String url) {
        try {
            driver.navigate().to(url);
            Thread.sleep(700);

            String current = driver.getCurrentUrl();

            if (!current.equals(url)) {

                redirectCount++;
                String message = url + " → Redirected To → " + current;

                test.log(Status.FAIL, message + attachScreenshot());
                logFail(message);

            } else {

                loadedCount++;
                String message = url + " → Loaded Successfully";

                test.log(Status.PASS, message);
                logPass(message);
            }

        } catch (Exception e) {

            failedCount++;
            String message = url + " → Failed To Load";

            test.log(Status.FAIL, message + attachScreenshot());
            logFail(message);
        }
    }

    // --------------------------------------------------------------
    // EXTENT REPORT SCREENSHOT FUNCTION
    // --------------------------------------------------------------
    public String attachScreenshot() {
        try {
            File src = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
            String path = "report/screenshots/" + System.currentTimeMillis() + ".png";

            new File("report/screenshots").mkdirs();
            FileHandler.copy(src, new File(path));

            return test.addScreenCaptureFromPath(path).toString();

        } catch (Exception e) {
            return "";
        }
    }

    // --------------------------------------------------------------
    // LOGGING (Console Only)
    // --------------------------------------------------------------
    public void logInfo(String msg) {
        System.out.println(getTimestamp() + "  INFO  " + msg);
    }

    public void logPass(String msg) {
        System.out.println(getTimestamp() + "  PASS  " + msg);
    }

    public void logFail(String msg) {
        System.out.println(getTimestamp() + "  FAIL  " + msg);
    }

    // --------------------------------------------------------------
    // TIMESTAMP
    // --------------------------------------------------------------
    public String getTimestamp() {
        SimpleDateFormat sdf = new SimpleDateFormat("hh:mm:ss a");
        return sdf.format(new Date());
    }
}
