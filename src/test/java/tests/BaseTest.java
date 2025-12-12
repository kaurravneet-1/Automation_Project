package tests;

import com.aventstack.extentreports.ExtentReports;
import com.aventstack.extentreports.reporter.ExtentSparkReporter;
import com.aventstack.extentreports.ExtentTest;

import io.github.bonigarcia.wdm.WebDriverManager;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;

import org.testng.annotations.*;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;

public class BaseTest {

    public WebDriver driver;     // ❗ Instance-level (not static!)
    public static ExtentReports extent;

  
    @BeforeSuite
    public void setupReport() {
        try {
            String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
            String reportDir = System.getProperty("user.dir") + "/report";
            new File(reportDir).mkdirs();
            String reportPath = reportDir + "/Report_" + timestamp + ".html";

            ExtentSparkReporter spark = new ExtentSparkReporter(reportPath);
            spark.config().setDocumentTitle("Automation Report");
            spark.config().setReportName("Full Website Validation Report");

            extent = new ExtentReports();
            extent.attachReporter(spark);
            extent.setSystemInfo("Tester", "Ravneet Kaur");
            extent.setSystemInfo("Environment", "Staging");

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

   
    @BeforeMethod
    public void setupDriver() {

        try {
            WebDriverManager.chromedriver().setup();

            ChromeOptions options = new ChromeOptions();
            options.addArguments("--disable-gpu");
            options.addArguments("--disable-dev-shm-usage");
            options.addArguments("--no-sandbox");
            options.addArguments("--disable-extensions");
            options.addArguments("--disable-notifications");
            options.addArguments("--window-size=1920,1080");
            options.addArguments("--remote-allow-origins=*");

          
            options.addArguments("--headless=new");

            driver = new ChromeDriver(options);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public WebDriver getDriver() {
        return driver;
    }

    @AfterMethod
    public void tearDown() {
        try {
            if (driver != null) {
                driver.quit();
            }
        } catch (Exception ignored) {}
    }

   
    @AfterSuite
    public void closeReport() {
        try {
            if (extent != null) {
                extent.flush();
            }
        } catch (Exception ignored) {}
    }

  
    public ExtentTest createTest(String name) {
        return extent.createTest(name);
    }
}

