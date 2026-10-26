package sk.testlab.cvicenie05;

import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toMap;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.openqa.selenium.Dimension;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.remote.RemoteWebDriver;
import sk.testlab.cvicenie05.db.DatabaseClient;
import sk.testlab.cvicenie05.db.DatabaseIdentity;
import sk.testlab.cvicenie05.db.OrderItemRow;
import sk.testlab.cvicenie05.db.OrderRow;
import sk.testlab.cvicenie05.db.ProductRow;
import sk.testlab.cvicenie05.db.ReadOnlyCheck;
import sk.testlab.cvicenie05.pages.CartPage;
import sk.testlab.cvicenie05.pages.CatalogPage;
import sk.testlab.cvicenie05.pages.CheckoutPage;
import sk.testlab.cvicenie05.pages.LoginPage;
import sk.testlab.cvicenie05.pages.OrderPage;
import sk.testlab.cvicenie05.support.Artifacts;
import sk.testlab.cvicenie05.support.BrowserFactory;
import sk.testlab.cvicenie05.support.TestConfig;

/**
 * Cvičenie 05:
 * 1. používateľ vytvorí objednávku cez Web GUI,
 * 2. test podľa vlastného UUID nezávisle číta MariaDB,
 * 3. zrušenie prebehne cez GUI a DB potvrdí stav aj návrat zásob.
 *
 * Databázový klient nevykonáva INSERT, UPDATE ani DELETE.
 */
@Tag("gui")
@Tag("database")
@Tag("e2e")
@Execution(ExecutionMode.SAME_THREAD)
@DisplayName("Cvičenie 05 – Web GUI a read-only overenie MariaDB")
public class GuiDatabaseOrderTest {
    private static final String KEYBOARD = "KB-001";
    private static final String MOUSE = "MS-001";

    private static final int INITIAL_KEYBOARD_STOCK = 100;
    private static final int INITIAL_MOUSE_STOCK = 100;

    private static final int KEYBOARD_PRICE = 3490;
    private static final int MOUSE_PRICE = 1990;

    private static final int EXPECTED_SUBTOTAL = 8970;
    private static final int EXPECTED_DISCOUNT = 897;
    private static final int EXPECTED_SHIPPING = 490;
    private static final int EXPECTED_TOTAL = 8563;

    private WebDriver driver;
    private TestConfig config;
    private DatabaseClient database;
    private Artifacts artifacts;

    private String ownOrderId = "";
    private boolean submitAttempted;
    private boolean normalCancellationCompleted;
    private Throwable primaryFailure;

    @BeforeEach
    void prepare() throws Exception {
        try {
            config = TestConfig.load();
            artifacts = new Artifacts(config.password(), config.dbPassword());
            database = new DatabaseClient(config);

            artifacts.put("workspace.declared", config.workspace());
            artifacts.put("expected.total.cents", EXPECTED_TOTAL);
            artifacts.put("expected.status.created", "created");
            artifacts.put("expected.status.cancelled", "cancelled");

            step("Kontrola read-only JDBC spojenia");
            DatabaseIdentity identity = database.identity();
            artifacts.put("database.name", identity.databaseName());
            artifacts.put("database.current.user", identity.currentUser());
            artifacts.put("database.server.version", identity.serverVersion());

            if (config.verifyReadOnly()) {
                ReadOnlyCheck check = database.verifyReadOnlyGrants();
                artifacts.put("database.grant.rows", check.grantRows());
                artifacts.put("database.readonly.check", "PASSED");
            } else {
                artifacts.put("database.readonly.check", "DISABLED");
            }

            step("Vytvorenie nového prehliadača");
            driver = BrowserFactory.create(config);
            driver.manage().timeouts().implicitlyWait(Duration.ZERO);
            driver.manage().timeouts().pageLoadTimeout(
                config.timeout().multipliedBy(2));
            driver.manage().window().setSize(new Dimension(1440, 1000));

            if (driver instanceof RemoteWebDriver remote) {
                artifacts.put(
                    "browser.name",
                    remote.getCapabilities().getBrowserName());
                artifacts.put(
                    "browser.version",
                    remote.getCapabilities().getBrowserVersion());
            }
        } catch (Exception | AssertionError error) {
            primaryFailure = error;
            if (artifacts != null) {
                artifacts.capture(driver, "setup-failure", error);
            }
            throw error;
        }
    }

    @Test
    @DisplayName(
        "GUI objednávka zodpovedá orders/order_items a zrušenie obnoví sklad")
    void orderCreatedInGuiMatchesDatabaseAndCancellationRestoresStock()
            throws Exception {
        try {
            // ARRANGE: nezávislý stav pred používateľskou operáciou.
            step("Načítanie východiskového stavu z MariaDB");
            long workspaceId = database.workspaceId(config.workspace());
            Map<String, ProductRow> before = database.products(
                workspaceId, KEYBOARD, MOUSE);

            ProductRow keyboardBefore = requiredProduct(before, KEYBOARD);
            ProductRow mouseBefore = requiredProduct(before, MOUSE);
            artifacts.write(
                "database-before.txt",
                formatProducts("Pred objednávkou", before));

            assertAll("Čerstvý izolovaný fixture",
                () -> assertEquals(
                    workspaceId, keyboardBefore.workspaceId(),
                    "KB-001 patrí do nesprávneho priestoru."),
                () -> assertEquals(
                    workspaceId, mouseBefore.workspaceId(),
                    "MS-001 patrí do nesprávneho priestoru."),
                () -> assertTrue(
                    keyboardBefore.active(), "KB-001 musí byť aktívny."),
                () -> assertTrue(
                    mouseBefore.active(), "MS-001 musí byť aktívny."),
                () -> assertEquals(
                    KEYBOARD_PRICE, keyboardBefore.priceCents(),
                    "Cena KB-001 v DB."),
                () -> assertEquals(
                    MOUSE_PRICE, mouseBefore.priceCents(),
                    "Cena MS-001 v DB."),
                () -> assertEquals(
                    INITIAL_KEYBOARD_STOCK, keyboardBefore.stock(),
                    "Fixture musí začínať so 100 kusmi KB-001."),
                () -> assertEquals(
                    INITIAL_MOUSE_STOCK, mouseBefore.stock(),
                    "Fixture musí začínať so 100 kusmi MS-001.")
            );

            // ACT: celá objednávka vznikne cez používateľské rozhranie.
            step("Prihlásenie a porovnanie katalógu s DB");
            CatalogPage catalog = LoginPage
                .open(driver, config.timeout(), config.baseUrl())
                .loginAs(config.email(), config.password());

            assertAll("GUI východiskový stav",
                () -> assertEquals(
                    0, catalog.cartCount(), "Košík musí byť prázdny."),
                () -> assertEquals(
                    keyboardBefore.stock(), catalog.stock(KEYBOARD),
                    "GUI a DB sklad KB-001 pred objednávkou."),
                () -> assertEquals(
                    mouseBefore.stock(), catalog.stock(MOUSE),
                    "GUI a DB sklad MS-001 pred objednávkou."),
                () -> assertEquals(
                    keyboardBefore.priceCents(), catalog.priceCents(KEYBOARD),
                    "GUI a DB cena KB-001."),
                () -> assertEquals(
                    mouseBefore.priceCents(), catalog.priceCents(MOUSE),
                    "GUI a DB cena MS-001.")
            );

            step("Výber produktov a cenová ponuka");
            CartPage cart = catalog
                .addToCart(KEYBOARD, 2)
                .addToCart(MOUSE, 1)
                .openCart()
                .useCourier()
                .applyCoupon("STUDENT10");

            assertAll("Košík pred checkoutom",
                () -> assertEquals(2, cart.quantity(KEYBOARD)),
                () -> assertEquals(1, cart.quantity(MOUSE)),
                () -> assertEquals(
                    EXPECTED_TOTAL, cart.quoteTotalCents(),
                    "Referenčná ponuka musí byť 8563 centov.")
            );

            CheckoutPage checkout = cart.checkout();
            String uniqueNote = "Cvičenie 05; beh " + artifacts.runId;
            checkout
                .fillCustomer(
                    "Študent Testovací",
                    "objednavka@example.test",
                    "Testovacia 12",
                    "Žilina",
                    "010 01",
                    uniqueNote)
                .acceptTerms();

            step("Jednorazové vytvorenie objednávky cez GUI");
            submitAttempted = true;
            OrderPage orderPage = checkout.submitOnce();

            // UUID získavame z vlastného GUI scenára. Nehľadáme globálne
            // poslednú objednávku podľa created_at.
            ownOrderId = orderPage.id();
            artifacts.write(
                "order-id.txt", ownOrderId + System.lineSeparator());
            artifacts.put("order.id", ownOrderId);

            GuiOrder createdGui = new GuiOrder(
                ownOrderId,
                orderPage.orderNumber(),
                orderPage.status(),
                orderPage.totalCents(),
                orderPage.visibleTotal(),
                orderPage.quantity(KEYBOARD),
                orderPage.quantity(MOUSE),
                orderPage.note(),
                orderPage.idInAddress()
            );
            artifacts.write("gui-created.txt", createdGui.toString());

            // ASSERT CREATED: nezávislý DB pohľad na presne to isté UUID.
            step("Overenie orders, order_items a skladu podľa vlastného UUID");
            OrderRow createdDb = database.orderRequired(ownOrderId);
            List<OrderItemRow> createdItems =
                database.orderItems(ownOrderId);
            Map<String, ProductRow> afterCreate = database.products(
                workspaceId, KEYBOARD, MOUSE);

            artifacts.write(
                "database-order-created.txt", formatOrder(createdDb));
            artifacts.write(
                "database-items-created.txt", formatItems(createdItems));
            artifacts.write(
                "database-after-create.txt",
                formatProducts("Po vytvorení", afterCreate));

            Map<String, OrderItemRow> itemsBySku =
                itemMap(createdItems);
            OrderItemRow keyboardItem =
                requiredItem(itemsBySku, KEYBOARD);
            OrderItemRow mouseItem =
                requiredItem(itemsBySku, MOUSE);
            ProductRow keyboardAfterCreate =
                requiredProduct(afterCreate, KEYBOARD);
            ProductRow mouseAfterCreate =
                requiredProduct(afterCreate, MOUSE);

            int storedLineSum = createdItems.stream()
                .mapToInt(OrderItemRow::lineTotalCents)
                .sum();

            assertAll("Vytvorená referenčná objednávka",
                // Správnosť GUI podľa špecifikácie.
                () -> assertTrue(
                    OrderPage.isUuid(createdGui.id()),
                    "GUI musí zobraziť platné UUID."),
                () -> assertEquals(
                    EXPECTED_TOTAL, createdGui.totalCents(),
                    "GUI total_cents musí byť 8563."),
                () -> assertEquals(
                    "85,63 €", createdGui.visibleTotal(),
                    "Viditeľná suma a mena."),
                () -> assertEquals(
                    "created", createdGui.status(),
                    "GUI stav po vytvorení."),
                () -> assertEquals(
                    createdGui.id(), createdGui.idInAddress(),
                    "UUID detailu a adresy."),
                () -> assertEquals(2, createdGui.keyboardQuantity()),
                () -> assertEquals(1, createdGui.mouseQuantity()),
                () -> assertEquals(uniqueNote, createdGui.note()),

                // Správnosť uloženého orders záznamu.
                () -> assertEquals(ownOrderId, createdDb.id()),
                () -> assertEquals(
                    createdGui.orderNumber(), createdDb.orderNumber(),
                    "Číslo objednávky GUI = DB."),
                () -> assertEquals(
                    workspaceId, createdDb.workspaceId(),
                    "Objednávka patrí do prideleného workspace."),
                () -> assertEquals("created", createdDb.status()),
                () -> assertEquals("EUR", createdDb.currency()),
                () -> assertEquals(
                    EXPECTED_SUBTOTAL, createdDb.subtotalCents()),
                () -> assertEquals(
                    EXPECTED_DISCOUNT, createdDb.discountCents()),
                () -> assertEquals(
                    EXPECTED_SHIPPING, createdDb.shippingCents()),
                () -> assertEquals(EXPECTED_TOTAL, createdDb.totalCents()),
                () -> assertEquals("STUDENT10", createdDb.couponCode()),
                () -> assertEquals("courier", createdDb.deliveryMethod()),
                () -> assertEquals(uniqueNote, createdDb.note()),
                () -> assertNotNull(
                    createdDb.createdAt(), "created_at musí byť uložené."),
                () -> assertNull(
                    createdDb.cancelledAt(),
                    "Nová objednávka ešte nemá cancelled_at."),

                // Konzistencia vrstiev – nestačí však bez nezávislých očakávaní.
                () -> assertEquals(
                    createdGui.totalCents(), createdDb.totalCents(),
                    "GUI suma = DB suma."),
                () -> assertEquals(
                    createdGui.status(), createdDb.status(),
                    "GUI stav = DB stav."),

                // order_items.
                () -> assertEquals(
                    2, createdItems.size(),
                    "Objednávka má presne dva riadky."),
                () -> assertEquals(ownOrderId, keyboardItem.orderId()),
                () -> assertEquals(KEYBOARD_PRICE, keyboardItem.unitPriceCents()),
                () -> assertEquals(2, keyboardItem.quantity()),
                () -> assertEquals(6980, keyboardItem.lineTotalCents()),
                () -> assertEquals(ownOrderId, mouseItem.orderId()),
                () -> assertEquals(MOUSE_PRICE, mouseItem.unitPriceCents()),
                () -> assertEquals(1, mouseItem.quantity()),
                () -> assertEquals(1990, mouseItem.lineTotalCents()),
                () -> assertEquals(
                    EXPECTED_SUBTOTAL, storedLineSum,
                    "Súčet uložených riadkov = subtotal."),

                // Vedľajší účinok na products.
                () -> assertEquals(
                    keyboardBefore.stock() - 2,
                    keyboardAfterCreate.stock(),
                    "KB-001 sa zníži práve o 2."),
                () -> assertEquals(
                    mouseBefore.stock() - 1,
                    mouseAfterCreate.stock(),
                    "MS-001 sa zníži práve o 1."),
                () -> assertEquals(98, keyboardAfterCreate.stock()),
                () -> assertEquals(99, mouseAfterCreate.stock())
            );

            artifacts.capture(driver, "created-verified", null);

            // ACT + ASSERT CANCELLED.
            step("Zrušenie tej istej objednávky cez GUI");
            orderPage.cancelOwnOrder(ownOrderId);
            String cancelledGuiStatus = orderPage.status();

            OrderRow cancelledDb = database.orderRequired(ownOrderId);
            List<OrderItemRow> cancelledItems =
                database.orderItems(ownOrderId);
            Map<String, ProductRow> afterCancel = database.products(
                workspaceId, KEYBOARD, MOUSE);

            artifacts.write(
                "database-order-cancelled.txt",
                formatOrder(cancelledDb));
            artifacts.write(
                "database-items-cancelled.txt",
                formatItems(cancelledItems));
            artifacts.write(
                "database-after-cancel.txt",
                formatProducts("Po zrušení", afterCancel));

            ProductRow keyboardAfterCancel =
                requiredProduct(afterCancel, KEYBOARD);
            ProductRow mouseAfterCancel =
                requiredProduct(afterCancel, MOUSE);

            assertAll("Zrušenie a návrat zásob",
                () -> assertEquals(
                    "cancelled", cancelledGuiStatus,
                    "GUI stav po zrušení."),
                () -> assertEquals(
                    "cancelled", cancelledDb.status(),
                    "DB stav po zrušení."),
                () -> assertNotNull(
                    cancelledDb.cancelledAt(),
                    "Zrušená objednávka musí mať cancelled_at."),
                () -> assertEquals(
                    EXPECTED_TOTAL, cancelledDb.totalCents(),
                    "Zrušenie nemení cenu objednávky."),
                () -> assertEquals(
                    createdItems, cancelledItems,
                    "Zrušenie nemení historické položky objednávky."),
                () -> assertEquals(
                    keyboardBefore.stock(), keyboardAfterCancel.stock(),
                    "Sklad KB-001 sa vráti na pôvodnú hodnotu."),
                () -> assertEquals(
                    mouseBefore.stock(), mouseAfterCancel.stock(),
                    "Sklad MS-001 sa vráti na pôvodnú hodnotu."),
                () -> assertFalse(
                    orderPage.cancelButtonDisplayed(),
                    "Po zrušení už GUI nesmie ponúkať ďalšie zrušenie.")
            );

            // Obnovenie detailu je iba čítanie. Nemá zmeniť DB ani sklad.
            OrderPage reloaded = orderPage.reload();
            Map<String, ProductRow> afterReload = database.products(
                workspaceId, KEYBOARD, MOUSE);

            assertAll("Stabilný zrušený detail po obnovení",
                () -> assertEquals(ownOrderId, reloaded.id()),
                () -> assertEquals("cancelled", reloaded.status()),
                () -> assertFalse(reloaded.cancelButtonDisplayed()),
                () -> assertEquals(afterCancel, afterReload,
                    "Samotné načítanie detailu nesmie meniť sklad.")
            );

            normalCancellationCompleted = true;
            artifacts.put("assertions", "PASSED");
            artifacts.put("normal.cancellation", "PASSED");
            artifacts.capture(driver, "cancelled-verified", null);

            System.out.println(
                "PASS: " + ownOrderId
                    + " | GUI + DB = 8563 centov"
                    + " | created -> cancelled"
                    + " | sklad 100/100 -> 98/99 -> 100/100");
        } catch (Exception | AssertionError error) {
            primaryFailure = error;
            if (artifacts != null) {
                artifacts.capture(driver, "failure", error);
            }
            throw error;
        }
    }

    @AfterEach
    void cleanUpAndClose() throws Exception {
        Throwable cleanupFailure = null;

        try {
            if (driver != null
                    && submitAttempted
                    && !normalCancellationCompleted) {
                cleanUpOwnOrderAfterFailure();
            } else if (artifacts != null) {
                artifacts.put(
                    "cleanup",
                    normalCancellationCompleted
                        ? "NOT_NEEDED_ALREADY_CANCELLED"
                        : "NO_ORDER_SUBMITTED");
            }
        } catch (Exception | AssertionError error) {
            cleanupFailure = error;
            if (artifacts != null) {
                artifacts.capture(driver, "cleanup-failure", error);
            }
        } finally {
            if (driver != null) {
                try {
                    driver.quit();
                } catch (RuntimeException error) {
                    if (cleanupFailure == null) cleanupFailure = error;
                    else cleanupFailure.addSuppressed(error);
                }
            }
        }

        if (artifacts != null) {
            try {
                artifacts.put("finished.utc", Instant.now().toString());
                artifacts.put(
                    "result",
                    primaryFailure != null
                        ? "FAILED"
                        : cleanupFailure != null
                            ? "CLEANUP_FAILED"
                            : "PASSED");
            } catch (Exception error) {
                if (cleanupFailure == null) cleanupFailure = error;
                else cleanupFailure.addSuppressed(error);
            }
        }

        if (cleanupFailure != null) {
            if (primaryFailure != null) {
                primaryFailure.addSuppressed(cleanupFailure);
                System.err.println(
                    "Zlyhalo aj upratanie; pôvodná chyba zostáva hlavná: "
                        + cleanupFailure);
            } else {
                throw new AssertionError(
                    "Test prešiel, ale upratanie alebo zatvorenie zlyhalo.",
                    cleanupFailure);
            }
        }
    }

    private void cleanUpOwnOrderAfterFailure() throws Exception {
        if (!OrderPage.isUuid(ownOrderId)) {
            String candidate = OrderPage.idFromUrl(driver.getCurrentUrl());
            if (OrderPage.isUuid(candidate)) {
                ownOrderId = candidate;
                artifacts.write(
                    "order-id.txt", ownOrderId + System.lineSeparator());
            }
        }

        if (!config.cleanupOrder()) {
            artifacts.put(
                "cleanup", "DISABLED_MANUAL_REVIEW_REQUIRED");
            return;
        }

        if (!OrderPage.isUuid(ownOrderId)) {
            artifacts.put(
                "cleanup", "UNKNOWN_ORDER_ID_MANUAL_REVIEW_REQUIRED");
            throw new IllegalStateException(
                "Odoslanie prebehlo, ale UUID nie je známe. "
                    + "Test nebude rušiť cudziu objednávku.");
        }

        OrderPage page = OrderPage.openOwn(
            driver, config.timeout(), config.baseUrl(), ownOrderId);
        page.cancelOwnOrder(ownOrderId);

        OrderRow row = database.orderRequired(ownOrderId);
        artifacts.put("cleanup.order.id", ownOrderId);
        artifacts.put("cleanup.database.status", row.status());
        artifacts.put("cleanup", "CANCELLED_OWN_ORDER");
    }

    private void step(String message) throws Exception {
        artifacts.put("step", message);
        System.out.println("[KROK] " + message);
    }

    private static ProductRow requiredProduct(
            Map<String, ProductRow> rows, String sku) {
        ProductRow row = rows.get(sku);
        if (row == null) {
            throw new AssertionError("V DB chýba produkt " + sku + ".");
        }
        return row;
    }

    private static Map<String, OrderItemRow> itemMap(
            List<OrderItemRow> items) {
        try {
            return items.stream().collect(toMap(
                OrderItemRow::sku,
                identity(),
                (first, second) -> {
                    throw new IllegalStateException(
                        "Duplicitný riadok SKU " + first.sku());
                },
                LinkedHashMap::new
            ));
        } catch (IllegalStateException error) {
            throw new AssertionError(
                "order_items neobsahuje jednoznačné SKU.", error);
        }
    }

    private static OrderItemRow requiredItem(
            Map<String, OrderItemRow> rows, String sku) {
        OrderItemRow row = rows.get(sku);
        if (row == null) {
            throw new AssertionError(
                "V order_items chýba SKU " + sku + ".");
        }
        return row;
    }

    private static String formatProducts(
            String title, Map<String, ProductRow> products) {
        StringBuilder text = new StringBuilder(title).append('\n');
        products.values().forEach(row -> text
            .append(row.sku())
            .append(" | workspace_id=").append(row.workspaceId())
            .append(" | price_cents=").append(row.priceCents())
            .append(" | stock=").append(row.stock())
            .append(" | active=").append(row.active())
            .append('\n'));
        return text.toString();
    }

    private static String formatOrder(OrderRow row) {
        return """
            id=%s
            order_number=%s
            workspace_id=%d
            user_id=%d
            status=%s
            currency=%s
            subtotal_cents=%d
            discount_cents=%d
            shipping_cents=%d
            total_cents=%d
            coupon_code=%s
            delivery_method=%s
            note=%s
            created_at=%s
            cancelled_at=%s
            """.formatted(
                row.id(),
                row.orderNumber(),
                row.workspaceId(),
                row.userId(),
                row.status(),
                row.currency(),
                row.subtotalCents(),
                row.discountCents(),
                row.shippingCents(),
                row.totalCents(),
                row.couponCode(),
                row.deliveryMethod(),
                row.note(),
                row.createdAt(),
                row.cancelledAt());
    }

    private static String formatItems(List<OrderItemRow> items) {
        StringBuilder text = new StringBuilder();
        for (OrderItemRow row : items) {
            text.append(row.sku())
                .append(" | product_id=").append(row.productId())
                .append(" | unit_price_cents=").append(row.unitPriceCents())
                .append(" | quantity=").append(row.quantity())
                .append(" | line_total_cents=").append(row.lineTotalCents())
                .append('\n');
        }
        return text.toString();
    }

    private record GuiOrder(
            String id,
            String orderNumber,
            String status,
            int totalCents,
            String visibleTotal,
            int keyboardQuantity,
            int mouseQuantity,
            String note,
            String idInAddress) {
    }
}
