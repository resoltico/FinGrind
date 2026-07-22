package dev.erst.fingrind.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/** End-to-end coverage for Account Registry amendment and retirement semantics. */
class FinGrindCliAccountRegistryLifecycleTest extends CliPublicDocsContractSupport {
  @Test
  void accountLifecycle_preservesHistoryWhileAllowingHistoricalReversals() throws IOException {
    Path bookFile = tempDirectory.resolve("books").resolve("account-lifecycle.sqlite");
    Path bookKeyFile = tempDirectory.resolve("keys").resolve("account-lifecycle.book-key");
    Path amendCashRequest = writeNamedRequest("amend-cash.json", amendedCashRequestJson());
    Path retireCashRequest = writeNamedRequest("retire-cash.json", "{\"accountCode\":\"cash\"}");
    Path contributionRequest =
        writeNamedRequest(
            "owner-contribution.json", ownerContributionRequestJson("contribution", "1000"));
    Path withdrawalRequest =
        writeNamedRequest(
            "owner-withdrawal.json", ownerWithdrawalRequestJson("withdrawal", "1000"));

    createExistingOwnerOnlyParentDirectory(bookKeyFile);
    runJsonCommand("generate-book-key-file", "--new-book-key-file", bookKeyFile.toString());
    runJsonCommand(openBookKeyFileArguments(bookFile, bookKeyFile));

    JsonNode amended =
        runJsonCommand(
            "amend-account",
            "--book-file",
            bookFile.toString(),
            "--book-key-file",
            bookKeyFile.toString(),
            "--request-file",
            amendCashRequest.toString());
    assertEquals("amended", amended.path("payload").path("outcome").stringValue());
    assertEquals(
        "Operating Cash",
        amended.path("payload").path("account").path("accountName").stringValue());

    JsonNode contribution =
        runJsonCommand(
            "record-owner-contribution",
            "--book-file",
            bookFile.toString(),
            "--book-key-file",
            bookKeyFile.toString(),
            "--request-file",
            contributionRequest.toString());
    String contributionPostingId = contribution.path("payload").path("postingId").stringValue();
    assertFalse(contributionPostingId.isBlank());

    JsonNode nonZeroRetirement =
        runJsonDiagnosticsCommandExpectingExit(
            2,
            "retire-account",
            "--book-file",
            bookFile.toString(),
            "--book-key-file",
            bookKeyFile.toString(),
            "--request-file",
            retireCashRequest.toString());
    assertEquals("account-balance-not-zero", nonZeroRetirement.path("code").stringValue());

    runJsonCommand(
        "record-owner-withdrawal",
        "--book-file",
        bookFile.toString(),
        "--book-key-file",
        bookKeyFile.toString(),
        "--request-file",
        withdrawalRequest.toString());

    JsonNode postedAccountAmendment =
        runJsonDiagnosticsCommandExpectingExit(
            2,
            "amend-account",
            "--book-file",
            bookFile.toString(),
            "--book-key-file",
            bookKeyFile.toString(),
            "--request-file",
            amendCashRequest.toString());
    assertEquals("account-has-dependents", postedAccountAmendment.path("code").stringValue());
    assertTrue(
        postedAccountAmendment.toString().contains("postings"),
        postedAccountAmendment.toPrettyString());

    JsonNode retired =
        runJsonCommand(
            "retire-account",
            "--book-file",
            bookFile.toString(),
            "--book-key-file",
            bookKeyFile.toString(),
            "--request-file",
            retireCashRequest.toString());
    assertEquals("retired", retired.path("payload").path("outcome").stringValue());
    assertFalse(retired.path("payload").path("account").path("active").asBoolean());

    Path ordinaryUseAfterRetirement =
        writeNamedRequest(
            "owner-contribution-after-retirement.json",
            ownerContributionRequestJson("ordinary-after-retirement", "1000"));
    JsonNode blockedOrdinaryUse =
        runJsonDiagnosticsCommandExpectingExit(
            2,
            "record-owner-contribution",
            "--book-file",
            bookFile.toString(),
            "--book-key-file",
            bookKeyFile.toString(),
            "--request-file",
            ordinaryUseAfterRetirement.toString());
    assertEquals("account-state-violations", blockedOrdinaryUse.path("code").stringValue());
    assertTrue(
        blockedOrdinaryUse.toString().contains("inactive-account"),
        blockedOrdinaryUse.toPrettyString());

    Path reversalRequest =
        writeNamedRequest("reverse-contribution.json", reversalRequestJson(contributionPostingId));
    JsonNode reversal =
        runJsonCommand(
            "record-reversal",
            "--book-file",
            bookFile.toString(),
            "--book-key-file",
            bookKeyFile.toString(),
            "--request-file",
            reversalRequest.toString());
    String reversalPostingId = reversal.path("payload").path("postingId").stringValue();
    assertFalse(reversalPostingId.isBlank());
    JsonNode persistedReversal =
        runJsonCommand(
            "get-posting",
            "--book-file",
            bookFile.toString(),
            "--book-key-file",
            bookKeyFile.toString(),
            "--posting-id",
            reversalPostingId);
    assertEquals(
        contributionPostingId,
        persistedReversal.path("payload").path("posting").path("reversesPostingId").stringValue());

    JsonNode retiredCashBalance =
        runJsonCommand(
            "account-balance",
            "--book-file",
            bookFile.toString(),
            "--book-key-file",
            bookKeyFile.toString(),
            "--account-code",
            "cash");
    JsonNode balance = retiredCashBalance.path("payload").path("balances").get(0);
    assertEquals("1000", balance.path("debitTotal").path("minorUnits").stringValue());
    assertEquals("2000", balance.path("creditTotal").path("minorUnits").stringValue());
    assertEquals("1000", balance.path("netAmount").path("minorUnits").stringValue());
    assertEquals("CREDIT", balance.path("balanceSide").stringValue());
  }

  private static String amendedCashRequestJson() {
    return """
        {
          "accountCode": "cash",
          "accountName": "Operating Cash",
          "accountType": "ASSET",
          "accountNodeKind": "POSTABLE",
          "financialPositionLineClassification": "CURRENT_ASSET",
          "cashFlowAssetClassification": "CASH_AND_CASH_EQUIVALENT"
        }
        """;
  }

  private static String ownerContributionRequestJson(String token, String minorUnits) {
    return """
        {
          "entryKind": "OWNER_CONTRIBUTION",
          "effectiveDate": "2026-04-07",
          "cashAccountCode": "cash",
          "equityAccountCode": "owner-capital",
          "amount": {"currencyCode": "EUR", "minorUnits": "%s"},
          "evidence": {
            "sourceDocuments": [{
              "sourceDocumentId": "%s-document",
              "sourceDocumentType": "owner-contribution",
              "documentDate": "2026-04-07"
            }],
            "approvals": []
          },
          "provenance": {
            "commandId": "018f0000-0000-7000-8000-000000000001",
            "idempotencyKey": "%s-idempotency",
            "causationId": "%s-cause"
          }
        }
        """
        .formatted(minorUnits, token, token, token);
  }

  private static String ownerWithdrawalRequestJson(String token, String minorUnits) {
    return """
        {
          "entryKind": "OWNER_WITHDRAWAL",
          "effectiveDate": "2026-04-07",
          "equityAccountCode": "owner-draws",
          "cashAccountCode": "cash",
          "amount": {"currencyCode": "EUR", "minorUnits": "%s"},
          "evidence": {
            "sourceDocuments": [{
              "sourceDocumentId": "%s-document",
              "sourceDocumentType": "owner-withdrawal",
              "documentDate": "2026-04-07"
            }],
            "approvals": []
          },
          "provenance": {
            "commandId": "018f0000-0000-7000-8000-000000000001",
            "idempotencyKey": "%s-idempotency",
            "causationId": "%s-cause"
          }
        }
        """
        .formatted(minorUnits, token, token, token);
  }

  private static String reversalRequestJson(String priorPostingId) {
    return """
        {
          "entryKind": "REVERSAL",
          "effectiveDate": "2026-04-07",
          "evidence": {
            "sourceDocuments": [{
              "sourceDocumentId": "reverse-contribution-document",
              "sourceDocumentType": "credit-note",
              "documentDate": "2026-04-07"
            }],
            "approvals": []
          },
          "provenance": {
            "commandId": "018f0000-0000-7000-8000-000000000001",
            "idempotencyKey": "reverse-contribution-idempotency",
            "causationId": "reverse-contribution-cause"
          },
          "reversal": {
            "priorPostingId": "%s",
            "reason": "Correct prior owner contribution"
          }
        }
        """
        .formatted(priorPostingId);
  }
}
