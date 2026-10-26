package sk.testlab.cvicenie05.support;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.WebDriver;

/**
 * Diagnostické výstupy testu.
 * Heslá sa maskujú v textoch; screenshot môže obsahovať simulované údaje z GUI.
 */
public final class Artifacts {
    public final String runId = UUID.randomUUID().toString();

    private final Path directory;
    private final List<String> secrets;
    private final Properties metadata = new Properties();

    public Artifacts(String... secrets) throws IOException {
        this.secrets = new ArrayList<>();
        for (String secret : secrets) {
            if (secret != null && !secret.isEmpty()) this.secrets.add(secret);
        }
        this.secrets.sort(Comparator.comparingInt(String::length).reversed());

        directory = Path.of("target", "artifacts", "run-" + runId);
        Files.createDirectories(directory);
        put("started.utc", Instant.now().toString());
        System.out.println("Artefakty: " + directory.toAbsolutePath());
    }

    public void put(String key, Object value) throws IOException {
        metadata.setProperty(key, redact(String.valueOf(value)));
        try (Writer writer = Files.newBufferedWriter(
                directory.resolve("run.properties"),
                StandardCharsets.UTF_8)) {
            metadata.store(writer, "TestLab Shop - cvicenie 05");
        }
    }

    public void write(String name, String text) throws IOException {
        Files.writeString(
            directory.resolve(name),
            redact(text),
            StandardCharsets.UTF_8
        );
    }

    public void capture(WebDriver driver, String prefix, Throwable failure) {
        if (failure != null) {
            StringWriter stack = new StringWriter();
            failure.printStackTrace(new PrintWriter(stack));
            attempt(() -> write(prefix + ".txt", stack.toString()), failure);
        }

        if (driver == null) {
            attempt(() -> write(
                prefix + "-browser-unavailable.txt",
                "Relácia prehliadača nebola vytvorená."
            ), failure);
            return;
        }

        attempt(() -> Files.write(
            directory.resolve(prefix + ".png"),
            ((TakesScreenshot) driver).getScreenshotAs(OutputType.BYTES)
        ), failure);

        attempt(() -> write(prefix + "-url.txt", driver.getCurrentUrl()), failure);

        attempt(() -> {
            String html = String.valueOf(
                ((JavascriptExecutor) driver).executeScript("""
                    const root = document.documentElement.cloneNode(true);
                    root.querySelectorAll(
                      'input[type="password"], input[type="hidden"]'
                    ).forEach(e => e.setAttribute('value', '[REDACTED]'));
                    return '<!doctype html>\\n' + root.outerHTML;
                    """)
            );
            write(prefix + ".html", html);
        }, failure);
    }

    private String redact(String text) {
        String result = text;
        for (String secret : secrets) {
            result = result.replace(secret, "[REDACTED]");
        }
        return result;
    }

    private void attempt(CheckedAction action, Throwable primary) {
        try {
            action.run();
        } catch (Exception problem) {
            if (primary != null) primary.addSuppressed(problem);
            System.err.println(
                "Nepodarilo sa uložiť artefakt: " + redact(problem.toString()));
        }
    }

    @FunctionalInterface
    private interface CheckedAction {
        void run() throws Exception;
    }
}
