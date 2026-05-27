package dev.erst.fingrind.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/** Shared workflow and fixture helpers for public CLI docs and example-contract tests. */
class CliPublicDocsContractSupport extends FinGrindCliTestSupport {
  private static final Pattern UUID_PATTERN =
      Pattern.compile(
          "\\b[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}\\b");

  protected static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  protected JsonNode runJsonCommand(String... arguments) throws IOException {
    return OBJECT_MAPPER.readTree(runPlainCommand(arguments));
  }

  protected JsonNode runJsonCommandExpectingExit(int expectedExitCode, String... arguments)
      throws IOException {
    return OBJECT_MAPPER.readTree(runPlainCommand(expectedExitCode, arguments));
  }

  protected JsonNode runRawJsonCommand(String... arguments) throws IOException {
    return OBJECT_MAPPER.readTree(runPlainCommand(arguments));
  }

  protected String runPlainCommand(String... arguments) {
    return runPlainCommand(0, arguments);
  }

  protected String runPlainCommand(int expectedExitCode, String... arguments) {
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    FinGrindCli cli =
        cli(new ByteArrayInputStream(new byte[0]), utf8PrintStream(outputStream), fixedClock());
    int exitCode = cli.run(arguments);
    assertEquals(
        expectedExitCode,
        exitCode,
        () ->
            "command failed: "
                + String.join(" ", arguments)
                + "\n"
                + outputStream.toString(StandardCharsets.UTF_8));
    return outputStream.toString(StandardCharsets.UTF_8);
  }

  protected Path copyExampleFixture(String fileName) throws IOException {
    Path source = repositoryRoot().resolve("docs/examples").resolve(fileName);
    Path destination = tempDirectory.resolve(fileName);
    Files.copy(source, destination);
    return destination;
  }

  protected void assertJsonFixture(String fixtureName, JsonNode actual) throws IOException {
    assertTextFixture(fixtureName, canonicalizeJsonFixture(actual).toString());
  }

  protected void assertTextFixture(String fixtureName, String actual) throws IOException {
    String normalizedActual = canonicalizeExampleFixture(normalizeLineEndings(actual));
    Path actualFixtureDirectory = repositoryRoot().resolve("build/tmp/public-doc-fixtures");
    Files.createDirectories(actualFixtureDirectory);
    Files.writeString(
        actualFixtureDirectory.resolve(fixtureName), normalizedActual, StandardCharsets.UTF_8);
    String expected =
        canonicalizeExampleFixture(
            normalizeLineEndings(
                Files.readString(
                    repositoryRoot().resolve("docs/examples").resolve(fixtureName),
                    StandardCharsets.UTF_8)));
    assertEquals(expected, normalizedActual, () -> "Fixture drift: docs/examples/" + fixtureName);
  }

  protected void recordJsonFixture(
      Map<String, String> recordedFixtures, String fixtureName, JsonNode actual)
      throws IOException {
    recordTextFixture(recordedFixtures, fixtureName, canonicalizeJsonFixture(actual).toString());
  }

  protected void recordTextFixture(
      Map<String, String> recordedFixtures, String fixtureName, String actual) throws IOException {
    String normalizedActual = canonicalizeExampleFixture(normalizeLineEndings(actual));
    writeActualFixture(fixtureName, normalizedActual);
    recordedFixtures.put(fixtureName, normalizedActual);
  }

  protected void assertRecordedFixtures(Map<String, String> recordedFixtures) throws IOException {
    List<Throwable> failures = new ArrayList<>();
    for (Map.Entry<String, String> entry : recordedFixtures.entrySet()) {
      String fixtureName = entry.getKey();
      String expected =
          canonicalizeExampleFixture(
              normalizeLineEndings(
                  Files.readString(
                      repositoryRoot().resolve("docs/examples").resolve(fixtureName),
                      StandardCharsets.UTF_8)));
      try {
        assertEquals(
            expected, entry.getValue(), () -> "Fixture drift: docs/examples/" + fixtureName);
      } catch (AssertionError failure) {
        failures.add(failure);
      }
    }
    if (!failures.isEmpty()) {
      AssertionError aggregate =
          new AssertionError("Published example fixtures diverged from generated live output.");
      failures.forEach(aggregate::addSuppressed);
      throw aggregate;
    }
  }

  protected static void replaceReversalPriorPostingId(Path requestFile, String postingId)
      throws IOException {
    ObjectNode root =
        (ObjectNode) OBJECT_MAPPER.readTree(Files.readString(requestFile, StandardCharsets.UTF_8));
    ((ObjectNode) root.path("reversal")).put("priorPostingId", postingId);
    Files.writeString(
        requestFile,
        OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(root) + "\n",
        StandardCharsets.UTF_8);
  }

  protected static void assertPostingIdsContain(
      JsonNode postings, String expectedPostingId, String expectedReversalPostingId) {
    boolean foundPosting = false;
    boolean foundReversalPosting = false;
    for (JsonNode posting : postings) {
      String postingId = posting.path("postingId").stringValue();
      if (expectedPostingId.equals(postingId)) {
        foundPosting = true;
      }
      if (expectedReversalPostingId.equals(postingId)) {
        foundReversalPosting = true;
      }
    }
    assertTrue(foundPosting, () -> "Missing posting id " + expectedPostingId + " in listing");
    assertTrue(
        foundReversalPosting,
        () -> "Missing reversal posting id " + expectedReversalPostingId + " in listing");
  }

  protected static String extractFencedBlock(String document, String marker, String language) {
    int markerIndex = document.indexOf(marker);
    assertTrue(markerIndex >= 0, () -> "Missing marker: " + marker);
    String fence = "```" + language + "\n";
    int fenceStart = document.indexOf(fence, markerIndex);
    assertTrue(fenceStart >= 0, () -> "Missing fenced block after marker: " + marker);
    int contentStart = fenceStart + fence.length();
    int fenceEnd = document.indexOf("\n```", contentStart);
    assertTrue(fenceEnd >= 0, () -> "Missing closing fence after marker: " + marker);
    return document.substring(contentStart, fenceEnd).strip() + "\n";
  }

  protected static String normalizeLineEndings(String text) {
    return text.replace("\r\n", "\n").replace('\r', '\n');
  }

  protected static Path repositoryRoot() {
    String launchWorkingDirectory = System.getenv("PWD");
    if (launchWorkingDirectory != null) {
      Path launchPath = Path.of(launchWorkingDirectory).toAbsolutePath();
      if (Files.exists(launchPath.resolve("settings.gradle.kts"))) {
        return launchPath;
      }
    }
    Path directory = Path.of(System.getProperty("user.dir")).toAbsolutePath();
    while (!Files.exists(
        Objects.requireNonNull(directory, "directory").resolve("settings.gradle.kts"))) {
      directory = directory.getParent();
    }
    return directory;
  }

  protected String canonicalizeExampleFixture(String text) {
    String pathCanonicalized =
        canonicalizeOwnedTemporaryPaths(trimSingleTerminalNewline(normalizeLineEndings(text)));
    return canonicalizeGeneratedIds(pathCanonicalized);
  }

  protected JsonNode canonicalizeJsonFixture(JsonNode actual) {
    if (actual.isObject()) {
      ObjectNode actualObject = (ObjectNode) actual;
      ObjectNode canonical = OBJECT_MAPPER.createObjectNode();
      actualObject
          .properties()
          .forEach(
              entry -> canonical.set(entry.getKey(), canonicalizeJsonFixture(entry.getValue())));
      return canonical;
    }
    if (actual.isArray()) {
      ArrayNode canonical = OBJECT_MAPPER.createArrayNode();
      actual.forEach(entry -> canonical.add(canonicalizeJsonFixture(entry)));
      return canonical;
    }
    if (actual.isTextual()) {
      return OBJECT_MAPPER
          .getNodeFactory()
          .textNode(canonicalizeOwnedTemporaryPaths(actual.textValue()));
    }
    return actual;
  }

  private static void writeActualFixture(String fixtureName, String normalizedActual)
      throws IOException {
    Path actualFixtureDirectory = repositoryRoot().resolve("build/tmp/public-doc-fixtures");
    Files.createDirectories(actualFixtureDirectory);
    Files.writeString(
        actualFixtureDirectory.resolve(fixtureName), normalizedActual, StandardCharsets.UTF_8);
  }

  private static String trimSingleTerminalNewline(String text) {
    return text.endsWith("\n") ? text.substring(0, text.length() - 1) : text;
  }

  private String canonicalizeOwnedTemporaryPaths(String text) {
    return text.contains("<redacted>") ? text.replace('\\', '/') : text;
  }

  private static String canonicalizeGeneratedIds(String text) {
    Matcher matcher = UUID_PATTERN.matcher(text);
    Map<String, String> replacements = new ConcurrentHashMap<>();
    StringBuilder normalized = new StringBuilder();
    while (matcher.find()) {
      String matchedUuid = matcher.group();
      String canonicalUuid =
          replacements.computeIfAbsent(
              matchedUuid, ignored -> canonicalUuid(replacements.size() + 1));
      matcher.appendReplacement(normalized, canonicalUuid);
    }
    matcher.appendTail(normalized);
    return normalized.toString();
  }

  private static String canonicalUuid(int ordinal) {
    return String.format("018f0000-0000-7000-8000-%012d", ordinal);
  }
}
