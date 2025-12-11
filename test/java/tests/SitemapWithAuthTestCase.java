package tests;

import com.aventstack.extentreports.*;
import org.testng.annotations.*;
import utils.*;
import java.io.*;
import java.util.*;
import java.net.http.*;
import java.net.URI;
import java.util.Base64;

public class SitemapWithAuthTestCase {

    private ExtentReports extent;
    private ExtentTest test;

    @BeforeSuite
    public void beforeSuite() {
        extent = ExtentReportManager.getReportInstance();
    }

    @Test
    public void validateAuthenticatedSitemap() throws Exception {
        test = extent.createTest("Validate Sitemap with Basic Authentication");

        // Read sitemap URLs and credentials from file
        File file = new File("websites01.txt");
        Scanner scanner = new Scanner(file);

        while (scanner.hasNextLine()) {
            String line = scanner.nextLine().trim();
            if (line.isEmpty()) continue;

            // Expected format: url,username,password
            String[] parts = line.split(",");
            if (parts.length < 3) {
                test.warning("Skipping invalid line: " + line);
                continue;
            }

            String sitemapUrl = parts[0];
            String username = parts[1];
            String password = parts[2];

            test.info("Reading sitemap: " + sitemapUrl);

            try {
                // Fetch sitemap with Basic Auth header
                String auth = username + ":" + password;
                String encodedAuth = Base64.getEncoder().encodeToString(auth.getBytes());

                HttpClient client = HttpClient.newBuilder().build();
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(sitemapUrl))
                        .header("Authorization", "Basic " + encodedAuth)
                        .build();

                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 200) {
                    test.pass("Successfully accessed sitemap: " + sitemapUrl);
                    List<String> links = SitemapParser.parseSitemapContent(response.body());
                    for (String link : links) {
                        int statusCode = LinkValidator.getResponseCode(link);
                        if (statusCode >= 400) {
                            test.fail(link + " → Broken link (HTTP " + statusCode + ")");
                        } else {
                            test.pass(link + " → Valid (HTTP " + statusCode + ")");
                        }
                    }
                } else {
                    test.fail("Failed to access sitemap (HTTP " + response.statusCode() + ")");
                }

            } catch (Exception e) {
                test.fail("Error reading sitemap (" + sitemapUrl + "): " + e.getMessage());
            }
        }

        scanner.close();
    }

    @AfterSuite
    public void tearDown() {
        extent.flush();
    }
}
