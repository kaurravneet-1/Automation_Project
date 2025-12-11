package utils;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.regex.*;
import java.util.zip.GZIPInputStream;

public class SitemapParser {

    /**
     * ---------------------------------------------------------
     * Read a sitemap URL and extract URLs from:
     * - XML <urlset>
     * - Nested <sitemapindex> (recursive)
     * - .xml.gz files
     * - HTML sitemap fallback
     * ---------------------------------------------------------
     */
    public static List<String> extractUrls(String sitemapUrl) {

        Set<String> finalUrls = new LinkedHashSet<>();

        try {
            System.out.println("🔍 Fetching sitemap: " + sitemapUrl);

            String content = fetchContent(sitemapUrl);
            if (content == null || content.trim().isEmpty()) {
                System.out.println("⚠ Empty or unreadable sitemap: " + sitemapUrl);
                return new ArrayList<>();
            }

            content = content.trim();

            // ---------------------------------------------------------
            // CASE 1 — SITEMAP INDEX (nested sitemaps)
            // ---------------------------------------------------------
            if (content.contains("<sitemapindex")) {

                List<String> nestedSitemaps = parseLocTags(content);

                System.out.println("📑 Found " + nestedSitemaps.size() + " nested sitemaps");

                for (String nestedUrl : nestedSitemaps) {
                    finalUrls.addAll(extractUrls(nestedUrl)); // recursive
                }

                return new ArrayList<>(finalUrls);
            }

            // ---------------------------------------------------------
            // CASE 2 — NORMAL XML SITEMAP <urlset>
            // ---------------------------------------------------------
            if (content.contains("<urlset")) {

                finalUrls.addAll(parseLocTags(content));
                return new ArrayList<>(finalUrls);
            }

            // ---------------------------------------------------------
            // CASE 3 — HTML Sitemap (fallback)
            // ---------------------------------------------------------
            finalUrls.addAll(parseHtmlLinks(content));

        } catch (Exception e) {
            System.out.println("❌ Error while parsing sitemap: " + e.getMessage());
        }

        return new ArrayList<>(finalUrls);
    }



    /**
     * ---------------------------------------------------------
     * Fetch sitemap content — supports:
     * - Normal XML
     * - Compressed XML (.xml.gz)
     * ---------------------------------------------------------
     */
    private static String fetchContent(String sitemapUrl) {
        try {
            URI uri = URI.create(sitemapUrl);  // Modern alternative to new URL()
            URL url = uri.toURL();

            HttpURLConnection conn = (HttpURLConnection) url.openConnection();

            conn.setRequestMethod("GET");
            conn.setRequestProperty("User-Agent", "Mozilla/5.0");
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(15000);

            int status = conn.getResponseCode();

            if (status != HttpURLConnection.HTTP_OK) {
                System.out.println("⚠ Unable to read sitemap: " + sitemapUrl + " | Status: " + status);
                return null;
            }

            InputStream inputStream = 
                sitemapUrl.endsWith(".gz") ? new GZIPInputStream(conn.getInputStream()) 
                                           : conn.getInputStream();

            BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
            StringBuilder sb = new StringBuilder();

            String line;
            while ((line = reader.readLine()) != null)
                sb.append(line).append("\n");

            reader.close();
            return sb.toString();

        } catch (Exception e) {
            System.out.println("❌ Connection Error: " + e.getMessage());
            return null;
        }
    }



    /**
     * ---------------------------------------------------------
     * Extract <loc> tags from any XML sitemap
     * ---------------------------------------------------------
     */
    private static List<String> parseLocTags(String content) {
        List<String> urls = new ArrayList<>();

        Pattern pattern = Pattern.compile("<loc>(.*?)</loc>");
        Matcher matcher = pattern.matcher(content);

        while (matcher.find()) {
            String url = matcher.group(1).trim();
            if (url.startsWith("http")) {
                urls.add(url);
            }
        }

        return urls;
    }



    /**
     * ---------------------------------------------------------
     * Extract links from HTML sitemap <a href="">
     * ---------------------------------------------------------
     */
    private static List<String> parseHtmlLinks(String content) {
        List<String> urls = new ArrayList<>();

        Pattern pattern = Pattern.compile("href=[\"'](.*?)[\"']");
        Matcher matcher = pattern.matcher(content);

        while (matcher.find()) {
            String link = matcher.group(1).trim();
            if (link.startsWith("http")) {
                urls.add(link);
            }
        }

        return urls;
    }



    /**
     * ---------------------------------------------------------
     * Public method for unit test support
     * ---------------------------------------------------------
     */
    public static List<String> parseSitemapContent(String content) {

        if (content == null || content.trim().isEmpty())
            return new ArrayList<>();

        content = content.trim();

        if (content.contains("<urlset") || content.contains("<sitemapindex"))
            return parseLocTags(content);

        return parseHtmlLinks(content);
    }
}
