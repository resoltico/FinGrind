package dev.erst.fingrind.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/**
 * Locks checked-in public example fixtures to live CLI workflows with deterministic normalization.
 */
class CliPublishedExampleFixtureContractTest extends CliPublicDocsContractSupport {
  @Test
  void publishedExamples_matchCheckedInFixtures() throws IOException {
    Map<String, String> recordedFixtures = new ConcurrentHashMap<>();
    PublishedFixtureScenario scenario = prepareScenario();
    initializeBookFixtures(recordedFixtures, scenario);
    recordPostingAndReportFixtures(recordedFixtures, scenario);
    recordDiagnosticsFixtures(recordedFixtures, scenario);
    assertRecordedFixtures(recordedFixtures);
  }

  private void initializeBookFixtures(
      Map<String, String> recordedFixtures, PublishedFixtureScenario scenario) throws IOException {
    JsonNode openBook =
        runJsonCommand(openBookKeyFileArguments(scenario.bookFile(), scenario.bookKeyFile()));
    assertEquals("ok", openBook.path("status").stringValue());
    recordJsonFixture(
        recordedFixtures,
        "inspect-book-response.json",
        runJsonCommand(
            "inspect-book",
            "--book-file",
            scenario.bookFile().toString(),
            "--book-key-file",
            scenario.bookKeyFile().toString()));
    declareExampleAccount(scenario.bookFile(), scenario.bookKeyFile(), scenario.declareCashFile());
    declareExampleAccount(
        scenario.bookFile(), scenario.bookKeyFile(), scenario.declareRevenueFile());
    recordJsonFixture(
        recordedFixtures,
        "list-accounts-response.json",
        runJsonCommand(
            "list-accounts",
            "--book-file",
            scenario.bookFile().toString(),
            "--book-key-file",
            scenario.bookKeyFile().toString(),
            "--limit",
            "1"));
  }

  private void recordPostingAndReportFixtures(
      Map<String, String> recordedFixtures, PublishedFixtureScenario scenario) throws IOException {
    JsonNode committed =
        runJsonCommand(
            "record-sale-settled",
            "--book-file",
            scenario.bookFile().toString(),
            "--book-key-file",
            scenario.bookKeyFile().toString(),
            "--request-file",
            scenario.postingRequestFile().toString());
    String postingId = committed.path("payload").path("postingId").stringValue();
    assertFalse(postingId.isBlank());
    recordJsonFixture(recordedFixtures, "basic-posting-committed-response.json", committed);
    recordJsonFixture(
        recordedFixtures,
        "get-posting-response.json",
        runJsonCommand(
            "get-posting",
            "--book-file",
            scenario.bookFile().toString(),
            "--book-key-file",
            scenario.bookKeyFile().toString(),
            "--posting-id",
            postingId));
    recordJsonFixture(
        recordedFixtures,
        "list-postings-response.json",
        runJsonCommand(
            "list-postings",
            "--book-file",
            scenario.bookFile().toString(),
            "--book-key-file",
            scenario.bookKeyFile().toString(),
            "--account-code",
            "cash",
            "--limit",
            "25"));
    recordJsonFixture(
        recordedFixtures,
        "account-balance-response.json",
        runJsonCommand(
            "account-balance",
            "--book-file",
            scenario.bookFile().toString(),
            "--book-key-file",
            scenario.bookKeyFile().toString(),
            "--account-code",
            "cash"));
    recordJsonFixture(
        recordedFixtures,
        "trial-balance-response.json",
        runJsonCommand(
            "trial-balance",
            "--book-file",
            scenario.bookFile().toString(),
            "--book-key-file",
            scenario.bookKeyFile().toString(),
            "--effective-date-as-of",
            "2026-04-07"));
    recordTextFixture(
        recordedFixtures,
        "trial-balance-text.txt",
        runPlainCommand(
            "trial-balance",
            "--book-file",
            scenario.bookFile().toString(),
            "--book-key-file",
            scenario.bookKeyFile().toString(),
            "--effective-date-as-of",
            "2026-04-07",
            "--output",
            "text"));
    recordJsonFixture(
        recordedFixtures,
        "account-ledger-response.json",
        runJsonCommand(
            "account-ledger",
            "--book-file",
            scenario.bookFile().toString(),
            "--book-key-file",
            scenario.bookKeyFile().toString(),
            "--account-code",
            "cash",
            "--effective-date-to",
            "2026-04-07"));
    recordTextFixture(
        recordedFixtures,
        "account-ledger.csv",
        runPlainCommand(
            "account-ledger",
            "--book-file",
            scenario.bookFile().toString(),
            "--book-key-file",
            scenario.bookKeyFile().toString(),
            "--account-code",
            "cash",
            "--effective-date-to",
            "2026-04-07",
            "--output",
            "csv"));
    recordJsonFixture(
        recordedFixtures,
        "period-summary-response.json",
        runJsonCommand(
            "period-summary",
            "--book-file",
            scenario.bookFile().toString(),
            "--book-key-file",
            scenario.bookKeyFile().toString(),
            "--period-start",
            "2026-04-07",
            "--period-end",
            "2026-04-07"));
    recordTextFixture(
        recordedFixtures,
        "period-summary-text.txt",
        runPlainCommand(
            "period-summary",
            "--book-file",
            scenario.bookFile().toString(),
            "--book-key-file",
            scenario.bookKeyFile().toString(),
            "--period-start",
            "2026-04-07",
            "--period-end",
            "2026-04-07",
            "--output",
            "text"));
  }

  private void recordDiagnosticsFixtures(
      Map<String, String> recordedFixtures, PublishedFixtureScenario scenario) throws IOException {
    recordJsonFixture(
        recordedFixtures,
        "account-state-violations-response.json",
        runJsonDiagnosticsCommandExpectingExit(
            2,
            "preflight-entry",
            "--book-file",
            scenario.bookFile().toString(),
            "--book-key-file",
            scenario.bookKeyFile().toString(),
            "--request-file",
            scenario.unknownAccountRequestFile().toString()));
    recordTextFixture(
        recordedFixtures,
        "account-state-violations-text.txt",
        runDiagnosticsCommand(
            2,
            "preflight-entry",
            "--book-file",
            scenario.bookFile().toString(),
            "--book-key-file",
            scenario.bookKeyFile().toString(),
            "--request-file",
            scenario.unknownAccountRequestFile().toString(),
            "--output",
            "text"));
    recordJsonFixture(
        recordedFixtures,
        "entry-semantics-violations-response.json",
        runJsonDiagnosticsCommandExpectingExit(
            2,
            "preflight-entry",
            "--book-file",
            scenario.bookFile().toString(),
            "--book-key-file",
            scenario.bookKeyFile().toString(),
            "--request-file",
            scenario.entrySemanticsRequestFile().toString()));
    recordTextFixture(
        recordedFixtures,
        "entry-semantics-violations-text.txt",
        runDiagnosticsCommand(
            2,
            "preflight-entry",
            "--book-file",
            scenario.bookFile().toString(),
            "--book-key-file",
            scenario.bookKeyFile().toString(),
            "--request-file",
            scenario.entrySemanticsRequestFile().toString(),
            "--output",
            "text"));
    recordJsonFixture(
        recordedFixtures,
        "invalid-page-cursor-error.json",
        runJsonDiagnosticsCommandExpectingExit(
            1,
            "list-postings",
            "--book-file",
            scenario.bookFile().toString(),
            "--book-key-file",
            scenario.bookKeyFile().toString(),
            "--cursor",
            "definitely-not-a-valid-cursor"));
    recordJsonFixture(
        recordedFixtures,
        "protected-book-verification-failed-error.json",
        runJsonDiagnosticsCommandExpectingExit(
            6,
            "inspect-book",
            "--book-file",
            scenario.brokenBookFile().toString(),
            "--book-key-file",
            scenario.brokenBookKeyFile().toString()));
    recordTextFixture(
        recordedFixtures,
        "interactive-prompt-unavailable-error.txt",
        runDiagnosticsCommand(
            5,
            "inspect-book",
            "--book-file",
            scenario.bookFile().toString(),
            "--book-passphrase-prompt",
            "--output",
            "text"));
  }

  private PublishedFixtureScenario prepareScenario() throws IOException {
    Path bookFile = tempDirectory.resolve("books").resolve("acme.sqlite");
    Path bookKeyFile = tempDirectory.resolve("keys").resolve("acme.book-key");
    Path declareCashFile = copyExampleFixture("declare-account-supplemental-cash-reserve.json");
    Path declareRevenueFile = copyExampleFixture("declare-account-supplemental-misc-revenue.json");
    Path postingRequestFile = copyExampleFixture("basic-posting-request.json");
    Path unknownAccountRequestFile = copyExampleFixture("unknown-account-request.json");
    Path entrySemanticsRequestFile =
        copyExampleFixture("entry-semantics-multi-violation-request.json");
    Path brokenBookFile = tempDirectory.resolve("books").resolve("broken.sqlite");
    Path brokenBookKeyFile = tempDirectory.resolve("keys").resolve("broken.book-key");
    Path booksDirectory = java.util.Objects.requireNonNull(bookFile.getParent(), "booksDirectory");
    Files.createDirectories(booksDirectory);
    CliTestPrivateDirectorySupport.hardenOwnerOnlyDirectory(booksDirectory);
    runJsonCommand("generate-book-key-file", "--new-book-key-file", bookKeyFile.toString());
    runJsonCommand("generate-book-key-file", "--new-book-key-file", brokenBookKeyFile.toString());
    Files.writeString(brokenBookFile, "not-a-protected-book", StandardCharsets.UTF_8);
    return new PublishedFixtureScenario(
        bookFile,
        bookKeyFile,
        declareCashFile,
        declareRevenueFile,
        postingRequestFile,
        unknownAccountRequestFile,
        entrySemanticsRequestFile,
        brokenBookFile,
        brokenBookKeyFile);
  }

  private void declareExampleAccount(Path bookFile, Path bookKeyFile, Path requestFile)
      throws IOException {
    runJsonCommand(
        "declare-account",
        "--book-file",
        bookFile.toString(),
        "--book-key-file",
        bookKeyFile.toString(),
        "--request-file",
        requestFile.toString());
  }

  private record PublishedFixtureScenario(
      Path bookFile,
      Path bookKeyFile,
      Path declareCashFile,
      Path declareRevenueFile,
      Path postingRequestFile,
      Path unknownAccountRequestFile,
      Path entrySemanticsRequestFile,
      Path brokenBookFile,
      Path brokenBookKeyFile) {}
}
