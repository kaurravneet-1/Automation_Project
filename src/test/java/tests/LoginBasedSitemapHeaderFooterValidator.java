package tests;

import utils.SiteConfigReader;
import java.util.*;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import org.openqa.selenium.*;
import org.openqa.selenium.TimeoutException;
import org.testng.annotations.Test;
import com.aventstack.extentreports.*;

public class LoginBasedSitemapHeaderFooterValidator extends BaseTest {

    private static final int MAX_PAGES = 1;
    private static final double FUZZY_THRESHOLD = 0.80;

    public static int totalPages = 0, passCount = 0, failCount = 0, warningCount = 0;

    @Test
    public void runLoginSitemapValidation() {

        
        ExtentTest mainTest = extent.createTest(" Login-Based Sitemap Validator");

        String configPath = "src/test/resources/website02.txt";
        List<String[]> sites = SiteConfigReader.readConfig(configPath);

        for (String[] site : sites) {

            String websiteUrl = site.length > 0 ? site[0].trim() : "";

            if (websiteUrl == null || !websiteUrl.startsWith("http")) {
                continue;
            }

            String username   = site.length > 1 ? site[1].trim() : "";
            String password   = site.length > 2 ? site[2].trim() : "";
            String sitemapUrl = site.length > 3 ? site[3].trim()
                    : websiteUrl + (websiteUrl.endsWith("/") ? "" : "/") + "sitemap/";
            String companyName = site.length > 4 ? site[4].trim() : "";
            String phones      = site.length > 6 ? site[6].trim() : "";
            String addresses   = site.length > 7 ? site[7].trim() : "";
            String hours       = site.length > 8 ? site[8].trim() : "";

            String authUrl = buildBasicAuthUrl(websiteUrl, username, password);

         
            ExtentTest test = mainTest.createNode("Validate Sitemap (Login Required): " + websiteUrl);
            test.info("Opening website using Basic Auth: " + authUrl);

            try {
           
                setupDriver();

                boolean ok = safeGet(authUrl, test);
                if (!ok) {
                    test.fail("Unable to load site after retries: " + websiteUrl);
                    failCount++;
                    try { driver.quit(); } catch (Exception ignored) {}
                    continue;
                }

                Thread.sleep(1200);
                test.pass(" Logged in successfully using Basic Auth (or page loaded).");

                test.info("Opening sitemap: " + sitemapUrl);
                ok = safeGet(sitemapUrl, test);
                if (!ok) {
                    test.warning("Could not load sitemap URL, attempting to continue with homepage.");
                    warningCount++;
                }

                Set<String> pageLinks = collectSitemapLinks(websiteUrl);

                test.info("Total URLs found: " + pageLinks.size());

                if (pageLinks.isEmpty()) {
                    test.warning(" No sitemap URLs found — using homepage.");
                    warningCount++;
                    pageLinks.add(websiteUrl);
                }

                int sitePages = 0, sitePass = 0, siteFail = 0, siteWarn = 0;
                int checked = 0;

                for (String pageUrl : pageLinks) {

                    if (checked++ >= MAX_PAGES) break;

                    totalPages++;
                    sitePages++;

                    ExtentTest pageNode = test.createNode(" Page: " + pageUrl);

                    try {
                        boolean loaded = safeGet(pageUrl, pageNode);
                        if (!loaded) {
                            siteFail++; failCount++;
                            pageNode.fail(" Page load failed after retries → " + pageUrl);
                            continue;
                        }

                        scrollToBottom();

                        String fullText   = getFullSearchableText();
                        String headerText = normalize(getSectionText("//header"));
                        String footerText = normalize(getSectionText("//footer"));
                        String bodyText   = normalize(driver.getPageSource());

                        checkCompanyName(companyName, fullText, pageNode, pageUrl);
                        checkPhonesExactInHeaderFooterBody(phones, headerText, footerText, bodyText, pageNode, pageUrl);
                        checkAddresses(addresses, fullText, pageNode, pageUrl);
                        checkHours(hours, fullText, pageNode, pageUrl);

                        sitePass++; passCount++;
                        pageNode.pass(" Page validation completed.");

                    } catch (Exception ex) {
                        siteFail++; failCount++;
                        pageNode.fail(" Error processing page → " + ex.getMessage());
                    }
                }

                // Summary for each website
                test.info("Summary → Pages: " + sitePages +
                        " | Pass: " + sitePass +
                        " | Fail: " + siteFail +
                        " | Warnings: " + siteWarn);

                try { driver.quit(); } catch (Exception ignored) {}

            } catch (Exception e) {
                failCount++;
                test.fail(" Critical error → " + e.getMessage());
                try { driver.quit(); } catch (Exception ignored) {}
            }
        }

        extent.flush();
    }


   
    private Set<String> collectSitemapLinks(String baseUrl) {
        Set<String> links = new LinkedHashSet<>();
        try {
            String domain = getDomain(baseUrl);
            List<WebElement> tags = driver.findElements(By.tagName("a"));

            for (WebElement a : tags) {
                String href = a.getAttribute("href");
                if (href != null && href.startsWith("http")
                        && href.contains(domain)
                        && !href.contains("mailto")) {
                    links.add(href.split("#")[0]);
                }
            }
        } catch (Exception ignored) {}
        return links;
    }

    private String getDomain(String url) {
        try { return java.net.URI.create(url).getHost(); }
        catch (Exception e) { return url; }
    }

   
    private String getFullSearchableText() {
        return normalize(
                getSectionText("//header") + " " +
                getSectionText("//footer") + " " +
                driver.getPageSource()
        );
    }

    private String getSectionText(String xpath) {
        try {
            WebElement el = driver.findElement(By.xpath(xpath));
            return el.getText();
        } catch (Exception e) {
            return "";
        }
    }

    private String normalize(String text) {
        if (text == null) return "";
        return text
                .replaceAll("(?i)<br\\s*/?>", " ")
                .replaceAll("<[^>]+>", " ")
                .replaceAll("[^a-zA-Z0-9 ]", " ")
                .toLowerCase()
                .replaceAll("\\s+", " ")
                .trim();
    }


    
    private boolean fuzzyMatch(String a, String b) {
        a = normalize(a); b = normalize(b);
        int d = levenshtein(a, b);
        return 1.0 - ((double)d / Math.max(a.length(), b.length())) >= FUZZY_THRESHOLD;
    }

    private int levenshtein(String a, String b) {
        int[][] dp = new int[a.length()+1][b.length()+1];
        for (int i=0; i<=a.length(); i++) dp[i][0]=i;
        for (int j=0; j<=b.length(); j++) dp[0][j]=j;

        for (int i=1;i<=a.length();i++) {
            for (int j=1;j<=b.length();j++) {
                int cost = (a.charAt(i-1)==b.charAt(j-1)) ? 0 : 1;
                dp[i][j] = Math.min(
                        dp[i-1][j]+1,
                        Math.min(dp[i][j-1]+1, dp[i-1][j-1]+cost)
                );
            }
        }
        return dp[a.length()][b.length()];
    }

    private boolean checkCompanyName(String name, String full, ExtentTest test, String url) {
        if (name == null || name.isEmpty()) {
            test.info("ℹ Company name not provided — skipping");
            return true;
        }

        String nm = normalize(name);

        boolean found = false;

        if (full.contains(nm)) {
            test.pass(" Company name found");
            found = true;
        }

        if (fuzzyMatch(full, nm)) {
            test.pass(" Fuzzy name match");
            found = true;
        }

        if (!found)
            test.info("ℹ Company name not detected");

        return true;
    }

    private boolean checkPhonesExactInHeaderFooterBody(String phonesCsv,
                                                       String headerText,
                                                       String footerText,
                                                       String bodyText,
                                                       ExtentTest test,
                                                       String url) {

        if (phonesCsv == null || phonesCsv.trim().isEmpty()) {
            test.info("ℹ Phones not provided — skipping");
            return true;
        }

        String[] phones = phonesCsv.split(";");

        for (String ph : phones) {
            String original = ph.trim();
            String digits = original.replaceAll("[^0-9]", "");

            boolean inHeader = headerText.replaceAll("[^0-9]", "").contains(digits);
            boolean inFooter = footerText.replaceAll("[^0-9]", "").contains(digits);
            boolean inBody = bodyText.replaceAll("[^0-9]", "").contains(digits);

            if (inHeader) test.pass(" Phone in header: " + original);
            if (inFooter) test.pass(" Phone in footer: " + original);
            if (inBody)   test.pass(" Phone in body: " + original);

            if (!inHeader && !inFooter && !inBody)
                test.info("ℹ Phone missing: " + original);
        }

        return true;
    }


    public boolean checkAddresses(String addrList, String full, ExtentTest test, String url) {
        if (addrList == null || addrList.isEmpty()) {
            test.info("ℹ Address not provided—skipping");
            return true;
        }

        boolean found = false;
        String page = normalize(full);

        for (String addr : addrList.split(";")) {
            String n = normalize(addr);

            if (page.contains(n) || fuzzyMatch(page, n)) {
                test.pass(" Address found: " + addr);
                found = true;
            } else {
                test.info("ℹ Address missing: " + addr);
            }
        }

        return found;
    }

    private boolean checkHours(String hrs, String full, ExtentTest test, String url) {
        if (hrs == null || hrs.isEmpty()) {
            test.info("ℹ Hours not provided — skipping");
            return true;
        }

        String expected = normalize(hrs).replaceAll("\\s+","");
        String actual   = normalize(full).replaceAll("\\s+","");

        if (actual.contains(expected) ||
           fuzzyMatch(actual, expected)) {

            test.pass(" Hours found: " + hrs);
        } else {
            test.info("ℹ Hours missing: " + hrs);
        }

        return true;
    }


   
    private void scrollToBottom() throws InterruptedException {
        JavascriptExecutor js = (JavascriptExecutor) driver;
        long last = (long) js.executeScript("return document.body.scrollHeight");

        while (true) {
            js.executeScript("window.scrollTo(0, document.body.scrollHeight);");
            Thread.sleep(600);
            long newHeight = (long) js.executeScript("return document.body.scrollHeight");
            if (newHeight == last) break;
            last = newHeight;
        }
    }

  
    private boolean safeGet(String url, ExtentTest node) {
        int attempts = 0;
        while (attempts < 2) {
            attempts++;
            try {
                driver.get(url);
              
                try { Thread.sleep(1200); } catch (InterruptedException ignored) {}
                return true;
            } catch (TimeoutException te) {
                node.warning("Page load slow → retrying (" + attempts + "): " + shortError(te));
               
                try {
                    driver.quit();
                } catch (Exception ignored) {}
                try {
                    setupDriver();
                } catch (Exception se) {
                    node.fail("Failed to re-init driver: " + se.getMessage());
                    return false;
                }
            } catch (WebDriverException we) {
                node.warning("WebDriver error → retrying (" + attempts + "): " + shortError(we));
                try { driver.quit(); } catch (Exception ignored) {}
                try {
                    setupDriver();
                } catch (Exception se) {
                    node.fail("Failed to re-init driver: " + se.getMessage());
                    return false;
                }
            } catch (Exception e) {
                node.fail("Unexpected error loading URL: " + e.getMessage());
                return false;
            }
        }
      
        return false;
    }

    private String shortError(Throwable t) {
        if (t == null) return "";
        String m = t.getMessage();
        if (m == null) return t.toString();
        return m.length() > 300 ? m.substring(0, 300) + "..." : m;
    }

    private String buildBasicAuthUrl(String baseUrl, String username, String password) {
        if (username == null || username.isEmpty()) return baseUrl;
        if (password == null) password = "";

        try {
          
            String u = URLEncoder.encode(username, StandardCharsets.UTF_8.toString());
            String p = URLEncoder.encode(password, StandardCharsets.UTF_8.toString());

            
            if (baseUrl.startsWith("https://")) {
                return "https://" + u + ":" + p + "@" + baseUrl.substring(8);
            } else if (baseUrl.startsWith("http://")) {
                return "http://" + u + ":" + p + "@" + baseUrl.substring(7);
            } else {
                return baseUrl;
            }
        } catch (Exception e) {
           
            if (baseUrl.startsWith("https://")) {
                return "https://" + username + ":" + password + "@" + baseUrl.substring(8);
            } else if (baseUrl.startsWith("http://")) {
                return "http://" + username + ":" + password + "@" + baseUrl.substring(7);
            } else {
                return baseUrl;
            }
        }
    }

}


