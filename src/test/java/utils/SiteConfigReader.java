package utils;

import java.io.*;
import java.util.*;

public class SiteConfigReader {

    public static List<String[]> readConfig(String fileName) {

        List<String[]> data = new ArrayList<>();

        try {
            File file = new File(System.getProperty("user.dir") + "/" + fileName);
            BufferedReader br = new BufferedReader(new FileReader(file));

            String line;

            while ((line = br.readLine()) != null) {

                if (line.trim().isEmpty()) continue;   // skip empty lines
                if (line.startsWith("#")) continue;    // skip comments

                // Split by comma — works like CSV
                String[] fields = parseCSVLine(line);

                data.add(fields);
            }

            br.close();
        } 
        catch (Exception e) {
            e.printStackTrace();
        }

        return data;
    }

    /** Handles quotes properly like a real CSV parser */
    private static String[] parseCSVLine(String line) {
        List<String> tokens = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        boolean insideQuotes = false;

        for (char c : line.toCharArray()) {

            if (c == '"') {
                insideQuotes = !insideQuotes;
            } 
            else if (c == ',' && !insideQuotes) {
                tokens.add(sb.toString().trim());
                sb.setLength(0);
            } 
            else {
                sb.append(c);
            }
        }

        tokens.add(sb.toString().trim());
        return tokens.toArray(new String[0]);
    }

    // ------------------------------------------------------------
    // ⭐⭐ NEW METHODS ADDED — NOTHING ABOVE IS CHANGED ⭐⭐
    // ------------------------------------------------------------

    /** 
     * Get the first URL from your config file.
     * Example: baseurl.txt or config.txt
     */
    public static String getSiteURL() {
        try {
            List<String[]> data = readConfig("config.txt"); // your existing config file
            if (!data.isEmpty()) {
                return data.get(0)[0];
            }
        } catch (Exception ignored) {}
        return "";
    }

    /**
     * Read sitemap URLs from sitemap.txt or urls.txt
     */
    public static List<String> getSitemapURLs() {
        List<String> urls = new ArrayList<>();

        try {
            BufferedReader br = new BufferedReader(new FileReader(
                System.getProperty("user.dir") + "/sitemap.txt"   // YOUR FILE
            ));

            String line;
            while ((line = br.readLine()) != null) {
                if (!line.trim().isEmpty())
                    urls.add(line.trim());
            }

            br.close();
        } catch (Exception e) {
            e.printStackTrace();
        }

        return urls;
    }

    /**
     * Generic helper: read any text file line-by-line.
     */
    public static List<String> readSimpleList(String fileName) {
        List<String> list = new ArrayList<>();
        try {
            BufferedReader br = new BufferedReader(new FileReader(
                System.getProperty("user.dir") + "/" + fileName
            ));
            String line;
            while ((line = br.readLine()) != null) {
                if (!line.trim().isEmpty()) list.add(line.trim());
            }
            br.close();
        } catch (Exception ignored) {}

        return list;
    }

	
}
