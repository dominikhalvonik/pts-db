package sk.testlab.cvicenie05.pages;

import java.time.Duration;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.Select;

public final class CartPage extends BasePage {
    private static final By DELIVERY = testId("delivery-method");
    private static final By COUPON = testId("coupon-input");
    private static final By APPLY = testId("coupon-apply");
    private static final By QUOTE_TOTAL = testId("quote-total");
    private static final By CHECKOUT = testId("proceed-checkout");

    public CartPage(WebDriver driver, Duration timeout) {
        super(driver, timeout);
        visible(DELIVERY);
        visible(QUOTE_TOTAL);
    }

    public int quantity(String sku) {
        return Integer.parseInt(
            inputValue(testId("quantity-" + sku)));
    }

    public CartPage useCourier() {
        Select select = new Select(visible(DELIVERY));
        String current = select.getFirstSelectedOption()
            .getDomAttribute("value");

        if (!"courier".equals(current)) {
            WebElement previous = visible(QUOTE_TOTAL);
            select.selectByValue("courier");
            awaitNewQuote(previous);
        }
        return this;
    }

    public CartPage applyCoupon(String coupon) {
        type(COUPON, coupon);
        WebElement previous = visible(QUOTE_TOTAL);
        click(APPLY);
        awaitNewQuote(previous);
        return this;
    }

    public int quoteTotalCents() {
        return Integer.parseInt(
            attribute(QUOTE_TOTAL, "data-cents"));
    }

    private void awaitNewQuote(WebElement previous) {
        wait.withMessage("Pôvodný cenový súhrn nebol nahradený.")
            .until(ExpectedConditions.stalenessOf(previous));

        // Čakáme na nový výsledok, nie na konkrétnu správnu cenu.
        attribute(QUOTE_TOTAL, "data-cents");
    }

    public CheckoutPage checkout() {
        click(CHECKOUT);
        return new CheckoutPage(driver, timeout);
    }
}
