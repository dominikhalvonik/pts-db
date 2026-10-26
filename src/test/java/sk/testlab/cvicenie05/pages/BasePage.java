package sk.testlab.cvicenie05.pages;

import java.time.Duration;
import java.util.List;
import org.openqa.selenium.By;
import org.openqa.selenium.StaleElementReferenceException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

/** Spoločné operácie Page Objectov. Polling iba číta stav; zápisovú akciu neopakuje. */
public abstract class BasePage {
    protected final WebDriver driver;
    protected final Duration timeout;
    protected final WebDriverWait wait;

    protected BasePage(WebDriver driver, Duration timeout) {
        this.driver = driver;
        this.timeout = timeout;
        this.wait = new WebDriverWait(driver, timeout);
        this.wait.pollingEvery(Duration.ofMillis(200));
        this.wait.ignoring(StaleElementReferenceException.class);
    }

    protected static By testId(String value) {
        if (!value.matches("[A-Za-z0-9_-]+")) {
            throw new IllegalArgumentException("Nepodporovaný data-testid: " + value);
        }
        return By.cssSelector("[data-testid='" + value + "']");
    }

    protected WebElement visible(By locator) {
        return wait.withMessage("Viditeľný prvok: " + locator)
            .until(ExpectedConditions.visibilityOfElementLocated(locator));
    }

    protected WebElement clickable(By locator) {
        return wait.withMessage("Klikateľný prvok: " + locator)
            .until(ExpectedConditions.elementToBeClickable(locator));
    }

    protected void click(By locator) {
        // Kliknutie je mimo polling callbacku, takže ho čakanie neopakuje.
        clickable(locator).click();
    }

    protected void type(By locator, String value) {
        WebElement element = clickable(locator);
        element.clear();
        element.sendKeys(value);
    }

    protected String text(By locator) {
        return wait.withMessage("Neprázdny text: " + locator).until(d -> {
            WebElement element = d.findElement(locator);
            if (!element.isDisplayed()) return null;
            String value = element.getText().trim();
            return value.isEmpty() ? null : value;
        });
    }

    protected String attribute(By locator, String name) {
        return wait.withMessage("Atribút " + name + ": " + locator).until(d -> {
            WebElement element = d.findElement(locator);
            if (!element.isDisplayed()) return null;
            String value = element.getDomAttribute(name);
            return value == null || value.isBlank() ? null : value;
        });
    }

    protected String inputValue(By locator) {
        return wait.withMessage("Hodnota vstupu: " + locator).until(d -> {
            WebElement element = d.findElement(locator);
            if (!element.isDisplayed()) return null;
            String value = element.getDomProperty("value");
            return value == null || value.isBlank() ? null : value;
        });
    }

    protected boolean isDisplayedIfPresent(By locator) {
        List<WebElement> elements = driver.findElements(locator);
        return elements.stream().anyMatch(WebElement::isDisplayed);
    }
}
