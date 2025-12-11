package tests;

import org.openqa.selenium.*;
import org.testng.annotations.Test;
import com.aventstack.extentreports.*;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URI;
import java.util.*;

public class FullSiteCrawlerTest extends BaseTest {

    private Set<String> visitedUrls = new HashSet<>();
    private Queue<String> urlsToVisit = new LinkedList<>();

    private int workingPages = 0;
    private int brokenPages = 0;
    private int warningPages = 0;
    private int skippedPages = 0;

    @Test
    public void fullSiteCrawler() throws Exception {

        // ===========================================
        // SUMMARY NODE AT TOP OF REPORT
        // ===========================================
        ExtentTest summaryNode = extent.createTest("📊 Crawl Summary");

        ExtentTest test = extent.createTest("Full Site Crawler – Validate Every Page Works");

        // ===========================================
        // 1. Load Base URL
        // ===========================================
        String csvPath = "src/test/resources/websiteurl.csv";
        String baseUrl = readUrlFromCSV(csvPath);

        test.info("Base URL → " + baseUrl);
        urlsToVisit.add(baseUrl);

        // ===========================================
        // 2. CRAWL LOOP
        // ===========================================
        while (!urlsToVisit.isEmpty()) {

            String currentUrl = urlsToVisit.poll();
            if (visitedUrls.contains(currentUrl)) continue;

            visitedUrls.add(currentUrl);

            // Skip invalid Elementor template links
            if (shouldSkipUrl(currentUrl)) {
                skippedPages++;
                test.warning("⏩ Skipped invalid/template URL → " + currentUrl);
                continue;
            }

            test.info("🔍 Checking → " + currentUrl);

            try {
                // HTTP Status
                int status = getStatusCode(currentUrl);

                if (status == 200) {
                    workingPages++;
                    test.pass("🟢 200 OK → " + currentUrl);
                } else {
                    brokenPages++;
                    test.fail("🔴 FAILED (" + status + ") → " + currentUrl +
                            " | Reason: " + getHttpError(status));
                }

                // Load page for crawling links
                driver.get(currentUrl);
                waitForPageLoad();

                // Check title
                String title = driver.getTitle();
                if (title == null || title.isBlank()) {
                    warningPages++;
                    test.warning("⚠ Empty Title → " + currentUrl);
                }

                // Extract internal links
                List<WebElement> links = driver.findElements(By.xpath("//a[@href]"));

                for (WebElement link : links) {
                    String url = link.getAttribute("href");

                    if (isValidInternalUrl(url, baseUrl)) {
                        if (!visitedUrls.contains(url)) {
                            urlsToVisit.add(url);
                        }
                    }
                }

            } catch (Exception e) {
                brokenPages++;
                test.fail("❌ Error loading → " + currentUrl + " | Reason: " + e.getMessage());
            }
        }

        // ===========================================
        // 3. FINAL SUMMARY (TOP OF REPORT)
        // ===========================================
        summaryNode.info("📌 Total Pages Found: " + visitedUrls.size());
        summaryNode.info("🟢 Working Pages: " + workingPages);
        summaryNode.info("🔴 Broken Pages: " + brokenPages);
        summaryNode.info("⚠ Pages with Warnings: " + warningPages);
        summaryNode.info("⏩ Skipped Template Pages: " + skippedPages);
        summaryNode.pass("✔ Crawl Completed Successfully.");
    }


    // ===========================================================
    // Utility Methods
    // ===========================================================

    private boolean shouldSkipUrl(String url) {
        if (url.contains("[") || url.contains("]")) return true;
        if (url.contains("%22")) return true;
        if (url.contains("AUD_BRAND")) return true;
        if (url.contains("{{")) return true;
        return false;
    }

    private boolean isValidInternalUrl(String url, String baseUrl) {
        if (url == null || url.isBlank()) return false;
        if (!url.startsWith("http")) return false;
        if (url.contains("#")) return false;
        if (shouldSkipUrl(url)) return false;
        return url.startsWith(baseUrl);
    }

    private String readUrlFromCSV(String csvPath) throws Exception {
        BufferedReader br = new BufferedReader(new FileReader(csvPath));
        String line = br.readLine();
        br.close();
        return line.trim();
    }

    private int getStatusCode(String url) {
        try {
            URI uri = URI.create(url);
            HttpURLConnection http = (HttpURLConnection) uri.toURL().openConnection();
            http.setRequestMethod("GET");
            http.setConnectTimeout(5000);
            http.setReadTimeout(5000);
            http.connect();
            return http.getResponseCode();
        } catch (Exception e) {
            return -1;
        }
    }

    private String getHttpError(int status) {
        return switch (status) {
            case -1 -> "Connection Failed (Timeout or Website Blocked)";
            case 404 -> "Not Found";
            case 500 -> "Server Error";
            case 403 -> "Forbidden";
            case 301, 302 -> "Redirected";
            default -> "Unknown Error";
        };
    }

    private void waitForPageLoad() {
        try { Thread.sleep(1000); } catch (Exception ignored) {}
    }
}
