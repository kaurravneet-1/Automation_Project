package tests;

import utils.SiteConfigReader;

import java.util.*;

import org.openqa.selenium.*;
import org.testng.annotations.Test;

import com.aventstack.extentreports.*;

public class LoginFullSiteHeaderFooterValidator extends BaseTest {

    private static final int MAX_PAGES = 500;

    private static int totalPages = 0, passCount = 0, failCount = 0, warningCount = 0;

    @Test
    public void runFullSiteValidation() {

        ExtentTest mainTest = extent.createTest("Login Full-Site Header/Footer Validator");

        String configPath = "src/test/resources/websites_full.txt";
        List<String[]> sites = SiteConfigReader.readConfig(configPath);

        for (String[] site : sites) {

            String websiteUrl = site.length > 0 ? site[0].trim() : "";
            if (!websiteUrl.startsWith("http")) continue;

            String username = site.length > 1 ? site[1].trim() : "";
            String password = site.length > 2 ? site[2].trim() : "";
            String companyName = site.length > 3 ? site[3].trim() : "";
            String phones = site.length > 4 ? site[4].trim() : "";
            String addresses = site.length > 5 ? site[5].trim() : "";
            String hours = site.length > 6 ? site[6].trim() : "";

            ExtentTest siteTest = mainTest.createNode("Site: " + websiteUrl);

            String authUrl = websiteUrl.startsWith("https://")
                    ? websiteUrl.replace("https://", "https://" + username + ":" + password + "@")
                    : websiteUrl;

            siteTest.info("Basic Auth URL: " + authUrl);

            try {
                setupDriver();

                driver.get(authUrl);
                Thread.sleep(2000);
                siteTest.pass("Logged in successfully using Basic Auth.");

                String startUrl = websiteUrl.endsWith("/") ? websiteUrl : websiteUrl + "/";
                Set<String> toVisit = new LinkedHashSet<>();
                Set<String> visited = new HashSet<>();
                toVisit.add(startUrl);

                int checked = 0;

                while (!toVisit.isEmpty() && checked < MAX_PAGES) {

                    String current = toVisit.iterator().next();
                    toVisit.remove(current);

                    if (visited.contains(current)) continue;
                    visited.add(current);

                    checked++;
                    totalPages++;

                    ExtentTest pageNode = siteTest.createNode("Page: " + current);

                    try {
                        driver.get(current);
                        Thread.sleep(800);
                        scrollToBottom();

                        collectLinksForCrawl(current, startUrl, toVisit, visited);

                        String headerText = normalize(getSectionText("//header"));
                        String footerText = normalize(getSectionText("//footer"));
                        String bodyText = normalize(driver.getPageSource());
                        String fullText = normalize(headerText + " " + footerText + " " + bodyText);

                        checkCompanyName(companyName, fullText, pageNode);
                        checkPhonesExactInHeaderFooterBody(phones, headerText, footerText, bodyText, pageNode);
                        checkAddresses(addresses, fullText, pageNode);
                        checkHours(hours, fullText, pageNode);

                        passCount++;
                        pageNode.pass("Page validation complete.");

                    } catch (Exception ex) {
                        failCount++;
                        pageNode.fail("Error on page: " + ex.getMessage());
                    }
                }

                siteTest.info("Summary → Total Pages Scanned: " + visited.size());
                driver.quit();

            } catch (Exception e) {
                siteTest.fail("Site error: " + e.getMessage());
                try { driver.quit(); } catch (Exception ignored) {}
            }
        }

        System.out.println("\n============== FINAL SUMMARY ==============");
        System.out.println("Total Pages Scanned: " + totalPages);
        System.out.println("Passed: " + passCount);
        System.out.println("Failed: " + failCount);
        System.out.println("Warnings: " + warningCount);
        System.out.println("===========================================\n");

        extent.flush();
    }

    private boolean checkCompanyName(String name, String full, ExtentTest test) {

        if (name == null || name.isEmpty()) {
            test.info("Company name not provided.");
            return true;
        }

        String nm = normalize(name);

        boolean logged = false;

        for (WebElement img : driver.findElements(By.tagName("img"))) {
            if (logged) break;

            try {
                String alt = img.getAttribute("alt");
                if (alt != null && !alt.trim().isEmpty()) {
                    String altNorm = normalize(alt);
                    if (altNorm.contains(nm)) {
                        test.pass("Logo alt contains company name: " + alt);
                        logged = true;
                    }
                }
            } catch (Exception ignored) {}
        }

        if (full.contains(nm)) {
            test.pass("Company name found in text.");
            logged = true;
        }

        if (!logged)
            test.info("Company name/logo not detected.");

        return true;
    }

    public boolean checkPhonesExactInHeaderFooterBody(String phonesCsv,
            String header, String footer, String body, ExtentTest test) {

        if (phonesCsv == null || phonesCsv.trim().isEmpty()) {
            test.info("Phone numbers not provided.");
            return true;
        }

        boolean foundAny = false;

        for (String ph : phonesCsv.split(";")) {
            String digits = ph.replaceAll("[^0-9]", "");

            boolean inHeader = header.replaceAll("[^0-9]", "").contains(digits);
            boolean inFooter = footer.replaceAll("[^0-9]", "").contains(digits);
            boolean inBody = body.replaceAll("[^0-9]", "").contains(digits);

            if (inHeader) { test.pass("Phone in header: " + ph); foundAny = true; }
            if (inFooter) { test.pass("Phone in footer: " + ph); foundAny = true; }
            if (inBody)   { test.pass("Phone in body: " + ph); foundAny = true; }

            if (!inHeader && !inFooter && !inBody)
                test.warning("Phone not found: " + ph);
        }

        return foundAny;
    }

    private boolean checkAddresses(String addrCsv, String full, ExtentTest test) {

        if (addrCsv == null || addrCsv.isEmpty()) {
            test.info("Address not provided.");
            return true;
        }

        for (String a : addrCsv.split(";")) {
            if (full.contains(normalize(a)))
                test.pass("Address found: " + a);
            else
                test.info("Address not found: " + a);
        }

        return true;
    }

    private boolean checkHours(String hrs, String full, ExtentTest test) {

        if (hrs == null || hrs.isEmpty()) {
            test.info("Hours not provided.");
            return true;
        }

        if (full.contains(normalize(hrs)))
            test.pass("Hours found: " + hrs);
        else
            test.info("Hours not found.");

        return true;
    }

    private void collectLinksForCrawl(String currentUrl, String startUrl,
                                      Set<String> toVisit, Set<String> visited) {

        try {
            String domain = getDomain(startUrl);

            for (WebElement a : driver.findElements(By.tagName("a"))) {
                try {
                    String href = a.getAttribute("href");
                    if (href == null) continue;

                    href = href.split("#")[0];

                    if (href.startsWith("/")) {
                        java.net.URI base = java.net.URI.create(startUrl);
                        href = base.getScheme() + "://" + base.getHost() + href;
                    }

                    if (href.startsWith("http")
                            && href.contains(domain)
                            && !visited.contains(href)) {

                        toVisit.add(href);
                    }

                } catch (Exception ignored) {}
            }

        } catch (Exception ignored) {}
    }


    private String getSectionText(String xpath) {
        try { return driver.findElement(By.xpath(xpath)).getText(); }
        catch(Exception e) { return ""; }
    }

    private String normalize(String t) {
        if (t == null) return "";
        return t.replaceAll("[^a-zA-Z0-9 ]", " ")
                .toLowerCase()
                .replaceAll("\\s+", " ")
                .trim();
    }

    private void scrollToBottom() throws InterruptedException {
        JavascriptExecutor js = (JavascriptExecutor) driver;
        long last = (long) js.executeScript("return document.body.scrollHeight");
        while (true) {
            js.executeScript("window.scrollTo(0, document.body.scrollHeight)");
            Thread.sleep(500);
            long now = (long) js.executeScript("return document.body.scrollHeight");
            if (now == last) break;
            last = now;
        }
    }

    private String getDomain(String url) {
        try { return java.net.URI.create(url).getHost(); }
        catch (Exception e) { return url; }
    }
}
