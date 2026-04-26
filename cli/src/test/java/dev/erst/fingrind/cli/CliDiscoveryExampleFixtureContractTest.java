package dev.erst.fingrind.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import org.jspecify.annotations.NullUnmarked;
import org.junit.jupiter.api.Test;

/** Locks checked-in discovery template fixtures to the live CLI output surface. */
@NullUnmarked
class CliDiscoveryExampleFixtureContractTest extends FinGrindCliTestSupport {
  @Test
  void printRequestTemplate_matchesCheckedInFixture() throws IOException {
    assertFixtureMatchesCommand("docs/examples/request-template.json", "print-request-template");
  }

  @Test
  void printPlanTemplate_matchesCheckedInFixture() throws IOException {
    assertFixtureMatchesCommand("docs/examples/ledger-plan-template.json", "print-plan-template");
  }

  private static void assertFixtureMatchesCommand(String fixturePath, String command)
      throws IOException {
    assertEquals(
        normalizeLineEndings(
            Files.readString(repositoryRoot().resolve(fixturePath), StandardCharsets.UTF_8)),
        normalizeLineEndings(runDiscoveryCommand(command)));
  }

  private static String runDiscoveryCommand(String command) {
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    FinGrindCli cli =
        new FinGrindCli(
            new ByteArrayInputStream(new byte[0]), utf8PrintStream(outputStream), fixedClock());

    int exitCode = cli.run(new String[] {command});

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
