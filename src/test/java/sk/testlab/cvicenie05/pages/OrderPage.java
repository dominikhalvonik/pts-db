package sk.testlab.cvicenie05.pages;

import java.net.URI;
import java.time.Duration;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;

public final class OrderPage extends BasePage {
    private static final By ID = testId("order-id");
    private static final By NUMBER = testId("order-number");
    private static final By TOTAL = testId("order-total");
    private static final By STATUS = testId("order-status");
    private static final By NOTE = testId("order-note-text");
    private static final By CANCEL = testId("cancel-order");
    private static final By CONFIRM_CANCEL = testId("confirm-cancel");

    private final String orderId;

    public OrderPage(
            WebDriver driver, Duration timeout, String previousId) {
        super(driver, timeout);
        orderId = wait.withMessage(
                "Nezobrazil sa nový detail objednávky s UUID.")
            .until(d -> {
                WebElement element = d.findElement(ID);
                if (!element.isDisplayed()) return null;
                String value = element.getText().trim();
                return !value.isEmpty() && !value.equals(previousId)
                    ? value
                    : null;
            });
    }

    public String id() {
        return orderId;
    }

    public String orderNumber() {
        return text(NUMBER);
    }

    public int totalCents() {
        return Integer.parseInt(
            attribute(TOTAL, "data-cents"));
    }

    public String visibleTotal() {
        return text(TOTAL)
            .replace('\u00a0', ' ')
            .replace('\u202f', ' ')
            .replaceAll("\\s+", " ")
            .trim();
    }

    public String status() {
        return attribute(STATUS, "data-status");
    }

    public int quantity(String sku) {
        return Integer.parseInt(
            text(testId("order-quantity-" + sku)));
    }

    public String note() {
        return text(NOTE);
    }

    public String idInAddress() {
        return idFromUrl(driver.getCurrentUrl());
    }

    public boolean cancelButtonDisplayed() {
        return isDisplayedIfPresent(CANCEL);
    }

    public OrderPage cancelOwnOrder(String knownId) {
        if (!isUuid(knownId)
                || !knownId.equals(id())
                || !knownId.equals(idInAddress())) {
            throw new IllegalStateException(
                "Test odmietol zrušiť objednávku s iným UUID.");
        }

        String current = status();
        if ("cancelled".equals(current)) return this;
        if (!"created".equals(current)) {
            throw new IllegalStateException(
                "Objednávku nemožno bezpečne zrušiť; stav: " + current);
        }

        click(CANCEL);
        click(CONFIRM_CANCEL);
        wait.withMessage("Objednávka neprešla do stavu cancelled.")
            .until(ExpectedConditions.attributeToBe(
                STATUS, "data-status", "cancelled"));
        return this;
    }

    public OrderPage reload() {
        driver.navigate().refresh();
        return new OrderPage(driver, timeout, "");
    }

    public static OrderPage openOwn(
            WebDriver driver,
            Duration timeout,
            String baseUrl,
            String knownId) {
        if (!isUuid(knownId)) {
            throw new IllegalArgumentException(
                "Nemožno otvoriť neplatné UUID objednávky.");
        }
        driver.get(baseUrl + "#/orders/" + knownId);
        return new OrderPage(driver, timeout, "");
    }

    public static boolean isUuid(String text) {
        return text != null && text.matches(
            "(?i)[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-"
                + "[0-9a-f]{4}-[0-9a-f]{12}");
    }

    public static String idFromUrl(String url) {
        try {
            String fragment = URI.create(url).getFragment();
            return fragment != null && fragment.startsWith("/orders/")
                ? fragment.substring("/orders/".length())
                : "";
        } catch (IllegalArgumentException error) {
            return "";
        }
    }
}
