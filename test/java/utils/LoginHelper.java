package utils;

import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;

public class LoginHelper {

    /**
     * Generic login method — can be reused for any website.
     * You can modify locator IDs according to your website’s login page.
     */
    public static void performLogin(WebDriver driver, String loginUrl, String username, String password) {
        try {
            driver.get(loginUrl);
            driver.findElement(By.id("username")).sendKeys(username);
            driver.findElement(By.id("password")).sendKeys(password);
            driver.findElement(By.id("loginButton")).click();
        } catch (Exception e) {
            throw new RuntimeException("Login failed: " + e.getMessage());
        }
    }
}
