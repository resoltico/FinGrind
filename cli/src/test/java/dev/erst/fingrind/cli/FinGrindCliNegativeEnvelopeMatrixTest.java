package dev.erst.fingrind.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.erst.fingrind.contract.protocol.OperationId;
import dev.erst.fingrind.contract.protocol.ProtocolCatalog;
import dev.erst.fingrind.core.IdempotencyKey;
import dev.erst.fingrind.core.NormalBalance;
import dev.erst.fingrind.core.PostingId;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/** Per-command negative-path matrix for the published CLI envelope contract. */
class FinGrindCliNegativeEnvelopeMatrixTest extends CliPublicDocsContractSupport {
  @Test
  void workflowCommands_publishCanonicalRejectedEnvelopesAcrossEverySingleCommandSurface()
      throws IOException {
    MatrixPaths paths = matrixPaths("rejection-matrix");

    for (RejectionSpec spec : rejectionSpecs(paths)) {
      JsonNode rejection =
          runJsonWorkflowDiagnosticsCommand(
              spec.workflow(), spec.expectedExitCode(), spec.arguments().toArray(String[]::new));
      assertRejectedDiagnosticsEnvelope(rejection, spec.expectedCode());
    }
  }

  @Test
  void workflowCommands_publishCanonicalInternalErrorEnvelopesAcrossEveryFailureSurface()
      throws IOException {
    MatrixPaths paths = matrixPaths("failure-matrix");
    CliBookWorkflow internalErrorWorkflow =
        new ExplodingWorkflow(new IllegalStateException("boom"));

    for (FailureSpec spec : failureSpecs(paths)) {
      JsonNode failure =
          runJsonWorkflowDiagnosticsCommand(
              internalErrorWorkflow, 70, spec.arguments().toArray(String[]::new));
      assertErrorDiagnosticsEnvelope(failure, "internal-error");
    }
  }

  private MatrixPaths matrixPaths(String rootName) throws IOException {
    Path root = tempDirectory.resolve(rootName);
    Path bookFilePath = root.resolve("books").resolve("entity.sqlite");
    Path bookKeyFilePath = writeBookKey(bookFilePath);
    Path replacementBookKeyFilePath = root.resolve("keys").resolve("replacement.book-key");
    Path backupBookFilePath = root.resolve("backup").resolve("entity.backup.sqlite");
    Path backupBookKeyFilePath = root.resolve("backup").resolve("entity.backup.key");
    Path rollbackArtifactPath = root.resolve("rollback").resolve("entity.rekey-rollback.sqlite");
    Path declareRequestFile =
        writeNamedRequest(rootName + "-declare.json", declareAccountJson("1000", "Cash", "DEBIT"));
    Path postingRequestFile =
        writeNamedRequest(rootName + "-post-entry.json", validRawJournalRequestJson());
    return new MatrixPaths(
        bookFilePath,
        bookKeyFilePath,
        replacementBookKeyFilePath,
        backupBookFilePath,
        backupBookKeyFilePath,
        rollbackArtifactPath,
        declareRequestFile,
        postingRequestFile);
  }

  private List<RejectionSpec> rejectionSpecs(MatrixPaths paths) {
    List<RejectionSpec> specs = new ArrayList<>();
    specs.addAll(administrativeRejectionSpecs(paths));
    specs.addAll(queryRejectionSpecs(paths));
    specs.addAll(reportRejectionSpecs(paths));
    specs.addAll(postingRejectionSpecs(paths));
    return List.copyOf(specs);
  }

  private List<RejectionSpec> administrativeRejectionSpecs(MatrixPaths paths) {
    dev.erst.fingrind.contract.bookkeeping.OpenBookResult.Rejected openBookRejected =
        new dev.erst.fingrind.contract.bookkeeping.OpenBookResult.Rejected(
            new dev.erst.fingrind.contract.bookkeeping.BookAdministrationRejection
                .BookAlreadyInitialized());
    dev.erst.fingrind.contract.bookkeeping.RekeyBookResult.Rejected rekeyRejected =
        new dev.erst.fingrind.contract.bookkeeping.RekeyBookResult.Rejected(
            new dev.erst.fingrind.contract.bookkeeping.BookAdministrationRejection
                .BookNotInitialized());
    dev.erst.fingrind.contract.bookkeeping.BackupBookResult.Rejected backupRejected =
        new dev.erst.fingrind.contract.bookkeeping.BackupBookResult.Rejected(
            new dev.erst.fingrind.contract.bookkeeping.BookMaintenanceRejection
                .BackupDestinationAlreadyExists(hint(paths.backupBookFilePath())));
    dev.erst.fingrind.contract.bookkeeping.RestoreBookResult.Rejected restoreRejected =
        new dev.erst.fingrind.contract.bookkeeping.RestoreBookResult.Rejected(
            new dev.erst.fingrind.contract.bookkeeping.BookMaintenanceRejection
                .BackupSourceHasBlockingArtifacts(
                hint(paths.backupBookFilePath()),
                List.of(
                    hint(paths.backupBookFilePath().resolveSibling("entity.backup.sqlite-wal")))));
    dev.erst.fingrind.contract.bookkeeping.RekeyRollbackResult.Rejected inspectRollbackRejected =
        new dev.erst.fingrind.contract.bookkeeping.RekeyRollbackResult.Rejected(
            new dev.erst.fingrind.contract.bookkeeping.BookMaintenanceRejection
                .NoRollbackArtifactsFound(hint(paths.bookFilePath())));
    dev.erst.fingrind.contract.bookkeeping.RekeyRollbackResult.Rejected restoreRollbackRejected =
        new dev.erst.fingrind.contract.bookkeeping.RekeyRollbackResult.Rejected(
            new dev.erst.fingrind.contract.bookkeeping.BookMaintenanceRejection
                .RollbackArtifactNotFound(hint(paths.rollbackArtifactPath())));
    dev.erst.fingrind.contract.bookkeeping.RekeyRollbackResult.Rejected deleteRollbackRejected =
        new dev.erst.fingrind.contract.bookkeeping.RekeyRollbackResult.Rejected(
            new dev.erst.fingrind.contract.bookkeeping.BookMaintenanceRejection
                .RollbackArtifactNotForBook(
                hint(paths.bookFilePath()), hint(paths.rollbackArtifactPath())));
    dev.erst.fingrind.contract.bookkeeping.DeclareAccountResult.Rejected declareRejected =
        new dev.erst.fingrind.contract.bookkeeping.DeclareAccountResult.Rejected(
            new dev.erst.fingrind.contract.bookkeeping.BookAdministrationRejection
                .BookNotInitialized());
    dev.erst.fingrind.contract.bookkeeping.InterimResultSweepResult.Rejected transferRejected =
        new dev.erst.fingrind.contract.bookkeeping.InterimResultSweepResult.Rejected(
            new dev.erst.fingrind.contract.bookkeeping.BookAdministrationRejection
                .BookNotInitialized());

    return List.of(
        rejectionSpec(
            CliAdministrativeExitCodes.exitCodeFor(openBookRejected),
            dev.erst.fingrind.contract.bookkeeping.BookAdministrationRejection.wireCode(
                openBookRejected.rejection()),
            contractWorkflow(
                openBookRejected,
                contractRekeyBookResult(),
                contractDeclareAccountResult(),
                contractListAccountsResult(),
                contractPreflightResult(),
                contractCommitResult()),
            openBookKeyFileArguments(paths.bookFilePath(), paths.bookKeyFilePath())),
        rejectionSpec(
            CliAdministrativeExitCodes.exitCodeFor(rekeyRejected),
            dev.erst.fingrind.contract.bookkeeping.BookAdministrationRejection.wireCode(
                rekeyRejected.rejection()),
            contractWorkflow(
                contractOpenBookResult(),
                rekeyRejected,
                contractDeclareAccountResult(),
                contractListAccountsResult(),
                contractPreflightResult(),
                contractCommitResult()),
            cmd(OperationId.REKEY_BOOK),
            "--book-file",
            paths.bookFilePath().toString(),
            "--book-key-file",
            paths.bookKeyFilePath().toString(),
            "--new-book-key-file",
            paths.replacementBookKeyFilePath().toString()),
        rejectionSpec(
            CliAdministrativeExitCodes.exitCodeFor(backupRejected),
            dev.erst.fingrind.contract.bookkeeping.BookMaintenanceRejection.wireCode(
                backupRejected.rejection()),
            workflowWithBackupResult(backupRejected),
            cmd(OperationId.BACKUP_BOOK),
            "--book-file",
            paths.bookFilePath().toString(),
            "--book-key-file",
            paths.bookKeyFilePath().toString(),
            "--backup-file",
            paths.backupBookFilePath().toString(),
            "--backup-key-file",
            paths.backupBookKeyFilePath().toString()),
        rejectionSpec(
            CliAdministrativeExitCodes.exitCodeFor(restoreRejected),
            dev.erst.fingrind.contract.bookkeeping.BookMaintenanceRejection.wireCode(
                restoreRejected.rejection()),
            workflowWithRestoreResult(restoreRejected),
            cmd(OperationId.RESTORE_BOOK),
            "--book-file",
            paths.bookFilePath().toString(),
            "--book-key-file",
            paths.bookKeyFilePath().toString(),
            "--backup-file",
            paths.backupBookFilePath().toString(),
            "--backup-key-file",
            paths.backupBookKeyFilePath().toString()),
        rejectionSpec(
            CliAdministrativeExitCodes.exitCodeFor(inspectRollbackRejected),
            dev.erst.fingrind.contract.bookkeeping.BookMaintenanceRejection.wireCode(
                inspectRollbackRejected.rejection()),
            workflowWithRollbackResult(inspectRollbackRejected),
            cmd(OperationId.INSPECT_REKEY_ROLLBACK),
            "--book-file",
            paths.bookFilePath().toString()),
        rejectionSpec(
            CliAdministrativeExitCodes.exitCodeFor(restoreRollbackRejected),
            dev.erst.fingrind.contract.bookkeeping.BookMaintenanceRejection.wireCode(
                restoreRollbackRejected.rejection()),
            workflowWithRollbackResult(restoreRollbackRejected),
            cmd(OperationId.RESTORE_REKEY_ROLLBACK),
            "--book-file",
            paths.bookFilePath().toString(),
            "--book-key-file",
            paths.bookKeyFilePath().toString(),
            "--rollback-book-file",
            paths.rollbackArtifactPath().toString()),
        rejectionSpec(
            CliAdministrativeExitCodes.exitCodeFor(deleteRollbackRejected),
            dev.erst.fingrind.contract.bookkeeping.BookMaintenanceRejection.wireCode(
                deleteRollbackRejected.rejection()),
            workflowWithRollbackResult(deleteRollbackRejected),
            cmd(OperationId.DELETE_REKEY_ROLLBACK),
            "--book-file",
            paths.bookFilePath().toString(),
            "--book-key-file",
            paths.bookKeyFilePath().toString(),
            "--rollback-book-file",
            paths.rollbackArtifactPath().toString()),
        rejectionSpec(
            CliAdministrativeExitCodes.exitCodeFor(declareRejected),
            dev.erst.fingrind.contract.bookkeeping.BookAdministrationRejection.wireCode(
                declareRejected.rejection()),
            contractWorkflow(
                contractOpenBookResult(),
                contractRekeyBookResult(),
                declareRejected,
                contractListAccountsResult(),
                contractPreflightResult(),
                contractCommitResult()),
            cmd(OperationId.DECLARE_ACCOUNT),
            "--book-file",
            paths.bookFilePath().toString(),
            "--book-key-file",
            paths.bookKeyFilePath().toString(),
            "--request-file",
            paths.declareRequestFile().toString()),
        rejectionSpec(
            CliAdministrativeExitCodes.exitCodeFor(transferRejected),
            dev.erst.fingrind.contract.bookkeeping.BookAdministrationRejection.wireCode(
                transferRejected.rejection()),
            workflowWithTransferResult(transferRejected),
            cmd(OperationId.INTERIM_RESULT_SWEEP),
            "--book-file",
            paths.bookFilePath().toString(),
            "--book-key-file",
            paths.bookKeyFilePath().toString(),
            "--through",
            "2026-04-07"));
  }

  private List<RejectionSpec> queryRejectionSpecs(MatrixPaths paths) {
    dev.erst.fingrind.contract.bookkeeping.ListAccountsResult.Rejected listAccountsRejected =
        new dev.erst.fingrind.contract.bookkeeping.ListAccountsResult.Rejected(
            new dev.erst.fingrind.contract.bookkeeping.BookQueryRejection.BookNotInitialized());
    dev.erst.fingrind.contract.bookkeeping.GetPostingResult.Rejected getPostingRejected =
        new dev.erst.fingrind.contract.bookkeeping.GetPostingResult.Rejected(
            new dev.erst.fingrind.contract.bookkeeping.BookQueryRejection.PostingNotFound(
                new PostingId("posting-missing")));
    dev.erst.fingrind.contract.bookkeeping.ListPostingsResult.Rejected listPostingsRejected =
        new dev.erst.fingrind.contract.bookkeeping.ListPostingsResult.Rejected(
            new dev.erst.fingrind.contract.bookkeeping.BookQueryRejection.BookNotInitialized());

    return List.of(
        rejectionSpec(
            CliBookQueryExitCodes.exitCodeFor(listAccountsRejected),
            dev.erst.fingrind.contract.bookkeeping.BookQueryRejection.wireCode(
                listAccountsRejected.rejection()),
            contractWorkflow(
                contractOpenBookResult(),
                contractRekeyBookResult(),
                contractDeclareAccountResult(),
                listAccountsRejected,
                contractPreflightResult(),
                contractCommitResult()),
            cmd(OperationId.LIST_ACCOUNTS),
            "--book-file",
            paths.bookFilePath().toString(),
            "--book-key-file",
            paths.bookKeyFilePath().toString()),
        rejectionSpec(
            CliBookQueryExitCodes.exitCodeFor(getPostingRejected),
            dev.erst.fingrind.contract.bookkeeping.BookQueryRejection.wireCode(
                getPostingRejected.rejection()),
            workflowWithGetPostingResult(getPostingRejected),
            cmd(OperationId.GET_POSTING),
            "--book-file",
            paths.bookFilePath().toString(),
            "--book-key-file",
            paths.bookKeyFilePath().toString(),
            "--posting-id",
            "posting-missing"),
        rejectionSpec(
            CliBookQueryExitCodes.exitCodeFor(listPostingsRejected),
            dev.erst.fingrind.contract.bookkeeping.BookQueryRejection.wireCode(
                listPostingsRejected.rejection()),
            workflowWithListPostingsResult(listPostingsRejected),
            cmd(OperationId.LIST_POSTINGS),
            "--book-file",
            paths.bookFilePath().toString(),
            "--book-key-file",
            paths.bookKeyFilePath().toString()));
  }

  private List<RejectionSpec> reportRejectionSpecs(MatrixPaths paths) {
    dev.erst.fingrind.contract.bookkeeping.AccountBalanceResult.Rejected accountBalanceRejected =
        new dev.erst.fingrind.contract.bookkeeping.AccountBalanceResult.Rejected(
            new dev.erst.fingrind.contract.bookkeeping.BookQueryRejection.BookNotInitialized());
    dev.erst.fingrind.contract.bookkeeping.TrialBalanceResult.Rejected trialBalanceRejected =
        new dev.erst.fingrind.contract.bookkeeping.TrialBalanceResult.Rejected(
            new dev.erst.fingrind.contract.bookkeeping.BookQueryRejection.BookNotInitialized());
    dev.erst.fingrind.contract.bookkeeping.AccountLedgerResult.Rejected accountLedgerRejected =
        new dev.erst.fingrind.contract.bookkeeping.AccountLedgerResult.Rejected(
            new dev.erst.fingrind.contract.bookkeeping.BookQueryRejection.BookNotInitialized());
    dev.erst.fingrind.contract.bookkeeping.PeriodSummaryResult.Rejected periodSummaryRejected =
        new dev.erst.fingrind.contract.bookkeeping.PeriodSummaryResult.Rejected(
            new dev.erst.fingrind.contract.bookkeeping.BookQueryRejection.BookNotInitialized());
    dev.erst.fingrind.contract.bookkeeping.FinancialPositionResult.Rejected
        financialPositionRejected =
            new dev.erst.fingrind.contract.bookkeeping.FinancialPositionResult.Rejected(
                new dev.erst.fingrind.contract.bookkeeping.BookQueryRejection.BookNotInitialized());
    dev.erst.fingrind.contract.bookkeeping.IncomeStatementResult.Rejected incomeStatementRejected =
        new dev.erst.fingrind.contract.bookkeeping.IncomeStatementResult.Rejected(
            new dev.erst.fingrind.contract.bookkeeping.BookQueryRejection.BookNotInitialized());
    dev.erst.fingrind.contract.bookkeeping.ChangesInEquityResult.Rejected changesInEquityRejected =
        new dev.erst.fingrind.contract.bookkeeping.ChangesInEquityResult.Rejected(
            new dev.erst.fingrind.contract.bookkeeping.BookQueryRejection.BookNotInitialized());

    CliBookWorkflow rejectedReportsWorkflow =
        reportingWorkflow(
            accountBalanceRejected,
            rejectedTaxObligationResult(),
            trialBalanceRejected,
            accountLedgerRejected,
            periodSummaryRejected,
            financialPositionRejected,
            incomeStatementRejected,
            changesInEquityRejected);

    return List.of(
        rejectionSpec(
            CliReportExitCodes.exitCodeFor(accountBalanceRejected),
            dev.erst.fingrind.contract.bookkeeping.BookQueryRejection.wireCode(
                accountBalanceRejected.rejection()),
            rejectedReportsWorkflow,
            cmd(OperationId.ACCOUNT_BALANCE),
            "--book-file",
            paths.bookFilePath().toString(),
            "--book-key-file",
            paths.bookKeyFilePath().toString(),
            "--account-code",
            "1000"),
        rejectionSpec(
            CliReportExitCodes.exitCodeFor(trialBalanceRejected),
            dev.erst.fingrind.contract.bookkeeping.BookQueryRejection.wireCode(
                trialBalanceRejected.rejection()),
            rejectedReportsWorkflow,
            cmd(OperationId.TRIAL_BALANCE),
            "--book-file",
            paths.bookFilePath().toString(),
            "--book-key-file",
            paths.bookKeyFilePath().toString()),
        rejectionSpec(
            CliReportExitCodes.exitCodeFor(accountLedgerRejected),
            dev.erst.fingrind.contract.bookkeeping.BookQueryRejection.wireCode(
                accountLedgerRejected.rejection()),
            rejectedReportsWorkflow,
            cmd(OperationId.ACCOUNT_LEDGER),
            "--book-file",
            paths.bookFilePath().toString(),
            "--book-key-file",
            paths.bookKeyFilePath().toString(),
            "--account-code",
            "1000",
            "--effective-date-from",
            "2026-04-01",
            "--effective-date-to",
            "2026-04-30"),
        rejectionSpec(
            CliReportExitCodes.exitCodeFor(periodSummaryRejected),
            dev.erst.fingrind.contract.bookkeeping.BookQueryRejection.wireCode(
                periodSummaryRejected.rejection()),
            rejectedReportsWorkflow,
            cmd(OperationId.PERIOD_SUMMARY),
            "--book-file",
            paths.bookFilePath().toString(),
            "--book-key-file",
            paths.bookKeyFilePath().toString(),
            "--period-start",
            "2026-04-01",
            "--period-end",
            "2026-04-30"),
        rejectionSpec(
            CliReportExitCodes.exitCodeFor(financialPositionRejected),
            dev.erst.fingrind.contract.bookkeeping.BookQueryRejection.wireCode(
                financialPositionRejected.rejection()),
            rejectedReportsWorkflow,
            cmd(OperationId.FINANCIAL_POSITION),
            "--book-file",
            paths.bookFilePath().toString(),
            "--book-key-file",
            paths.bookKeyFilePath().toString(),
            "--effective-date-as-of",
            "2026-04-07"),
        rejectionSpec(
            CliReportExitCodes.exitCodeFor(incomeStatementRejected),
            dev.erst.fingrind.contract.bookkeeping.BookQueryRejection.wireCode(
                incomeStatementRejected.rejection()),
            rejectedReportsWorkflow,
            cmd(OperationId.INCOME_STATEMENT),
            "--book-file",
            paths.bookFilePath().toString(),
            "--book-key-file",
            paths.bookKeyFilePath().toString(),
            "--period-start",
            "2026-04-01",
            "--period-end",
            "2026-04-30"),
        rejectionSpec(
            CliReportExitCodes.exitCodeFor(changesInEquityRejected),
            dev.erst.fingrind.contract.bookkeeping.BookQueryRejection.wireCode(
                changesInEquityRejected.rejection()),
            rejectedReportsWorkflow,
            cmd(OperationId.CHANGES_IN_EQUITY),
            "--book-file",
            paths.bookFilePath().toString(),
            "--book-key-file",
            paths.bookKeyFilePath().toString(),
            "--period-start",
            "2026-04-01",
            "--period-end",
            "2026-04-30"));
  }

  private List<RejectionSpec> postingRejectionSpecs(MatrixPaths paths) {
    dev.erst.fingrind.contract.bookkeeping.PostEntryResult.PreflightRejected preflightRejected =
        new dev.erst.fingrind.contract.bookkeeping.PostEntryResult.PreflightRejected(
            new IdempotencyKey("idem-1"),
            new dev.erst.fingrind.contract.bookkeeping.PostingRejection.BookNotInitialized());
    dev.erst.fingrind.contract.bookkeeping.PostEntryResult.CommitRejected commitRejected =
        new dev.erst.fingrind.contract.bookkeeping.PostEntryResult.CommitRejected(
            new IdempotencyKey("idem-1"),
            new dev.erst.fingrind.contract.bookkeeping.PostingRejection.BookNotInitialized());

    return List.of(
        rejectionSpec(
            CliPostingExitCodes.exitCodeFor(preflightRejected),
            dev.erst.fingrind.contract.bookkeeping.PostingRejection.wireCode(
                preflightRejected.rejection()),
            contractWorkflow(
                contractOpenBookResult(),
                contractRekeyBookResult(),
                contractDeclareAccountResult(),
                contractListAccountsResult(),
                preflightRejected,
                contractCommitResult()),
            cmd(OperationId.PREFLIGHT_ENTRY),
            "--book-file",
            paths.bookFilePath().toString(),
            "--book-key-file",
            paths.bookKeyFilePath().toString(),
            "--request-file",
            paths.postingRequestFile().toString()),
        rejectionSpec(
            CliPostingExitCodes.exitCodeFor(commitRejected),
            dev.erst.fingrind.contract.bookkeeping.PostingRejection.wireCode(
                commitRejected.rejection()),
            contractWorkflow(
                contractOpenBookResult(),
                contractRekeyBookResult(),
                contractDeclareAccountResult(),
                contractListAccountsResult(),
                contractPreflightResult(),
                commitRejected),
            cmd(OperationId.POST_ENTRY),
            "--book-file",
            paths.bookFilePath().toString(),
            "--book-key-file",
            paths.bookKeyFilePath().toString(),
            "--request-file",
            paths.postingRequestFile().toString()));
  }

  private List<FailureSpec> failureSpecs(MatrixPaths paths) {
    List<FailureSpec> specs = new ArrayList<>();
    specs.addAll(administrativeFailureSpecs(paths));
    specs.addAll(readFailureSpecs(paths));
    specs.addAll(reportFailureSpecs(paths));
    specs.addAll(postingFailureSpecs(paths));
    return List.copyOf(specs);
  }

  private List<FailureSpec> administrativeFailureSpecs(MatrixPaths paths) {
    return List.of(
        failureSpec(
            cmd(OperationId.OPEN_BOOK),
            openBookKeyFileArguments(paths.bookFilePath(), paths.bookKeyFilePath())),
        failureSpec(
            cmd(OperationId.REKEY_BOOK),
            cmd(OperationId.REKEY_BOOK),
            "--book-file",
            paths.bookFilePath().toString(),
            "--book-key-file",
            paths.bookKeyFilePath().toString(),
            "--new-book-key-file",
            paths.replacementBookKeyFilePath().toString()),
        failureSpec(
            cmd(OperationId.BACKUP_BOOK),
            cmd(OperationId.BACKUP_BOOK),
            "--book-file",
            paths.bookFilePath().toString(),
            "--book-key-file",
            paths.bookKeyFilePath().toString(),
            "--backup-file",
            paths.backupBookFilePath().toString(),
            "--backup-key-file",
            paths.backupBookKeyFilePath().toString()),
        failureSpec(
            cmd(OperationId.RESTORE_BOOK),
            cmd(OperationId.RESTORE_BOOK),
            "--book-file",
            paths.bookFilePath().toString(),
            "--book-key-file",
            paths.bookKeyFilePath().toString(),
            "--backup-file",
            paths.backupBookFilePath().toString(),
            "--backup-key-file",
            paths.backupBookKeyFilePath().toString()),
        failureSpec(
            cmd(OperationId.INSPECT_REKEY_ROLLBACK),
            cmd(OperationId.INSPECT_REKEY_ROLLBACK),
            "--book-file",
            paths.bookFilePath().toString()),
        failureSpec(
            cmd(OperationId.RESTORE_REKEY_ROLLBACK),
            cmd(OperationId.RESTORE_REKEY_ROLLBACK),
            "--book-file",
            paths.bookFilePath().toString(),
            "--book-key-file",
            paths.bookKeyFilePath().toString(),
            "--rollback-book-file",
            paths.rollbackArtifactPath().toString()),
        failureSpec(
            cmd(OperationId.DELETE_REKEY_ROLLBACK),
            cmd(OperationId.DELETE_REKEY_ROLLBACK),
            "--book-file",
            paths.bookFilePath().toString(),
            "--book-key-file",
            paths.bookKeyFilePath().toString(),
            "--rollback-book-file",
            paths.rollbackArtifactPath().toString()),
        failureSpec(
            cmd(OperationId.DECLARE_ACCOUNT),
            cmd(OperationId.DECLARE_ACCOUNT),
            "--book-file",
            paths.bookFilePath().toString(),
            "--book-key-file",
            paths.bookKeyFilePath().toString(),
            "--request-file",
            paths.declareRequestFile().toString()),
        failureSpec(
            cmd(OperationId.INTERIM_RESULT_SWEEP),
            cmd(OperationId.INTERIM_RESULT_SWEEP),
            "--book-file",
            paths.bookFilePath().toString(),
            "--book-key-file",
            paths.bookKeyFilePath().toString(),
            "--through",
            "2026-04-07"));
  }

  private List<FailureSpec> readFailureSpecs(MatrixPaths paths) {
    return List.of(
        failureSpec(
            cmd(OperationId.INSPECT_BOOK),
            cmd(OperationId.INSPECT_BOOK),
            "--book-file",
            paths.bookFilePath().toString(),
            "--book-key-file",
            paths.bookKeyFilePath().toString()),
        failureSpec(
            cmd(OperationId.LIST_ACCOUNTS),
            cmd(OperationId.LIST_ACCOUNTS),
            "--book-file",
            paths.bookFilePath().toString(),
            "--book-key-file",
            paths.bookKeyFilePath().toString()),
        failureSpec(
            cmd(OperationId.GET_POSTING),
            cmd(OperationId.GET_POSTING),
            "--book-file",
            paths.bookFilePath().toString(),
            "--book-key-file",
            paths.bookKeyFilePath().toString(),
            "--posting-id",
            "posting-1"),
        failureSpec(
            cmd(OperationId.LIST_POSTINGS),
            cmd(OperationId.LIST_POSTINGS),
            "--book-file",
            paths.bookFilePath().toString(),
            "--book-key-file",
            paths.bookKeyFilePath().toString()));
  }

  private List<FailureSpec> reportFailureSpecs(MatrixPaths paths) {
    return List.of(
        failureSpec(
            cmd(OperationId.ACCOUNT_BALANCE),
            cmd(OperationId.ACCOUNT_BALANCE),
            "--book-file",
            paths.bookFilePath().toString(),
            "--book-key-file",
            paths.bookKeyFilePath().toString(),
            "--account-code",
            "1000"),
        failureSpec(
            cmd(OperationId.TRIAL_BALANCE),
            cmd(OperationId.TRIAL_BALANCE),
            "--book-file",
            paths.bookFilePath().toString(),
            "--book-key-file",
            paths.bookKeyFilePath().toString()),
        failureSpec(
            cmd(OperationId.ACCOUNT_LEDGER),
            cmd(OperationId.ACCOUNT_LEDGER),
            "--book-file",
            paths.bookFilePath().toString(),
            "--book-key-file",
            paths.bookKeyFilePath().toString(),
            "--account-code",
            "1000",
            "--effective-date-from",
            "2026-04-01",
            "--effective-date-to",
            "2026-04-30"),
        failureSpec(
            cmd(OperationId.PERIOD_SUMMARY),
            cmd(OperationId.PERIOD_SUMMARY),
            "--book-file",
            paths.bookFilePath().toString(),
            "--book-key-file",
            paths.bookKeyFilePath().toString(),
            "--period-start",
            "2026-04-01",
            "--period-end",
            "2026-04-30"),
        failureSpec(
            cmd(OperationId.FINANCIAL_POSITION),
            cmd(OperationId.FINANCIAL_POSITION),
            "--book-file",
            paths.bookFilePath().toString(),
            "--book-key-file",
            paths.bookKeyFilePath().toString(),
            "--effective-date-as-of",
            "2026-04-07"),
        failureSpec(
            cmd(OperationId.INCOME_STATEMENT),
            cmd(OperationId.INCOME_STATEMENT),
            "--book-file",
            paths.bookFilePath().toString(),
            "--book-key-file",
            paths.bookKeyFilePath().toString(),
            "--period-start",
            "2026-04-01",
            "--period-end",
            "2026-04-30"),
        failureSpec(
            cmd(OperationId.CHANGES_IN_EQUITY),
            cmd(OperationId.CHANGES_IN_EQUITY),
            "--book-file",
            paths.bookFilePath().toString(),
            "--book-key-file",
            paths.bookKeyFilePath().toString(),
            "--period-start",
            "2026-04-01",
            "--period-end",
            "2026-04-30"));
  }

  private List<FailureSpec> postingFailureSpecs(MatrixPaths paths) {
    return List.of(
        failureSpec(
            cmd(OperationId.PREFLIGHT_ENTRY),
            cmd(OperationId.PREFLIGHT_ENTRY),
            "--book-file",
            paths.bookFilePath().toString(),
            "--book-key-file",
            paths.bookKeyFilePath().toString(),
            "--request-file",
            paths.postingRequestFile().toString()),
        failureSpec(
            cmd(OperationId.POST_ENTRY),
            cmd(OperationId.POST_ENTRY),
            "--book-file",
            paths.bookFilePath().toString(),
            "--book-key-file",
            paths.bookKeyFilePath().toString(),
            "--request-file",
            paths.postingRequestFile().toString()));
  }

  private static String cmd(OperationId operationId) {
    return ProtocolCatalog.operationName(operationId);
  }

  private static void assertRejectedEnvelope(JsonNode envelope) {
    assertEquals("rejected", envelope.path("status").stringValue());
    assertTrue(envelope.path("code").isTextual());
    assertTrue(envelope.path("message").isTextual());
    assertTrue(envelope.path("artifacts").isMissingNode());
  }

  private static void assertRejectedDiagnosticsEnvelope(JsonNode envelope, String expectedCode) {
    assertRejectedEnvelope(envelope);
    assertEquals(expectedCode, envelope.path("code").stringValue());
    assertTrue(envelope.path("payload").isMissingNode());
  }

  private static void assertErrorDiagnosticsEnvelope(JsonNode envelope, String expectedCode) {
    assertEquals("error", envelope.path("status").stringValue());
    assertEquals(expectedCode, envelope.path("code").stringValue());
    assertTrue(envelope.path("message").isTextual());
    assertTrue(envelope.path("artifacts").isMissingNode());
    assertTrue(envelope.path("payload").isMissingNode());
  }

  private JsonNode runJsonWorkflowDiagnosticsCommand(
      CliBookWorkflow workflow, int expectedExitCode, String... arguments) throws IOException {
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    ByteArrayOutputStream diagnosticsStream = new ByteArrayOutputStream();
    FinGrindCli cli =
        cli(
            new ByteArrayInputStream(new byte[0]),
            utf8PrintStream(outputStream),
            utf8PrintStream(diagnosticsStream),
            fixedClock(),
            workflow);
    String[] jsonArguments = jsonArguments(arguments);
    int exitCode = cli.run(jsonArguments);
    assertEquals(expectedExitCode, exitCode);
    assertEquals("", outputStream.toString(java.nio.charset.StandardCharsets.UTF_8));
    return OBJECT_MAPPER.readTree(diagnosticsStream.toByteArray());
  }

  private static RecordingWorkflow contractWorkflow() {
    return contractWorkflow(
        contractOpenBookResult(),
        contractRekeyBookResult(),
        contractDeclareAccountResult(),
        contractListAccountsResult(),
        contractPreflightResult(),
        contractCommitResult());
  }

  private static RecordingWorkflow contractWorkflow(
      dev.erst.fingrind.contract.bookkeeping.OpenBookResult openBookResult,
      dev.erst.fingrind.contract.bookkeeping.RekeyBookResult rekeyBookResult,
      dev.erst.fingrind.contract.bookkeeping.DeclareAccountResult declareAccountResult,
      dev.erst.fingrind.contract.bookkeeping.ListAccountsResult listAccountsResult,
      dev.erst.fingrind.contract.bookkeeping.PreflightEntryResult preflightResult,
      dev.erst.fingrind.contract.bookkeeping.CommitEntryResult commitResult) {
    return new RecordingWorkflow(
        openBookResult,
        rekeyBookResult,
        declareAccountResult,
        listAccountsResult,
        preflightResult,
        commitResult);
  }

  private static dev.erst.fingrind.contract.bookkeeping.OpenBookResult contractOpenBookResult() {
    return openedBookResult(Instant.parse("2026-04-07T12:00:00Z"));
  }

  private static dev.erst.fingrind.contract.bookkeeping.RekeyBookResult contractRekeyBookResult() {
    return new dev.erst.fingrind.contract.bookkeeping.RekeyBookResult.Rekeyed(
        Path.of("unused.sqlite"));
  }

  private static dev.erst.fingrind.contract.bookkeeping.DeclareAccountResult
      contractDeclareAccountResult() {
    return new dev.erst.fingrind.contract.bookkeeping.DeclareAccountResult.Declared(
        declaredAccount(
            "1000",
            "Cash",
            dev.erst.fingrind.core.AccountType.ASSET,
            NormalBalance.DEBIT,
            true,
            Instant.parse("2026-04-07T12:00:00Z")));
  }

  private static dev.erst.fingrind.contract.bookkeeping.ListAccountsResult
      contractListAccountsResult() {
    return new dev.erst.fingrind.contract.bookkeeping.ListAccountsResult.Listed(
        accountPage(List.of(), 50, java.util.Optional.empty()));
  }

  private static dev.erst.fingrind.contract.bookkeeping.PreflightEntryResult
      contractPreflightResult() {
    return CliPostEntryResultFixtures.preflightAccepted(
        new IdempotencyKey("idem-1"), LocalDate.parse("2026-04-07"));
  }

  private static dev.erst.fingrind.contract.bookkeeping.CommitEntryResult contractCommitResult() {
    return CliPostEntryResultFixtures.committed(
        new PostingId("posting-1"),
        new IdempotencyKey("idem-1"),
        LocalDate.parse("2026-04-07"),
        Instant.parse("2026-04-07T10:15:30Z"),
        false);
  }

  private static CliBookWorkflow workflowWithBackupResult(
      dev.erst.fingrind.contract.bookkeeping.BackupBookResult backupBookResult) {
    RecordingWorkflow workflow = contractWorkflow();
    workflow.setBackupBookResult(backupBookResult);
    return workflow;
  }

  private static CliBookWorkflow workflowWithRestoreResult(
      dev.erst.fingrind.contract.bookkeeping.RestoreBookResult restoreBookResult) {
    RecordingWorkflow workflow = contractWorkflow();
    workflow.setRestoreBookResult(restoreBookResult);
    return workflow;
  }

  private static CliBookWorkflow workflowWithRollbackResult(
      dev.erst.fingrind.contract.bookkeeping.RekeyRollbackResult rollbackResult) {
    RecordingWorkflow workflow = contractWorkflow();
    workflow.setRekeyRollbackResult(rollbackResult);
    return workflow;
  }

  private static CliBookWorkflow workflowWithTransferResult(
      dev.erst.fingrind.contract.bookkeeping.InterimResultSweepResult transferResult) {
    return new CliBookWorkflowAdapter() {
      @Override
      public dev.erst.fingrind.contract.runtime.ContractDecision<
              dev.erst.fingrind.contract.bookkeeping.InterimResultSweepResult>
          interimResultSweep(
              dev.erst.fingrind.contract.runtime.BookAccess bookAccess,
              dev.erst.fingrind.contract.bookkeeping.InterimResultSweepCommand command) {
        return accepted(transferResult);
      }
    };
  }

  private static CliBookWorkflow workflowWithGetPostingResult(
      dev.erst.fingrind.contract.bookkeeping.GetPostingResult getPostingResult) {
    return new CliBookWorkflowAdapter() {
      @Override
      public dev.erst.fingrind.contract.runtime.ContractDecision<
              dev.erst.fingrind.contract.bookkeeping.GetPostingResult>
          getPosting(
              dev.erst.fingrind.contract.runtime.BookAccess bookAccess, PostingId postingId) {
        return accepted(getPostingResult);
      }
    };
  }

  private static CliBookWorkflow workflowWithListPostingsResult(
      dev.erst.fingrind.contract.bookkeeping.ListPostingsResult listPostingsResult) {
    return new CliBookWorkflowAdapter() {
      @Override
      public dev.erst.fingrind.contract.runtime.ContractDecision<
              dev.erst.fingrind.contract.bookkeeping.ListPostingsResult>
          listPostings(
              dev.erst.fingrind.contract.runtime.BookAccess bookAccess,
              dev.erst.fingrind.contract.bookkeeping.ListPostingsQuery query) {
        return accepted(listPostingsResult);
      }
    };
  }

  private static RejectionSpec rejectionSpec(
      int expectedExitCode, String expectedCode, CliBookWorkflow workflow, String... arguments) {
    return new RejectionSpec(expectedExitCode, expectedCode, workflow, List.of(arguments));
  }

  private static FailureSpec failureSpec(String operationName, String... arguments) {
    return new FailureSpec(operationName, List.of(arguments));
  }

  private record MatrixPaths(
      Path bookFilePath,
      Path bookKeyFilePath,
      Path replacementBookKeyFilePath,
      Path backupBookFilePath,
      Path backupBookKeyFilePath,
      Path rollbackArtifactPath,
      Path declareRequestFile,
      Path postingRequestFile) {}

  private record RejectionSpec(
      int expectedExitCode,
      String expectedCode,
      CliBookWorkflow workflow,
      List<String> arguments) {}

  private record FailureSpec(String operationName, List<String> arguments) {}
}
