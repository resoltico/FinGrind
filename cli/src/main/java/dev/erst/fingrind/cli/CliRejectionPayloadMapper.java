package dev.erst.fingrind.cli;

import dev.erst.fingrind.cli.json.CliEnvelopeJsonModels;
import dev.erst.fingrind.cli.json.CliRejectionJsonModels;
import dev.erst.fingrind.contract.bookkeeping.BookAdministrationRejection;
import dev.erst.fingrind.contract.bookkeeping.BookMaintenanceRejection;
import dev.erst.fingrind.contract.bookkeeping.BookQueryRejection;
import dev.erst.fingrind.contract.bookkeeping.PostingRejection;
import dev.erst.fingrind.contract.bookkeeping.RejectionNarrative;
import dev.erst.fingrind.contract.protocol.OperationId;
import dev.erst.fingrind.contract.protocol.ProtocolCatalog;
import dev.erst.fingrind.contract.protocol.ProtocolRejectionStatus;
import dev.erst.fingrind.core.AccountTaxonomy;

/** Maps deterministic rejection families into the CLI JSON envelope model. */
final class CliRejectionPayloadMapper {
  private static final String OPEN_BOOK_OPERATION =
      ProtocolCatalog.operationName(OperationId.OPEN_BOOK);
  private static final String INSPECT_BOOK_OPERATION =
      ProtocolCatalog.operationName(OperationId.INSPECT_BOOK);
  private static final String LIST_ACCOUNTS_OPERATION =
      ProtocolCatalog.operationName(OperationId.LIST_ACCOUNTS);
  private static final String GET_POSTING_OPERATION =
      ProtocolCatalog.operationName(OperationId.GET_POSTING);
  private static final String LIST_POSTINGS_OPERATION =
      ProtocolCatalog.operationName(OperationId.LIST_POSTINGS);
  private static final String BACKUP_BOOK_OPERATION =
      ProtocolCatalog.operationName(OperationId.BACKUP_BOOK);
  private static final String RESTORE_BOOK_OPERATION =
      ProtocolCatalog.operationName(OperationId.RESTORE_BOOK);
  private static final String TRANSFER_PERIOD_RESULT_OPERATION =
      ProtocolCatalog.operationName(OperationId.TRANSFER_PERIOD_RESULT);
  private static final String INSPECT_REKEY_ROLLBACK_OPERATION =
      ProtocolCatalog.operationName(OperationId.INSPECT_REKEY_ROLLBACK);
  private static final String DELETE_REKEY_ROLLBACK_OPERATION =
      ProtocolCatalog.operationName(OperationId.DELETE_REKEY_ROLLBACK);
  private static final String RESTORE_REKEY_ROLLBACK_OPERATION =
      ProtocolCatalog.operationName(OperationId.RESTORE_REKEY_ROLLBACK);

  private CliRejectionPayloadMapper() {}

  static CliEnvelopeJsonModels.RejectedEnvelope postingRejectedEnvelope(
      String requestIdempotencyKey, PostingRejection rejection) {
    return new CliEnvelopeJsonModels.RejectedEnvelope(
        ProtocolRejectionStatus.REJECTED,
        PostingRejection.wireCode(rejection),
        RejectionNarrative.message(rejection),
        postingRejectionHint(rejection),
        requestIdempotencyKey,
        postingRejectionDetails(rejection));
  }

  static CliEnvelopeJsonModels.RejectedEnvelope administrationRejectedEnvelope(
      BookAdministrationRejection rejection) {
    return new CliEnvelopeJsonModels.RejectedEnvelope(
        ProtocolRejectionStatus.REJECTED,
        BookAdministrationRejection.wireCode(rejection),
        RejectionNarrative.message(rejection),
        administrationRejectionHint(rejection),
        null,
        administrationRejectionDetails(rejection));
  }

  static CliEnvelopeJsonModels.RejectedEnvelope maintenanceRejectedEnvelope(
      BookMaintenanceRejection rejection) {
    return new CliEnvelopeJsonModels.RejectedEnvelope(
        ProtocolRejectionStatus.REJECTED,
        BookMaintenanceRejection.wireCode(rejection),
        RejectionNarrative.message(rejection),
        maintenanceRejectionHint(rejection),
        null,
        maintenanceRejectionDetails(rejection));
  }

  static CliEnvelopeJsonModels.RejectedEnvelope queryRejectedEnvelope(
      BookQueryRejection rejection) {
    return new CliEnvelopeJsonModels.RejectedEnvelope(
        ProtocolRejectionStatus.REJECTED,
        BookQueryRejection.wireCode(rejection),
        RejectionNarrative.message(rejection),
        queryRejectionHint(rejection),
        null,
        queryRejectionDetails(rejection));
  }

  private static String postingRejectionHint(PostingRejection rejection) {
    return switch (rejection) {
      case PostingRejection.BookNotInitialized _ ->
          "Run "
              + OPEN_BOOK_OPERATION
              + " first for a new book, or verify the selected --book-file and book passphrase source for an existing book.";
      case PostingRejection.AccountStateViolations _ ->
          "Declare or reactivate every account named in details.violations, then rerun the request with a fresh provenance.idempotencyKey.";
      case PostingRejection.EntrySemanticsViolations _ ->
          "Choose accounts and source-document types that match the selected entry kind, then rerun the request with a fresh provenance.idempotencyKey.";
      case PostingRejection.DuplicateIdempotencyKey _ ->
          "Inspect the already-committed posting for this idempotency key instead of retrying the same key, or submit a new posting with a fresh provenance.idempotencyKey.";
      case PostingRejection.BookFunctionalCurrencyMismatch _ ->
          "Use the selected book's functional currency for every journal line in this request, or open a separate book for another currency.";
      case PostingRejection.TransferredPeriodResultViolation _ ->
          "Use an effective date after the transferred-through horizon, or close the next contiguous reporting period before posting into later dates.";
      case PostingRejection.OpeningBalanceWindowClosed rejectionWindowClosed ->
          "Opening balances are only accepted before the first committed posting in the book. The window closed with "
              + rejectionWindowClosed.firstBlockingPostingKind().wireValue()
              + " on "
              + rejectionWindowClosed.firstBlockingEffectiveDate()
              + "; create a new book if the opening statement was not seeded completely.";
      case PostingRejection.OpeningBalanceTouchesNominalAccount _ ->
          "Opening-balance postings may seed only asset, liability, or equity accounts. Move revenue and expense setup into real operating-period postings instead.";
      case PostingRejection.ResultHoldingAccountReserved _ ->
          "Post directly to ordinary accounts only; let "
              + TRANSFER_PERIOD_RESULT_OPERATION
              + " generate result-holding postings automatically.";
      case PostingRejection.ReversalTargetNotFound _ ->
          "Use "
              + GET_POSTING_OPERATION
              + " or "
              + LIST_POSTINGS_OPERATION
              + " to confirm the prior posting id before retrying the reversal.";
      case PostingRejection.ReversalAlreadyExists _ ->
          "Inspect the existing reversal for the referenced posting instead of retrying another reversal.";
      case PostingRejection.ReversalDoesNotNegateTarget _ ->
          "Build a full negating journal entry for the referenced posting so every line, amount, and side inverts the original exactly.";
    };
  }

  private static String administrationRejectionHint(BookAdministrationRejection rejection) {
    if (rejection instanceof BookAdministrationRejection.BookAlreadyInitialized) {
      return "Use "
          + INSPECT_BOOK_OPERATION
          + " or the normal read/write commands for this book instead of rerunning "
          + OPEN_BOOK_OPERATION
          + ".";
    }
    if (rejection instanceof BookAdministrationRejection.BookNotInitialized) {
      return "Run "
          + OPEN_BOOK_OPERATION
          + " first for a new book, or verify the selected --book-file and book passphrase source for an existing book.";
    }
    if (rejection instanceof BookAdministrationRejection.BookContainsSchema) {
      return "Select an empty target path, or remove the unintended SQLite file before rerunning "
          + OPEN_BOOK_OPERATION
          + ".";
    }
    if (rejection instanceof BookAdministrationRejection.AccountTypeConflict
        || rejection instanceof BookAdministrationRejection.AccountRoleConflict) {
      return "Keep the existing account identity as declared, or choose a different accountCode for a differently classified account.";
    }
    if (rejection instanceof BookAdministrationRejection.AccountTaxonomyConflict) {
      return "Keep the existing taxonomy for this account, or choose a different accountCode for an account with different hierarchy or statement classification.";
    }
    if (rejection instanceof BookAdministrationRejection.ParentAccountMissing) {
      return "Declare the requested parent account first, or remove parentAccountCode and rerun "
          + ProtocolCatalog.operationName(OperationId.DECLARE_ACCOUNT)
          + ".";
    }
    if (rejection instanceof BookAdministrationRejection.ParentAccountInactive) {
      return "Reactivate the requested parent account by redeclaring it, or choose an active parentAccountCode before rerunning "
          + ProtocolCatalog.operationName(OperationId.DECLARE_ACCOUNT)
          + ".";
    }
    if (rejection instanceof BookAdministrationRejection.ParentAccountTypeConflict) {
      return "Choose a parentAccountCode with the same accountType as the child account, or declare the child under the correct accountType before rerunning "
          + ProtocolCatalog.operationName(OperationId.DECLARE_ACCOUNT)
          + ".";
    }
    if (rejection instanceof BookAdministrationRejection.ParentAccountRoleConflict) {
      return "Choose a parentAccountCode with the same accountRole as the child account, or declare the child under the correct doctrinal role before rerunning "
          + ProtocolCatalog.operationName(OperationId.DECLARE_ACCOUNT)
          + ".";
    }
    if (rejection instanceof BookAdministrationRejection.ParentAccountNotHeader) {
      return "Choose a parentAccountCode declared as HEADER, or remove parentAccountCode and rerun "
          + ProtocolCatalog.operationName(OperationId.DECLARE_ACCOUNT)
          + ".";
    }
    if (rejection instanceof BookAdministrationRejection.ParentAccountTaxonomyConflict) {
      return "Choose a parentAccountCode in the same statement-classification family as the child account, or adjust the child taxonomy before rerunning "
          + ProtocolCatalog.operationName(OperationId.DECLARE_ACCOUNT)
          + ".";
    }
    if (rejection instanceof BookAdministrationRejection.AccountHierarchyCycle) {
      return "Choose a parentAccountCode that is not the account itself and not one of its descendants, then rerun "
          + ProtocolCatalog.operationName(OperationId.DECLARE_ACCOUNT)
          + ".";
    }
    if (rejection
        instanceof BookAdministrationRejection.ResultHoldingAccountCandidateMissing missing) {
      return missing.inactiveCandidateAccountCodes().isEmpty()
          ? "Declare one active equity account whose financialPositionLineClassification is "
              + missing.requiredFinancialPositionLineClassification().wireValue()
              + ", then rerun "
              + TRANSFER_PERIOD_RESULT_OPERATION
              + "."
          : "Reactivate one of the matching equity accounts or declare exactly one active replacement with financialPositionLineClassification "
              + missing.requiredFinancialPositionLineClassification().wireValue()
              + ", then rerun "
              + TRANSFER_PERIOD_RESULT_OPERATION
              + ".";
    }
    if (rejection instanceof BookAdministrationRejection.ResultHoldingAccountCandidateAmbiguous) {
      return "Leave exactly one active equity account with the built-in closing classification for this book, then rerun "
          + TRANSFER_PERIOD_RESULT_OPERATION
          + ".";
    }
    if (rejection instanceof BookAdministrationRejection.PeriodResultTransferMustStartAt) {
      return "Rerun "
          + TRANSFER_PERIOD_RESULT_OPERATION
          + " with the required --effective-date-from value and the next contiguous unclosed end date.";
    }
    if (rejection instanceof BookAdministrationRejection.PeriodResultTransferFutureDate) {
      return "Choose an --effective-date-to on or before the current UTC date, then rerun "
          + TRANSFER_PERIOD_RESULT_OPERATION
          + ".";
    }
    return "Choose --effective-date-from and --effective-date-to that remain inside one fiscal year for this book, then rerun "
        + TRANSFER_PERIOD_RESULT_OPERATION
        + ".";
  }

  private static String queryRejectionHint(BookQueryRejection rejection) {
    return switch (rejection) {
      case BookQueryRejection.BookNotInitialized _ ->
          "Run "
              + OPEN_BOOK_OPERATION
              + " first for a new book, or verify the selected --book-file and book passphrase source for an existing book.";
      case BookQueryRejection.UnknownAccount _ ->
          "Use "
              + LIST_ACCOUNTS_OPERATION
              + " to confirm the account code, or declare the missing account before rerunning the query.";
      case BookQueryRejection.PostingNotFound _ ->
          "Use "
              + LIST_POSTINGS_OPERATION
              + " or "
              + GET_POSTING_OPERATION
              + " with a known posting id from this book before rerunning the query.";
    };
  }

  private static String maintenanceRejectionHint(BookMaintenanceRejection rejection) {
    return switch (rejection) {
      case BookMaintenanceRejection.BookHasBlockingArtifacts _ ->
          "Close every process using the selected book, remove SQLite sidecars only by finishing or restoring the interrupted workflow, and rerun the maintenance command after "
              + INSPECT_BOOK_OPERATION
              + " and "
              + INSPECT_REKEY_ROLLBACK_OPERATION
              + " confirm one clean closed-copy state.";
      case BookMaintenanceRejection.BackupSourceHasBlockingArtifacts _ ->
          "Choose one encrypted backup copy with no sibling SQLite sidecars or rollback artifacts, or recreate the backup with "
              + BACKUP_BOOK_OPERATION
              + ".";
      case BookMaintenanceRejection.BackupSourceMatchesLiveBook _ ->
          "Choose one backup copy path that differs from the selected --book-file path, then rerun "
              + RESTORE_BOOK_OPERATION
              + ".";
      case BookMaintenanceRejection.ArtifactBusy artifactBusy ->
          "Close the process using the "
              + artifactBusy.artifactRole().wireValue()
              + " artifact, wait for the active maintenance workflow to finish, then rerun the command.";
      case BookMaintenanceRejection.BackupDestinationAlreadyExists _ ->
          "Choose a new --backup-file-out path or remove the existing encrypted backup copy yourself before rerunning "
              + BACKUP_BOOK_OPERATION
              + ".";
      case BookMaintenanceRejection.BackupKeyFileAlreadyExists _ ->
          "Choose a new --backup-book-key-file-out path or remove the existing key file yourself before rerunning "
              + BACKUP_BOOK_OPERATION
              + ".";
      case BookMaintenanceRejection.ArtifactVerificationFailed verificationFailed ->
          "Use an artifact that opens as one initialized FinGrind protected book for role "
              + verificationFailed.artifactRole().wireValue()
              + ", with the matching passphrase source for that artifact, then rerun the maintenance command.";
      case BookMaintenanceRejection.NoRollbackArtifactsFound _ ->
          "Rerun "
              + INSPECT_REKEY_ROLLBACK_OPERATION
              + " to confirm that no stale rollback copies remain.";
      case BookMaintenanceRejection.RollbackArtifactSelectionRequired _ ->
          "Rerun "
              + RESTORE_REKEY_ROLLBACK_OPERATION
              + " or "
              + DELETE_REKEY_ROLLBACK_OPERATION
              + " with one explicit --rollback-file path from details.rollbackArtifacts.";
      case BookMaintenanceRejection.RollbackArtifactNotFound _ ->
          "Choose an existing rollback artifact path returned by "
              + INSPECT_REKEY_ROLLBACK_OPERATION
              + " inspection output and rerun the command.";
      case BookMaintenanceRejection.RollbackArtifactNotForBook _ ->
          "Choose one rollback artifact that lives beside the selected --book-file and matches FinGrind's canonical rollback naming.";
    };
  }

  private static CliRejectionJsonModels.@org.jspecify.annotations.Nullable RejectionDetails
      postingRejectionDetails(PostingRejection rejection) {
    return switch (rejection) {
      case PostingRejection.BookNotInitialized _ -> null;
      case PostingRejection.AccountStateViolations violations ->
          new CliRejectionJsonModels.AccountStateViolationsDetails(
              violations.violations().stream()
                  .map(CliRejectionPayloadMapper::accountStateViolationPayload)
                  .toList());
      case PostingRejection.EntrySemanticsViolations violations ->
          new CliRejectionJsonModels.EntrySemanticsViolationsDetails(
              violations.violations().stream()
                  .map(CliRejectionPayloadMapper::entrySemanticsViolationPayload)
                  .toList());
      case PostingRejection.DuplicateIdempotencyKey _ -> null;
      case PostingRejection.BookFunctionalCurrencyMismatch rejectionCurrencyMismatch ->
          new CliRejectionJsonModels.FunctionalCurrencyMismatchDetails(
              rejectionCurrencyMismatch.functionalCurrency().code(),
              rejectionCurrencyMismatch.attemptedCurrency().code());
      case PostingRejection.TransferredPeriodResultViolation violation ->
          new CliRejectionJsonModels.TransferredPeriodResultViolationDetails(
              violation.transferredThroughEffectiveDate().toString(),
              violation.attemptedEffectiveDate().toString());
      case PostingRejection.OpeningBalanceWindowClosed rejectionWindowClosed ->
          new CliRejectionJsonModels.OpeningBalanceWindowClosedDetails(
              rejectionWindowClosed.firstBlockingPostingKind().wireValue(),
              rejectionWindowClosed.firstBlockingEffectiveDate().toString());
      case PostingRejection.OpeningBalanceTouchesNominalAccount rejectionOpeningBalance ->
          new CliRejectionJsonModels.OpeningBalanceNominalAccountDetails(
              rejectionOpeningBalance.accountCode().value(),
              rejectionOpeningBalance.accountType().wireValue());
      case PostingRejection.ResultHoldingAccountReserved rejectionReserved ->
          new CliRejectionJsonModels.ResultHoldingAccountDetails(
              rejectionReserved.accountCode().value());
      case PostingRejection.ReversalTargetNotFound reversalTargetNotFound ->
          new CliRejectionJsonModels.PriorPostingDetails(
              reversalTargetNotFound.priorPostingId().value());
      case PostingRejection.ReversalAlreadyExists reversalAlreadyExists ->
          new CliRejectionJsonModels.PriorPostingDetails(
              reversalAlreadyExists.priorPostingId().value());
      case PostingRejection.ReversalDoesNotNegateTarget reversalDoesNotNegateTarget ->
          new CliRejectionJsonModels.PriorPostingDetails(
              reversalDoesNotNegateTarget.priorPostingId().value());
    };
  }

  private static CliRejectionJsonModels.AccountStateViolationPayload accountStateViolationPayload(
      PostingRejection.AccountStateViolation violation) {
    return switch (violation) {
      case PostingRejection.UnknownAccount unknownAccount ->
          new CliRejectionJsonModels.AccountStateViolationPayload(
              PostingRejection.wireCode(unknownAccount),
              unknownAccount.accountCode().value(),
              null);
      case PostingRejection.InactiveAccount inactiveAccount ->
          new CliRejectionJsonModels.AccountStateViolationPayload(
              PostingRejection.wireCode(inactiveAccount),
              inactiveAccount.accountCode().value(),
              null);
      case PostingRejection.NonPostableAccount nonPostableAccount ->
          new CliRejectionJsonModels.AccountStateViolationPayload(
              PostingRejection.wireCode(nonPostableAccount),
              nonPostableAccount.accountCode().value(),
              nonPostableAccount.accountNodeKind().wireValue());
    };
  }

  private static CliRejectionJsonModels.EntrySemanticsViolationPayload
      entrySemanticsViolationPayload(PostingRejection.EntrySemanticsViolation violation) {
    return new CliRejectionJsonModels.EntrySemanticsViolationPayload(
        violation.code(), violation.field(), violation.message());
  }

  private static CliRejectionJsonModels.@org.jspecify.annotations.Nullable RejectionDetails
      administrationRejectionDetails(BookAdministrationRejection rejection) {
    return switch (rejection) {
      case BookAdministrationRejection.BookAlreadyInitialized _ -> null;
      case BookAdministrationRejection.BookNotInitialized _ -> null;
      case BookAdministrationRejection.BookContainsSchema _ -> null;
      case BookAdministrationRejection.AccountTypeConflict conflict ->
          new CliRejectionJsonModels.AccountTypeConflictDetails(
              conflict.accountCode().value(),
              conflict.existingAccountType().wireValue(),
              conflict.requestedAccountType().wireValue());
      case BookAdministrationRejection.AccountRoleConflict conflict ->
          new CliRejectionJsonModels.AccountRoleConflictDetails(
              conflict.accountCode().value(),
              conflict.existingAccountRole().wireValue(),
              conflict.requestedAccountRole().wireValue());
      case BookAdministrationRejection.AccountTaxonomyConflict conflict ->
          new CliRejectionJsonModels.AccountTaxonomyConflictDetails(
              conflict.accountCode().value(),
              taxonomyDetails(conflict.existingAccountTaxonomy()),
              taxonomyDetails(conflict.requestedAccountTaxonomy()));
      case BookAdministrationRejection.ParentAccountMissing conflict ->
          new CliRejectionJsonModels.ParentAccountDetails(
              conflict.accountCode().value(), conflict.parentAccountCode().value());
      case BookAdministrationRejection.ParentAccountInactive conflict ->
          new CliRejectionJsonModels.ParentAccountDetails(
              conflict.accountCode().value(), conflict.parentAccountCode().value());
      case BookAdministrationRejection.ParentAccountTypeConflict conflict ->
          new CliRejectionJsonModels.ParentAccountTypeConflictDetails(
              conflict.accountCode().value(),
              conflict.requestedAccountType().wireValue(),
              conflict.parentAccountCode().value(),
              conflict.parentAccountType().wireValue());
      case BookAdministrationRejection.ParentAccountRoleConflict conflict ->
          new CliRejectionJsonModels.ParentAccountRoleConflictDetails(
              conflict.accountCode().value(),
              conflict.requestedAccountRole().wireValue(),
              conflict.parentAccountCode().value(),
              conflict.parentAccountRole().wireValue());
      case BookAdministrationRejection.ParentAccountNotHeader conflict ->
          new CliRejectionJsonModels.ParentAccountNodeKindDetails(
              conflict.accountCode().value(),
              conflict.parentAccountCode().value(),
              conflict.parentAccountNodeKind().wireValue());
      case BookAdministrationRejection.ParentAccountTaxonomyConflict conflict ->
          new CliRejectionJsonModels.ParentAccountTaxonomyConflictDetails(
              conflict.accountCode().value(),
              taxonomyDetails(conflict.requestedAccountTaxonomy()),
              conflict.parentAccountCode().value(),
              taxonomyDetails(conflict.parentAccountTaxonomy()));
      case BookAdministrationRejection.AccountHierarchyCycle conflict ->
          new CliRejectionJsonModels.ParentAccountDetails(
              conflict.accountCode().value(), conflict.parentAccountCode().value());
      case BookAdministrationRejection.ResultHoldingAccountCandidateMissing conflict ->
          new CliRejectionJsonModels.ResultHoldingAccountCandidateMissingDetails(
              conflict.requiredFinancialPositionLineClassification().wireValue(),
              conflict.inactiveCandidateAccountCodes().stream().map(code -> code.value()).toList());
      case BookAdministrationRejection.ResultHoldingAccountCandidateAmbiguous conflict ->
          new CliRejectionJsonModels.ResultHoldingAccountCandidateAmbiguousDetails(
              conflict.requiredFinancialPositionLineClassification().wireValue(),
              conflict.candidateAccountCodes().stream().map(code -> code.value()).toList());
      case BookAdministrationRejection.PeriodResultTransferMustStartAt conflict ->
          new CliRejectionJsonModels.PeriodResultTransferStartDetails(
              conflict.requiredEffectiveDateFrom().toString());
      case BookAdministrationRejection.PeriodResultTransferFutureDate conflict ->
          new CliRejectionJsonModels.PeriodResultTransferFutureDateDetails(
              conflict.attemptedEffectiveDateTo().toString());
      case BookAdministrationRejection.PeriodResultTransferCrossesFiscalYearBoundary conflict ->
          new CliRejectionJsonModels.PeriodResultTransferFiscalYearDetails(
              conflict.attemptedEffectiveDateFrom().toString(),
              conflict.attemptedEffectiveDateTo().toString(),
              conflict.fiscalYearStart().wireValue());
    };
  }

  private static CliRejectionJsonModels.@org.jspecify.annotations.Nullable RejectionDetails
      queryRejectionDetails(BookQueryRejection rejection) {
    return switch (rejection) {
      case BookQueryRejection.BookNotInitialized _ -> null;
      case BookQueryRejection.UnknownAccount unknownAccount ->
          new CliRejectionJsonModels.UnknownAccountDetails(unknownAccount.accountCode().value());
      case BookQueryRejection.PostingNotFound postingNotFound ->
          new CliRejectionJsonModels.PostingNotFoundDetails(postingNotFound.postingId().value());
    };
  }

  private static CliRejectionJsonModels.@org.jspecify.annotations.Nullable RejectionDetails
      maintenanceRejectionDetails(BookMaintenanceRejection rejection) {
    return switch (rejection) {
      case BookMaintenanceRejection.BookHasBlockingArtifacts blockingArtifacts ->
          new CliRejectionJsonModels.BlockingArtifactsDetails(
              blockingArtifacts.bookFilePath().value(),
              blockingArtifacts.blockingArtifactPaths().stream()
                  .map(path -> path.value())
                  .toList());
      case BookMaintenanceRejection.BackupSourceHasBlockingArtifacts blockingArtifacts ->
          new CliRejectionJsonModels.BlockingArtifactsDetails(
              blockingArtifacts.backupFilePath().value(),
              blockingArtifacts.blockingArtifactPaths().stream()
                  .map(path -> path.value())
                  .toList());
      case BookMaintenanceRejection.BackupSourceMatchesLiveBook sourceMatchesLiveBook ->
          new CliRejectionJsonModels.BookAndBackupFileDetails(
              sourceMatchesLiveBook.bookFilePath().value(),
              sourceMatchesLiveBook.backupFilePath().value());
      case BookMaintenanceRejection.ArtifactBusy artifactBusy ->
          new CliRejectionJsonModels.ArtifactBusyDetails(
              artifactBusy.artifactRole().wireValue(), artifactBusy.artifactPath().value());
      case BookMaintenanceRejection.BackupDestinationAlreadyExists destinationAlreadyExists ->
          new CliRejectionJsonModels.BackupFileDetails(
              destinationAlreadyExists.backupFilePath().value());
      case BookMaintenanceRejection.BackupKeyFileAlreadyExists destinationAlreadyExists ->
          new CliRejectionJsonModels.BackupBookKeyFileDetails(
              destinationAlreadyExists.backupBookKeyFilePath().value());
      case BookMaintenanceRejection.ArtifactVerificationFailed verificationFailed ->
          new CliRejectionJsonModels.ArtifactVerificationFailureDetails(
              verificationFailed.artifactRole().wireValue(),
              verificationFailed.artifactPath().value(),
              verificationFailed.verificationFailure().wireValue());
      case BookMaintenanceRejection.NoRollbackArtifactsFound noRollbackArtifactsFound ->
          new CliRejectionJsonModels.BookFileDetails(
              noRollbackArtifactsFound.bookFilePath().value());
      case BookMaintenanceRejection.RollbackArtifactSelectionRequired selectionRequired ->
          new CliRejectionJsonModels.RollbackArtifactSelectionDetails(
              selectionRequired.bookFilePath().value(),
              selectionRequired.rollbackArtifactPaths().stream()
                  .map(path -> path.value())
                  .toList());
      case BookMaintenanceRejection.RollbackArtifactNotFound rollbackArtifactNotFound ->
          new CliRejectionJsonModels.RollbackArtifactDetails(
              rollbackArtifactNotFound.rollbackArtifactPath().value());
      case BookMaintenanceRejection.RollbackArtifactNotForBook rollbackArtifactNotForBook ->
          new CliRejectionJsonModels.RollbackArtifactMismatchDetails(
              rollbackArtifactNotForBook.bookFilePath().value(),
              rollbackArtifactNotForBook.rollbackArtifactPath().value());
    };
  }

  private static CliRejectionJsonModels.AccountTaxonomyDetails taxonomyDetails(
      AccountTaxonomy accountTaxonomy) {
    return new CliRejectionJsonModels.AccountTaxonomyDetails(
        accountTaxonomy.nodeKind().wireValue(),
        accountTaxonomy.parentAccountCode().map(accountCode -> accountCode.value()).orElse(null),
        accountTaxonomy
            .financialPositionLineClassification()
            .map(classification -> classification.wireValue())
            .orElse(null),
        accountTaxonomy
            .profitAndLossLineClassification()
            .map(classification -> classification.wireValue())
            .orElse(null));
  }
}
