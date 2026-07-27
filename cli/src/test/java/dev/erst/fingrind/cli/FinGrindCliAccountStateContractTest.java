package dev.erst.fingrind.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.bookkeeping.CommitEntryResult;
import dev.erst.fingrind.contract.bookkeeping.DeclareAccountResult;
import dev.erst.fingrind.contract.bookkeeping.InventoryWriteDownExceedsCarryingCost;
import dev.erst.fingrind.contract.bookkeeping.PostEntryResult;
import dev.erst.fingrind.contract.bookkeeping.PostingRejection;
import dev.erst.fingrind.contract.bookkeeping.PreflightEntryResult;
import dev.erst.fingrind.contract.bookkeeping.ProtectedBookPairPublicationCompletion;
import dev.erst.fingrind.contract.bookkeeping.RekeyBookResult;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.IdempotencyKey;
import dev.erst.fingrind.core.NormalBalance;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/** End-to-end coverage for account-state rejection envelopes at the public CLI boundary. */
class FinGrindCliAccountStateContractTest extends CliWorkflowFixtureSupport {
  @Test
  void run_rejectsUnknownAccountWithUniformViolationCoreAcrossPreflightCommitAndOutputModes()
      throws IOException {
    Path bookFilePath = tempDirectory.resolve("account-state-books").resolve("entity.sqlite");
    Path bookKeyFilePath = writeBookKey(bookFilePath);
    Path declareCashFile =
        writeNamedRequest("declare-cash.json", declareAccountJson("1000", "Cash", "DEBIT"));
    Path requestFile = writeNamedRequest("unknown-account.json", unknownAccountRequestJson());

    openBook(bookFilePath, bookKeyFilePath);
    declareAccount(bookFilePath, bookKeyFilePath, declareCashFile);

    String expectedMessage = null;
    for (String commandName : List.of("preflight-entry", "post-entry")) {
      for (String outputMode : List.of("json", "text")) {
        ObservedInvocation observed =
            runEntryCommand(commandName, outputMode, bookFilePath, bookKeyFilePath, requestFile);

        assertEquals(2, observed.exitCode(), commandName + ":" + outputMode);
        assertEquals("", observed.stdout());
        String diagnostics = observed.stderr();
        if ("json".equals(outputMode)) {
          JsonNode rejectionEnvelope =
              CliJsonObjectMappers.configuredObjectMapper().readTree(diagnostics);
          String message = rejectionEnvelope.path("message").stringValue();

          assertEquals("rejected", rejectionEnvelope.path("status").stringValue(), diagnostics);
          assertEquals("account-state-violations", rejectionEnvelope.path("code").stringValue());
          assertEquals("Posting rejected with 1 account-state issue.", message);
          assertTrue(
              rejectionEnvelope.path("hint").isMissingNode(), rejectionEnvelope.toPrettyString());
          assertTrue(
              hasAccountStateViolation(rejectionEnvelope, "unknown-account"),
              rejectionEnvelope.toPrettyString());
          JsonNode violation = rejectionEnvelope.path("details").path("violations").get(0);
          assertEquals("lines[].accountCode", violation.path("field").stringValue());
          assertEquals("account-registry", violation.path("category").stringValue());
          assertEquals("9998", violation.path("accountCode").stringValue());
          assertTrue(violation.path("message").stringValue().contains("undeclared account '9998'"));
          assertTrue(
              violation.path("repair").stringValue().contains("Declare the missing account"));

          if (expectedMessage == null) {
            expectedMessage = message;
          } else {
            assertEquals(expectedMessage, message);
          }
        } else {
          assertTrue(diagnostics.contains("Rejected"), diagnostics);
          assertTrue(diagnostics.contains("account-state-violations"), diagnostics);
          assertTrue(diagnostics.contains("Summary"), diagnostics);
          assertTrue(diagnostics.contains("Issue 1 | unknown-account"), diagnostics);
          assertTrue(diagnostics.contains("lines[].accountCode"), diagnostics);
          assertTrue(diagnostics.contains("account-registry"), diagnostics);
          assertTrue(diagnostics.contains("9998"), diagnostics);
          assertTrue(diagnostics.contains("Why"), diagnostics);
          assertTrue(diagnostics.contains("Declare the missing account"), diagnostics);
          assertFalse(diagnostics.contains("Hint"), diagnostics);
        }
        assertFalse(diagnostics.contains("Exception"), diagnostics);
        assertFalse(diagnostics.contains("\tat "), diagnostics);
      }
    }
  }

  @Test
  void run_rejectsTypedTradingSaleThatWouldOverRelieveInventoryAcrossPreflightCommitAndOutputModes()
      throws IOException {
    Path bookFilePath = tempDirectory.resolve("account-state-books").resolve("trading.sqlite");
    Path bookKeyFilePath = writeBookKey(bookFilePath);
    Path purchaseRequestFile =
        writeNamedRequest("purchase-settled.json", purchaseSettledRequestJson("idem-purchase"));
    Path saleRequestFile =
        writeNamedRequest("sale-over-relief.json", saleSettledOverReliefRequestJson("idem-sale"));

    openTradingBook(bookFilePath, bookKeyFilePath);
    assertEquals(
        0,
        runEntryCommand(
                "record-purchase-settled",
                "json",
                bookFilePath,
                bookKeyFilePath,
                purchaseRequestFile)
            .exitCode());

    String expectedMessage = null;
    for (String commandName : List.of("preflight-entry", "record-sale-settled")) {
      for (String outputMode : List.of("json", "text")) {
        ObservedInvocation observed =
            runEntryCommand(
                commandName, outputMode, bookFilePath, bookKeyFilePath, saleRequestFile);

        assertEquals(2, observed.exitCode(), commandName + ":" + outputMode);
        assertEquals("", observed.stdout());
        String diagnostics = observed.stderr();
        if ("json".equals(outputMode)) {
          JsonNode rejectionEnvelope =
              CliJsonObjectMappers.configuredObjectMapper().readTree(diagnostics);
          String message = rejectionEnvelope.path("message").stringValue();

          assertEquals("rejected", rejectionEnvelope.path("status").stringValue(), diagnostics);
          assertEquals("account-state-violations", rejectionEnvelope.path("code").stringValue());
          assertEquals("Posting rejected with 1 account-state issue.", message);
          assertTrue(
              rejectionEnvelope.path("hint").isMissingNode(), rejectionEnvelope.toPrettyString());
          assertTrue(
              hasAccountStateViolation(rejectionEnvelope, "inventory-quantity-below-zero"),
              rejectionEnvelope.toPrettyString());
          JsonNode violation = rejectionEnvelope.path("details").path("violations").get(0);
          assertEquals("inventoryRelief.quantity", violation.path("field").stringValue());
          assertEquals("inventory-quantity", violation.path("category").stringValue());
          assertEquals("inventory", violation.path("accountCode").stringValue());
          assertTrue(
              violation.path("message").stringValue().contains("shortfall would be 4"),
              violation.toPrettyString());
          assertTrue(
              violation.path("repair").stringValue().contains("inventory acquisition"),
              violation.toPrettyString());

          if (expectedMessage == null) {
            expectedMessage = message;
          } else {
            assertEquals(expectedMessage, message);
          }
        } else {
          assertTrue(diagnostics.contains("Rejected"), diagnostics);
          assertTrue(diagnostics.contains("account-state-violations"), diagnostics);
          assertTrue(diagnostics.contains("Summary"), diagnostics);
          assertTrue(diagnostics.contains("Issue 1 | inventory-quantity-below-zero"), diagnostics);
          assertTrue(diagnostics.contains("inventoryRelief.quantity"), diagnostics);
          assertTrue(diagnostics.contains("inventory-quantity"), diagnostics);
          assertTrue(diagnostics.contains("inventory"), diagnostics);
          assertTrue(diagnostics.contains("shortfall would be 4"), diagnostics);
          assertTrue(diagnostics.contains("inventory acquisition"), diagnostics);
          assertFalse(diagnostics.contains("Hint"), diagnostics);
        }
        assertFalse(diagnostics.contains("Exception"), diagnostics);
        assertFalse(diagnostics.contains("\tat "), diagnostics);
      }
    }
  }

  @Test
  void run_rejectsBackdatedInventoryMovementBeforeSqliteHorizonBackstopAcrossOutputModes()
      throws IOException {
    Path bookFilePath =
        tempDirectory.resolve("account-state-books").resolve("trading-horizon.sqlite");
    Path bookKeyFilePath = writeBookKey(bookFilePath);
    Path purchaseRequestFile =
        writeNamedRequest(
            "purchase-settled-horizon.json", purchaseSettledRequestJson("idem-purchase-horizon"));
    Path saleRequestFile =
        writeNamedRequest(
            "sale-backdated-horizon.json",
            saleSettledBackdatedHorizonRequestJson("idem-sale-horizon"));

    openTradingBook(bookFilePath, bookKeyFilePath);
    assertEquals(
        0,
        runEntryCommand(
                "record-purchase-settled",
                "json",
                bookFilePath,
                bookKeyFilePath,
                purchaseRequestFile)
            .exitCode());

    String expectedMessage = null;
    for (String commandName : List.of("preflight-entry", "record-sale-settled")) {
      for (String outputMode : List.of("json", "text")) {
        ObservedInvocation observed =
            runEntryCommand(
                commandName, outputMode, bookFilePath, bookKeyFilePath, saleRequestFile);

        assertEquals(2, observed.exitCode(), commandName + ":" + outputMode);
        assertEquals("", observed.stdout());
        String diagnostics = observed.stderr();
        if ("json".equals(outputMode)) {
          JsonNode rejectionEnvelope =
              CliJsonObjectMappers.configuredObjectMapper().readTree(diagnostics);
          String message = rejectionEnvelope.path("message").stringValue();

          assertEquals("rejected", rejectionEnvelope.path("status").stringValue(), diagnostics);
          assertEquals("account-state-violations", rejectionEnvelope.path("code").stringValue());
          assertEquals("Posting rejected with 1 account-state issue.", message);
          assertTrue(
              rejectionEnvelope.path("hint").isMissingNode(), rejectionEnvelope.toPrettyString());
          assertTrue(
              hasAccountStateViolation(
                  rejectionEnvelope, "inventory-movement-precedes-account-horizon"),
              rejectionEnvelope.toPrettyString());
          JsonNode violation = rejectionEnvelope.path("details").path("violations").get(0);
          assertEquals("inventoryRelief.quantity", violation.path("field").stringValue());
          assertEquals("inventory-horizon", violation.path("category").stringValue());
          assertEquals("inventory", violation.path("accountCode").stringValue());
          assertTrue(
              violation
                  .path("message")
                  .stringValue()
                  .contains("durable inventory history through '2026-04-07'"),
              violation.toPrettyString());
          assertTrue(
              violation
                  .path("repair")
                  .stringValue()
                  .contains("effective date on or after the account horizon"),
              violation.toPrettyString());

          if (expectedMessage == null) {
            expectedMessage = message;
          } else {
            assertEquals(expectedMessage, message);
          }
        } else {
          assertTrue(diagnostics.contains("Rejected"), diagnostics);
          assertTrue(diagnostics.contains("account-state-violations"), diagnostics);
          assertTrue(
              diagnostics.contains("Issue 1 | inventory-movement-precedes-account-horizon"),
              diagnostics);
          assertTrue(diagnostics.contains("inventoryRelief.quantity"), diagnostics);
          assertTrue(diagnostics.contains("inventory-horizon"), diagnostics);
          assertTrue(
              diagnostics.contains("durable inventory history through '2026-04-07'"), diagnostics);
          assertTrue(
              diagnostics.contains("effective date on or after the account horizon"), diagnostics);
          assertFalse(diagnostics.contains("storage-runtime-failure"), diagnostics);
          assertFalse(diagnostics.contains("Hint"), diagnostics);
        }
        assertFalse(diagnostics.contains("Exception"), diagnostics);
        assertFalse(diagnostics.contains("\tat "), diagnostics);
      }
    }
  }

  @Test
  void run_publishesInventoryCarryingCostViolationAcrossPreflightCommitAndOutputModes()
      throws IOException {
    Path bookFilePath =
        tempDirectory.resolve("account-state-books").resolve("workflow-carrying-cost.sqlite");
    Path bookKeyFilePath = writeBookKey(bookFilePath);
    Path requestFile =
        writeNamedRequest(
            "reversal-carrying-cost.json", CliRequestReaderTestSupport.validRequestJson(true));
    PostingRejection.AccountStateViolations rejection =
        new PostingRejection.AccountStateViolations(
            List.of(
                new InventoryWriteDownExceedsCarryingCost(
                    new AccountCode("inventory"),
                    "reversal.priorPostingId",
                    LocalDate.parse("2026-04-09"),
                    money("EUR", "5.00"),
                    money("EUR", "9.00"),
                    money("EUR", "4.00"))));
    CliRecordingWorkflow workflow =
        contractWorkflow(
            new PostEntryResult.PreflightRejected(new IdempotencyKey("idem-1"), rejection),
            new PostEntryResult.CommitRejected(new IdempotencyKey("idem-1"), rejection));

    String expectedMessage = null;
    for (String commandName : List.of("preflight-entry", "record-reversal")) {
      for (String outputMode : List.of("json", "text")) {
        ObservedInvocation observed =
            runEntryCommand(
                workflow, commandName, outputMode, bookFilePath, bookKeyFilePath, requestFile);

        assertEquals(2, observed.exitCode(), commandName + ":" + outputMode);
        assertEquals("", observed.stdout());
        String diagnostics = observed.stderr();
        if ("json".equals(outputMode)) {
          JsonNode rejectionEnvelope =
              CliJsonObjectMappers.configuredObjectMapper().readTree(diagnostics);
          String message = rejectionEnvelope.path("message").stringValue();

          assertEquals("rejected", rejectionEnvelope.path("status").stringValue(), diagnostics);
          assertEquals("account-state-violations", rejectionEnvelope.path("code").stringValue());
          assertEquals("Posting rejected with 1 account-state issue.", message);
          assertTrue(
              rejectionEnvelope.path("hint").isMissingNode(), rejectionEnvelope.toPrettyString());
          assertTrue(
              hasAccountStateViolation(
                  rejectionEnvelope, "inventory-write-down-exceeds-carrying-cost"),
              rejectionEnvelope.toPrettyString());
          JsonNode violation = rejectionEnvelope.path("details").path("violations").get(0);
          assertEquals("reversal.priorPostingId", violation.path("field").stringValue());
          assertEquals("inventory-carrying-cost", violation.path("category").stringValue());
          assertEquals("inventory", violation.path("accountCode").stringValue());
          assertTrue(
              violation.path("message").stringValue().contains("shortfall would be EUR 4.00"),
              violation.toPrettyString());
          assertTrue(
              violation.path("repair").stringValue().contains("capitalize the missing cost first"),
              violation.toPrettyString());

          if (expectedMessage == null) {
            expectedMessage = message;
          } else {
            assertEquals(expectedMessage, message);
          }
        } else {
          assertTrue(diagnostics.contains("Rejected"), diagnostics);
          assertTrue(diagnostics.contains("account-state-violations"), diagnostics);
          assertTrue(
              diagnostics.contains("Issue 1 | inventory-write-down-exceeds-carrying-cost"),
              diagnostics);
          assertTrue(diagnostics.contains("reversal.priorPostingId"), diagnostics);
          assertTrue(diagnostics.contains("inventory-carrying-cost"), diagnostics);
          assertTrue(diagnostics.contains("inventory"), diagnostics);
          assertTrue(diagnostics.contains("shortfall would be EUR 4.00"), diagnostics);
          assertTrue(diagnostics.contains("capitalize the missing cost first"), diagnostics);
          assertFalse(diagnostics.contains("Hint"), diagnostics);
        }
        assertFalse(diagnostics.contains("Exception"), diagnostics);
        assertFalse(diagnostics.contains("\tat "), diagnostics);
      }
    }
  }

  private ObservedInvocation runEntryCommand(
      String commandName,
      String outputMode,
      Path bookFilePath,
      Path bookKeyFilePath,
      Path requestFile) {
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    ByteArrayOutputStream diagnosticsStream = new ByteArrayOutputStream();
    FinGrindCli cli =
        cli(
            new ByteArrayInputStream(new byte[0]),
            utf8PrintStream(outputStream),
            utf8PrintStream(diagnosticsStream),
            fixedClock());
    String[] commandArguments =
        new String[] {
          commandName,
          "--book-file",
          bookFilePath.toString(),
          "--book-key-file",
          bookKeyFilePath.toString(),
          "--request-file",
          requestFile.toString(),
          "--output",
          outputMode
        };
    int exitCode =
        cli.run(
            "preflight-entry".equals(commandName)
                ? jsonArguments(commandArguments)
                : attestedJsonArguments(commandArguments));
    return new ObservedInvocation(
        exitCode,
        outputStream.toString(StandardCharsets.UTF_8),
        diagnosticsStream.toString(StandardCharsets.UTF_8));
  }

  private ObservedInvocation runEntryCommand(
      CliBookWorkflow workflow,
      String commandName,
      String outputMode,
      Path bookFilePath,
      Path bookKeyFilePath,
      Path requestFile) {
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    ByteArrayOutputStream diagnosticsStream = new ByteArrayOutputStream();
    FinGrindCli cli =
        cli(
            new ByteArrayInputStream(new byte[0]),
            utf8PrintStream(outputStream),
            utf8PrintStream(diagnosticsStream),
            fixedClock(),
            workflow);
    int exitCode =
        cli.run(
            new String[] {
              commandName,
              "--book-file",
              bookFilePath.toString(),
              "--book-key-file",
              bookKeyFilePath.toString(),
              "--request-file",
              requestFile.toString(),
              "--output",
              outputMode
            });
    return new ObservedInvocation(
        exitCode,
        outputStream.toString(StandardCharsets.UTF_8),
        diagnosticsStream.toString(StandardCharsets.UTF_8));
  }

  private void openBook(Path bookFilePath, Path bookKeyFilePath) {
    assertEquals(
        0,
        cli(
                new ByteArrayInputStream(new byte[0]),
                utf8PrintStream(new ByteArrayOutputStream()),
                fixedClock())
            .run(jsonArguments(openBookKeyFileArguments(bookFilePath, bookKeyFilePath))));
  }

  private void openTradingBook(Path bookFilePath, Path bookKeyFilePath) {
    assertEquals(
        0,
        cli(
                new ByteArrayInputStream(new byte[0]),
                utf8PrintStream(new ByteArrayOutputStream()),
                fixedClock())
            .run(jsonArguments(openTradingBookKeyFileArguments(bookFilePath, bookKeyFilePath))));
  }

  private void declareAccount(Path bookFilePath, Path bookKeyFilePath, Path requestFile) {
    assertEquals(
        0,
        cli(
                new ByteArrayInputStream(new byte[0]),
                utf8PrintStream(new ByteArrayOutputStream()),
                fixedClock())
            .run(
                attestedJsonArguments(
                    "declare-account",
                    "--book-file",
                    bookFilePath.toString(),
                    "--book-key-file",
                    bookKeyFilePath.toString(),
                    "--request-file",
                    requestFile.toString())));
  }

  private static boolean hasAccountStateViolation(JsonNode rejectionEnvelope, String code) {
    return StreamSupport.stream(
            rejectionEnvelope.path("details").path("violations").spliterator(), false)
        .anyMatch(violation -> code.equals(violation.path("code").stringValue()));
  }

  private static String unknownAccountRequestJson() {
    return """
        {
          "entryKind": "DIRECT_JOURNAL",
          "effectiveDate": "2026-04-07",
          "lines": [
            {
              "accountCode": "1000",
              "side": "DEBIT",
              "amount": {
                "currencyCode": "EUR",
                "minorUnits": "1000"
              }
            },
            {
              "accountCode": "9998",
              "side": "CREDIT",
              "amount": {
                "currencyCode": "EUR",
                "minorUnits": "1000"
              }
            }
          ],
          "evidence": {
            "sourceDocuments": [
              {
                "sourceDocumentId": "document-unknown-account",
                "sourceDocumentType": "working-note",
                "documentDate": "2026-04-07"
              }
            ],
            "approvals": []
          },
          "provenance": {
            "commandId": "018f0000-0000-7000-8000-000000000001",
            "idempotencyKey": "idem-unknown-account",
            "causationId": "cause-unknown-account"
          }
        }
        """;
  }

  private static String purchaseSettledRequestJson(String idempotencyKey) {
    return """
        {
          "entryKind": "PURCHASE_SETTLED",
          "effectiveDate": "2026-04-07",
          "inventoryAccountCode": "inventory",
          "cashAccountCode": "cash",
          "quantity": "1",
          "unitCost": {
            "currencyCode": "EUR",
            "minorUnits": "1000"
          },
          "evidence": {
            "sourceDocuments": [
              {
                "sourceDocumentId": "document-%s",
                "sourceDocumentType": "purchase-receipt",
                "documentDate": "2026-04-07"
              }
            ],
            "approvals": []
          },
          "provenance": {
            "commandId": "018f0000-0000-7000-8000-000000000001",
            "idempotencyKey": "%s",
            "causationId": "cause-%s"
          }
        }
        """
        .formatted(idempotencyKey, idempotencyKey, idempotencyKey);
  }

  private static String saleSettledOverReliefRequestJson(String idempotencyKey) {
    return """
        {
          "entryKind": "SALE_SETTLED",
          "effectiveDate": "2026-04-07",
          "cashAccountCode": "cash",
          "revenueAccountCode": "sales-revenue",
          "amount": {
            "currencyCode": "EUR",
            "minorUnits": "7000"
          },
          "inventoryRelief": {
            "inventoryAccountCode": "inventory",
            "costOfSalesAccountCode": "cost-of-sales",
            "quantity": "5"
          },
          "evidence": {
            "sourceDocuments": [
              {
                "sourceDocumentId": "document-%s",
                "sourceDocumentType": "cash-receipt",
                "documentDate": "2026-04-07"
              }
            ],
            "approvals": []
          },
          "provenance": {
            "commandId": "018f0000-0000-7000-8000-000000000001",
            "idempotencyKey": "%s",
            "causationId": "cause-%s"
          }
        }
        """
        .formatted(idempotencyKey, idempotencyKey, idempotencyKey);
  }

  private static String saleSettledBackdatedHorizonRequestJson(String idempotencyKey) {
    return """
        {
          "entryKind": "SALE_SETTLED",
          "effectiveDate": "2026-04-06",
          "cashAccountCode": "cash",
          "revenueAccountCode": "sales-revenue",
          "amount": {
            "currencyCode": "EUR",
            "minorUnits": "7000"
          },
          "inventoryRelief": {
            "inventoryAccountCode": "inventory",
            "costOfSalesAccountCode": "cost-of-sales",
            "quantity": "1"
          },
          "evidence": {
            "sourceDocuments": [
              {
                "sourceDocumentId": "document-%s",
                "sourceDocumentType": "cash-receipt",
                "documentDate": "2026-04-06"
              }
            ],
            "approvals": []
          },
          "provenance": {
            "commandId": "018f0000-0000-7000-8000-000000000001",
            "idempotencyKey": "%s",
            "causationId": "cause-%s"
          }
        }
        """
        .formatted(idempotencyKey, idempotencyKey, idempotencyKey);
  }

  private static String[] openTradingBookKeyFileArguments(Path bookFilePath, Path bookKeyFilePath) {
    String[] arguments = openBookKeyFileArguments(bookFilePath, bookKeyFilePath);
    for (int index = 0; index < arguments.length; index++) {
      if ("--book-template-id".equals(arguments[index])) {
        arguments[index + 1] = tradingBookIdentity().bookDoctrine().bookTemplateId().wireValue();
      }
    }
    String[] withInventoryCosting = java.util.Arrays.copyOf(arguments, arguments.length + 2);
    withInventoryCosting[arguments.length] = "--inventory-costing";
    withInventoryCosting[arguments.length + 1] =
        dev.erst.fingrind.core.InventoryCostingDoctrine.WEIGHTED_AVERAGE.wireValue();
    return withInventoryCosting;
  }

  private static CliRecordingWorkflow contractWorkflow(
      PreflightEntryResult preflightResult, CommitEntryResult commitResult) {
    return new CliRecordingWorkflow(
        openedBookResult(Instant.parse("2026-04-07T12:00:00Z")),
        new RekeyBookResult.Rekeyed(
            Path.of("unused.sqlite"),
            Path.of("unused.key"),
            attestationCommit(),
            ProtectedBookPairPublicationCompletion.PUBLISHED,
            CliFixtureSupport.pairPublicationRetention(
                Path.of("unused.sqlite"), Path.of("unused.key"))),
        new DeclareAccountResult.Declared(
            declaredAccount("1000", "Cash", NormalBalance.DEBIT), attestationCommit()),
        listedAccounts(accountPage(List.of(), 50, Optional.empty())),
        preflightResult,
        commitResult);
  }

  private record ObservedInvocation(int exitCode, String stdout, String stderr) {}
}
