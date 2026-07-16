package dev.erst.fingrind.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.discovery.MachineContract;
import dev.erst.fingrind.contract.protocol.OperationId;
import dev.erst.fingrind.contract.protocol.ProtocolArtifactOutput;
import dev.erst.fingrind.contract.protocol.ProtocolCatalog;
import dev.erst.fingrind.contract.runtime.ContractResponseCatalog;
import dev.erst.fingrind.core.IdempotencyKey;
import dev.erst.fingrind.core.NormalBalance;
import dev.erst.fingrind.core.PostingId;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/**
 * Workflow-level contract tests for the canonical CLI success, rejection, and failure envelopes.
 */
class FinGrindCliEnvelopeContractTest extends CliPublicDocsContractSupport {
  @Test
  void discoveryCommands_publishProtocolVersionAndCanonicalFailureEnvelope() throws IOException {
    for (String command : List.of("help", "capabilities", "version", "environment")) {
      JsonNode success = runJsonCommand(command);
      assertSuccessEnvelope(success);
      if (!"environment".equals(command)) {
        assertProtocolVersion(success.path("payload"));
      }
    }

    for (String command : List.of("help", "capabilities", "version", "environment")) {
      JsonNode failure = runJsonDiagnosticsCommandExpectingExit(1, command, "--bogus");
      assertErrorDiagnosticsEnvelope(failure, "invalid-request");
      assertEquals("--bogus", failure.path("argument").stringValue(), command);
    }
  }

  @Test
  void templateCommands_emitRawDocumentsInsteadOfEnvelopes() throws IOException {
    JsonNode requestTemplate = runRawJsonCommand("print-request-template");
    JsonNode planTemplate = runRawJsonCommand("print-plan-template");

    assertTrue(requestTemplate.isObject());
    assertTrue(planTemplate.isObject());
    assertTrue(requestTemplate.path("status").isMissingNode());
    assertTrue(planTemplate.path("status").isMissingNode());

    for (String command : List.of("print-request-template", "print-plan-template")) {
      JsonNode failure = runJsonDiagnosticsCommandExpectingExit(1, command, "--bogus");
      assertErrorDiagnosticsEnvelope(failure, "invalid-request");
      assertEquals("--bogus", failure.path("argument").stringValue(), command);
    }
  }

  @Test
  void generateBookKeyFile_publishesArtifactSuccessAndCanonicalFailureEnvelope()
      throws IOException {
    Path bookKeyFilePath = tempDirectory.resolve("generated-book-key").resolve("entity.book-key");

    JsonNode success =
        runJsonCommand("generate-book-key-file", "--new-book-key-file", bookKeyFilePath.toString());
    assertSuccessEnvelope(success);
    assertSingleArtifact(success, ProtocolArtifactOutput.bookKeyFileFormat(), bookKeyFilePath);

    JsonNode failure = runJsonDiagnosticsCommandExpectingExit(1, "generate-book-key-file");
    assertErrorDiagnosticsEnvelope(failure, "invalid-request");
    assertEquals("--new-book-key-file", failure.path("argument").stringValue());
  }

  @Test
  void administrationCommands_publishSuccessRejectionAndFailureEnvelopes() throws IOException {
    Path bookFilePath = tempDirectory.resolve("admin-contract").resolve("entity.sqlite");
    Path bookKeyFilePath = writeBookKey(bookFilePath);

    JsonNode success = runJsonCommand(openBookKeyFileArguments(bookFilePath, bookKeyFilePath));
    assertSuccessEnvelope(success);
    assertEquals(
        "Acme Studio",
        success.path("payload").path("bookIdentity").path("entityName").stringValue());

    JsonNode occupiedDestination =
        runJsonDiagnosticsCommandExpectingExit(
            7, openBookKeyFileArguments(bookFilePath, bookKeyFilePath));
    assertErrorDiagnosticsEnvelope(occupiedDestination, "book-destination-occupied");
    assertEquals("--book-file", occupiedDestination.path("argument").stringValue());

    Path missingBookFilePath = tempDirectory.resolve("admin-contract").resolve("missing.sqlite");
    Path missingBookKeyFilePath = writeBookKey(missingBookFilePath);
    Path declarationRequestFile =
        writeNamedRequest(
            "admin-contract-declare-account.json", declareAccountJson("1000", "Cash", "DEBIT"));
    JsonNode rejection =
        runJsonDiagnosticsCommandExpectingExit(
            2,
            "declare-account",
            "--book-file",
            missingBookFilePath.toString(),
            "--book-key-file",
            missingBookKeyFilePath.toString(),
            "--request-file",
            declarationRequestFile.toString());
    assertRejectedEnvelope(rejection);

    Path invalidBookFilePath = tempDirectory.resolve("admin-contract").resolve("invalid.sqlite");
    Path invalidBookKeyFilePath = writeBookKey(invalidBookFilePath);
    JsonNode failure =
        runJsonDiagnosticsCommandExpectingExit(
            1,
            "open-book",
            "--book-file",
            invalidBookFilePath.toString(),
            "--book-key-file",
            invalidBookKeyFilePath.toString());
    assertErrorEnvelope(failure);
    assertEquals("--entity-name", failure.path("argument").stringValue());
  }

  @Test
  void writeCommands_publishSuccessRejectionAndFailureEnvelopes() throws IOException {
    Path bookFilePath = tempDirectory.resolve("write-contract").resolve("entity.sqlite");
    Path bookKeyFilePath = writeBookKey(bookFilePath);
    Path declareCashFile =
        writeNamedRequest("write-declare-cash.json", declareAccountJson("1000", "Cash", "DEBIT"));
    Path declareBankFile =
        writeNamedRequest(
            "write-declare-bank.json",
            declareAccountJson(
                "operating-bank", "Operating Bank", "ASSET", "CURRENT_ASSET", null, "NON_CASH"));
    Path requestFile = writeRequest(validAdmissibleRawJournalRequestJson());
    Path malformedRequestFile = writeNamedRequest("write-malformed.json", "{");

    runJsonCommand(openBookKeyFileArguments(bookFilePath, bookKeyFilePath));
    runJsonCommand(
        "declare-account",
        "--book-file",
        bookFilePath.toString(),
        "--book-key-file",
        bookKeyFilePath.toString(),
        "--request-file",
        declareCashFile.toString());
    runJsonCommand(
        "declare-account",
        "--book-file",
        bookFilePath.toString(),
        "--book-key-file",
        bookKeyFilePath.toString(),
        "--request-file",
        declareBankFile.toString());

    JsonNode success =
        runJsonCommand(
            "post-entry",
            "--book-file",
            bookFilePath.toString(),
            "--book-key-file",
            bookKeyFilePath.toString(),
            "--request-file",
            requestFile.toString());
    assertSuccessEnvelope(success);
    assertTrue(success.path("payload").path("postingId").isTextual());
    assertTrue(success.path("payload").path("resolvedJournal").isObject());
    assertTrue(
        success
            .path("payload")
            .path("resolvedJournal")
            .path("classification")
            .path("eventClass")
            .isTextual());

    Path missingBookFilePath = tempDirectory.resolve("write-contract").resolve("missing.sqlite");
    Path missingBookKeyFilePath = writeBookKey(missingBookFilePath);
    JsonNode rejection =
        runJsonDiagnosticsCommandExpectingExit(
            2,
            "post-entry",
            "--book-file",
            missingBookFilePath.toString(),
            "--book-key-file",
            missingBookKeyFilePath.toString(),
            "--request-file",
            requestFile.toString());
    assertRejectedEnvelope(rejection);

    JsonNode failure =
        runJsonDiagnosticsCommandExpectingExit(
            1,
            "post-entry",
            "--book-file",
            bookFilePath.toString(),
            "--book-key-file",
            bookKeyFilePath.toString(),
            "--request-file",
            malformedRequestFile.toString());
    assertErrorEnvelope(failure);
    assertEquals("invalid-request", failure.path("code").stringValue());
  }

  @Test
  void queryCommands_publishSuccessRejectionAndFailureEnvelopes() throws IOException {
    Path bookFilePath = tempDirectory.resolve("query-contract").resolve("entity.sqlite");
    Path bookKeyFilePath = writeBookKey(bookFilePath);

    runJsonCommand(openBookKeyFileArguments(bookFilePath, bookKeyFilePath));
    JsonNode success =
        runJsonCommand(
            "list-accounts",
            "--book-file",
            bookFilePath.toString(),
            "--book-key-file",
            bookKeyFilePath.toString());
    assertSuccessEnvelope(success);
    assertTrue(success.path("payload").path("accounts").isArray());

    Path missingBookFilePath = tempDirectory.resolve("query-contract").resolve("missing.sqlite");
    Path missingBookKeyFilePath = writeBookKey(missingBookFilePath);
    JsonNode rejection =
        runJsonDiagnosticsCommandExpectingExit(
            2,
            "list-accounts",
            "--book-file",
            missingBookFilePath.toString(),
            "--book-key-file",
            missingBookKeyFilePath.toString());
    assertRejectedEnvelope(rejection);

    JsonNode failure =
        runJsonDiagnosticsCommandExpectingExit(
            1,
            "list-accounts",
            "--book-file",
            bookFilePath.toString(),
            "--book-key-file",
            bookKeyFilePath.toString(),
            "--cursor",
            "not-a-cursor");
    assertErrorEnvelope(failure);
    assertEquals("invalid-page-cursor", failure.path("code").stringValue());
  }

  @Test
  void executePlan_publishesSuccessRejectionAndFailureOnPrimaryOutput() throws IOException {
    Path openOnlyPlanFile = writeNamedRequest("contract-open-plan.json", openOnlyPlanJson());
    Path validPlanFile = writeNamedRequest("contract-valid-plan.json", validPlanJson());
    Path assertionPlanFile =
        writeNamedRequest("contract-assertion-plan.json", openThenFailAssertionPlanJson());

    Path successBookFilePath = tempDirectory.resolve("plan-contract").resolve("open-only.sqlite");
    Path successBookKeyFilePath = writeBookKey(successBookFilePath);
    JsonNode success =
        runJsonCommandExpectingExit(
            0,
            "execute-plan",
            "--book-file",
            successBookFilePath.toString(),
            "--book-key-file",
            successBookKeyFilePath.toString(),
            "--result-detail",
            "full",
            "--request-file",
            openOnlyPlanFile.toString());
    assertSuccessEnvelope(success);
    assertEquals("succeeded", success.path("payload").path("status").stringValue());

    Path rejectedBookFilePath = tempDirectory.resolve("plan-contract").resolve("missing.sqlite");
    Path rejectedBookKeyFilePath = writeBookKey(rejectedBookFilePath);
    JsonNode rejection =
        runJsonCommandExpectingExit(
            2,
            "execute-plan",
            "--book-file",
            rejectedBookFilePath.toString(),
            "--book-key-file",
            rejectedBookKeyFilePath.toString(),
            "--result-detail",
            "full",
            "--request-file",
            validPlanFile.toString());
    assertRejectedEnvelope(rejection);
    assertTrue(rejection.path("payload").isObject());
    assertEquals("rejected", rejection.path("payload").path("status").stringValue());

    Path failedBookFilePath =
        tempDirectory.resolve("plan-contract").resolve("assertion-failure.sqlite");
    Path failedBookKeyFilePath = writeBookKey(failedBookFilePath);
    JsonNode failure =
        runJsonCommandExpectingExit(
            3,
            "execute-plan",
            "--book-file",
            failedBookFilePath.toString(),
            "--book-key-file",
            failedBookKeyFilePath.toString(),
            "--result-detail",
            "full",
            "--request-file",
            assertionPlanFile.toString());
    assertErrorEnvelope(failure);
    assertTrue(failure.path("payload").isObject());
    assertEquals("assertion-failed", failure.path("payload").path("status").stringValue());
  }

  @Test
  void realCliSuccessSweep_coversRemainingEnvelopeCommandsAndArtifactHomes() throws IOException {
    Path root = tempDirectory.resolve("success-sweep");
    Path bookFilePath = root.resolve("books").resolve("entity.sqlite");
    Path currentBookKeyFilePath = writeBookKey(bookFilePath);
    Path declareCashFile =
        writeNamedRequest("sweep-declare-cash.json", declareAccountJson("1000", "Cash", "DEBIT"));
    Path declareBankFile =
        writeNamedRequest(
            "sweep-declare-bank.json",
            declareAccountJson(
                "operating-bank", "Operating Bank", "ASSET", "CURRENT_ASSET", null, "NON_CASH"));
    Path requestFile = writeRequest(validAdmissibleRawJournalRequestJson());

    assertGeneratedBookKeyArtifact(root);
    String postingId =
        runSuccessSweepBookLifecycle(
            bookFilePath, currentBookKeyFilePath, declareCashFile, declareBankFile, requestFile);
    assertSuccessSweepReadCommands(bookFilePath, currentBookKeyFilePath, postingId);
    Path replacementBookKeyFilePath = runSuccessSweepRekey(bookFilePath, currentBookKeyFilePath);

    Path backupBookFilePath = root.resolve("backup").resolve("entity.sqlite");
    Path backupBookKeyFilePath = root.resolve("backup").resolve("entity.book-key");
    JsonNode backupBook =
        runJsonCommand(
            "backup-book",
            "--book-file",
            bookFilePath.toString(),
            "--book-key-file",
            replacementBookKeyFilePath.toString(),
            "--backup-file",
            backupBookFilePath.toString(),
            "--new-backup-key-file",
            backupBookKeyFilePath.toString());
    assertSuccessEnvelope(backupBook);
    assertArtifactList(
        backupBook,
        List.of(
            artifactExpectation(ProtocolArtifactOutput.backupFileFormat(), backupBookFilePath),
            artifactExpectation(
                ProtocolArtifactOutput.backupKeyFileFormat(), backupBookKeyFilePath)));

    Path restoredBookFilePath = root.resolve("restored").resolve("entity.sqlite");
    JsonNode restoredBook =
        runJsonCommand(
            "restore-book",
            "--book-file",
            restoredBookFilePath.toString(),
            "--new-book-key-file",
            root.resolve("restored").resolve("entity.book-key").toString(),
            "--backup-file",
            backupBookFilePath.toString(),
            "--backup-key-file",
            backupBookKeyFilePath.toString());
    assertSuccessEnvelope(restoredBook);
    assertArtifactList(
        restoredBook,
        List.of(
            artifactExpectation(ProtocolArtifactOutput.bookFileFormat(), restoredBookFilePath),
            artifactExpectation(
                ProtocolArtifactOutput.bookKeyFileFormat(),
                root.resolve("restored").resolve("entity.book-key"))));
  }

  private void assertGeneratedBookKeyArtifact(Path root) throws IOException {
    Path generatedBookKeyPath = root.resolve("secrets").resolve("generated.book-key");
    JsonNode generatedBookKey =
        runJsonCommand(
            "generate-book-key-file", "--new-book-key-file", generatedBookKeyPath.toString());
    assertSuccessEnvelope(generatedBookKey);
    assertSingleArtifact(
        generatedBookKey, ProtocolArtifactOutput.bookKeyFileFormat(), generatedBookKeyPath);
  }

  private String runSuccessSweepBookLifecycle(
      Path bookFilePath,
      Path currentBookKeyFilePath,
      Path declareCashFile,
      Path declareBankFile,
      Path requestFile)
      throws IOException {
    assertNonArtifactSuccess(
        runJsonCommand(openBookKeyFileArguments(bookFilePath, currentBookKeyFilePath)));
    assertDeclareAccountSuccess(bookFilePath, currentBookKeyFilePath, declareCashFile);
    assertDeclareAccountSuccess(bookFilePath, currentBookKeyFilePath, declareBankFile);
    assertNonArtifactSuccess(
        runJsonCommand(
            "preflight-entry",
            "--book-file",
            bookFilePath.toString(),
            "--book-key-file",
            currentBookKeyFilePath.toString(),
            "--request-file",
            requestFile.toString()));
    JsonNode committedEntry =
        runJsonCommand(
            "post-entry",
            "--book-file",
            bookFilePath.toString(),
            "--book-key-file",
            currentBookKeyFilePath.toString(),
            "--request-file",
            requestFile.toString());
    assertNonArtifactSuccess(committedEntry);
    return committedEntry.path("payload").path("postingId").stringValue();
  }

  private void assertSuccessSweepReadCommands(
      Path bookFilePath, Path currentBookKeyFilePath, String postingId) throws IOException {
    assertNonArtifactSuccess(
        runJsonCommand(
            "inspect-book",
            "--book-file",
            bookFilePath.toString(),
            "--book-key-file",
            currentBookKeyFilePath.toString()));
    assertNonArtifactSuccess(
        runJsonCommand(
            "list-accounts",
            "--book-file",
            bookFilePath.toString(),
            "--book-key-file",
            currentBookKeyFilePath.toString()));
    assertNonArtifactSuccess(
        runJsonCommand(
            "get-posting",
            "--book-file",
            bookFilePath.toString(),
            "--book-key-file",
            currentBookKeyFilePath.toString(),
            "--posting-id",
            postingId));
    assertNonArtifactSuccess(
        runJsonCommand(
            "list-postings",
            "--book-file",
            bookFilePath.toString(),
            "--book-key-file",
            currentBookKeyFilePath.toString(),
            "--limit",
            "10"));
    assertNonArtifactSuccess(
        runJsonCommand(
            "account-balance",
            "--book-file",
            bookFilePath.toString(),
            "--book-key-file",
            currentBookKeyFilePath.toString(),
            "--account-code",
            "1000"));
    assertNonArtifactSuccess(
        runJsonCommand(
            "trial-balance",
            "--book-file",
            bookFilePath.toString(),
            "--book-key-file",
            currentBookKeyFilePath.toString()));
    assertNonArtifactSuccess(
        runJsonCommand(
            "account-ledger",
            "--book-file",
            bookFilePath.toString(),
            "--book-key-file",
            currentBookKeyFilePath.toString(),
            "--account-code",
            "1000",
            "--effective-date-from",
            "2026-04-01",
            "--effective-date-to",
            "2026-04-30"));
    assertNonArtifactSuccess(
        runJsonCommand(
            "period-summary",
            "--book-file",
            bookFilePath.toString(),
            "--book-key-file",
            currentBookKeyFilePath.toString(),
            "--period-start",
            "2026-04-01",
            "--period-end",
            "2026-04-30"));
    assertNonArtifactSuccess(
        runJsonCommand(
            "interim-result-sweep",
            "--book-file",
            bookFilePath.toString(),
            "--book-key-file",
            currentBookKeyFilePath.toString(),
            "--through",
            "2026-04-07"));
    assertNonArtifactSuccess(
        runJsonCommand(
            "financial-position",
            "--book-file",
            bookFilePath.toString(),
            "--book-key-file",
            currentBookKeyFilePath.toString(),
            "--effective-date-as-of",
            "2026-04-07"));
    assertNonArtifactSuccess(
        runJsonCommand(
            "income-statement",
            "--book-file",
            bookFilePath.toString(),
            "--book-key-file",
            currentBookKeyFilePath.toString(),
            "--period-start",
            "2026-04-07",
            "--period-end",
            "2026-04-07"));
    assertNonArtifactSuccess(
        runJsonCommand(
            "changes-in-equity",
            "--book-file",
            bookFilePath.toString(),
            "--book-key-file",
            currentBookKeyFilePath.toString(),
            "--period-start",
            "2026-04-07",
            "--period-end",
            "2026-04-07"));
  }

  private Path runSuccessSweepRekey(Path bookFilePath, Path currentBookKeyFilePath)
      throws IOException {
    Path replacementBookKeyFilePath = tempDirectory.resolve("success-sweep-rotated.key");
    JsonNode rekeyedBook =
        runJsonCommand(
            "rekey-book",
            "--book-file",
            bookFilePath.toString(),
            "--book-key-file",
            currentBookKeyFilePath.toString(),
            "--new-book-key-file",
            replacementBookKeyFilePath.toString());
    assertSuccessEnvelope(rekeyedBook);
    assertSingleArtifact(
        rekeyedBook, ProtocolArtifactOutput.bookKeyFileFormat(), replacementBookKeyFilePath);
    JsonNode inspectRollback =
        runJsonCommand("inspect-rekey-rollback", "--book-file", bookFilePath.toString());
    assertSuccessEnvelope(inspectRollback);
    assertTrue(inspectRollback.path("artifacts").isMissingNode());
    return replacementBookKeyFilePath;
  }

  private void assertDeclareAccountSuccess(
      Path bookFilePath, Path currentBookKeyFilePath, Path requestFile) throws IOException {
    assertNonArtifactSuccess(
        runJsonCommand(
            "declare-account",
            "--book-file",
            bookFilePath.toString(),
            "--book-key-file",
            currentBookKeyFilePath.toString(),
            "--request-file",
            requestFile.toString()));
  }

  @Test
  void rollbackRecoveryWorkflowSuccesses_publishArtifactsOnlyAtTopLevel() throws IOException {
    Path bookFilePath = tempDirectory.resolve("workflow-rollback").resolve("entity.sqlite");
    Path bookKeyFilePath = tempDirectory.resolve("workflow-rollback").resolve("entity.key");
    Path rollbackArtifactPath =
        tempDirectory.resolve("workflow-rollback").resolve("entity.rekey-rollback.sqlite");

    RecordingWorkflow inspectWorkflow = contractWorkflow();
    inspectWorkflow.setRekeyRollbackResult(
        new dev.erst.fingrind.contract.bookkeeping.RekeyRollbackResult.Inspected(
            hint(bookFilePath), List.of(hint(rollbackArtifactPath))));
    JsonNode inspectedRollback =
        runJsonWorkflowCommand(
            inspectWorkflow, "inspect-rekey-rollback", "--book-file", bookFilePath.toString());
    assertSuccessEnvelope(inspectedRollback);
    assertSingleArtifact(
        inspectedRollback, ProtocolArtifactOutput.rollbackBookFileFormat(), rollbackArtifactPath);

    RecordingWorkflow restoreWorkflow = contractWorkflow();
    restoreWorkflow.setRekeyRollbackResult(
        new dev.erst.fingrind.contract.bookkeeping.RekeyRollbackResult.Restored(
            hint(bookFilePath), hint(rollbackArtifactPath)));
    JsonNode restoredRollback =
        runJsonWorkflowCommand(
            restoreWorkflow,
            "restore-rekey-rollback",
            "--book-file",
            bookFilePath.toString(),
            "--book-key-file",
            bookKeyFilePath.toString(),
            "--rollback-book-file",
            rollbackArtifactPath.toString());
    assertSuccessEnvelope(restoredRollback);
    assertSingleArtifact(
        restoredRollback, ProtocolArtifactOutput.rollbackBookFileFormat(), rollbackArtifactPath);

    RecordingWorkflow deleteWorkflow = contractWorkflow();
    deleteWorkflow.setRekeyRollbackResult(
        new dev.erst.fingrind.contract.bookkeeping.RekeyRollbackResult.Deleted(
            hint(bookFilePath), hint(rollbackArtifactPath)));
    JsonNode deletedRollback =
        runJsonWorkflowCommand(
            deleteWorkflow,
            "delete-rekey-rollback",
            "--book-file",
            bookFilePath.toString(),
            "--book-key-file",
            bookKeyFilePath.toString(),
            "--rollback-book-file",
            rollbackArtifactPath.toString());
    assertSuccessEnvelope(deletedRollback);
    assertSingleArtifact(
        deletedRollback, ProtocolArtifactOutput.rollbackBookFileFormat(), rollbackArtifactPath);
  }

  @Test
  void contractCoverageTracksEveryPublishedOperation() {
    Set<OperationId> coveredOperations =
        EnumSet.of(
            OperationId.HELP,
            OperationId.VERSION,
            OperationId.CAPABILITIES,
            OperationId.ENVIRONMENT,
            OperationId.PRINT_REQUEST_TEMPLATE,
            OperationId.PRINT_PLAN_TEMPLATE,
            OperationId.GENERATE_BOOK_KEY_FILE,
            OperationId.OPEN_BOOK,
            OperationId.REKEY_BOOK,
            OperationId.BACKUP_BOOK,
            OperationId.RESTORE_BOOK,
            OperationId.INSPECT_REKEY_ROLLBACK,
            OperationId.DELETE_REKEY_ROLLBACK,
            OperationId.RESTORE_REKEY_ROLLBACK,
            OperationId.DECLARE_ACCOUNT,
            OperationId.AMEND_ACCOUNT,
            OperationId.RETIRE_ACCOUNT,
            OperationId.DECLARE_TAX_REGISTRATION,
            OperationId.INTERIM_RESULT_SWEEP,
            OperationId.FISCAL_YEAR_CLOSE,
            OperationId.INSPECT_BOOK,
            OperationId.LIST_ACCOUNTS,
            OperationId.LIST_TAX_REGISTRATIONS,
            OperationId.TAX_OBLIGATION,
            OperationId.GET_POSTING,
            OperationId.LIST_POSTINGS,
            OperationId.ACCOUNT_BALANCE,
            OperationId.TRIAL_BALANCE,
            OperationId.ACCOUNT_LEDGER,
            OperationId.PERIOD_SUMMARY,
            OperationId.FINANCIAL_POSITION,
            OperationId.INVENTORY_VALUATION,
            OperationId.ACCRUAL_CUTOFF_SCHEDULE,
            OperationId.FIXED_ASSET_REGISTER,
            OperationId.FINANCING_REGISTER,
            OperationId.REALIZED_FOREIGN_EXCHANGE_REGISTER,
            OperationId.LATVIAN_PAYROLL_REGISTER,
            OperationId.INCOME_STATEMENT,
            OperationId.CASH_FLOW_STATEMENT,
            OperationId.CHANGES_IN_EQUITY,
            OperationId.EXECUTE_PLAN,
            OperationId.PREFLIGHT_ENTRY,
            OperationId.RECORD_SALE_SETTLED,
            OperationId.RECORD_SALE_ON_CREDIT,
            OperationId.RECORD_PURCHASE_SETTLED,
            OperationId.RECORD_PURCHASE_ON_CREDIT,
            OperationId.RECORD_INVENTORY_CAPITALIZATION_SETTLED,
            OperationId.RECORD_INVENTORY_CAPITALIZATION_ON_CREDIT,
            OperationId.RECORD_INVENTORY_WRITE_DOWN,
            OperationId.RECORD_INVENTORY_SHRINKAGE,
            OperationId.RECORD_INVENTORY_COUNT_INCREASE,
            OperationId.RECORD_PREPAYMENT,
            OperationId.RECORD_DEFERRED_REVENUE,
            OperationId.RECORD_ACCRUED_EXPENSE,
            OperationId.RECORD_ACCRUAL_CUTOFF_RECOGNITION,
            OperationId.RECORD_ACCRUED_EXPENSE_SETTLEMENT,
            OperationId.RECORD_LATVIAN_MONTHLY_PAYROLL,
            OperationId.RECORD_LATVIAN_PAYROLL_NET_WAGE_SETTLEMENT,
            OperationId.RECORD_LATVIAN_PAYROLL_STATE_REMITTANCE,
            OperationId.RECORD_FIXED_ASSET_CAPITALIZATION,
            OperationId.RECORD_FIXED_ASSET_DEPRECIATION,
            OperationId.RECORD_FIXED_ASSET_DISPOSAL,
            OperationId.RECORD_FINANCING_BORROWING,
            OperationId.RECORD_FINANCING_PRINCIPAL_REPAYMENT,
            OperationId.RECORD_FINANCING_INTEREST_ACCRUAL,
            OperationId.RECORD_FINANCING_INTEREST_PAYMENT,
            OperationId.RECORD_FOREIGN_CURRENCY_OBLIGATION,
            OperationId.RECORD_REALIZED_FOREIGN_EXCHANGE_SETTLEMENT,
            OperationId.RECORD_EXPENSE_SETTLED,
            OperationId.RECORD_EXPENSE_ON_CREDIT,
            OperationId.RECORD_RECEIPT,
            OperationId.RECORD_PAYMENT,
            OperationId.RECORD_OWNER_CONTRIBUTION,
            OperationId.RECORD_OWNER_WITHDRAWAL,
            OperationId.RECORD_OPENING_POSITION,
            OperationId.RECORD_REVERSAL,
            OperationId.POST_ENTRY);

    assertEquals(
        ProtocolCatalog.operations().stream()
            .map(operation -> operation.id())
            .collect(java.util.stream.Collectors.toSet()),
        coveredOperations);
  }

  private static void assertSuccessEnvelope(JsonNode envelope) {
    assertEquals("ok", envelope.path("status").stringValue());
    assertTrue(envelope.path("payload").isObject());
    assertTrue(envelope.path("code").isMissingNode());
    assertTrue(envelope.path("message").isMissingNode());
    assertTrue(envelope.path("category").isMissingNode());
  }

  private static void assertNonArtifactSuccess(JsonNode envelope) {
    assertSuccessEnvelope(envelope);
    assertNoArtifacts(envelope);
  }

  private static void assertRejectedEnvelope(JsonNode envelope) {
    assertEquals("rejected", envelope.path("status").stringValue());
    assertTrue(envelope.path("code").isTextual());
    assertTrue(envelope.path("message").isTextual());
    assertEquals(
        ContractResponseCatalog.failureCategoryFor(envelope.path("code").stringValue()).wireValue(),
        envelope.path("category").stringValue());
    assertTrue(envelope.path("artifacts").isMissingNode());
  }

  private static void assertErrorEnvelope(JsonNode envelope) {
    assertEquals("error", envelope.path("status").stringValue());
    assertTrue(envelope.path("code").isTextual());
    assertTrue(envelope.path("message").isTextual());
    assertEquals(
        ContractResponseCatalog.failureCategoryFor(envelope.path("code").stringValue()).wireValue(),
        envelope.path("category").stringValue());
    assertTrue(envelope.path("artifacts").isMissingNode());
  }

  private static void assertErrorEnvelope(JsonNode envelope, String expectedCode) {
    assertErrorEnvelope(envelope);
    assertEquals(expectedCode, envelope.path("code").stringValue());
  }

  private static void assertErrorDiagnosticsEnvelope(JsonNode envelope, String expectedCode) {
    assertErrorEnvelope(envelope, expectedCode);
    assertTrue(envelope.path("payload").isMissingNode());
  }

  private static void assertProtocolVersion(JsonNode payload) {
    assertEquals(MachineContract.protocolVersion(), payload.path("protocolVersion").stringValue());
  }

  private JsonNode runJsonWorkflowCommand(CliBookWorkflow workflow, String... arguments)
      throws IOException {
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    FinGrindCli cli =
        cli(
            new ByteArrayInputStream(new byte[0]),
            utf8PrintStream(outputStream),
            fixedClock(),
            workflow);
    String[] jsonArguments = jsonArguments(arguments);
    int exitCode = cli.run(jsonArguments);
    assertEquals(
        0,
        exitCode,
        () ->
            "command failed: "
                + String.join(" ", jsonArguments)
                + "\n"
                + outputStream.toString(java.nio.charset.StandardCharsets.UTF_8));
    return OBJECT_MAPPER.readTree(outputStream.toByteArray());
  }

  private static RecordingWorkflow contractWorkflow() {
    return new RecordingWorkflow(
        openedBookResult(Instant.parse("2026-04-07T12:00:00Z")),
        new dev.erst.fingrind.contract.bookkeeping.RekeyBookResult.Rekeyed(
            Path.of("unused.sqlite")),
        new dev.erst.fingrind.contract.bookkeeping.DeclareAccountResult.Declared(
            declaredAccount(
                "1000",
                "Cash",
                dev.erst.fingrind.core.AccountType.ASSET,
                NormalBalance.DEBIT,
                true,
                Instant.parse("2026-04-07T12:00:00Z"))),
        new dev.erst.fingrind.contract.bookkeeping.ListAccountsResult.Listed(
            accountPage(List.of(), 50, java.util.Optional.empty())),
        CliPostEntryResultFixtures.preflightAccepted(
            new IdempotencyKey("idem-1"), LocalDate.parse("2026-04-07")),
        CliPostEntryResultFixtures.committed(
            new PostingId("posting-1"),
            new IdempotencyKey("idem-1"),
            LocalDate.parse("2026-04-07"),
            Instant.parse("2026-04-07T10:15:30Z"),
            false));
  }

  private static void assertNoArtifacts(JsonNode envelope) {
    assertTrue(envelope.path("artifacts").isMissingNode());
  }

  private static void assertSingleArtifact(
      JsonNode envelope, String expectedFormat, Path expectedPath) {
    assertArtifactList(envelope, List.of(artifactExpectation(expectedFormat, expectedPath)));
  }

  private static void assertArtifactList(
      JsonNode envelope, List<ArtifactExpectation> expectedArtifacts) {
    assertEquals(expectedArtifacts.size(), envelope.path("artifacts").size());
    for (int index = 0; index < expectedArtifacts.size(); index++) {
      ArtifactExpectation expectedArtifact = expectedArtifacts.get(index);
      JsonNode actualArtifact = envelope.path("artifacts").get(index);
      assertEquals(expectedArtifact.format(), actualArtifact.path("format").stringValue());
      assertEquals(
          CliPublicPaths.absoluteValue(expectedArtifact.path()),
          actualArtifact.path("path").stringValue());
    }
  }

  private static ArtifactExpectation artifactExpectation(String format, Path path) {
    return new ArtifactExpectation(format, path);
  }

  private static String openThenFailAssertionPlanJson() {
    return """
            {
              "planId": "plan-assertion-failure",
              "steps": [
                {
                  "stepId": "open",
                  "kind": "ensure-book",
                  "ensureBook": {
                    "entityName": "Acme Studio",
                    "bookTemplateId": "OWNER_MANAGED_SERVICE",
                    "accountingBasis": "CASH",
                    "functionalCurrency": "EUR",
                    "fiscalYearStart": "01-01"
                  }
                },
                {
                  "stepId": "declare-cash",
                  "kind": "declare-account",
                  "declareAccount": {
                    "accountCode": "1000",
                    "accountName": "Cash",
                    "accountType": "ASSET",
                    "accountNodeKind": "POSTABLE",
                    "financialPositionLineClassification": "CURRENT_ASSET",
                    "cashFlowAssetClassification": "CASH_AND_CASH_EQUIVALENT"
                  }
                },
                {
                  "stepId": "assert-missing-posting",
                  "kind": "assert",
                  "assertion": {
                    "kind": "assert-posting-exists",
                    "postingId": "posting-missing"
                  }
                }
              ]
            }
            """;
  }

  private record ArtifactExpectation(String format, Path path) {}
}
