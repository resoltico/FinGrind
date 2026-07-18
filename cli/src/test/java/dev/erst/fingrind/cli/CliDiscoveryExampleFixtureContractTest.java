package dev.erst.fingrind.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

/** Locks checked-in discovery template fixtures to the live CLI output surface. */
class CliDiscoveryExampleFixtureContractTest extends FinGrindCliTestSupport {
  private static final ObjectMapper TEST_JSON_MAPPER = new ObjectMapper();

  @Test
  void printRequestTemplate_matchesMachineFixtureAndPublishedExample() throws IOException {
    assertMachineFixtureMatchesCommand(
        "dev/erst/fingrind/cli/machine-fixtures/request-template.json", "print-request-template");
    assertPublishedExampleMatchesCommand(
        "docs/examples/request-template.json", "print-request-template");
  }

  @Test
  void printPlanTemplate_matchesMachineFixtureAndPublishedExample() throws IOException {
    assertMachineFixtureMatchesCommand(
        "dev/erst/fingrind/cli/machine-fixtures/plan-template.json", "print-plan-template");
    assertPublishedExampleMatchesCommand(
        "docs/examples/ledger-plan-template.json", "print-plan-template");
  }

  private static void assertMachineFixtureMatchesCommand(String resourcePath, String... command)
      throws IOException {
    ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
    try (var resourceStream = classLoader.getResourceAsStream(resourcePath)) {
      assertEquals(
          normalizeLineEndings(
              new String(
                  Objects.requireNonNull(resourceStream).readAllBytes(), StandardCharsets.UTF_8)),
          normalizeLineEndings(runDiscoveryCommand(command)));
    }
  }

  private static void assertPublishedExampleMatchesCommand(String fixturePath, String... command)
      throws IOException {
    assertEquals(
        TEST_JSON_MAPPER.readTree(
            Files.readString(repositoryRoot().resolve(fixturePath), StandardCharsets.UTF_8)),
        TEST_JSON_MAPPER.readTree(runDiscoveryCommand(command)));
  }

  private static String runDiscoveryCommand(String... command) {
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    FinGrindCli cli =
        cli(new ByteArrayInputStream(new byte[0]), utf8PrintStream(outputStream), fixedClock());
    int exitCode = cli.run(command);
    assertEquals(0, exitCode);
    return outputStream.toString(StandardCharsets.UTF_8);
  }

  private static String normalizeLineEndings(String text) {
    return text.replace("\r\n", "\n").replace('\r', '\n');
  }

  private static Path repositoryRoot() {
    Path directory = Path.of(System.getProperty("user.dir")).toAbsolutePath();
    while (!Files.exists(
        Objects.requireNonNull(directory, "directory").resolve("settings.gradle.kts"))) {
      directory = directory.getParent();
    }
    return directory;
  }
}
