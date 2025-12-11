package tests;

import com.aventstack.extentreports.ExtentReports;
import com.aventstack.extentreports.ExtentTest;
import com.aventstack.extentreports.Status;
import org.testng.annotations.*;
import utils.ExtentReportManager;
import utils.SitemapParser;
import utils.LinkValidator;

import java.io.*;
import java.util.List;

public class MultiSitemapTestCase {

    private ExtentReports extent;

    @BeforeSuite
    public void beforeSuite() {
        extent = ExtentReportManager.getReportInstance();
    }

    @Test
    public void validateSitemapsFromFile() {
        List<String> sitemaps = readSitemapList("websites.txt");

        for (String sitemap : sitemaps) {

            ExtentTest test = extent.createTest("Sitemap: " + sitemap);
            test.info("Reading sitemap: " + sitemap);

            List<String> urls = SitemapParser.extractUrls(sitemap);
            test.info("URLs found: " + urls.size());

            int working = 0;
            int broken = 0;

            for (String url : urls) {

                // ⭐ WAIT before checking – fixes slow-loading WP pages
                try { Thread.sleep(2000); } catch (Exception ignored) {}

                // ⭐ First try HEAD (existing method)
                int status = LinkValidator.getStatus(url);
                String message = LinkValidator.getStatusMessage(status);

                // ⭐ If HEAD fails → retry with GET request
                if (!LinkValidator.isOk(status)) {

                    int getStatus = tryGetRequest(url);

                    if (getStatus != -1) {
                        status = getStatus;
                        message = "OK (GET Request)";
                    }
                }

                // Reporting logic (NO CHANGES)
                if (LinkValidator.isOk(status)) {
                    working++;
                    test.log(Status.PASS,
                            "<span style='color:green;'>" + url + " → " + message + "</span>");
                } else if (status == -1 || status == -2) {
                    broken++;
                    test.log(Status.WARNING,
                            "<span style='color:orange;'>" + url + " → " + message + "</span>");
                } else {
                    broken++;
                    test.log(Status.FAIL,
                            "<span style='color:red;'>" + url + " → " + message + "</span>");
                }
            }

            // Summary (NO CHANGES)
            test.info("<b>Summary →</b> Total: " + (working + broken)
                    + " | <span style='color:green;'>Working: " + working + "</span>"
                    + " | <span style='color:red;'>Broken: " + broken + "</span>");
        }
    }

    /**
     * ⭐ NEW METHOD ⭐
     * If HEAD fails → perform real GET request to verify URL.
     */
    private int tryGetRequest(String url) {
        try {
            java.net.URL finalUrl = java.net.URI.create(url).toURL();

            java.net.HttpURLConnection conn =
                    (java.net.HttpURLConnection) finalUrl.openConnection();

            conn.setRequestMethod("GET");
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(8000);
            conn.connect();

            return conn.getResponseCode();

        } catch (Exception e) {
            return -1;
        }
    }


    @AfterSuite
    public void afterSuite() {
        extent.flush();
        System.out.println("✅ Report generated successfully. Check the /reports folder.");
    }

    private List<String> readSitemapList(String path) {
        java.util.List<String> lines = new java.util.ArrayList<>();
        try (BufferedReader br = new BufferedReader(new FileReader(path))) {
            String l;
            while ((l = br.readLine()) != null) {
                l = l.trim();
                if (!l.isEmpty() && !l.startsWith("#")) lines.add(l);
            }
        } catch (Exception e) {
            System.out.println("⚠️ Could not read " + path + ": " + e.getMessage());
        }
        return lines;
    }
}
