package sk.testlab.cvicenie05.pages;

import java.time.Duration;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;

public final class CheckoutPage extends BasePage {
    private static final By FORM = testId("checkout-form");
    private static final By NAME = testId("customer-name");
    private static final By EMAIL = testId("customer-email");
    private static final By ADDRESS = testId("customer-address");
    private static final By CITY = testId("customer-city");
    private static final By POSTAL_CODE = testId("customer-postal-code");
    private static final By NOTE = testId("order-note");
    private static final By TERMS = testId("accept-terms");
    private static final By PLACE_ORDER = testId("place-order");

    public CheckoutPage(WebDriver driver, Duration timeout) {
        super(driver, timeout);
        visible(FORM);
    }

    public CheckoutPage fillCustomer(
            String name,
            String email,
            String address,
            String city,
            String postalCode,
            String note) {
        type(NAME, name);
        type(EMAIL, email);
        type(ADDRESS, address);
        type(CITY, city);
        type(POSTAL_CODE, postalCode);
        type(NOTE, note);
        return this;
    }

    public CheckoutPage acceptTerms() {
        WebElement checkbox = visible(TERMS);
        if (!checkbox.isSelected()) click(TERMS);
        return this;
    }

    public OrderPage submitOnce() {
        String previousId = OrderPage.idFromUrl(driver.getCurrentUrl());

        // Jedna zápisová akcia. Timeout nie je dôvod na slepé druhé odoslanie.
        click(PLACE_ORDER);
        return new OrderPage(driver, timeout, previousId);
    }
}
