package tests;

import com.aventstack.extentreports.*;
import org.jsoup.*;
import org.jsoup.nodes.*;

import org.testng.annotations.Test;

import java.io.BufferedReader;
import java.io.FileReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.util.*;

public class BrokenLinkScannerTest extends BaseTest {

    private Set<String> visitedPages = new HashSet<>();
    private Queue<String> pageQueue = new LinkedList<>();

    private Set<String> allLinks = new HashSet<>();
    private Set<String> brokenLinks = new HashSet<>();
    private Set<String> workingLinks = new HashSet<>();

    @Test
    public void brokenLinkScanner() throws Exception {

        ExtentTest summary = extent.createTest("📊 Broken Link Scanner Summary");
        ExtentTest test = extent.createTest("🔎 Broken Link Scanner – Full Website Audit");

        // Load base URL
        String baseUrl = readUrlFromCSV("src/test/resources/urls.csv");
        summary.info("🌍 Base URL: " + baseUrl);

        pageQueue.add(baseUrl);

        // ===========================================
        // 🚀 STEP 1 — Crawl All Pages via Jsoup (HTML only)
        // ===========================================
        while (!pageQueue.isEmpty()) {

            String currentPage = pageQueue.poll();
            if (visitedPages.contains(currentPage)) continue;

            visitedPages.add(currentPage);

            try {
                Document doc = Jsoup.connect(currentPage)
                        .timeout(6000)
                        .userAgent("Mozilla/5.0")
                        .ignoreContentType(true)
                        .get();

                test.info("🟢 Page Loaded Successfully: " + currentPage);

                extractHtmlLinks(test, doc, baseUrl);

            } catch (Exception e) {
                test.warning("⚠ Could not load HTML page: " + currentPage);
            }
        }

        summary.info("📘 Total Pages Crawled: " + visitedPages.size());
        summary.info("🔗 Total Unique URLs Found: " + allLinks.size());

        // ===========================================
        // 🚀 STEP 2 — VALIDATE ALL LINKS (HTTP only)
        // ===========================================
        test.info("⏳ Validating all links…");

        for (String url : allLinks) {

            int status = getStatus(url);

            if (status == 200) {
                workingLinks.add(url);
            } else {
                brokenLinks.add(url);
            }
        }

        // ===========================================
        // 🚀 STEP 3 — CLEAN SIMPLE SUMMARY
        // ===========================================
        summary.info("=====================================");
        summary.info("🔗 BROKEN LINK SUMMARY");
        summary.info("=====================================");
        summary.info("🔸 Total Links Found: " + allLinks.size());
        summary.info("🟢 Working Links: " + workingLinks.size());
        summary.info("🔴 Broken Links: " + brokenLinks.size());
        summary.pass("✔ Broken Link Scan Completed Successfully");
    }

    // =======================================================
    // Extract ONLY HTML links using Jsoup
    // =======================================================
    private void extractHtmlLinks(ExtentTest test, Document doc, String baseUrl) {

        // 1. Anchor tags
        for (Element a : doc.select("a[href]")) {
            processUrl(a.absUrl("href"), baseUrl);
        }

        // 2. Images
        for (Element img : doc.select("img[src]")) {
            processUrl(img.absUrl("src"), baseUrl);
        }

        // 3. CSS Files
        for (Element css : doc.select("link[href]")) {
            processUrl(css.absUrl("href"), baseUrl);
        }

        // 4. JS Files
        for (Element js : doc.select("script[src]")) {
            processUrl(js.absUrl("src"), baseUrl);
        }
    }

    // =======================================================
    // Add links + schedule internal pages for crawling
    // =======================================================
    private void processUrl(String url, String baseUrl) {

        if (url == null || url.isBlank()) return;

        // Remove # anchors
        if (url.contains("#")) url = url.substring(0, url.indexOf("#"));

        allLinks.add(url);

        // Crawl internal pages only
        if (url.startsWith(baseUrl) &&
                !visitedPages.contains(url) &&
                (url.endsWith("/") || url.endsWith(".html"))) {

            pageQueue.add(url);
        }
    }

    // =======================================================
    // HTTP STATUS CHECK
    // =======================================================
    private int getStatus(String url) {
        try {
            HttpURLConnection http = (HttpURLConnection)
                    new URI(url).toURL().openConnection();

            http.setRequestMethod("HEAD"); // FASTEST
            http.setConnectTimeout(5000);
            http.setReadTimeout(5000);
            http.connect();

            return http.getResponseCode();
        } catch (Exception e) {
            return -1;  // Timeout or connection refused
        }
    }

    private String readUrlFromCSV(String filePath) throws Exception {
        BufferedReader br = new BufferedReader(new FileReader(filePath));
        String url = br.readLine();
        br.close();
        return url.trim();
    }
}
