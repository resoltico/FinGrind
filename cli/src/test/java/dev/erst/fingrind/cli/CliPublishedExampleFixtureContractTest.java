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
import tools.jackson.databind.node.ObjectNode;

/**
 * Locks checked-in public example fixtures to live CLI workflows with deterministic normalization.
 */
class CliPublishedExampleFixtureContractTest extends CliPublicDocsContractSupport {
  @Test
  void publishedExamples_matchCheckedInFixtures() throws IOException {
    Map<String, String> recordedFixtures = new ConcurrentHashMap<>();
    Path bookFile = tempDirectory.resolve("books").resolve("acme.sqlite");
    Path bookKeyFile = tempDirectory.resolve("keys").resolve("acme.book-key");
    Path declareCashFile = copyExampleFixture("declare-account-cash.json");
    Path declareRevenueFile = copyExampleFixture("declare-account-revenue.json");
    Path postingRequestFile = copyExampleFixture("basic-posting-request.json");
    Path unknownAccountRequestFile = copyExampleFixture("unknown-account-request.json");
    Path reversalRequestFile = copyExampleFixture("reversal-request.json");
    Path planRequestFile = copyExampleFixture("ledger-plan-request.json");
    Path queryPlanRequestFile = copyExampleFixture("ledger-plan-query-request.json");
    Path assertionFailurePlanRequestFile =
        tempDirectory.resolve("ledger-plan-assertion-failed.json");
    Path brokenBookFile = tempDirectory.resolve("books").resolve("broken.sqlite");
    Path brokenBookKeyFile = tempDirectory.resolve("keys").resolve("broken.book-key");
    Path planBookFile = tempDirectory.resolve("books").resolve("acme-plan.sqlite");
    Path queryPlanBookFile = tempDirectory.resolve("books").resolve("acme-plan-query.sqlite");
    Path assertionFailurePlanBookFile =
        tempDirectory.resolve("books").resolve("acme-plan-assertion.sqlite");
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
            "1000",
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
            "1000"));
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
            "1000",
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
            "1000",
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
            "--effective-date-from",
            "2026-04-07",
            "--effective-date-to",
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
            "--effective-date-from",
            "2026-04-07",
            "--effective-date-to",
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
    recordTextFixture(
        recordedFixtures,
        "interactive-prompt-unavailable-error.txt",
        runPlainCommand(
            5,
            "inspect-book",
            "--book-file",
            bookFile.toString(),
            "--book-passphrase-prompt",
            "--output",
            "text"));

    replaceReversalPriorPostingId(reversalRequestFile, postingId);
    JsonNode reversal =
        runJsonCommand(
            "post-entry",
            "--book-file",
            bookFile.toString(),
            "--book-key-file",
            bookKeyFile.toString(),
            "--request-file",
            reversalRequestFile.toString());
    String reversalPostingId = reversal.path("payload").path("postingId").stringValue();
    assertFalse(reversalPostingId.isBlank());

    recordJsonFixture(
        recordedFixtures,
        "execute-plan-committed-response.json",
        runJsonCommand(
            "execute-plan",
            "--book-file",
            planBookFile.toString(),
            "--book-key-file",
            bookKeyFile.toString(),
            "--result-detail",
            "full",
            "--request-file",
            planRequestFile.toString()));
    recordJsonFixture(
        recordedFixtures,
        "execute-plan-query-response.json",
        runJsonCommand(
            "execute-plan",
            "--book-file",
            queryPlanBookFile.toString(),
            "--book-key-file",
            bookKeyFile.toString(),
            "--result-detail",
            "full",
            "--request-file",
            queryPlanRequestFile.toString()));

    Files.copy(planRequestFile, assertionFailurePlanRequestFile);
    ObjectNode failingPlan =
        (ObjectNode)
            OBJECT_MAPPER.readTree(
                Files.readString(assertionFailurePlanRequestFile, StandardCharsets.UTF_8));
    ((ObjectNode) failingPlan.path("steps").get(4).path("assertion").path("netAmount"))
        .put("minorUnits", "11");
    Files.writeString(
        assertionFailurePlanRequestFile,
        OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(failingPlan) + "\n",
        StandardCharsets.UTF_8);
    recordJsonFixture(
        recordedFixtures,
        "execute-plan-assertion-failed-response.json",
        runJsonCommandExpectingExit(
            3,
            "execute-plan",
            "--book-file",
            assertionFailurePlanBookFile.toString(),
            "--book-key-file",
            bookKeyFile.toString(),
            "--result-detail",
            "full",
            "--request-file",
            assertionFailurePlanRequestFile.toString()));
    assertRecordedFixtures(recordedFixtures);
  }
}
