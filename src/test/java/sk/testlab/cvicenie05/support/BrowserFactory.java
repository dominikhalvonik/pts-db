package sk.testlab.cvicenie05.support;

import java.io.File;
import java.nio.file.Path;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeDriverService;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.edge.EdgeDriver;
import org.openqa.selenium.edge.EdgeDriverService;
import org.openqa.selenium.edge.EdgeOptions;
import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.firefox.FirefoxOptions;
import org.openqa.selenium.firefox.GeckoDriverService;

/** Vytvorí novú reláciu prehliadača bez zdieľaného používateľského profilu. */
public final class BrowserFactory {
    private BrowserFactory() {}

    public static WebDriver create(TestConfig config) {
        File executable = config.driverPath().isBlank()
            ? null
            : Path.of(config.driverPath()).toFile();

        if (executable != null && !executable.isFile()) {
            throw new IllegalArgumentException(
                "driver.path neukazuje na existujúci súbor.");
        }

        return switch (config.browser()) {
            case "chrome" -> chrome(config, executable);
            case "edge" -> edge(config, executable);
            case "firefox" -> firefox(config, executable);
            default -> throw new IllegalArgumentException(
                "Podporované prehliadače: chrome, edge, firefox.");
        };
    }

    private static WebDriver chrome(TestConfig config, File executable) {
        ChromeOptions options = new ChromeOptions();
        if (config.headless()) options.addArguments("--headless=new");
        if (!config.browserBinary().isBlank()) {
            options.setBinary(config.browserBinary());
        }

        return executable == null
            ? new ChromeDriver(options)
            : new ChromeDriver(
                new ChromeDriverService.Builder()
                    .usingDriverExecutable(executable)
                    .build(),
                options);
    }

    private static WebDriver edge(TestConfig config, File executable) {
        EdgeOptions options = new EdgeOptions();
        if (config.headless()) options.addArguments("--headless=new");
        if (!config.browserBinary().isBlank()) {
            options.setBinary(config.browserBinary());
        }

        return executable == null
            ? new EdgeDriver(options)
            : new EdgeDriver(
                new EdgeDriverService.Builder()
                    .usingDriverExecutable(executable)
                    .build(),
                options);
    }

    private static WebDriver firefox(TestConfig config, File executable) {
        FirefoxOptions options = new FirefoxOptions();
        if (config.headless()) options.addArguments("-headless");
        if (!config.browserBinary().isBlank()) {
            options.setBinary(config.browserBinary());
        }

        return executable == null
            ? new FirefoxDriver(options)
            : new FirefoxDriver(
                new GeckoDriverService.Builder()
                    .usingDriverExecutable(executable)
                    .build(),
                options);
    }
}
