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
    Path bookFile = tempDirectory.resolve("books").resolve("acme.sqlite");
    Path bookKeyFile = tempDirectory.resolve("keys").resolve("acme.book-key");
    Path declareCashFile = copyExampleFixture("declare-account-supplemental-cash-reserve.json");
    Path declareRevenueFile = copyExampleFixture("declare-account-supplemental-misc-revenue.json");
    Path postingRequestFile = copyExampleFixture("basic-posting-request.json");
    Path unknownAccountRequestFile = copyExampleFixture("unknown-account-request.json");
    Path brokenBookFile = tempDirectory.resolve("books").resolve("broken.sqlite");
    Path brokenBookKeyFile = tempDirectory.resolve("keys").resolve("broken.book-key");
    Path booksDirectory = java.util.Objects.requireNonNull(bookFile.getParent(), "booksDirectory");

    Files.createDirectories(booksDirectory);
    CliTestPrivateDirectorySupport.hardenOwnerOnlyDirectory(booksDirectory);
    runJsonCommand("generate-book-key-file", "--book-key-file", bookKeyFile.toString());
    runJsonCommand("generate-book-key-file", "--book-key-file", brokenBookKeyFile.toString());
    Files.writeString(brokenBookFile, "not-a-protected-book", StandardCharsets.UTF_8);

    JsonNode openBook = runJsonCommand(openBookKeyFileArguments(bookFile, bookKeyFile));
    assertEquals("ok", openBook.path("status").stringValue());
    recordJsonFixture(
        recordedFixtures,
        "inspect-book-response.json",
        runJsonCommand(
            "inspect-book",
            "--book-file",
            bookFile.toString(),
            "--book-key-file",
            bookKeyFile.toString()));

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

    recordJsonFixture(
        recordedFixtures,
        "list-accounts-response.json",
        runJsonCommand(
            "list-accounts",
            "--book-file",
            bookFile.toString(),
            "--book-key-file",
            bookKeyFile.toString(),
            "--limit",
            "1"));

    JsonNode committed =
        runJsonCommand(
            "post-entry",
            "--book-file",
            bookFile.toString(),
            "--book-key-file",
            bookKeyFile.toString(),
            "--request-file",
            postingRequestFile.toString());
    String postingId = committed.path("payload").path("postingId").stringValue();
    assertFalse(postingId.isBlank());
    recordJsonFixture(recordedFixtures, "basic-posting-committed-response.json", committed);

    recordJsonFixture(
        recordedFixtures,
        "get-posting-response.json",
        runJsonCommand(
            "get-posting",
            "--book-file",
            bookFile.toString(),
            "--book-key-file",
            bookKeyFile.toString(),
            "--posting-id",
            postingId));
    recordJsonFixture(
        recordedFixtures,
        "list-postings-response.json",
        runJsonCommand(
            "list-postings",
            "--book-file",
            bookFile.toString(),
            "--book-key-file",
            bookKeyFile.toString(),
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
            bookFile.toString(),
            "--book-key-file",
            bookKeyFile.toString(),
            "--account-code",
            "cash"));
    recordJsonFixture(
        recordedFixtures,
        "trial-balance-response.json",
        runJsonCommand(
            "trial-balance",
            "--book-file",
            bookFile.toString(),
            "--book-key-file",
            bookKeyFile.toString(),
            "--effective-date-as-of",
            "2026-04-08"));
    recordTextFixture(
        recordedFixtures,
        "trial-balance-text.txt",
        runPlainCommand(
            "trial-balance",
            "--book-file",
            bookFile.toString(),
            "--book-key-file",
            bookKeyFile.toString(),
            "--effective-date-as-of",
            "2026-04-08",
            "--output",
            "text"));
    recordJsonFixture(
        recordedFixtures,
        "account-ledger-response.json",
        runJsonCommand(
            "account-ledger",
            "--book-file",
            bookFile.toString(),
            "--book-key-file",
            bookKeyFile.toString(),
            "--account-code",
            "cash",
            "--effective-date-to",
            "2026-04-08"));
    recordTextFixture(
        recordedFixtures,
        "account-ledger.csv",
        runPlainCommand(
            "account-ledger",
            "--book-file",
            bookFile.toString(),
            "--book-key-file",
            bookKeyFile.toString(),
            "--account-code",
            "cash",
            "--effective-date-to",
            "2026-04-08",
            "--output",
            "csv"));
    recordJsonFixture(
        recordedFixtures,
        "period-summary-response.json",
        runJsonCommand(
            "period-summary",
            "--book-file",
            bookFile.toString(),
            "--book-key-file",
            bookKeyFile.toString(),
            "--period-start",
            "2026-04-07",
            "--period-end",
            "2026-04-08"));
    recordTextFixture(
        recordedFixtures,
        "period-summary-text.txt",
        runPlainCommand(
            "period-summary",
            "--book-file",
            bookFile.toString(),
            "--book-key-file",
            bookKeyFile.toString(),
            "--period-start",
            "2026-04-07",
            "--period-end",
            "2026-04-08",
            "--output",
            "text"));

    recordJsonFixture(
        recordedFixtures,
        "account-state-violations-response.json",
        runJsonCommandExpectingExit(
            2,
            "preflight-entry",
            "--book-file",
            bookFile.toString(),
            "--book-key-file",
            bookKeyFile.toString(),
            "--request-file",
            unknownAccountRequestFile.toString()));
    recordJsonFixture(
        recordedFixtures,
        "invalid-page-cursor-error.json",
        runJsonCommandExpectingExit(
            1,
            "list-postings",
            "--book-file",
            bookFile.toString(),
            "--book-key-file",
            bookKeyFile.toString(),
            "--cursor",
            "definitely-not-a-valid-cursor"));
    recordJsonFixture(
        recordedFixtures,
        "protected-book-verification-failed-error.json",
        runJsonCommandExpectingExit(
            6,
            "inspect-book",
            "--book-file",
            brokenBookFile.toString(),
            "--book-key-file",
            brokenBookKeyFile.toString()));
    recordJsonFixture(
        recordedFixtures,
        "interactive-prompt-unavailable-error.json",
        runJsonCommandExpectingExit(
            5,
            "inspect-book",
            "--book-file",
            bookFile.toString(),
            "--book-passphrase-prompt",
            "--output",
            "text"));
    assertRecordedFixtures(recordedFixtures);
  }
}
