package utils;

import com.aventstack.extentreports.ExtentReports;
import com.aventstack.extentreports.reporter.ExtentSparkReporter;

import tests.BaseTest;

import java.text.SimpleDateFormat;
import java.util.Date;

public class ExtentReportManager extends BaseTest {

    private static ExtentReports extent;

    // ✅ Use this method everywhere (in MultiSitemapTestCase, SitemapWithAuthTestCase, etc.)
    public static ExtentReports getReportInstance() {
        if (extent == null) {
            String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
            String reportPath = System.getProperty("user.dir") + "/report/Report_" + timestamp + ".html";

            ExtentSparkReporter spark = new ExtentSparkReporter(reportPath);
            spark.config().setDocumentTitle("Sitemap Link Validation Report");
            spark.config().setReportName("Sitemap Validation Results");

            extent = new ExtentReports();
            extent.attachReporter(spark);
            extent.setSystemInfo("Tester", "Ravneet Kaur");
            extent.setSystemInfo("Environment", "Staging");
        }
        return extent;
    }

	public static ExtentReports createInstance() {
		// TODO Auto-generated method stub
		return null;
	}

	public static ExtentReports createInstance(String string) {
		// TODO Auto-generated method stub
		return null;
	}

    // ❌ Remove this unused stub if it exists
    // It causes confusion because it returns null.
    // public static ExtentReports createInstance() {
    //     return null;
    // }
}

