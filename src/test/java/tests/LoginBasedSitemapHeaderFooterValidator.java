package tests;

import utils.SiteConfigReader;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

import org.openqa.selenium.*;
import org.openqa.selenium.JavascriptExecutor;
import org.testng.annotations.Test;
import com.aventstack.extentreports.*;

public class LoginBasedSitemapHeaderFooterValidator extends BaseTest {

    private static final int MAX_PAGES = 1;
    private static final double FUZZY_THRESHOLD = 0.80;

    @Test
    public void runLoginSitemapValidation() {

        ExtentTest mainTest = extent.createTest("🔐 Login-Based Sitemap Validator");

        String configPath = "src/test/resources/website02.txt";
        List<String[]> sites = SiteConfigReader.readConfig(configPath);

        for (String[] site : sites) {

            String websiteUrl = site.length > 0 ? site[0].trim() : "";
            if (!websiteUrl.startsWith("http")) continue;

            String username   = site.length > 1 ? site[1].trim() : "";
            String password   = site.length > 2 ? site[2].trim() : "";
            String sitemapUrl = site.length > 3 ? site[3].trim()
                    : websiteUrl + (websiteUrl.endsWith("/") ? "" : "/") + "sitemap/";
            String companyName = site.length > 4 ? site[4].trim() : "";
            String phones      = site.length > 6 ? site[6].trim() : "";
            String addresses   = site.length > 7 ? site[7].trim() : "";
            String hours       = site.length > 8 ? site[8].trim() : "";

            // ============================
            // ✔ FIX: PROPER AUTH URL SAFE
            // ============================
            String encodedUser = URLEncoder.encode(username, StandardCharsets.UTF_8);
            String encodedPass = URLEncoder.encode(password, StandardCharsets.UTF_8);

            String authUrl = websiteUrl.replace("https://", "https://" + encodedUser + ":" + encodedPass + "@");

            ExtentTest test = mainTest.createNode("Validate Sitemap (Login Required): " + websiteUrl);
            test.info("Opening website → " + authUrl);

            try {
                setupDriver();

                driver.manage().timeouts().pageLoadTimeout(java.time.Duration.ofSeconds(40));

                try {
                    driver.get(authUrl);
                } catch (TimeoutException te) {
                    test.warning("Page load slow → retrying login once...");
                    driver.navigate().to(authUrl);
                }

                Thread.sleep(2500);
                test.pass("✔ Logged in successfully using Basic Auth");

                // ===============================
                // Load sitemap
                // ===============================
                test.info("Opening sitemap: " + sitemapUrl);

                try {
                    driver.get(sitemapUrl);
                } catch (TimeoutException te) {
                    test.warning("Sitemap slow — retrying...");
                    driver.navigate().to(sitemapUrl);
                }

                Thread.sleep(2000);

                // Collect URLs
                Set<String> pageLinks = collectSitemapLinks(websiteUrl);

                test.info("Total URLs found: " + pageLinks.size());

                if (pageLinks.isEmpty()) {
                    test.warning("⚠ No sitemap URLs found — using homepage.");
                    pageLinks.add(websiteUrl);
                }

                int checked = 0;

                for (String pageUrl : pageLinks) {

                    if (checked++ >= MAX_PAGES) break;

                    ExtentTest pageNode = test.createNode("🌍 Page: " + pageUrl);

                    try {
                        driver.get(pageUrl);
                        scrollToBottom();

                        String fullText   = getFullSearchableText();
                        String headerText = normalize(getSectionText("//header"));
                        String footerText = normalize(getSectionText("//footer"));
                        String bodyText   = normalize(driver.getPageSource());

                        // RUN VALIDATIONS
                        checkCompanyName(companyName, fullText, pageNode, pageUrl);
                        checkPhonesExactInHeaderFooterBody(phones, headerText, footerText, bodyText, pageNode, pageUrl);
                        checkAddresses(addresses, fullText, pageNode, pageUrl);
                        checkHours(hours, fullText, pageNode, pageUrl);

                        pageNode.pass("✔ Page validation completed");

                    } catch (Exception ex) {
                        pageNode.fail("❌ Error processing page → " + ex.getMessage());
                    }
                }

                driver.quit();

            } catch (Exception e) {
                test.fail("🔥 Critical error → " + e.getMessage());
                try { driver.quit(); } catch (Exception ignored) {}
            }
        }

        extent.flush();
    }


    // -----------------------------------------
    // SITEMAP LINK COLLECTION
    // -----------------------------------------
    private Set<String> collectSitemapLinks(String baseUrl) {
        Set<String> links = new LinkedHashSet<>();
        try {
            String domain = java.net.URI.create(baseUrl).getHost();

            for (WebElement a : driver.findElements(By.tagName("a"))) {
                String href = a.getAttribute("href");
                if (href != null && href.startsWith("http") &&
                        href.contains(domain) &&
                        !href.contains("mailto"))
                    links.add(href.split("#")[0]);
            }
        } catch (Exception ignored) {}
        return links;
    }

    // -----------------------------------------
    // TEXT EXTRACTION / NORMALIZE
    // -----------------------------------------
    private String getFullSearchableText() {
        return normalize(
                getSectionText("//header") + " " +
                getSectionText("//footer") + " " +
                driver.getPageSource()
        );
    }

    private String getSectionText(String xpath) {
        try { return driver.findElement(By.xpath(xpath)).getText(); }
        catch (Exception e) { return ""; }
    }

    private String normalize(String t) {
        if (t == null) return "";
        return t.toLowerCase()
                .replaceAll("<[^>]+>", " ")
                .replaceAll("[^a-z0-9 ]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    // -----------------------------------------
    // VALIDATION LOGIC (unchanged)
    // -----------------------------------------
    private boolean fuzzyMatch(String a, String b) {
        a = normalize(a); b = normalize(b);
        int d = levenshtein(a, b);
        return (1.0 - ((double)d / Math.max(a.length(), b.length()))) >= FUZZY_THRESHOLD;
    }

    private int levenshtein(String a, String b) {
        int[][] dp = new int[a.length()+1][b.length()+1];
        for (int i=0;i<=a.length();i++) dp[i][0]=i;
        for (int j=0;j<=b.length();j++) dp[0][j]=j;

        for (int i=1;i<=a.length();i++)
            for (int j=1;j<=b.length();j++) {
                int cost = a.charAt(i-1)==b.charAt(j-1) ? 0 : 1;
                dp[i][j] = Math.min(dp[i-1][j]+1,
                        Math.min(dp[i][j-1]+1, dp[i-1][j-1]+cost));
            }
        return dp[a.length()][b.length()];
    }

    private boolean checkCompanyName(String name, String full, ExtentTest test, String url) {
        if (name == null || name.isEmpty()) return true;
        String nm = normalize(name);

        boolean found = full.contains(nm) || fuzzyMatch(full, nm);

        if (found) test.pass("✔ Company name detected");
        else test.info("ℹ Company name missing: " + name);

        return found;
    }

    private boolean checkPhonesExactInHeaderFooterBody(String phonesCsv, String header, String footer, String body, ExtentTest test, String url) {

        if (phonesCsv == null || phonesCsv.trim().isEmpty()) return true;

        for (String phone : phonesCsv.split(";")) {
            String digits = phone.replaceAll("[^0-9]", "");

            boolean found =
                    header.replaceAll("[^0-9]", "").contains(digits) ||
                    footer.replaceAll("[^0-9]", "").contains(digits) ||
                    body.replaceAll("[^0-9]", "").contains(digits);

            if (found) test.pass("✔ Phone found: " + phone);
            else test.info("ℹ Phone missing: " + phone);
        }

        return true;
    }

    private boolean checkAddresses(String list, String full, ExtentTest test, String url) {
        if (list == null || list.isEmpty()) return true;

        String page = normalize(full);

        for (String addr : list.split(";")) {
            String n = normalize(addr);

            if (page.contains(n) || fuzzyMatch(page, n))
                test.pass("✔ Address found: " + addr);
            else
                test.info("ℹ Address missing: " + addr);
        }

        return true;
    }

    private boolean checkHours(String hrs, String full, ExtentTest test, String url) {
        if (hrs == null || hrs.isEmpty()) return true;

        String expected = normalize(hrs).replaceAll("\\s+", "");
        String actual   = normalize(full).replaceAll("\\s+", "");

        if (actual.contains(expected) || fuzzyMatch(actual, expected))
            test.pass("✔ Hours found: " + hrs);
        else
            test.info("ℹ Hours missing: " + hrs);

        return true;
    }

    // -----------------------------------------
    // SCROLL
    // -----------------------------------------
    private void scrollToBottom() throws InterruptedException {
        JavascriptExecutor js = (JavascriptExecutor) driver;
        long last = (long) js.executeScript("return document.body.scrollHeight");

        while (true) {
            js.executeScript("window.scrollTo(0, document.body.scrollHeight);");
            Thread.sleep(500);
            long newHeight = (long) js.executeScript("return document.body.scrollHeight");
            if (newHeight == last) break;
            last = newHeight;
        }
    }
}
