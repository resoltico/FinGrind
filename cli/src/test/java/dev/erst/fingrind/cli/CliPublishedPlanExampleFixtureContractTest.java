package dev.erst.fingrind.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
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

    runJsonCommand("generate-book-key-file", "--new-book-key-file", bookKeyFile.toString());
    runJsonCommand(openBookKeyFileArguments(planBookFile, bookKeyFile));
    runJsonCommand(openBookKeyFileArguments(queryPlanBookFile, bookKeyFile));
    runJsonCommand(openBookKeyFileArguments(assertionFailurePlanBookFile, bookKeyFile));

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
    runJsonCommand(
        "execute-plan",
        "--book-file",
        queryPlanBookFile.toString(),
        "--book-key-file",
        bookKeyFile.toString(),
        "--result-detail",
        "full",
        "--request-file",
        planRequestFile.toString());
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
    ArrayNode steps = (ArrayNode) failingPlan.path("steps");
    ObjectNode assertion = steps.addObject();
    assertion.put("stepId", "assert-tax-payable-balance");
    assertion.put("kind", "assert");
    ObjectNode assertionDetails = assertion.putObject("assertion");
    assertionDetails.put("kind", "assert-account-balance");
    assertionDetails.put("accountCode", "tax-payable-vat");
    assertionDetails.putObject("netAmount").put("currencyCode", "EUR").put("minorUnits", "11");
    assertionDetails.put("balanceSide", "CREDIT");
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
  void publishedTaxSetupPlan_createsARegistrationUsableByTaxObligation() throws IOException {
    Path bookFile = tempDirectory.resolve("books").resolve("tax-setup.sqlite");
    Path bookKeyFile = tempDirectory.resolve("keys").resolve("tax-setup.book-key");
    Path planRequestFile = copyExampleFixture("ledger-plan-request.json");
    Path cashRequestFile =
        writeNamedRequest(
            "declare-plan-cash.json",
            """
            {
              "accountCode": "cash",
              "accountName": "Cash",
              "accountType": "ASSET",
              "accountNodeKind": "POSTABLE",
              "financialPositionLineClassification": "CURRENT_ASSET",
              "cashFlowAssetClassification": "CASH_AND_CASH_EQUIVALENT"
            }
            """);
    Path revenueRequestFile =
        writeNamedRequest(
            "declare-plan-revenue.json",
            """
            {
              "accountCode": "service-revenue",
              "accountName": "Service Revenue",
              "accountType": "REVENUE",
              "accountNodeKind": "POSTABLE",
              "profitAndLossLineClassification": "OPERATING_REVENUE"
            }
            """);
    Path saleRequestFile =
        writeNamedRequest(
            "record-plan-taxed-sale.json",
            """
            {
              "entryKind": "SALE_SETTLED",
              "effectiveDate": "2026-04-07",
              "cashAccountCode": "cash",
              "revenueAccountCode": "service-revenue",
              "amount": {
                "currencyCode": "EUR",
                "minorUnits": "10000"
              },
              "tax": {
                "taxRegistrationId": "vat-lv",
                "taxCode": "vat-standard-sale"
              },
              "evidence": {
                "sourceDocuments": [
                  {
                    "sourceDocumentId": "tax-setup-sale-1",
                    "sourceDocumentType": "cash-receipt",
                    "documentDate": "2026-04-07"
                  }
                ],
                "approvals": []
              },
              "provenance": {
                "commandId": "018f0000-0000-7000-8000-000000000001",
                "idempotencyKey": "tax-setup-sale-idempotency",
                "causationId": "tax-setup-sale-cause"
              }
            }
            """);

    runJsonCommand("generate-book-key-file", "--new-book-key-file", bookKeyFile.toString());
    runJsonCommand(openBookKeyFileArguments(bookFile, bookKeyFile));
    JsonNode plan =
        runJsonCommand(
            "execute-plan",
            "--book-file",
            bookFile.toString(),
            "--book-key-file",
            bookKeyFile.toString(),
            "--result-detail",
            "full",
            "--request-file",
            planRequestFile.toString());
    assertEquals("succeeded", plan.path("payload").path("status").stringValue());
    assertEquals(
        "declared",
        plan.path("payload")
            .path("journal")
            .path("steps")
            .get(2)
            .path("data")
            .path("outcome")
            .stringValue());
    assertEquals(
        "vat-lv",
        plan.path("payload")
            .path("journal")
            .path("steps")
            .get(2)
            .path("data")
            .path("taxRegistration")
            .path("taxRegistrationId")
            .stringValue());

    runJsonCommand(
        "declare-account",
        "--book-file",
        bookFile.toString(),
        "--book-key-file",
        bookKeyFile.toString(),
        "--request-file",
        cashRequestFile.toString());
    runJsonCommand(
        "declare-account",
        "--book-file",
        bookFile.toString(),
        "--book-key-file",
        bookKeyFile.toString(),
        "--request-file",
        revenueRequestFile.toString());
    runJsonCommand(
        "record-sale-settled",
        "--book-file",
        bookFile.toString(),
        "--book-key-file",
        bookKeyFile.toString(),
        "--request-file",
        saleRequestFile.toString());

    JsonNode obligation =
        runJsonCommand(
            "tax-obligation",
            "--book-file",
            bookFile.toString(),
            "--book-key-file",
            bookKeyFile.toString(),
            "--tax-registration-id",
            "vat-lv",
            "--period-start",
            "2026-04-01",
            "--period-end",
            "2026-04-30");
    assertEquals(
        "2100",
        obligation
            .path("payload")
            .path("totals")
            .path("netPayable")
            .path("minorUnits")
            .stringValue());
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
    runJsonCommand("generate-book-key-file", "--new-book-key-file", bookKeyFile.toString());
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
            "record-sale-settled",
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
