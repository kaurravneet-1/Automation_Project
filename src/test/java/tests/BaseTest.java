package tests;

import com.aventstack.extentreports.ExtentReports;
import com.aventstack.extentreports.reporter.ExtentSparkReporter;
import com.aventstack.extentreports.ExtentTest;

import io.github.bonigarcia.wdm.WebDriverManager;
import org.openqa.selenium.WebDriver;

import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;

import org.testng.annotations.AfterSuite;
import org.testng.annotations.BeforeSuite;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.*;

public class BaseTest {

    public static WebDriver driver;
    public static ExtentReports extent;

    @BeforeSuite
    public void setupReport() {
        try {
            String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
            String reportDir = System.getProperty("user.dir") + "/report";
            new File(reportDir).mkdirs();
            String reportPath = reportDir + "/Report_" + timestamp + ".html";

            ExtentSparkReporter spark = new ExtentSparkReporter(new File(reportPath));
            spark.config().setDocumentTitle("Sitemap + Header/Footer Validation");
            spark.config().setReportName("Sitemap Header/Footer Report");

            extent = new ExtentReports();
            extent.attachReporter(spark);
            extent.setSystemInfo("Tester", "Ravneet Kaur");
            extent.setSystemInfo("Environment", "Staging");

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // ⭐⭐⭐ HYBRID CHROME SETUP — FAST + COMPATIBLE ⭐⭐⭐
    public void setupDriver() {

        try {
            WebDriverManager.chromedriver().browserVersion("142").setup();

            ChromeOptions options = new ChromeOptions();

            options.addArguments("--disable-gpu");
            options.addArguments("--disable-dev-shm-usage");
            options.addArguments("--no-sandbox");
            options.addArguments("--disable-extensions");
            options.addArguments("--disable-popup-blocking");
            options.addArguments("--disable-notifications");
            options.addArguments("--start-maximized");

            System.setProperty("webdriver.http.factory", "jdk-http-client");
            options.addArguments("--remote-allow-origins=*");
            options.addArguments("--disable-features=IsolateOrigins,site-per-process");

            options.addArguments("--disable-blink-features=AutomationControlled");

            // Headless mode
            options.addArguments("--headless=new");
            options.addArguments("--window-size=1920,1080");

            driver = new ChromeDriver(options);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // ================================================================
    @BeforeSuite
    public void setupDriverBeforeSuite() {
        if (driver == null) setupDriver();
    }

    public static WebDriver getDriver() {
        return driver;
    }
    // ================================================================

    @AfterSuite
    public void tearDown() {
        try {
            if (driver != null) driver.quit();
            if (extent != null) extent.flush();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // ------------------------------------------------------------------
    // ⭐ ADDED: Generic helper for creating tests (safe, no interference)
    // ------------------------------------------------------------------
    public ExtentTest createTest(String testName) {
        return extent.createTest(testName);
    }

    // ------------------------------------------------------------------
    // ⭐ ADDED: Helper to log formatted summary (optional)
    // ------------------------------------------------------------------
    public void logSummary(ExtentTest test, int working, int broken, int skipped) {
        test.info("<b>Summary →</b> " +
                "<span style='color:green;'>Working: " + working + "</span> | " +
                "<span style='color:red;'>Broken: " + broken + "</span> | " +
                "<span style='color:gray;'>Skipped: " + skipped + "</span>");
    }
}
