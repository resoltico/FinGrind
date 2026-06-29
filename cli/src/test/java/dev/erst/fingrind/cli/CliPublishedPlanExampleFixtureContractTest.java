package dev.erst.fingrind.cli;

import static org.junit.jupiter.api.Assertions.assertFalse;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

/** Locks published ledger-plan and reversal example fixtures to live CLI workflows. */
class CliPublishedPlanExampleFixtureContractTest extends CliPublicDocsContractSupport {
  @Test
  void publishedPlanExamples_matchCheckedInFixtures() throws IOException {
    Path bookKeyFile = tempDirectory.resolve("keys").resolve("acme.book-key");
    Path planRequestFile = copyExampleFixture("ledger-plan-request.json");
    Path queryPlanRequestFile = copyExampleFixture("ledger-plan-query-request.json");
    Path assertionFailurePlanRequestFile =
        tempDirectory.resolve("ledger-plan-assertion-failed.json");
    Path planBookFile = tempDirectory.resolve("books").resolve("acme-plan.sqlite");
    Path queryPlanBookFile = tempDirectory.resolve("books").resolve("acme-plan-query.sqlite");
    Path assertionFailurePlanBookFile =
        tempDirectory.resolve("books").resolve("acme-plan-assertion.sqlite");

    runJsonCommand("generate-book-key-file", "--book-key-file", bookKeyFile.toString());

    assertJsonFixture(
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
    assertJsonFixture(
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
    ((ObjectNode)
            failingPlan
                .path("steps")
                .get(failingPlan.path("steps").size() - 1)
                .path("assertion")
                .path("netAmount"))
        .put("minorUnits", "11");
    Files.writeString(
        assertionFailurePlanRequestFile,
        OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(failingPlan) + "\n",
        StandardCharsets.UTF_8);
    assertJsonFixture(
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
  }

  @Test
  void publishedReversalExample_matchesLiveWorkflow() throws IOException {
    Path bookFile = tempDirectory.resolve("books").resolve("acme.sqlite");
    Path bookKeyFile = tempDirectory.resolve("keys").resolve("acme.book-key");
    Path declareCashFile = copyExampleFixture("declare-account-supplemental-cash-reserve.json");
    Path declareRevenueFile = copyExampleFixture("declare-account-supplemental-misc-revenue.json");
    Path postingRequestFile = copyExampleFixture("basic-posting-request.json");
    Path reversalRequestFile = copyExampleFixture("reversal-request.json");
    Path booksDirectory = java.util.Objects.requireNonNull(bookFile.getParent(), "booksDirectory");

    Files.createDirectories(booksDirectory);
    CliTestPrivateDirectorySupport.hardenOwnerOnlyDirectory(booksDirectory);
    runJsonCommand("generate-book-key-file", "--book-key-file", bookKeyFile.toString());
    runJsonCommand(openBookKeyFileArguments(bookFile, bookKeyFile));
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
            "record-sale",
            "--book-file",
            bookFile.toString(),
            "--book-key-file",
            bookKeyFile.toString(),
            "--request-file",
            postingRequestFile.toString());
    String postingId = committed.path("payload").path("postingId").stringValue();
    assertFalse(postingId.isBlank());

    replaceReversalPriorPostingId(reversalRequestFile, postingId);
    JsonNode reversal =
        runJsonCommand(
            "record-reversal",
            "--book-file",
            bookFile.toString(),
            "--book-key-file",
            bookKeyFile.toString(),
            "--request-file",
            reversalRequestFile.toString());
    assertFalse(reversal.path("payload").path("postingId").stringValue().isBlank());
  }
}
