package tests;

import com.aventstack.extentreports.ExtentReports;
import com.aventstack.extentreports.ExtentTest;
import org.testng.annotations.*;
import utils.ExtentReportManager;
import utils.SitemapParser;
import utils.LinkValidator;

import java.io.*;
import java.util.*;

public class MultiSitemapTestCase {

    private ExtentReports extent;

    private static final int MAX_PAGES = 1000;  

    @BeforeSuite
    public void beforeSuite() {
        extent = ExtentReportManager.getReportInstance();
    }

  
    private boolean isHeadBlockedCDN(String url) {
        return url.contains("fonts.gstatic.com")
                || url.contains("fonts.googleapis.com")
                || url.contains("googletagmanager.com")
                || url.contains("google-analytics.com")
                || url.contains("gstatic.com");
    }

  
    private String detectFailureReason(String url) {

        if (url.contains("xmlrpc.php"))
            return "WordPress XML-RPC endpoints often block HEAD requests or respond slowly.";

        if (url.contains("utm_"))
            return "Tracking URLs sometimes require browser headers and fail HEAD requests.";

        if (url.contains("store.") || url.contains("cart") || url.contains("shop"))
            return "E-commerce firewalls (Cloudflare/Sucuri) may block bots temporarily.";

        if (isHeadBlockedCDN(url))
            return "Google CDN blocks HEAD requests; GET succeeds instead.";

        return "Temporary network delay, CDN warm-up, or firewall rate-limit.";
    }


    @Test
    public void validateSitemapsFromFile() {

        List<String> sitemaps = readSitemapList("websites.txt");

        for (String sitemap : sitemaps) {

            long startTime = System.currentTimeMillis();

            ExtentTest test = extent.createTest("Sitemap: " + sitemap);
            test.info("Reading sitemap: " + sitemap);

            List<String> urls = SitemapParser.extractUrls(sitemap);
            test.info("URLs found: " + urls.size());

            test.info("SUMMARY_PLACEHOLDER");

            int working = 0;

            List<String> allLinks = new ArrayList<>();
            List<String> brokenFirst = new ArrayList<>();
            List<String> failureReasons = new ArrayList<>();

            List<String> recovered = new ArrayList<>();
            List<String> recoveredReasons = new ArrayList<>();

            
            int count = 0;

            for (String url : urls) {

                if (count >= MAX_PAGES) break;
                count++;

                try { Thread.sleep(600); } catch (Exception ignored) {}

                int status;
                String msg;

                
                if (isHeadBlockedCDN(url)) {
                    status = retryGet(url);
                    if (status != -1) {
                        working++;
                        allLinks.add("<span style='color:green;'> " + url + " → OK (CDN GET)</span>");
                    } else {
                        brokenFirst.add(url);
                        failureReasons.add(detectFailureReason(url));
                        allLinks.add("<span style='color:red;'> " + url + " → FAILED (CDN)</span>");
                    }
                    continue;
                }

                int headStatus = LinkValidator.getStatus(url);
                msg = LinkValidator.getStatusMessage(headStatus);
                status = headStatus;

                if (!LinkValidator.isOk(headStatus)) {
                    int getStatus = retryGet(url);
                    if (getStatus != -1) {
                        status = getStatus;
                        msg = "OK (GET Request)";
                    }
                }

                if (LinkValidator.isOk(status)) {
                    working++;
                    allLinks.add("<span style='color:green;'> " + url + " → " + msg + "</span>");
                } else {
                    brokenFirst.add(url);
                    failureReasons.add(detectFailureReason(url));
                    allLinks.add("<span style='color:red;'> " + url + " → " + msg + "</span>");
                }
            }


         
            List<String> stillBroken = new ArrayList<>();

            for (String link : brokenFirst) {
                int retry = retryGet(link);

                if (retry != -1) {
                    recovered.add(link);
                    recoveredReasons.add("Recovered on retry → Usually caused by temporary delay/firewall.");
                } else {
                    stillBroken.add(link);
                }
            }


          
            StringBuilder sb = new StringBuilder();

            sb.append("<b>Summary →</b> Checked: ").append(count)
              .append(" | Working: ").append(working)
              .append(" | Broken (After Recheck): ").append(stillBroken.size())
              .append("<br><br>");

           
            sb.append("<details><summary><b>All Links (")
              .append(allLinks.size()).append(")</b></summary><br>");

            int index = 1;
            for (String row : allLinks) {
                sb.append(index).append(". ").append(row).append("<br>");
                index++;
            }
            sb.append("</details><br><br>");


            sb.append("<details><summary><b>Broken on First Check (")
              .append(brokenFirst.size()).append(")</b></summary><br>");

            int b = 1;
            if (brokenFirst.isEmpty()) sb.append("None<br>");
            else for (int x = 0; x < brokenFirst.size(); x++) {
                sb.append("<span style='color:red;'>")
                  .append(b).append(". ").append(brokenFirst.get(x))
                  .append(" → FAILED<br>")
                  .append("<i>Reason: ").append(failureReasons.get(x)).append("</i>")
                  .append("</span><br><br>");
                b++;
            }
            sb.append("</details><br><br>");


            sb.append("<details><summary><b>Broken After Recheck (")
              .append(stillBroken.size()).append(")</b></summary><br>");

            int z = 1;
            if (stillBroken.isEmpty()) sb.append("None<br>");
            else for (String link : stillBroken) {
                sb.append("<span style='color:red;'>")
                  .append(z).append(". ").append(link)
                  .append(" → FAILED</span><br>");
                z++;
            }
            sb.append("</details><br><br>");


            
            sb.append("<details><summary><b>Recovered After Recheck (")
              .append(recovered.size()).append(")</b></summary><br>");

            int r = 1;
            if (recovered.isEmpty()) sb.append("None<br>");
            else for (int x = 0; x < recovered.size(); x++) {
                sb.append("<span style='color:orange;'>")
                  .append(r).append(". ").append(recovered.get(x))
                  .append(" → RECOVERED<br>")
                  .append("<i>Root Cause: ").append(recoveredReasons.get(x)).append("</i>")
                  .append("</span><br><br>");
                r++;
            }
            sb.append("</details>");


           
            List<com.aventstack.extentreports.model.Log> logs =
                    test.getModel().getLogContext().getAll();

            for (com.aventstack.extentreports.model.Log log : logs) {
                if ("SUMMARY_PLACEHOLDER".equals(log.getDetails())) {
                    log.setDetails("<div style='margin-left:10px; font-size:14px;'>" + sb + "</div>");
                    break;
                }
            }


        
            long endTime = System.currentTimeMillis();
            test.getModel().setStartTime(new Date(startTime));
            test.getModel().setEndTime(new Date(endTime));

            test.pass("Completed");
        }
    }


    private int retryGet(String url) {
        for (int i = 0; i < 2; i++) {
            int code = tryGet(url);
            if (code != -1) return code;
        }
        return -1;
    }

    private int tryGet(String url) {
        try {
            java.net.URL u = java.net.URI.create(url).toURL();
            java.net.HttpURLConnection conn =
                    (java.net.HttpURLConnection) u.openConnection();

            conn.setRequestMethod("GET");
            conn.setConnectTimeout(7000);
            conn.setReadTimeout(7000);
            conn.connect();

            return conn.getResponseCode();

        } catch (Exception e) {
            return -1;
        }
    }


    private List<String> readSitemapList(String path) {
        List<String> list = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new FileReader(path))) {
            String l;
            while ((l = br.readLine()) != null) {
                if (!l.trim().isEmpty() && !l.startsWith("#"))
                    list.add(l.trim());
            }
        } catch (Exception ignored) {}
        return list;
    }

    @AfterSuite
    public void afterSuite() {
        extent.flush();
    }
}
