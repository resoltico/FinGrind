package dev.erst.fingrind.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.runtime.ContractErrors;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Unit tests for {@link FinGrindCli}. */
class FinGrindCliMutationWorkflowTest extends FinGrindCliTestSupport {
  @Test
  void run_rekeyBookThroughDefaultSqliteWorkflowRotatesBookKey() throws IOException {
    Path bookFilePath = tempDirectory.resolve("rekey-books").resolve("entity.sqlite");
    Path currentBookKeyFilePath = writeBookKey(bookFilePath, TEST_BOOK_KEY);
    Path replacementBookKeyFilePath = tempDirectory.resolve("replacement-book.key");
    ByteArrayOutputStream openOutput = new ByteArrayOutputStream();
    FinGrindCli openCli =
        cli(new ByteArrayInputStream(new byte[0]), utf8PrintStream(openOutput), fixedClock());
    assertEquals(
        0,
        openCli.run(jsonArguments(openBookKeyFileArguments(bookFilePath, currentBookKeyFilePath))),
        () -> openOutput.toString(StandardCharsets.UTF_8));
    ByteArrayOutputStream rekeyOutput = new ByteArrayOutputStream();
    FinGrindCli rekeyCli =
        cli(new ByteArrayInputStream(new byte[0]), utf8PrintStream(rekeyOutput), fixedClock());
    assertEquals(
        0,
        rekeyCli.run(
            jsonArguments(
                "rekey-book",
                "--book-file",
                bookFilePath.toString(),
                "--book-key-file",
                currentBookKeyFilePath.toString(),
                "--new-book-key-file",
                replacementBookKeyFilePath.toString())),
        () -> rekeyOutput.toString(StandardCharsets.UTF_8));
    assertJsonContains(rekeyOutput, "\"bookFile\"");
    ByteArrayOutputStream oldKeyOutput = new ByteArrayOutputStream();
    FinGrindCli oldKeyCli =
        cli(new ByteArrayInputStream(new byte[0]), utf8PrintStream(oldKeyOutput), fixedClock());
    assertEquals(
        6,
        oldKeyCli.run(
            jsonArguments(
                "list-accounts",
                "--book-file",
                bookFilePath.toString(),
                "--book-key-file",
                currentBookKeyFilePath.toString())));
    JsonNode oldKeyFailureEnvelope = new ObjectMapper().readTree(oldKeyOutput.toByteArray());
    assertEquals(
        ContractErrors.Descriptor.PROTECTED_BOOK_VERIFICATION_FAILED.code(),
        oldKeyFailureEnvelope.path("code").stringValue());
    assertFalse(oldKeyFailureEnvelope.path("message").stringValue().contains("SQLITE_NOTADB"));
    ByteArrayOutputStream newKeyOutput = new ByteArrayOutputStream();
    FinGrindCli newKeyCli =
        cli(new ByteArrayInputStream(new byte[0]), utf8PrintStream(newKeyOutput), fixedClock());
    assertEquals(
        0,
        newKeyCli.run(
            jsonArguments(
                "list-accounts",
                "--book-file",
                bookFilePath.toString(),
                "--book-key-file",
                replacementBookKeyFilePath.toString())));
    assertJsonContains(newKeyOutput, "\"status\":\"ok\"");
  }

  @Test
  void run_inspectBookOnCorruptedProtectedBook_reportsProtectedBookVerificationFailure()
      throws IOException {
    Path bookFilePath = tempDirectory.resolve("corrupted-books").resolve("entity.sqlite");
    Path bookKeyFilePath = writeBookKey(bookFilePath);
    ByteArrayOutputStream openOutput = new ByteArrayOutputStream();
    FinGrindCli openCli =
        cli(new ByteArrayInputStream(new byte[0]), utf8PrintStream(openOutput), fixedClock());
    assertEquals(
        0, openCli.run(jsonArguments(openBookKeyFileArguments(bookFilePath, bookKeyFilePath))));
    Path corruptedBookPath =
        tempDirectory.resolve("corrupted-books").resolve("entity-corrupted.sqlite");
    byte[] corruptedBytes = Files.readAllBytes(bookFilePath);
    corruptedBytes[Math.min(200, corruptedBytes.length - 1)] ^= 0x5A;
    Files.write(corruptedBookPath, corruptedBytes);
    ByteArrayOutputStream inspectOutput = new ByteArrayOutputStream();
    FinGrindCli inspectCli =
        cli(new ByteArrayInputStream(new byte[0]), utf8PrintStream(inspectOutput), fixedClock());
    assertEquals(
        6,
        inspectCli.run(
            jsonArguments(
                "inspect-book",
                "--book-file",
                corruptedBookPath.toString(),
                "--book-key-file",
                bookKeyFilePath.toString())));
    JsonNode failureEnvelope = new ObjectMapper().readTree(inspectOutput.toByteArray());
    assertEquals(
        ContractErrors.Descriptor.PROTECTED_BOOK_VERIFICATION_FAILED.code(),
        failureEnvelope.path("code").stringValue());
    assertTrue(failureEnvelope.path("message").stringValue().contains("verify"));
    assertTrue(failureEnvelope.path("hint").stringValue().contains("damaged or truncated"));
  }

  @Test
  void run_openBookDeclareAccountListAccountsAndCommitThroughDefaultSqliteWorkflow()
      throws IOException {
    Path requestFile = writeRequest(validRequestJson());
    Path declareCashFile =
        writeNamedRequest("declare-cash.json", declareAccountJson("1000", "Cash", "DEBIT"));
    Path declareRevenueFile =
        writeNamedRequest("declare-revenue.json", declareAccountJson("2000", "Revenue", "CREDIT"));
    Path bookFilePath = tempDirectory.resolve("committed-books").resolve("entity.sqlite");
    Path bookKeyFilePath = writeBookKey(bookFilePath);
    FinGrindCli cli;
    ByteArrayOutputStream openOutput = new ByteArrayOutputStream();
    cli = cli(new ByteArrayInputStream(new byte[0]), utf8PrintStream(openOutput), fixedClock());
    assertEquals(
        0, cli.run(jsonArguments(openBookKeyFileArguments(bookFilePath, bookKeyFilePath))));
    assertJsonContains(openOutput, "\"initializedAt\"");
    assertJsonContains(openOutput, "\"entityName\":\"Acme Studio\"");
    ByteArrayOutputStream declareCashOutput = new ByteArrayOutputStream();
    cli =
        cli(
            new ByteArrayInputStream(new byte[0]),
            utf8PrintStream(declareCashOutput),
            fixedClock());
    assertEquals(
        0,
        cli.run(
            jsonArguments(
                "declare-account",
                "--book-file",
                bookFilePath.toString(),
                "--book-key-file",
                bookKeyFilePath.toString(),
                "--request-file",
                declareCashFile.toString())));
    assertJsonContains(declareCashOutput, "\"accountCode\":\"1000\"");
    ByteArrayOutputStream declareRevenueOutput = new ByteArrayOutputStream();
    cli =
        cli(
            new ByteArrayInputStream(new byte[0]),
            utf8PrintStream(declareRevenueOutput),
            fixedClock());
    assertEquals(
        0,
        cli.run(
            attestedArguments(
                "declare-account",
                "--book-file",
                bookFilePath.toString(),
                "--book-key-file",
                bookKeyFilePath.toString(),
                "--request-file",
                declareRevenueFile.toString())));
    ByteArrayOutputStream listOutput = new ByteArrayOutputStream();
    cli = cli(new ByteArrayInputStream(new byte[0]), utf8PrintStream(listOutput), fixedClock());
    assertEquals(
        0,
        cli.run(
            jsonArguments(
                "list-accounts",
                "--book-file",
                bookFilePath.toString(),
                "--book-key-file",
                bookKeyFilePath.toString())));
    assertJsonContains(listOutput, "\"accountName\":\"Cash\"");
    assertJsonContains(listOutput, "\"accountName\":\"Revenue\"");
    ByteArrayOutputStream preflightOutput = new ByteArrayOutputStream();
    cli =
        cli(new ByteArrayInputStream(new byte[0]), utf8PrintStream(preflightOutput), fixedClock());
    assertEquals(
        0,
        cli.run(
            jsonArguments(
                "preflight-entry",
                "--book-file",
                bookFilePath.toString(),
                "--book-key-file",
                bookKeyFilePath.toString(),
                "--request-file",
                requestFile.toString())));
    assertJsonContains(preflightOutput, "\"status\":\"ok\"");
    JsonNode preflightEnvelope =
        new ObjectMapper().readTree(preflightOutput.toString(StandardCharsets.UTF_8));
    assertEquals(
        "SETTLED_SALE",
        preflightEnvelope
            .path("payload")
            .path("resolvedJournal")
            .path("classification")
            .path("eventClass")
            .stringValue());
    ByteArrayOutputStream commitOutput = new ByteArrayOutputStream();
    cli = cli(new ByteArrayInputStream(new byte[0]), utf8PrintStream(commitOutput), fixedClock());
    assertEquals(
        0,
        cli.run(
            jsonArguments(
                "record-sale-settled",
                "--book-file",
                bookFilePath.toString(),
                "--book-key-file",
                bookKeyFilePath.toString(),
                "--request-file",
                requestFile.toString())));
    JsonNode envelope = new ObjectMapper().readTree(commitOutput.toString(StandardCharsets.UTF_8));
    assertEquals("ok", envelope.path("status").stringValue());
    assertEquals(
        "SETTLED_SALE",
        envelope
            .path("payload")
            .path("resolvedJournal")
            .path("classification")
            .path("eventClass")
            .stringValue());
    UUID postingId = UUID.fromString(envelope.path("payload").path("postingId").stringValue());
    assertEquals(7, postingId.version());
    assertEquals(2, postingId.variant());
    assertTrue(Files.exists(bookFilePath));
  }

  @Test
  void run_preflightTradingSalePublishesDerivedInventoryCostingInJsonAndText() throws IOException {
    Path bookFilePath = tempDirectory.resolve("trading-preflight-books").resolve("entity.sqlite");
    Path bookKeyFilePath = writeBookKey(bookFilePath);
    Path purchaseRequestFile =
        writeNamedRequest(
            "trading-purchase-settled.json",
            tradingPurchaseSettledRequestJson("idem-trading-purchase"));
    Path saleRequestFile =
        writeNamedRequest(
            "trading-sale-settled.json", tradingSaleSettledRequestJson("idem-trading-sale"));

    ByteArrayOutputStream openOutput = new ByteArrayOutputStream();
    FinGrindCli openCli =
        cli(new ByteArrayInputStream(new byte[0]), utf8PrintStream(openOutput), fixedClock());
    assertEquals(
        0,
        openCli.run(jsonArguments(openTradingBookKeyFileArguments(bookFilePath, bookKeyFilePath))));

    ByteArrayOutputStream purchaseOutput = new ByteArrayOutputStream();
    FinGrindCli purchaseCli =
        cli(new ByteArrayInputStream(new byte[0]), utf8PrintStream(purchaseOutput), fixedClock());
    assertEquals(
        0,
        purchaseCli.run(
            jsonArguments(
                "record-purchase-settled",
                "--book-file",
                bookFilePath.toString(),
                "--book-key-file",
                bookKeyFilePath.toString(),
                "--request-file",
                purchaseRequestFile.toString())));

    ByteArrayOutputStream preflightJsonOutput = new ByteArrayOutputStream();
    FinGrindCli preflightJsonCli =
        cli(
            new ByteArrayInputStream(new byte[0]),
            utf8PrintStream(preflightJsonOutput),
            fixedClock());
    assertEquals(
        0,
        preflightJsonCli.run(
            jsonArguments(
                "preflight-entry",
                "--book-file",
                bookFilePath.toString(),
                "--book-key-file",
                bookKeyFilePath.toString(),
                "--request-file",
                saleRequestFile.toString())));

    JsonNode preflightEnvelope =
        new ObjectMapper().readTree(preflightJsonOutput.toString(StandardCharsets.UTF_8));
    assertEquals("ok", preflightEnvelope.path("status").stringValue());
    assertEquals(
        "SETTLED_SALE",
        preflightEnvelope
            .path("payload")
            .path("resolvedJournal")
            .path("classification")
            .path("eventClass")
            .stringValue());
    JsonNode resolvedLines =
        preflightEnvelope
            .path("payload")
            .path("resolvedJournal")
            .path("expandedLines")
            .path("lines");
    assertEquals(4, resolvedLines.size());
    assertEquals("cost-of-sales", resolvedLines.get(2).path("accountCode").stringValue());
    assertEquals("DEBIT", resolvedLines.get(2).path("side").stringValue());
    assertEquals("1000", resolvedLines.get(2).path("amount").path("minorUnits").stringValue());
    assertEquals("inventory", resolvedLines.get(3).path("accountCode").stringValue());
    assertEquals("CREDIT", resolvedLines.get(3).path("side").stringValue());
    assertEquals("1000", resolvedLines.get(3).path("amount").path("minorUnits").stringValue());

    ByteArrayOutputStream preflightTextOutput = new ByteArrayOutputStream();
    FinGrindCli preflightTextCli =
        cli(
            new ByteArrayInputStream(new byte[0]),
            utf8PrintStream(preflightTextOutput),
            fixedClock());
    assertEquals(
        0,
        preflightTextCli.run(
            attestedArguments(
                "preflight-entry",
                "--book-file",
                bookFilePath.toString(),
                "--book-key-file",
                bookKeyFilePath.toString(),
                "--request-file",
                saleRequestFile.toString(),
                "--output",
                "text")));
    String preflightText = preflightTextOutput.toString(StandardCharsets.UTF_8);
    assertTrue(preflightText.contains("Entry Preflight Passed"), preflightText);
    assertTrue(preflightText.contains("Journal lines"), preflightText);
    assertTrue(preflightText.contains("cost-of-sales"), preflightText);
    assertTrue(preflightText.contains("inventory"), preflightText);
    assertTrue(preflightText.contains("10.00"), preflightText);
  }

  @Test
  void run_inventoryMaintenanceCommandsThroughDefaultSqliteWorkflow() throws IOException {
    Path bookFilePath =
        tempDirectory.resolve("inventory-maintenance-books").resolve("entity.sqlite");
    Path bookKeyFilePath = writeBookKey(bookFilePath);
    assertEquals(
        0,
        cli(
                new ByteArrayInputStream(new byte[0]),
                utf8PrintStream(new ByteArrayOutputStream()),
                fixedClock())
            .run(
                jsonArguments(
                    openAccrualTradingBookKeyFileArguments(bookFilePath, bookKeyFilePath))));

    assertResolvedEventClass(
        recordEntry(
            bookFilePath,
            bookKeyFilePath,
            "record-purchase-settled",
            writeNamedRequest(
                "inventory-maintenance-purchase.json",
                tradingPurchaseSettledRequestJson("inventory-maintenance-purchase"))),
        "SETTLED_PURCHASE");
    assertResolvedEventClass(
        recordEntry(
            bookFilePath,
            bookKeyFilePath,
            "record-inventory-capitalization-settled",
            writeNamedRequest(
                "inventory-capitalization-settled.json",
                inventoryMaintenanceRequestJson(
                    "INVENTORY_CAPITALIZATION_SETTLED",
                    """
                    "inventoryAccountCode": "inventory",
                    "cashAccountCode": "cash",
                    "amount": {
                      "currencyCode": "EUR",
                      "minorUnits": "200"
                    }
                    """,
                    "landed-cost-invoice",
                    "inventory-capitalization-settled"))),
        "INVENTORY_CAPITALIZATION");
    assertResolvedEventClass(
        recordEntry(
            bookFilePath,
            bookKeyFilePath,
            "record-inventory-capitalization-on-credit",
            writeNamedRequest(
                "inventory-capitalization-on-credit.json",
                inventoryMaintenanceRequestJson(
                    "INVENTORY_CAPITALIZATION_ON_CREDIT",
                    """
                    "inventoryAccountCode": "inventory",
                    "payableAccountCode": "accounts-payable",
                    "amount": {
                      "currencyCode": "EUR",
                      "minorUnits": "300"
                    }
                    """,
                    "landed-cost-invoice",
                    "inventory-capitalization-on-credit"))),
        "INVENTORY_CAPITALIZATION");
    assertResolvedEventClass(
        recordEntry(
            bookFilePath,
            bookKeyFilePath,
            "record-inventory-shrinkage",
            writeNamedRequest(
                "inventory-shrinkage.json",
                inventoryMaintenanceRequestJson(
                    "INVENTORY_SHRINKAGE",
                    """
                    "inventoryAccountCode": "inventory",
                    "shrinkageLossAccountCode": "inventory-shrinkage-loss",
                    "quantity": "1"
                    """,
                    "inventory-count-sheet",
                    "inventory-shrinkage"))),
        "INVENTORY_SHRINKAGE");
    assertResolvedEventClass(
        recordEntry(
            bookFilePath,
            bookKeyFilePath,
            "record-inventory-count-increase",
            writeNamedRequest(
                "inventory-count-increase.json",
                inventoryMaintenanceRequestJson(
                    "INVENTORY_COUNT_INCREASE",
                    """
                    "inventoryAccountCode": "inventory",
                    "countGainAccountCode": "inventory-count-gain",
                    "quantity": "2",
                    "unitCost": {
                      "currencyCode": "EUR",
                      "minorUnits": "1000"
                    }
                    """,
                    "inventory-count-sheet",
                    "inventory-count-increase"))),
        "INVENTORY_COUNT_INCREASE");
    assertResolvedEventClass(
        recordEntry(
            bookFilePath,
            bookKeyFilePath,
            "record-inventory-write-down",
            writeNamedRequest(
                "inventory-write-down.json",
                inventoryMaintenanceRequestJson(
                    "INVENTORY_WRITE_DOWN",
                    """
                    "inventoryAccountCode": "inventory",
                    "writeDownLossAccountCode": "inventory-write-down-loss",
                    "amount": {
                      "currencyCode": "EUR",
                      "minorUnits": "100"
                    }
                    """,
                    "inventory-write-down-assessment",
                    "inventory-write-down"))),
        "INVENTORY_WRITE_DOWN");

    ByteArrayOutputStream balanceOutput = new ByteArrayOutputStream();
    assertEquals(
        0,
        cli(new ByteArrayInputStream(new byte[0]), utf8PrintStream(balanceOutput), fixedClock())
            .run(
                jsonArguments(
                    "account-balance",
                    "--book-file",
                    bookFilePath.toString(),
                    "--book-key-file",
                    bookKeyFilePath.toString(),
                    "--account-code",
                    "inventory")));
    assertJsonContains(balanceOutput, "\"status\":\"ok\"");
    assertJsonContains(balanceOutput, "\"accountName\":\"Inventory\"");
    assertJsonContains(balanceOutput, "\"minorUnits\":\"1900\"");
    assertJsonContains(balanceOutput, "\"balanceSide\":\"DEBIT\"");
  }

  @Test
  void run_rejectsPlaceholderRequestScaffoldBeforePreflightOrCommit() throws IOException {
    Path bookFilePath = tempDirectory.resolve("placeholder-books").resolve("entity.sqlite");
    Path bookKeyFilePath = writeBookKey(bookFilePath);
    ByteArrayOutputStream templateOutput = new ByteArrayOutputStream();
    FinGrindCli templateCli =
        cli(new ByteArrayInputStream(new byte[0]), utf8PrintStream(templateOutput), fixedClock());
    assertEquals(0, templateCli.run(new String[] {"print-request-template"}));
    Path requestFile =
        writeNamedRequest(
            "placeholder-request.json", templateOutput.toString(StandardCharsets.UTF_8));
    FinGrindCli openCli =
        cli(
            new ByteArrayInputStream(new byte[0]),
            utf8PrintStream(new ByteArrayOutputStream()),
            fixedClock());
    assertEquals(
        0, openCli.run(jsonArguments(openBookKeyFileArguments(bookFilePath, bookKeyFilePath))));

    ByteArrayOutputStream preflightOutput = new ByteArrayOutputStream();
    FinGrindCli preflightCli =
        cli(new ByteArrayInputStream(new byte[0]), utf8PrintStream(preflightOutput), fixedClock());
    assertEquals(
        1,
        preflightCli.run(
            jsonArguments(
                "preflight-entry",
                "--book-file",
                bookFilePath.toString(),
                "--book-key-file",
                bookKeyFilePath.toString(),
                "--request-file",
                requestFile.toString())));
    assertTrue(
        preflightOutput
            .toString(StandardCharsets.UTF_8)
            .contains("Scaffold placeholder must be replaced before submission"));

    ByteArrayOutputStream commitOutput = new ByteArrayOutputStream();
    FinGrindCli commitCli =
        cli(new ByteArrayInputStream(new byte[0]), utf8PrintStream(commitOutput), fixedClock());
    assertEquals(
        1,
        commitCli.run(
            jsonArguments(
                "record-sale-settled",
                "--book-file",
                bookFilePath.toString(),
                "--book-key-file",
                bookKeyFilePath.toString(),
                "--request-file",
                requestFile.toString())));
    assertTrue(
        commitOutput
            .toString(StandardCharsets.UTF_8)
            .contains("Scaffold placeholder must be replaced before submission"));
  }

  @Test
  void run_rekeyBookWithWrongCurrentKey_doesNotEchoCurrentOrReplacementSecret() throws IOException {
    Path bookFilePath = tempDirectory.resolve("wrong-rekey-books").resolve("entity.sqlite");
    Path currentBookKeyFilePath = writeBookKey(bookFilePath, TEST_BOOK_KEY);
    Path wrongCurrentBookKeyFilePath =
        writeNamedBookKey("wrong-current-book.key", "wrong-current-secret");
    Path replacementBookKeyFilePath = tempDirectory.resolve("replacement-secret-book.key");
    ByteArrayOutputStream openOutput = new ByteArrayOutputStream();
    FinGrindCli openCli =
        cli(new ByteArrayInputStream(new byte[0]), utf8PrintStream(openOutput), fixedClock());
    assertEquals(
        0,
        openCli.run(jsonArguments(openBookKeyFileArguments(bookFilePath, currentBookKeyFilePath))));
    ByteArrayOutputStream rekeyOutput = new ByteArrayOutputStream();
    FinGrindCli rekeyCli =
        cli(new ByteArrayInputStream(new byte[0]), utf8PrintStream(rekeyOutput), fixedClock());
    assertEquals(
        6,
        rekeyCli.run(
            jsonArguments(
                "rekey-book",
                "--book-file",
                bookFilePath.toString(),
                "--book-key-file",
                wrongCurrentBookKeyFilePath.toString(),
                "--new-book-key-file",
                replacementBookKeyFilePath.toString())));
    String outputText = rekeyOutput.toString(StandardCharsets.UTF_8);
    JsonNode failureEnvelope = new ObjectMapper().readTree(outputText);
    assertEquals("artifact-verification-failed", failureEnvelope.path("code").stringValue());
    assertFalse(outputText.contains("wrong-current-secret"));
    assertFalse(outputText.contains("replacement-secret"));
  }

  private static String[] openTradingBookKeyFileArguments(Path bookFilePath, Path bookKeyFilePath) {
    return founderAttestedArguments(
        bookFilePath,
        "open-book",
        "--book-file",
        bookFilePath.toString(),
        "--book-key-file",
        bookKeyFilePath.toString(),
        "--entity-name",
        tradingBookIdentity().entityName().value(),
        "--book-template-id",
        tradingBookIdentity().bookDoctrine().bookTemplateId().wireValue(),
        "--accounting-basis",
        tradingBookIdentity().bookDoctrine().accountingBasis().wireValue(),
        "--inventory-costing",
        dev.erst.fingrind.core.InventoryCostingDoctrine.WEIGHTED_AVERAGE.wireValue(),
        "--functional-currency",
        tradingBookIdentity().functionalCurrency().code(),
        "--fiscal-year-start",
        tradingBookIdentity().fiscalYearStart().wireValue(),
        "--book-start-effective-date",
        tradingBookIdentity().bookStartEffectiveDate().toString());
  }

  private static String[] openAccrualTradingBookKeyFileArguments(
      Path bookFilePath, Path bookKeyFilePath) {
    return founderAttestedArguments(
        bookFilePath,
        "open-book",
        "--book-file",
        bookFilePath.toString(),
        "--book-key-file",
        bookKeyFilePath.toString(),
        "--entity-name",
        tradingBookIdentity().entityName().value(),
        "--book-template-id",
        tradingBookIdentity().bookDoctrine().bookTemplateId().wireValue(),
        "--accounting-basis",
        dev.erst.fingrind.core.AccountingBasis.ACCRUAL.wireValue(),
        "--inventory-costing",
        dev.erst.fingrind.core.InventoryCostingDoctrine.WEIGHTED_AVERAGE.wireValue(),
        "--functional-currency",
        tradingBookIdentity().functionalCurrency().code(),
        "--fiscal-year-start",
        tradingBookIdentity().fiscalYearStart().wireValue(),
        "--book-start-effective-date",
        tradingBookIdentity().bookStartEffectiveDate().toString());
  }

  private JsonNode recordEntry(
      Path bookFilePath, Path bookKeyFilePath, String command, Path requestFile)
      throws IOException {
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    int exitCode =
        cli(new ByteArrayInputStream(new byte[0]), utf8PrintStream(output), fixedClock())
            .run(
                jsonArguments(
                    command,
                    "--book-file",
                    bookFilePath.toString(),
                    "--book-key-file",
                    bookKeyFilePath.toString(),
                    "--request-file",
                    requestFile.toString()));
    String response = output.toString(StandardCharsets.UTF_8);
    assertEquals(0, exitCode, response);
    JsonNode envelope = new ObjectMapper().readTree(response);
    assertEquals("ok", envelope.path("status").stringValue(), envelope.toPrettyString());
    return envelope;
  }

  private static void assertResolvedEventClass(JsonNode envelope, String expectedEventClass) {
    assertEquals(
        expectedEventClass,
        envelope
            .path("payload")
            .path("resolvedJournal")
            .path("classification")
            .path("eventClass")
            .stringValue(),
        envelope.toPrettyString());
  }

  private static String inventoryMaintenanceRequestJson(
      String entryKind, String entryFactsJson, String sourceDocumentType, String idempotencyKey) {
    return """
        {
          "entryKind": "%s",
          "effectiveDate": "2026-04-07",
          %s,
          "evidence": {
            "sourceDocuments": [
              {
                "sourceDocumentId": "document-%s",
                "sourceDocumentType": "%s",
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
        .formatted(
            entryKind,
            entryFactsJson,
            idempotencyKey,
            sourceDocumentType,
            idempotencyKey,
            idempotencyKey);
  }

  private static String tradingPurchaseSettledRequestJson(String idempotencyKey) {
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

  private static String tradingSaleSettledRequestJson(String idempotencyKey) {
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
            "quantity": "1"
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

  @Test
  void run_backupRestoreAndTrialBalanceThroughDefaultSqliteWorkflowPreservesReadableFacts()
      throws IOException {
    Path requestFile = writeRequest(validRequestJson());
    Path declareCashFile =
        writeNamedRequest("restore-declare-cash.json", declareAccountJson("1000", "Cash", "DEBIT"));
    Path declareRevenueFile =
        writeNamedRequest(
            "restore-declare-revenue.json", declareAccountJson("2000", "Revenue", "CREDIT"));
    Path bookFilePath = tempDirectory.resolve("restore-books").resolve("entity.sqlite");
    Path bookKeyFilePath = writeBookKey(bookFilePath);
    Path backupFilePath = tempDirectory.resolve("restore-books").resolve("entity-backup.sqlite");
    Path backupKeyFilePath = tempDirectory.resolve("restore-books").resolve("entity-backup.key");
    Path restoredBookFilePath =
        tempDirectory.resolve("restore-books").resolve("entity-restored.sqlite");
    Path restoredBookKeyFilePath =
        tempDirectory.resolve("restore-books").resolve("entity-restored.key");

    assertEquals(
        0,
        cli(
                new ByteArrayInputStream(new byte[0]),
                utf8PrintStream(new ByteArrayOutputStream()),
                fixedClock())
            .run(jsonArguments(openBookKeyFileArguments(bookFilePath, bookKeyFilePath))));
    assertEquals(
        0,
        cli(
                new ByteArrayInputStream(new byte[0]),
                utf8PrintStream(new ByteArrayOutputStream()),
                fixedClock())
            .run(
                attestedArguments(
                    "declare-account",
                    "--book-file",
                    bookFilePath.toString(),
                    "--book-key-file",
                    bookKeyFilePath.toString(),
                    "--request-file",
                    declareCashFile.toString())));
    assertEquals(
        0,
        cli(
                new ByteArrayInputStream(new byte[0]),
                utf8PrintStream(new ByteArrayOutputStream()),
                fixedClock())
            .run(
                attestedArguments(
                    "declare-account",
                    "--book-file",
                    bookFilePath.toString(),
                    "--book-key-file",
                    bookKeyFilePath.toString(),
                    "--request-file",
                    declareRevenueFile.toString())));
    assertEquals(
        0,
        cli(
                new ByteArrayInputStream(new byte[0]),
                utf8PrintStream(new ByteArrayOutputStream()),
                fixedClock())
            .run(
                jsonArguments(
                    "record-sale-settled",
                    "--book-file",
                    bookFilePath.toString(),
                    "--book-key-file",
                    bookKeyFilePath.toString(),
                    "--request-file",
                    requestFile.toString())));
    assertEquals(
        0,
        cli(
                new ByteArrayInputStream(new byte[0]),
                utf8PrintStream(new ByteArrayOutputStream()),
                fixedClock())
            .run(
                jsonArguments(
                    "backup-book",
                    "--book-file",
                    bookFilePath.toString(),
                    "--book-key-file",
                    bookKeyFilePath.toString(),
                    "--backup-file",
                    backupFilePath.toString(),
                    "--backup-id",
                    "018f0000-0000-7000-8000-000000000003",
                    "--new-backup-key-file",
                    backupKeyFilePath.toString())));
    ByteArrayOutputStream restoreOutput = new ByteArrayOutputStream();
    assertEquals(
        0,
        cli(new ByteArrayInputStream(new byte[0]), utf8PrintStream(restoreOutput), fixedClock())
            .run(
                attestedArgumentsForBook(
                    bookFilePath,
                    "restore-book",
                    "--book-file",
                    restoredBookFilePath.toString(),
                    "--new-book-key-file",
                    restoredBookKeyFilePath.toString(),
                    "--backup-file",
                    backupFilePath.toString(),
                    "--backup-key-file",
                    backupKeyFilePath.toString(),
                    "--output",
                    "json")));
    JsonNode restoreEnvelope = new ObjectMapper().readTree(restoreOutput.toByteArray());
    assertEquals("ok", restoreEnvelope.path("status").stringValue());
    assertEquals(
        CliPublicPaths.absoluteValue(restoredBookFilePath),
        restoreEnvelope.path("payload").path("bookFile").stringValue());
    assertEquals(
        CliPublicPaths.absoluteValue(restoredBookKeyFilePath),
        restoreEnvelope.path("payload").path("bookKeyFilePath").stringValue());

    ByteArrayOutputStream trialBalanceOutput = new ByteArrayOutputStream();
    assertEquals(
        0,
        cli(
                new ByteArrayInputStream(new byte[0]),
                utf8PrintStream(trialBalanceOutput),
                fixedClock())
            .run(
                jsonArguments(
                    "trial-balance",
                    "--book-file",
                    restoredBookFilePath.toString(),
                    "--book-key-file",
                    restoredBookKeyFilePath.toString())));
    assertJsonContains(trialBalanceOutput, "\"status\":\"ok\"");
    assertJsonContains(trialBalanceOutput, "\"family\":\"trial-balance\"");

    ByteArrayOutputStream wrongKeyOutput = new ByteArrayOutputStream();
    assertEquals(
        6,
        cli(new ByteArrayInputStream(new byte[0]), utf8PrintStream(wrongKeyOutput), fixedClock())
            .run(
                jsonArguments(
                    "trial-balance",
                    "--book-file",
                    restoredBookFilePath.toString(),
                    "--book-key-file",
                    backupKeyFilePath.toString())));
    JsonNode wrongKeyEnvelope = new ObjectMapper().readTree(wrongKeyOutput.toByteArray());
    assertEquals(
        ContractErrors.Descriptor.PROTECTED_BOOK_VERIFICATION_FAILED.code(),
        wrongKeyEnvelope.path("code").stringValue());
  }
}
