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
import tools.jackson.databind.ObjectMapper;

/** Executes the published quick-start and example workflows against the live CLI surface. */
class CliPublicDocsWorkflowContractTest extends CliPublicDocsContractSupport {
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  @Test
  void quickStartGuide_describesTheAgentScaffoldAndPublishedWorkflowRuns() throws IOException {
    String guide =
        normalizeLineEndings(
            Files.readString(
                repositoryRoot().resolve("docs/USER_QUICK_START.md"), StandardCharsets.UTF_8));
    assertTrue(guide.contains("concrete sample document"));
    assertTrue(guide.contains("same initialized file"));
    assertTrue(guide.contains("--entity-name"));
    assertTrue(guide.contains("--functional-currency"));
    assertTrue(guide.contains("--fiscal-year-start"));
    assertTrue(guide.contains("starter chart"));
    assertTrue(guide.contains("\"entryKind\": \"SALE\""));
    assertTrue(guide.contains("\"cashAccountCode\": \"cash\""));
    assertTrue(guide.contains("\"revenueAccountCode\": \"service-revenue\""));
    assertFalse(guide.contains("\"recipeKind\": \"CASH_REVENUE\""));
    assertTrue(guide.contains("quick-start-request.json"));
    Path workspace = tempDirectory.resolve("quick-start");
    Path bookFile = workspace.resolve("acme.sqlite");
    Path bookKeyFile = workspace.resolve("acme.book-key");
    Path requestFile =
        writeNamedRequest(
            "quick-start-request.json",
            normalizeLineEndings(
                Files.readString(
                    repositoryRoot().resolve("cli/src/bundle/root/quick-start-request.json"),
                    StandardCharsets.UTF_8)));
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
    JsonNode quickStartRequest = readJson(requestFile);
    assertEquals("SALE", quickStartRequest.path("entryKind").stringValue());
    assertEquals("cash", quickStartRequest.path("cashAccountCode").stringValue());
    assertEquals("service-revenue", quickStartRequest.path("revenueAccountCode").stringValue());
    assertEquals(
        "quick-start-cash-receipt-1",
        quickStartRequest
            .path("evidence")
            .path("sourceDocuments")
            .get(0)
            .path("sourceDocumentId")
            .stringValue());
    assertEquals(
        "cash-receipt",
        quickStartRequest
            .path("evidence")
            .path("sourceDocuments")
            .get(0)
            .path("sourceDocumentType")
            .stringValue());
    assertEquals(
        "2026-04-08",
        quickStartRequest
            .path("evidence")
            .path("sourceDocuments")
            .get(0)
            .path("documentDate")
            .stringValue());
    assertEquals(0, quickStartRequest.path("evidence").path("approvals").size());
    assertEquals("PERSON", quickStartRequest.path("provenance").path("actorType").stringValue());
    assertEquals(
        "quick-start-idem-1",
        quickStartRequest.path("provenance").path("idempotencyKey").stringValue());
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
            "record-sale",
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
    assertTrue(trialBalance.contains("cash"));
    assertTrue(trialBalance.contains("service-revenue"));
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
    assertTrue(examplesGuide.contains("--functional-currency"));
    assertTrue(examplesGuide.contains("--fiscal-year-start"));
    assertTrue(examplesGuide.contains("starter chart"));
    assertTrue(examplesGuide.contains("entry-semantics-multi-violation-request.json"));
    assertTrue(examplesGuide.contains("entry-semantics-violations-text.txt"));
    assertTrue(requestsGuide.contains("placeholder-first sample"));
    assertTrue(requestsGuide.contains("single-use per book"));
    assertTrue(requestsGuide.contains("account-state-violations-text.txt"));
    assertTrue(requestsGuide.contains("entry-semantics-violations-text.txt"));
    assertFalse(requestsGuide.contains("businessActivityTags"));
    assertFalse(
        requestsGuide.contains(
            "print-plan-template` emits the accepted `execute-plan` request shape directly"));
    Path workspace = tempDirectory.resolve("examples");
    Path bookFile = workspace.resolve("acme.sqlite");
    Path bookKeyFile = workspace.resolve("acme.book-key");
    Path declareCashFile = copyExampleFixture("declare-account-supplemental-cash-reserve.json");
    Path declareRevenueFile = copyExampleFixture("declare-account-supplemental-misc-revenue.json");
    Path postingRequestFile = copyExampleFixture("basic-posting-request.json");
    Path unknownAccountRequestFile = copyExampleFixture("unknown-account-request.json");
    Path entrySemanticsRequestFile =
        copyExampleFixture("entry-semantics-multi-violation-request.json");
    Path reversalRequestFile = copyExampleFixture("reversal-request.json");
    Path planBookFile = workspace.resolve("acme-plan.sqlite");
    Path planRequestFile = copyExampleFixture("ledger-plan-request.json");
    Path queryPlanBookFile = workspace.resolve("acme-plan-query.sqlite");
    Path queryPlanRequestFile = copyExampleFixture("ledger-plan-query-request.json");
    JsonNode postingRequest = readJson(postingRequestFile);
    assertTrue(postingRequest.path("recipeKind").isMissingNode());
    assertEquals("SALE", postingRequest.path("entryKind").stringValue());
    assertEquals("cash", postingRequest.path("cashAccountCode").stringValue());
    JsonNode planRequest = readJson(planRequestFile);
    JsonNode planPosting = canonicalPostingStep(planRequest).path("posting");
    assertTrue(planPosting.path("recipeKind").isMissingNode());
    assertEquals("SALE", planPosting.path("entryKind").stringValue());
    assertEquals("cash", planPosting.path("cashAccountCode").stringValue());
    JsonNode queryPlanRequest = readJson(queryPlanRequestFile);
    assertEquals(
        "SALE",
        canonicalPostingStep(queryPlanRequest).path("posting").path("entryKind").stringValue());
    JsonNode unknownAccountRequest = readJson(unknownAccountRequestFile);
    assertEquals("SALE", unknownAccountRequest.path("entryKind").stringValue());
    JsonNode entrySemanticsRequest = readJson(entrySemanticsRequestFile);
    assertEquals("SALE", entrySemanticsRequest.path("entryKind").stringValue());
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
    JsonNode entrySemanticsPreflight =
        runJsonDiagnosticsCommandExpectingExit(
            2,
            "preflight-entry",
            "--book-file",
            bookFile.toString(),
            "--book-key-file",
            bookKeyFile.toString(),
            "--request-file",
            entrySemanticsRequestFile.toString());
    assertEquals("rejected", entrySemanticsPreflight.path("status").stringValue());
    assertEquals("entry-semantics-violations", entrySemanticsPreflight.path("code").stringValue());
    assertTrue(entrySemanticsPreflight.toString().contains("\"distinct-role-accounts-required\""));
    assertTrue(
        entrySemanticsPreflight.toString().contains("\"source-document-type-not-accepted\""));
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
    JsonNode rawPlanPosting = canonicalPostingStep(rawPlanTemplate).path("posting");
    assertTrue(rawPlanPosting.path("recipeKind").isMissingNode());
    assertEquals("SALE", rawPlanPosting.path("entryKind").stringValue());
    assertEquals("cash", rawPlanPosting.path("cashAccountCode").stringValue());
    assertEquals(
        "replace-before-commit-source-document-id",
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
    assertEquals("ok", planResult.path("status").stringValue());
    assertEquals("succeeded", planResult.path("payload").path("status").stringValue());
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
    assertEquals("ok", queryPlanResult.path("status").stringValue());
    assertEquals("succeeded", queryPlanResult.path("payload").path("status").stringValue());
    JsonNode queryData =
        findStepData(queryPlanResult.path("payload").path("journal"), "page-accounts");
    assertEquals(1, queryData.path("count").asInt());
  }

  private static JsonNode findStepData(JsonNode journal, String stepId) {
    for (JsonNode step : journal.path("steps")) {
      if (stepId.equals(step.path("stepId").stringValue())) {
        return step.path("data");
      }
    }
    throw new AssertionError("Missing plan journal step: " + stepId);
  }

  private static JsonNode canonicalPostingStep(JsonNode planDocument) {
    JsonNode matchingStep = null;
    for (JsonNode step : planDocument.path("steps")) {
      if (isCommittedPostingKind(step.path("kind").stringValue()) && step.has("posting")) {
        if (matchingStep != null) {
          throw new AssertionError(
              "Expected exactly one canonical committed-posting scaffold step in the plan document.");
        }
        matchingStep = step;
      }
    }
    if (matchingStep == null) {
      throw new AssertionError(
          "Expected exactly one canonical committed-posting scaffold step in the plan document.");
    }
    return matchingStep;
  }

  private static boolean isCommittedPostingKind(String kind) {
    return switch (kind) {
      case "record-sale",
          "record-expense",
          "record-owner-contribution",
          "record-owner-withdrawal",
          "record-opening-position",
          "record-reversal",
          "post-entry" ->
          true;
      default -> false;
    };
  }

  private static JsonNode readJson(Path path) throws IOException {
    return OBJECT_MAPPER.readTree(Files.readString(path, StandardCharsets.UTF_8));
  }
}
