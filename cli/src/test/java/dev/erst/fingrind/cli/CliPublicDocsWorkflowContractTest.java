package dev.erst.fingrind.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/** Executes the published quick-start and example workflows against the live CLI surface. */
class CliPublicDocsWorkflowContractTest extends CliPublicDocsContractSupport {

  @Test
  void quickStartGuide_describesTheAgentScaffoldAndPublishedWorkflowRuns() throws IOException {
    String guide =
        normalizeLineEndings(
            Files.readString(
                repositoryRoot().resolve("docs/USER_QUICK_START.md"), StandardCharsets.UTF_8));
    assertTrue(guide.contains("placeholder-first sample document"));
    assertTrue(guide.contains("same book is rejected"));
    assertTrue(guide.contains("--entity-name"));
    assertTrue(guide.contains("--business-activity-tag"));
    assertTrue(guide.contains("--functional-currency"));
    assertTrue(guide.contains("--fiscal-year-start"));
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
    JsonNode generatedKey =
        runJsonCommand("generate-book-key-file", "--book-key-file", bookKeyFile.toString());
    assertEquals("ok", generatedKey.path("status").stringValue());
    assertGeneratedKeyFileIsSecure(
        bookKeyFile, generatedKey.path("payload").path("permissions").stringValue());
    JsonNode openBook = runJsonCommand(openBookKeyFileArguments(bookFile, bookKeyFile));
    assertEquals("ok", openBook.path("status").stringValue());
    assertEquals(
        "Acme Studio",
        openBook.path("payload").path("bookIdentity").path("entityName").stringValue());
    JsonNode requestTemplate = runRawJsonCommand("print-request-template");
    assertEquals("2026-01-15", requestTemplate.path("effectiveDate").stringValue());
    assertEquals(
        "replace-with-cash-receipt-id",
        requestTemplate
            .path("evidence")
            .path("sourceDocuments")
            .get(0)
            .path("sourceDocumentId")
            .stringValue());
    assertEquals(
        "cash-receipt",
        requestTemplate
            .path("evidence")
            .path("sourceDocuments")
            .get(0)
            .path("sourceDocumentType")
            .stringValue());
    assertEquals(
        "replace-with-secure-storage-locator://cash-receipt",
        requestTemplate
            .path("evidence")
            .path("sourceDocuments")
            .get(0)
            .path("storageLocator")
            .stringValue());
    assertEquals(0, requestTemplate.path("evidence").path("approvals").size());
    assertEquals("PERSON", requestTemplate.path("provenance").path("actorType").stringValue());
    assertEquals(
        "replace-with-idempotency-key",
        requestTemplate.path("provenance").path("idempotencyKey").stringValue());
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
    JsonNode preflight =
        runJsonCommand(
            "preflight-entry",
            "--book-file",
            bookFile.toString(),
            "--book-key-file",
            bookKeyFile.toString(),
            "--request-file",
            requestFile.toString());
    assertEquals("ok", preflight.path("status").stringValue());
    JsonNode committed =
        runJsonCommand(
            "post-entry",
            "--book-file",
            bookFile.toString(),
            "--book-key-file",
            bookKeyFile.toString(),
            "--request-file",
            requestFile.toString());
    assertEquals("ok", committed.path("status").stringValue());
    String trialBalance =
        runPlainCommand(
            "trial-balance",
            "--book-file",
            bookFile.toString(),
            "--book-key-file",
            bookKeyFile.toString(),
            "--output",
            "text");
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
    assertTrue(examplesGuide.contains("placeholder-first sample"));
    assertTrue(examplesGuide.contains("single-use per book"));
    assertTrue(examplesGuide.contains("--entity-name"));
    assertTrue(examplesGuide.contains("--business-activity-tag"));
    assertTrue(examplesGuide.contains("--functional-currency"));
    assertTrue(examplesGuide.contains("--fiscal-year-start"));
    assertTrue(requestsGuide.contains("placeholder-first sample"));
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
    Path unknownAccountRequestFile = copyExampleFixture("unknown-account-request.json");
    Path reversalRequestFile = copyExampleFixture("reversal-request.json");
    Path planBookFile = workspace.resolve("acme-plan.sqlite");
    Path planRequestFile = copyExampleFixture("ledger-plan-request.json");
    Path queryPlanBookFile = workspace.resolve("acme-plan-query.sqlite");
    Path queryPlanRequestFile = copyExampleFixture("ledger-plan-query-request.json");
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
            "post-entry",
            "--book-file",
            bookFile.toString(),
            "--book-key-file",
            bookKeyFile.toString(),
            "--request-file",
            postingRequestFile.toString());
    String postingId = committed.path("payload").path("postingId").stringValue();
    assertFalse(postingId.isBlank());
    JsonNode unknownAccountPreflight =
        runJsonCommandExpectingExit(
            2,
            "preflight-entry",
            "--book-file",
            bookFile.toString(),
            "--book-key-file",
            bookKeyFile.toString(),
            "--request-file",
            unknownAccountRequestFile.toString());
    assertEquals("rejected", unknownAccountPreflight.path("status").stringValue());
    assertEquals("account-state-violations", unknownAccountPreflight.path("code").stringValue());
    assertTrue(unknownAccountPreflight.toString().contains("\"accountCode\":\"9998\""));
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
    assertEquals("ok", reversal.path("status").stringValue());
    String reversalPostingId = reversal.path("payload").path("postingId").stringValue();
    assertFalse(reversalPostingId.isBlank());
    JsonNode listing =
        runJsonCommand(
            "list-postings",
            "--book-file",
            bookFile.toString(),
            "--book-key-file",
            bookKeyFile.toString(),
            "--limit",
            "10");
    assertPostingIdsContain(listing.path("payload").path("postings"), postingId, reversalPostingId);
    JsonNode rawPlanTemplate = runRawJsonCommand("print-plan-template");
    JsonNode rawPlanPosting = rawPlanTemplate.path("steps").get(3).path("posting");
    assertEquals(
        "replace-with-cash-receipt-id",
        rawPlanPosting
            .path("evidence")
            .path("sourceDocuments")
            .get(0)
            .path("sourceDocumentId")
            .stringValue());
    assertEquals("PERSON", rawPlanPosting.path("provenance").path("actorType").stringValue());
    JsonNode planResult =
        runJsonCommand(
            "execute-plan",
            "--book-file",
            planBookFile.toString(),
            "--book-key-file",
            bookKeyFile.toString(),
            "--result-detail",
            "full",
            "--request-file",
            planRequestFile.toString());
    assertEquals("succeeded", planResult.path("status").stringValue());
    JsonNode queryPlanResult =
        runJsonCommand(
            "execute-plan",
            "--book-file",
            queryPlanBookFile.toString(),
            "--book-key-file",
            bookKeyFile.toString(),
            "--result-detail",
            "full",
            "--request-file",
            queryPlanRequestFile.toString());
    assertEquals("succeeded", queryPlanResult.path("status").stringValue());
    JsonNode queryData =
        queryPlanResult.path("payload").path("journal").path("steps").get(4).path("data");
    assertEquals(1, queryData.path("count").asInt());
  }
}
