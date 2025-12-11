package tests;

import org.openqa.selenium.*;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.testng.annotations.Test;

import com.aventstack.extentreports.*;

import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.*;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URI;
import java.time.Duration;
import java.util.*;

public class FullSiteMasterValidatorTest extends BaseTest {

    // Storage
    private Set<String> sitemapUrls = new HashSet<>();
    private Set<String> crawlerUrls = new HashSet<>();
    private Set<String> mergedUrls = new HashSet<>();

    private Queue<String> crawlQueue = new LinkedList<>();

    // Counters
    private int working = 0, broken = 0, warnings = 0, skipped = 0, orphan = 0;

    // Summary Node
    private ExtentTest summary;

    @Test
    public void masterValidator() throws Exception {

        summary = extent.createTest("📊 MASTER SITE VALIDATOR (Sitemap + Crawler)");

        ExtentTest test = extent.createTest("Full Validation Process");

        String csvPath = "src/test/resources/websiteurl.csv";
        String baseUrl = readUrl(csvPath);

        summary.info("🌐 Base URL → " + baseUrl);

        // ================================
        // 🚀 Step 1 — Load Sitemap.xml
        // ================================
        String sitemapUrl = baseUrl + (baseUrl.endsWith("/") ? "" : "/") + "sitemap.xml";

        test.info("📖 Checking sitemap at: " + sitemapUrl);

        try {
            sitemapUrls = parseSitemap(sitemapUrl);
            test.pass("📌 Sitemap URLs Found: " + sitemapUrls.size());
        } catch (Exception e) {
            test.warning("⚠ No sitemap.xml found. Skipping sitemap phase.");
        }

        // ================================
        // 🚀 Step 2 — Crawl Website
        // ================================
        test.info("🔍 Starting crawler…");

        crawlQueue.add(baseUrl);

        while (!crawlQueue.isEmpty()) {
            String url = crawlQueue.poll();

            if (crawlerUrls.contains(url)) continue;
            crawlerUrls.add(url);

            if (shouldSkipUrl(url)) {
                skipped++;
                continue;
            }

            int status = getStatus(url);
            if (status == 200) test.pass("🟢 " + url);
            else test.fail("🔴 " + url + " → " + reason(status));

            // Extract links
            try {
                driver.get(url);
                waitForLoad();

                List<WebElement> links = driver.findElements(By.xpath("//a[@href]"));

                for (WebElement a : links) {
                    String u = a.getAttribute("href");

                    if (isInternal(u, baseUrl) && !crawlerUrls.contains(u)) {
                        crawlQueue.add(u);
                    }
                }

            } catch (Exception ignore) {}
        }

        test.info("🧭 Crawler Found Pages: " + crawlerUrls.size());

        // ================================
        // 🚀 Step 3 — Merge Sitemap + Crawler
        // ================================
        mergedUrls.addAll(sitemapUrls);
        mergedUrls.addAll(crawlerUrls);

        test.info("📌 Total Unique Merged Pages: " + mergedUrls.size());

        // ================================
        // 🚀 Step 4 — Validate All Pages
        // ================================
        for (String url : mergedUrls) {

            if (shouldSkipUrl(url)) {
                skipped++;
                continue;
            }

            int code = getStatus(url);

            if (code == 200) {
                working++;
            } else {
                broken++;
            }

            // Orphan Pages (in sitemap but crawler did NOT find)
            if (sitemapUrls.contains(url) && !crawlerUrls.contains(url)) {
                orphan++;
            }
        }

        // ================================
        // 🚀 Step 5 — SUMMARY
        // ================================
        summary.info("====================================");
        summary.info("📊 FINAL SUMMARY");
        summary.info("====================================");
        summary.info("📌 Sitemap URLs: " + sitemapUrls.size());
        summary.info("📌 Crawler URLs: " + crawlerUrls.size());
        summary.info("📌 Total Unique URLs: " + mergedUrls.size());
        summary.info("------------------------------------");
        summary.info("🟢 Working: " + working);
        summary.info("🔴 Broken: " + broken);
        summary.info("⚠ Warnings: " + warnings);
        summary.info("⏩ Skipped: " + skipped);
        summary.info("🧩 Orphan Pages: " + orphan);
        summary.info("====================================");

        summary.pass("✔ Master Validation Completed");
    }

    // --------------------------
    // SITEMAP PARSER
    // --------------------------
    private Set<String> parseSitemap(String sitemapUrl) throws Exception {
        Set<String> urls = new HashSet<>();

        InputStream input = new URI(sitemapUrl).toURL().openStream();
        Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(input);

        NodeList locNodes = doc.getElementsByTagName("loc");

        for (int i = 0; i < locNodes.getLength(); i++) {
            urls.add(locNodes.item(i).getTextContent().trim());
        }

        return urls;
    }

    // --------------------------
    // HELPERS
    // --------------------------
    private String readUrl(String path) throws Exception {
        BufferedReader br = new BufferedReader(new FileReader(path));
        String u = br.readLine();
        br.close();
        return u.trim();
    }

    private boolean shouldSkipUrl(String url) {
        if (url == null) return true;
        if (url.contains("#")) return true;
        if (url.contains("[") || url.contains("]")) return true;
        if (url.contains("%22")) return true;
        if (url.contains("AUD_BRAND")) return true;
        return false;
    }

    private boolean isInternal(String url, String base) {
        if (url == null || url.isBlank()) return false;
        return url.startsWith(base);
    }

    private int getStatus(String url) {
        try {
            URI uri = URI.create(url);
            HttpURLConnection http = (HttpURLConnection) uri.toURL().openConnection();
            http.setRequestMethod("GET");
            http.setConnectTimeout(6000);
            http.setReadTimeout(6000);
            return http.getResponseCode();
        } catch (Exception e) {
            return -1;
        }
    }

    private String reason(int status) {
        return switch (status) {
            case 200 -> "OK";
            case 404 -> "Not Found";
            case 500 -> "Server Error";
            case 403 -> "Forbidden";
            case -1 -> "Connection Failed / Blocked";
            default -> "Unknown Error";
        };
    }

    private void waitForLoad() {
        try {
            new WebDriverWait(driver, Duration.ofSeconds(5))
                    .until(ExpectedConditions.presenceOfElementLocated(By.tagName("body")));
        } catch (Exception ignored) {}
    }
}
