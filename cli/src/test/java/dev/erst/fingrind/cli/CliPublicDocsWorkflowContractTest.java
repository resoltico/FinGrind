package dev.erst.fingrind.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import org.jspecify.annotations.NullUnmarked;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Executes the published quick-start and example workflows against the live CLI surface. */
@NullUnmarked
class CliPublicDocsWorkflowContractTest extends FinGrindCliTestSupport {
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  @Test
  void quickStartGuide_describesTheAgentScaffoldAndPublishedWorkflowRuns() throws IOException {
    String guide =
        normalizeLineEndings(
            Files.readString(
                repositoryRoot().resolve("docs/USER_QUICK_START.md"), StandardCharsets.UTF_8));
    assertTrue(guide.contains("replace-before-commit-*"));
    assertTrue(guide.contains("same book is rejected"));
    assertTrue(guide.contains("--request-file ./declare-account-cash.json"));
    assertTrue(guide.contains("--request-file ./declare-account-revenue.json"));

    Path workspace = tempDirectory.resolve("quick-start");
    Path bookFile = workspace.resolve("acme.sqlite");
    Path bookKeyFile = workspace.resolve("acme.book-key");
    Path declareCashFile =
        writeNamedRequest(
            "declare-account-cash.json",
            extractFencedBlock(guide, "Create `./declare-account-cash.json` with:", "json"));
    Path declareRevenueFile =
        writeNamedRequest(
            "declare-account-revenue.json",
            extractFencedBlock(guide, "Create `./declare-account-revenue.json` with:", "json"));
    Path requestFile =
        writeNamedRequest(
            "quick-start-request.json",
            extractFencedBlock(
                guide,
                "Replace the contents of `./request.json` with one balanced entry, for example:",
                "json"));
    Path rawTemplateRequestFile = workspace.resolve("quick-start-request-template.json");

    JsonNode generatedKey =
        runJsonCommand("generate-book-key-file", "--book-key-file", bookKeyFile.toString());
    assertEquals("ok", generatedKey.path("status").stringValue());
    assertGeneratedKeyFileIsSecure(
        bookKeyFile, generatedKey.path("payload").path("permissions").stringValue());

    JsonNode openBook =
        runJsonCommand(
            "open-book",
            "--book-file",
            bookFile.toString(),
            "--book-key-file",
            bookKeyFile.toString());
    assertEquals("ok", openBook.path("status").stringValue());

    JsonNode requestTemplate = runRawJsonCommand("print-request-template");
    assertEquals(
        "replace-before-commit-effective-date",
        requestTemplate.path("effectiveDate").stringValue());
    assertEquals("AGENT", requestTemplate.path("provenance").path("actorType").stringValue());
    assertEquals(
        "replace-before-commit-idempotency-key",
        requestTemplate.path("provenance").path("idempotencyKey").stringValue());
    Files.writeString(
        rawTemplateRequestFile, requestTemplate.toPrettyString(), StandardCharsets.UTF_8);

    runJsonCommand(
        "declare-account",
        "--book-file",
        bookFile.toString(),
        "--book-key-file",
        bookKeyFile.toString(),
        "--request-file",
        declareCashFile.toString());
    runJsonCommand(
        "declare-account",
        "--book-file",
        bookFile.toString(),
        "--book-key-file",
        bookKeyFile.toString(),
        "--request-file",
        declareRevenueFile.toString());

    JsonNode rawTemplateFailure =
        runJsonCommandExpectingExit(
            1,
            "post-entry",
            "--book-file",
            bookFile.toString(),
            "--book-key-file",
            bookKeyFile.toString(),
            "--request-file",
            rawTemplateRequestFile.toString());
    assertEquals("error", rawTemplateFailure.path("status").stringValue());
    assertEquals("invalid-request", rawTemplateFailure.path("code").stringValue());
    assertTrue(rawTemplateFailure.path("message").stringValue().contains("effectiveDate"));
    assertTrue(rawTemplateFailure.path("hint").stringValue().contains("print-request-template"));

    JsonNode preflight =
        runJsonCommand(
            "preflight-entry",
            "--book-file",
            bookFile.toString(),
            "--book-key-file",
            bookKeyFile.toString(),
            "--request-file",
            requestFile.toString());
    assertEquals("preflight-accepted", preflight.path("status").stringValue());

    JsonNode committed =
        runJsonCommand(
            "post-entry",
            "--book-file",
            bookFile.toString(),
            "--book-key-file",
            bookKeyFile.toString(),
            "--request-file",
            requestFile.toString());
    assertEquals("committed", committed.path("status").stringValue());

    String trialBalance =
        runPlainCommand(
            "trial-balance",
            "--book-file",
            bookFile.toString(),
            "--book-key-file",
            bookKeyFile.toString(),
            "--output",
            "human");
    assertTrue(trialBalance.contains("Trial Balance"));
    assertTrue(trialBalance.contains("1000"));
    assertTrue(trialBalance.contains("2000"));
  }

  @Test
  void exampleWorkflowsRemainRunnableAgainstTheLiveCliSurface() throws IOException {
    String examplesGuide =
        normalizeLineEndings(
            Files.readString(
                repositoryRoot().resolve("docs/USER_EXAMPLES.md"), StandardCharsets.UTF_8));
    String requestsGuide =
        normalizeLineEndings(
            Files.readString(
                repositoryRoot().resolve("docs/USER_REQUESTS.md"), StandardCharsets.UTF_8));
    assertTrue(examplesGuide.contains("replace-before-commit-*"));
    assertTrue(examplesGuide.contains("single-use per book"));
    assertTrue(requestsGuide.contains("replace-before-commit-*"));
    assertTrue(requestsGuide.contains("single-use per book"));
    assertFalse(
        requestsGuide.contains(
            "print-plan-template` emits the accepted `execute-plan` request shape directly"));

    Path workspace = tempDirectory.resolve("examples");
    Path bookFile = workspace.resolve("acme.sqlite");
    Path bookKeyFile = workspace.resolve("acme.book-key");
    Path declareCashFile = copyExampleFixture("declare-account-cash.json");
    Path declareRevenueFile = copyExampleFixture("declare-account-revenue.json");
    Path postingRequestFile = copyExampleFixture("basic-posting-request.json");
    Path planBookFile = workspace.resolve("acme-plan.sqlite");
    Path rawPlanTemplateFile = workspace.resolve("raw-ledger-plan-template.json");
    Path planRequestFile = copyExampleFixture("ledger-plan-request.json");
    Path queryPlanBookFile = workspace.resolve("acme-plan-query.sqlite");
    Path queryPlanRequestFile = copyExampleFixture("ledger-plan-query-request.json");

    runJsonCommand("generate-book-key-file", "--book-key-file", bookKeyFile.toString());
    runJsonCommand(
        "open-book", "--book-file", bookFile.toString(), "--book-key-file", bookKeyFile.toString());
    runJsonCommand(
        "declare-account",
        "--book-file",
        bookFile.toString(),
        "--book-key-file",
        bookKeyFile.toString(),
        "--request-file",
        declareCashFile.toString());
    runJsonCommand(
        "declare-account",
        "--book-file",
        bookFile.toString(),
        "--book-key-file",
        bookKeyFile.toString(),
        "--request-file",
        declareRevenueFile.toString());

    JsonNode committed =
        runJsonCommand(
            "post-entry",
            "--book-file",
            bookFile.toString(),
            "--book-key-file",
            bookKeyFile.toString(),
            "--request-file",
            postingRequestFile.toString());
    String postingId = committed.path("postingId").stringValue();
    assertFalse(postingId.isBlank());

    JsonNode listing =
        runJsonCommand(
            "list-postings",
            "--book-file",
            bookFile.toString(),
            "--book-key-file",
            bookKeyFile.toString(),
            "--limit",
            "10");
    assertEquals(
        postingId, listing.path("payload").path("postings").get(0).path("postingId").stringValue());

    JsonNode rawPlanTemplate = runRawJsonCommand("print-plan-template");
    Files.writeString(
        rawPlanTemplateFile, rawPlanTemplate.toPrettyString(), StandardCharsets.UTF_8);
    JsonNode rawPlanFailure =
        runJsonCommandExpectingExit(
            1,
            "execute-plan",
            "--book-file",
            planBookFile.toString(),
            "--book-key-file",
            bookKeyFile.toString(),
            "--request-file",
            rawPlanTemplateFile.toString());
    assertEquals("error", rawPlanFailure.path("status").stringValue());
    assertEquals("invalid-request", rawPlanFailure.path("code").stringValue());
    assertTrue(rawPlanFailure.path("message").stringValue().contains("effectiveDate"));
    assertTrue(rawPlanFailure.path("hint").stringValue().contains("print-plan-template"));

    JsonNode planResult =
        runJsonCommand(
            "execute-plan",
            "--book-file",
            planBookFile.toString(),
            "--book-key-file",
            bookKeyFile.toString(),
            "--request-file",
            planRequestFile.toString());
    assertEquals("plan-committed", planResult.path("status").stringValue());

    JsonNode queryPlanResult =
        runJsonCommand(
            "execute-plan",
            "--book-file",
            queryPlanBookFile.toString(),
            "--book-key-file",
            bookKeyFile.toString(),
            "--request-file",
            queryPlanRequestFile.toString());
    assertEquals("plan-committed", queryPlanResult.path("status").stringValue());
    JsonNode queryFacts =
        queryPlanResult.path("payload").path("journal").path("steps").get(4).path("facts");
    assertEquals("count", queryFacts.get(0).path("name").stringValue());
    assertEquals(1, queryFacts.get(0).path("value").asInt());
  }

  private JsonNode runJsonCommand(String... arguments) throws IOException {
    return OBJECT_MAPPER.readTree(runPlainCommand(arguments));
  }

  private JsonNode runJsonCommandExpectingExit(int expectedExitCode, String... arguments)
      throws IOException {
    return OBJECT_MAPPER.readTree(runPlainCommand(expectedExitCode, arguments));
  }

  private JsonNode runRawJsonCommand(String... arguments) throws IOException {
    return OBJECT_MAPPER.readTree(runPlainCommand(arguments));
  }

  private String runPlainCommand(String... arguments) {
    return runPlainCommand(0, arguments);
  }

  private String runPlainCommand(int expectedExitCode, String... arguments) {
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

  private Path copyExampleFixture(String fileName) throws IOException {
    Path source = repositoryRoot().resolve("docs/examples").resolve(fileName);
    Path destination = tempDirectory.resolve(fileName);
    Files.copy(source, destination);
    return destination;
  }

  private static String extractFencedBlock(String document, String marker, String language) {
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
