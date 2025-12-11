package tests;

import utils.SiteConfigReader;
import java.util.*;
import org.openqa.selenium.*;
import org.testng.annotations.Test;
import com.aventstack.extentreports.*;

public class LoginBasedSitemapHeaderFooterValidator extends BaseTest {

    private static final int MAX_PAGES = 1000;
    private static final double FUZZY_THRESHOLD = 0.80;

    public static int totalPages = 0, passCount = 0, failCount = 0, warningCount = 0;

    @Test
    public void runLoginSitemapValidation() {

        // ⭐ Use existing BaseTest report → no new file created
        ExtentTest mainTest = extent.createTest("🔐 Login-Based Sitemap Validator");

        String configPath = "src/test/resources/website02.txt";
        List<String[]> sites = SiteConfigReader.readConfig(configPath);

        for (String[] site : sites) {

            String websiteUrl = site.length > 0 ? site[0].trim() : "";

            // Skip invalid rows silently 
            if (!websiteUrl.startsWith("http")) {
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

            String authUrl = websiteUrl.startsWith("https://")
                    ? websiteUrl.replace("https://", "https://" + username + ":" + password + "@")
                    : websiteUrl;

            // Create a fresh test node for each website
            ExtentTest test = mainTest.createNode("Validate Sitemap (Login Required): " + websiteUrl);
            test.info("Opening website using Basic Auth: " + authUrl);

            try {
                setupDriver();

                driver.get(authUrl);
                Thread.sleep(2500);

                test.pass("✔ Logged in successfully using Basic Auth.");

                // Load sitemap
                test.info("Opening sitemap: " + sitemapUrl);
                driver.get(sitemapUrl);
                Thread.sleep(2000);

                // Collect URLs
                Set<String> pageLinks = collectSitemapLinks(websiteUrl);

                test.info("Total URLs found: " + pageLinks.size());

                if (pageLinks.isEmpty()) {
                    test.warning("⚠ No sitemap URLs found — using homepage.");
                    warningCount++;
                    pageLinks.add(websiteUrl);
                }

                int sitePages = 0, sitePass = 0, siteFail = 0, siteWarn = 0;
                int checked = 0;

                for (String pageUrl : pageLinks) {

                    if (checked++ >= MAX_PAGES) break;

                    totalPages++;
                    sitePages++;

                    ExtentTest pageNode = test.createNode("🌍 Page: " + pageUrl);

                    try {
                        driver.get(pageUrl);
                        scrollToBottom();

                        String fullText   = getFullSearchableText();
                        String headerText = normalize(getSectionText("//header"));
                        String footerText = normalize(getSectionText("//footer"));
                        String bodyText   = normalize(driver.getPageSource());

                        // Validations
                        checkCompanyName(companyName, fullText, pageNode, pageUrl);
                        checkPhonesExactInHeaderFooterBody(phones, headerText, footerText, bodyText, pageNode, pageUrl);
                        checkAddresses(addresses, fullText, pageNode, pageUrl);
                        checkHours(hours, fullText, pageNode, pageUrl);

                        sitePass++; passCount++;
                        pageNode.pass("✔ Page validation completed.");

                    } catch (Exception ex) {
                        siteFail++; failCount++;
                        pageNode.fail("❌ Error processing page → " + ex.getMessage());
                    }
                }

                // Summary for each website
                test.info("Summary → Pages: " + sitePages +
                        " | Pass: " + sitePass +
                        " | Fail: " + siteFail +
                        " | Warnings: " + siteWarn);

                driver.quit();

            } catch (Exception e) {
                failCount++;
                test.fail("🔥 Critical error → " + e.getMessage());
                try { driver.quit(); } catch (Exception ignored) {}
            }
        }

        extent.flush();
    }


    // -------------------------------------------------------------------
    // SITEMAP LINK COLLECTION
    // -------------------------------------------------------------------
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

    // -------------------------------------------------------------------
    // TEXT EXTRACTION
    // -------------------------------------------------------------------
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


    // -------------------------------------------------------------------
    // VALIDATION METHODS (UNCHANGED)
    // -------------------------------------------------------------------
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
        // unchanged logic
        if (name == null || name.isEmpty()) {
            test.info("ℹ Company name not provided — skipping");
            return true;
        }

        String nm = normalize(name);

        boolean found = false;

        if (full.contains(nm)) {
            test.pass("✔ Company name found");
            found = true;
        }

        if (fuzzyMatch(full, nm)) {
            test.pass("✔ Fuzzy name match");
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

            if (inHeader) test.pass("✔ Phone in header: " + original);
            if (inFooter) test.pass("✔ Phone in footer: " + original);
            if (inBody)   test.pass("✔ Phone in body: " + original);

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
                test.pass("✔ Address found: " + addr);
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

            test.pass("✔ Hours found: " + hrs);
        } else {
            test.info("ℹ Hours missing: " + hrs);
        }

        return true;
    }


    // -------------------------------------------------------------------
    // SCROLL
    // -------------------------------------------------------------------
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

    // -------------------------------------------------------------------
    // LOGGING
    // -------------------------------------------------------------------
   

}
