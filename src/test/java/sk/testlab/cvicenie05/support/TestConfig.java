package sk.testlab.cvicenie05.support;

import java.io.IOException;
import java.io.Reader;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Locale;
import java.util.Properties;

/**
 * Konfigurácia jedného integrovaného GUI + JDBC testu.
 * Heslá sa nikdy nevypisujú v toString().
 */
public record TestConfig(
        String baseUrl,
        String workspace,
        String email,
        String password,
        String browser,
        boolean headless,
        Duration timeout,
        boolean cleanupOrder,
        String driverPath,
        String browserBinary,
        String dbUrl,
        String dbUser,
        String dbPassword,
        boolean verifyReadOnly) {

    public static TestConfig load() throws IOException {
        Path file = Path.of(System.getProperty("test.config", "config/test.properties"));
        if (!Files.isRegularFile(file)) {
            throw new IllegalStateException("Chýba " + file.toAbsolutePath()
                + ". Pracovný priečinok musí byť koreň Maven projektu.");
        }

        Properties p = new Properties();
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            p.load(reader);
        }

        String baseUrl = normalizedWebUrl(value(p, "base.url", "BASE_URL",
            "http://127.0.0.1:8080/"));
        String workspace = requiredText(p, "test.workspace", "TEST_WORKSPACE");
        if (!workspace.matches("run-[A-Za-z0-9_-]+")) {
            throw new IllegalArgumentException(
                "test.workspace musí byť konkrétny izolovaný run-* priestor.");
        }

        String email = requiredText(p, "test.email", "TEST_EMAIL");
        String password = requiredSecret(p, "test.password", "TEST_PASSWORD");

        int timeoutSeconds = integer(p, "timeout.seconds", "TIMEOUT_SECONDS", 30, 1, 120);
        String browser = value(p, "browser", "BROWSER", "chrome").toLowerCase(Locale.ROOT);
        if (!browser.equals("chrome") && !browser.equals("edge") && !browser.equals("firefox")) {
            throw new IllegalArgumentException("browser musí byť chrome, edge alebo firefox.");
        }

        String dbUrl = requiredText(p, "db.url", "DB_URL");
        validateJdbcUrl(dbUrl);
        String dbUser = requiredText(p, "db.user", "DB_USER");
        String dbPassword = requiredSecret(p, "db.password", "DB_PASSWORD");

        return new TestConfig(
            baseUrl,
            workspace,
            email,
            password,
            browser,
            bool(p, "headless", "HEADLESS", false),
            Duration.ofSeconds(timeoutSeconds),
            bool(p, "cleanup.order", "CLEANUP_ORDER", true),
            value(p, "driver.path", "DRIVER_PATH", ""),
            value(p, "browser.binary", "BROWSER_BINARY", ""),
            dbUrl,
            dbUser,
            dbPassword,
            bool(p, "db.verify.readonly", "DB_VERIFY_READONLY", true)
        );
    }

    private static String normalizedWebUrl(String value) {
        URI uri = URI.create(value);
        if (!("http".equalsIgnoreCase(uri.getScheme())
                || "https".equalsIgnoreCase(uri.getScheme()))
                || uri.getHost() == null
                || uri.getUserInfo() != null
                || uri.getRawQuery() != null
                || uri.getRawFragment() != null) {
            throw new IllegalArgumentException(
                "base.url musí byť HTTP(S) adresa webu bez údajov, query a fragmentu.");
        }
        return value.endsWith("/") ? value : value + "/";
    }

    private static void validateJdbcUrl(String value) {
        if (!value.startsWith("jdbc:mariadb://")) {
            throw new IllegalArgumentException(
                "db.url musí používať formát jdbc:mariadb://host:port/databaza.");
        }
        URI uri = URI.create(value.substring("jdbc:".length()));
        if (uri.getRawAuthority() == null || uri.getRawAuthority().isBlank()
                || uri.getPath() == null || uri.getPath().length() <= 1
                || uri.getUserInfo() != null) {
            throw new IllegalArgumentException(
                "db.url musí obsahovať host a databázu a nesmie obsahovať prihlasovacie údaje.");
        }
        String query = uri.getRawQuery();
        if (query != null) {
            String lower = query.toLowerCase(Locale.ROOT);
            if (lower.contains("password=") || lower.contains("user=")) {
                throw new IllegalArgumentException(
                    "Používateľa a heslo nedávajte do db.url; použite db.user a db.password.");
            }
        }
    }

    private static String value(
            Properties p, String key, String env, String fallback) {
        String system = System.getProperty(key);
        if (system != null) return system.trim();

        String environment = System.getenv(env);
        if (environment != null) return environment.trim();

        return p.getProperty(key, fallback).trim();
    }

    private static String requiredText(Properties p, String key, String env) {
        String text = value(p, key, env, "");
        if (text.isBlank() || text.startsWith("SEM_")) {
            throw new IllegalArgumentException(
                "Vyplňte " + key + " v config/test.properties alebo " + env + ".");
        }
        return text;
    }

    private static String requiredSecret(Properties p, String key, String env) {
        String system = System.getProperty(key);
        String environment = System.getenv(env);
        String text = system != null ? system
            : environment != null ? environment
            : p.getProperty(key, "");

        if (text.isBlank() || text.trim().startsWith("SEM_")) {
            throw new IllegalArgumentException(
                "Vyplňte " + key + " v config/test.properties alebo " + env + ".");
        }
        return text;
    }

    private static boolean bool(
            Properties p, String key, String env, boolean fallback) {
        String text = value(p, key, env, Boolean.toString(fallback));
        if (!text.equalsIgnoreCase("true") && !text.equalsIgnoreCase("false")) {
            throw new IllegalArgumentException(key + " musí byť true alebo false.");
        }
        return Boolean.parseBoolean(text);
    }

    private static int integer(
            Properties p, String key, String env,
            int fallback, int min, int max) {
        int result;
        try {
            result = Integer.parseInt(value(p, key, env, Integer.toString(fallback)));
        } catch (NumberFormatException error) {
            throw new IllegalArgumentException(key + " musí byť celé číslo.", error);
        }
        if (result < min || result > max) {
            throw new IllegalArgumentException(
                key + " musí byť v rozsahu " + min + " až " + max + ".");
        }
        return result;
    }

    @Override
    public String toString() {
        return "TestConfig[workspace=" + workspace
            + ", browser=" + browser
            + ", credentials=REDACTED]";
    }
}
