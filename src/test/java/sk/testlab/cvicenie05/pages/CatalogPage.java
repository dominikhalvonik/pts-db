package sk.testlab.cvicenie05.pages;

import java.time.Duration;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.support.ui.ExpectedConditions;

public final class CatalogPage extends BasePage {
    private static final By READY = testId("catalog-ready");
    private static final By USER = testId("current-user");
    private static final By CART_COUNT = testId("cart-count");
    private static final By NAV_CART = testId("nav-cart");

    public CatalogPage(WebDriver driver, Duration timeout) {
        super(driver, timeout);
        visible(USER);
        visible(READY);
    }

    public String currentUserName() {
        return text(USER);
    }

    public int cartCount() {
        return Integer.parseInt(text(CART_COUNT));
    }

    public int stock(String sku) {
        return Integer.parseInt(
            attribute(testId("stock-" + sku), "data-stock"));
    }

    public int priceCents(String sku) {
        return Integer.parseInt(
            attribute(testId("price-" + sku), "data-cents"));
    }

    public CatalogPage addToCart(String sku, int quantity) {
        if (quantity < 1 || quantity > 20) {
            throw new IllegalArgumentException("Množstvo musí byť 1 až 20.");
        }

        for (int i = 0; i < quantity; i++) {
            int before = cartCount();
            click(testId("add-to-cart-" + sku));
            wait.withMessage("Po pridaní " + sku + " sa nezmenil počet v košíku.")
                .until(ExpectedConditions.textToBe(
                    CART_COUNT, Integer.toString(before + 1)));
        }
        return this;
    }

    public CartPage openCart() {
        click(NAV_CART);
        return new CartPage(driver, timeout);
    }
}
