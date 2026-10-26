package sk.testlab.cvicenie05.db;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import sk.testlab.cvicenie05.support.TestConfig;

/**
 * Databázu používa iba ako pozorovateľa.
 * Všetky aplikačné údaje sa čítajú parametrizovanými SELECT dotazmi.
 */
public final class DatabaseClient {
    private static final Pattern FORBIDDEN_PRIVILEGE = Pattern.compile(
        "\\b(INSERT|UPDATE|DELETE|CREATE|DROP|ALTER|INDEX|TRIGGER|"
            + "EXECUTE|FILE|PROCESS|RELOAD|SHUTDOWN|SUPER|EVENT|"
            + "LOCK TABLES|CREATE USER|GRANT OPTION)\\b"
    );

    private final TestConfig config;

    public DatabaseClient(TestConfig config) {
        this.config = config;
    }

    public DatabaseIdentity identity() throws SQLException {
        String sql = """
            SELECT DATABASE() AS database_name,
                   CURRENT_USER() AS authenticated_user,
                   VERSION() AS server_version
            """;

        try (Connection connection = open();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet result = statement.executeQuery()) {
            if (!result.next()) {
                throw new SQLException("MariaDB nevrátila identitu spojenia.");
            }
            return new DatabaseIdentity(
                result.getString("database_name"),
                result.getString("authenticated_user"),
                result.getString("server_version")
            );
        }
    }

    /**
     * Best-effort kontrola SHOW GRANTS.
     * Skutočnú bezpečnostnú hranicu tvoria oprávnenia účtu na serveri.
     */
    public ReadOnlyCheck verifyReadOnlyGrants() throws SQLException {
        int rows = 0;
        boolean selectSeen = false;
        Set<String> forbidden = new LinkedHashSet<>();

        try (Connection connection = open();
             PreparedStatement statement =
                 connection.prepareStatement("SHOW GRANTS");
             ResultSet result = statement.executeQuery()) {
            while (result.next()) {
                rows++;
                String grant = result.getString(1);
                String upper = grant == null
                    ? ""
                    : grant.toUpperCase(Locale.ROOT);

                if (Pattern.compile("\\bSELECT\\b").matcher(upper).find()) {
                    selectSeen = true;
                }
                if (upper.contains("ALL PRIVILEGES")) {
                    forbidden.add("ALL PRIVILEGES");
                }

                var matcher = FORBIDDEN_PRIVILEGE.matcher(upper);
                while (matcher.find()) forbidden.add(matcher.group(1));
            }
        }

        ReadOnlyCheck check = new ReadOnlyCheck(
            rows, selectSeen, List.copyOf(forbidden));

        if (!check.passed()) {
            throw new SecurityException(
                "JDBC účet nevyzerá ako read-only. "
                    + "SELECT=" + selectSeen
                    + ", zakázané oprávnenia=" + forbidden
                    + ". Použite database/create-readonly-user.sql.");
        }
        return check;
    }

    public long workspaceId(String workspaceCode) throws SQLException {
        String sql = "SELECT id FROM workspaces WHERE code = ?";

        try (Connection connection = open();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, workspaceCode);

            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    throw new SQLException(
                        "Pracovný priestor neexistuje: " + workspaceCode);
                }
                long id = result.getLong("id");
                if (result.next()) {
                    throw new SQLException(
                        "Kód pracovného priestoru nie je jednoznačný.");
                }
                return id;
            }
        }
    }

    public Map<String, ProductRow> products(
            long workspaceId, String firstSku, String secondSku)
            throws SQLException {
        String sql = """
            SELECT id, workspace_id, sku, name, price_cents, stock, active
            FROM products
            WHERE workspace_id = ?
              AND sku IN (?, ?)
            ORDER BY sku
            """;

        Map<String, ProductRow> rows = new LinkedHashMap<>();
        try (Connection connection = open();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, workspaceId);
            statement.setString(2, firstSku);
            statement.setString(3, secondSku);

            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    ProductRow row = new ProductRow(
                        result.getLong("id"),
                        result.getLong("workspace_id"),
                        result.getString("sku"),
                        result.getString("name"),
                        result.getInt("price_cents"),
                        result.getInt("stock"),
                        result.getInt("active") == 1
                    );
                    if (rows.put(row.sku(), row) != null) {
                        throw new SQLException(
                            "Duplicitné SKU v pracovnom priestore: " + row.sku());
                    }
                }
            }
        }
        return rows;
    }

    public OrderRow orderRequired(String orderId) throws SQLException {
        String sql = """
            SELECT id, order_number, workspace_id, user_id, status, currency,
                   subtotal_cents, discount_cents, shipping_cents, total_cents,
                   coupon_code, delivery_method, customer_name, customer_email,
                   address_line, city, postal_code, note, created_at, cancelled_at
            FROM orders
            WHERE id = ?
            """;

        try (Connection connection = open();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, orderId);

            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    throw new SQLException(
                        "Objednávka s UUID " + orderId + " v databáze chýba.");
                }
                OrderRow row = mapOrder(result);
                if (result.next()) {
                    throw new SQLException(
                        "Primárny kľúč objednávky nie je jednoznačný.");
                }
                return row;
            }
        }
    }

    public List<OrderItemRow> orderItems(String orderId) throws SQLException {
        String sql = """
            SELECT order_id, product_id, sku, product_name,
                   unit_price_cents, quantity, line_total_cents
            FROM order_items
            WHERE order_id = ?
            ORDER BY sku
            """;

        List<OrderItemRow> rows = new ArrayList<>();
        try (Connection connection = open();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, orderId);

            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    rows.add(new OrderItemRow(
                        result.getString("order_id"),
                        result.getLong("product_id"),
                        result.getString("sku"),
                        result.getString("product_name"),
                        result.getInt("unit_price_cents"),
                        result.getInt("quantity"),
                        result.getInt("line_total_cents")
                    ));
                }
            }
        }
        return List.copyOf(rows);
    }

    private Connection open() throws SQLException {
        Connection connection = DriverManager.getConnection(
            config.dbUrl(), config.dbUser(), config.dbPassword());

        // Toto je dodatočný signál pre driver. Oprávnenia účtu musia byť
        // v MariaDB naozaj obmedzené cez GRANT SELECT.
        connection.setReadOnly(true);
        connection.setAutoCommit(true);
        connection.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
        return connection;
    }

    private static OrderRow mapOrder(ResultSet result) throws SQLException {
        return new OrderRow(
            result.getString("id"),
            result.getString("order_number"),
            result.getLong("workspace_id"),
            result.getLong("user_id"),
            result.getString("status"),
            result.getString("currency"),
            result.getInt("subtotal_cents"),
            result.getInt("discount_cents"),
            result.getInt("shipping_cents"),
            result.getInt("total_cents"),
            result.getString("coupon_code"),
            result.getString("delivery_method"),
            result.getString("customer_name"),
            result.getString("customer_email"),
            result.getString("address_line"),
            result.getString("city"),
            result.getString("postal_code"),
            result.getString("note"),
            localDateTime(result.getTimestamp("created_at")),
            localDateTime(result.getTimestamp("cancelled_at"))
        );
    }

    private static LocalDateTime localDateTime(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }
}
