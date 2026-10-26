package sk.testlab.cvicenie05.pages;

import java.time.Duration;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;

public final class LoginPage extends BasePage {
    private static final By NAV_LOGIN = testId("nav-login");
    private static final By EMAIL = testId("login-email");
    private static final By PASSWORD = testId("login-password");
    private static final By SUBMIT = testId("login-submit");

    private LoginPage(WebDriver driver, Duration timeout) {
        super(driver, timeout);
    }

    public static LoginPage open(
            WebDriver driver, Duration timeout, String baseUrl) {
        driver.get(baseUrl);
        LoginPage page = new LoginPage(driver, timeout);
        page.click(NAV_LOGIN);
        page.visible(EMAIL);
        return page;
    }

    public CatalogPage loginAs(String email, String password) {
        type(EMAIL, email);
        type(PASSWORD, password);
        click(SUBMIT);
        return new CatalogPage(driver, timeout);
    }
}
