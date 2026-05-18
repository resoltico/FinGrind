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
  private static final String CLOSE_PERIOD_OPERATION =
      ProtocolCatalog.operationName(OperationId.CLOSE_PERIOD);
  private static final String RECOVER_REKEY_OPERATION =
      ProtocolCatalog.operationName(OperationId.RECOVER_REKEY);

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
      case PostingRejection.DuplicateIdempotencyKey _ ->
          "Inspect the already-committed posting for this idempotency key instead of retrying the same key, or submit a new posting with a fresh provenance.idempotencyKey.";
      case PostingRejection.PostingKindReserved _ ->
          "Use postingKind STANDARD or OPENING_BALANCE on direct posting requests; let "
              + CLOSE_PERIOD_OPERATION
              + " generate PERIOD_CLOSE entries.";
      case PostingRejection.BookFunctionalCurrencyMismatch _ ->
          "Use the selected book's functional currency for every journal line in this request, or open a separate book for another currency.";
      case PostingRejection.ClosedPeriodViolation _ ->
          "Use an effective date after the closed-through horizon, or close the next contiguous reporting period before posting into later dates.";
      case PostingRejection.OpeningBalanceWindowClosed rejectionWindowClosed ->
          "Opening balances are only accepted before the first committed posting in the book. The window closed with "
              + rejectionWindowClosed.firstBlockingPostingKind().wireValue()
              + " on "
              + rejectionWindowClosed.firstBlockingEffectiveDate()
              + "; create a new book if the opening statement was not seeded completely.";
      case PostingRejection.OpeningBalanceTouchesNominalAccount _ ->
          "Opening-balance postings may seed only asset, liability, or equity accounts. Move revenue and expense setup into real operating-period postings instead.";
      case PostingRejection.ClosingEquityAccountReserved _ ->
          "Post directly to ordinary accounts only; let "
              + CLOSE_PERIOD_OPERATION
              + " generate closing-equity postings automatically.";
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
    if (rejection instanceof BookAdministrationRejection.ClosingEquityAccountMissing) {
      return "Declare the selected closing equity account first, using accountType EQUITY and the built-in financial position classification required for this book's entity form, then rerun "
          + CLOSE_PERIOD_OPERATION
          + " with --closing-equity-account set to that code.";
    }
    if (rejection
        instanceof
        BookAdministrationRejection.ClosingEquityAccountClassificationMismatch conflict) {
      return "Choose an account whose declared financialPositionLineClassification matches the built-in closing classification "
          + conflict.requiredFinancialPositionLineClassification().wireValue()
          + ", or redeclare the selected account with the correct policy-owned classification before rerunning "
          + CLOSE_PERIOD_OPERATION
          + ".";
    }
    if (rejection instanceof BookAdministrationRejection.ClosingEquityAccountInactive) {
      return "Redeclare the closing equity account to reactivate it, or declare the correct closing equity account before rerunning "
          + CLOSE_PERIOD_OPERATION
          + ".";
    }
    if (rejection instanceof BookAdministrationRejection.PeriodCloseMustStartAt) {
      return "Rerun "
          + CLOSE_PERIOD_OPERATION
          + " with the required --effective-date-from value and the next contiguous unclosed end date.";
    }
    if (rejection instanceof BookAdministrationRejection.PeriodCloseFutureDate) {
      return "Choose an --effective-date-to on or before the current UTC date, then rerun "
          + CLOSE_PERIOD_OPERATION
          + ".";
    }
    return "Choose --effective-date-from and --effective-date-to that remain inside one fiscal year for this book, then rerun "
        + CLOSE_PERIOD_OPERATION
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
              + RECOVER_REKEY_OPERATION
              + " confirm one clean closed-copy state.";
      case BookMaintenanceRejection.BackupSourceHasBlockingArtifacts _ ->
          "Choose one encrypted backup copy with no sibling SQLite sidecars or rollback artifacts, or recreate the backup with "
              + BACKUP_BOOK_OPERATION
              + ".";
      case BookMaintenanceRejection.BackupDestinationAlreadyExists _ ->
          "Choose a new --backup-file path or remove the existing encrypted backup copy yourself before rerunning "
              + BACKUP_BOOK_OPERATION
              + ".";
      case BookMaintenanceRejection.BackupKeyFileAlreadyExists _ ->
          "Choose a new --backup-book-key-file path or remove the existing key file yourself before rerunning "
              + BACKUP_BOOK_OPERATION
              + ".";
      case BookMaintenanceRejection.NoRollbackArtifactsFound _ ->
          "Rerun "
              + RECOVER_REKEY_OPERATION
              + " without mutation flags to confirm that no stale rollback copies remain.";
      case BookMaintenanceRejection.RollbackArtifactSelectionRequired _ ->
          "Rerun "
              + RECOVER_REKEY_OPERATION
              + " with one explicit --rollback-file path from details.rollbackArtifacts.";
      case BookMaintenanceRejection.RollbackArtifactNotFound _ ->
          "Choose an existing rollback artifact path returned by "
              + RECOVER_REKEY_OPERATION
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
      case PostingRejection.DuplicateIdempotencyKey _ -> null;
      case PostingRejection.PostingKindReserved rejectionPostingKind ->
          new CliRejectionJsonModels.PostingKindDetails(
              rejectionPostingKind.postingKind().wireValue());
      case PostingRejection.BookFunctionalCurrencyMismatch rejectionCurrencyMismatch ->
          new CliRejectionJsonModels.FunctionalCurrencyMismatchDetails(
              rejectionCurrencyMismatch.functionalCurrency().code(),
              rejectionCurrencyMismatch.attemptedCurrency().code());
      case PostingRejection.ClosedPeriodViolation violation ->
          new CliRejectionJsonModels.ClosedPeriodViolationDetails(
              violation.closedThroughEffectiveDate().toString(),
              violation.attemptedEffectiveDate().toString());
      case PostingRejection.OpeningBalanceWindowClosed rejectionWindowClosed ->
          new CliRejectionJsonModels.OpeningBalanceWindowClosedDetails(
              rejectionWindowClosed.firstBlockingPostingKind().wireValue(),
              rejectionWindowClosed.firstBlockingEffectiveDate().toString());
      case PostingRejection.OpeningBalanceTouchesNominalAccount rejectionOpeningBalance ->
          new CliRejectionJsonModels.OpeningBalanceNominalAccountDetails(
              rejectionOpeningBalance.accountCode().value(),
              rejectionOpeningBalance.accountType().wireValue());
      case PostingRejection.ClosingEquityAccountReserved rejectionReserved ->
          new CliRejectionJsonModels.ClosingEquityAccountDetails(
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
              PostingRejection.wireCode(unknownAccount), unknownAccount.accountCode().value());
      case PostingRejection.InactiveAccount inactiveAccount ->
          new CliRejectionJsonModels.AccountStateViolationPayload(
              PostingRejection.wireCode(inactiveAccount), inactiveAccount.accountCode().value());
    };
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
      case BookAdministrationRejection.ParentAccountTaxonomyConflict conflict ->
          new CliRejectionJsonModels.ParentAccountTaxonomyConflictDetails(
              conflict.accountCode().value(),
              taxonomyDetails(conflict.requestedAccountTaxonomy()),
              conflict.parentAccountCode().value(),
              taxonomyDetails(conflict.parentAccountTaxonomy()));
      case BookAdministrationRejection.AccountHierarchyCycle conflict ->
          new CliRejectionJsonModels.ParentAccountDetails(
              conflict.accountCode().value(), conflict.parentAccountCode().value());
      case BookAdministrationRejection.ClosingEquityAccountMissing conflict ->
          new CliRejectionJsonModels.ClosingEquityAccountDetails(conflict.accountCode().value());
      case BookAdministrationRejection.ClosingEquityAccountClassificationMismatch conflict ->
          new CliRejectionJsonModels.ClosingEquityAccountClassificationMismatchDetails(
              conflict.accountCode().value(),
              conflict.requiredFinancialPositionLineClassification().wireValue(),
              conflict.actualFinancialPositionLineClassification().wireValue());
      case BookAdministrationRejection.ClosingEquityAccountInactive conflict ->
          new CliRejectionJsonModels.ClosingEquityAccountDetails(conflict.accountCode().value());
      case BookAdministrationRejection.PeriodCloseMustStartAt conflict ->
          new CliRejectionJsonModels.PeriodCloseStartDetails(
              conflict.requiredEffectiveDateFrom().toString());
      case BookAdministrationRejection.PeriodCloseFutureDate conflict ->
          new CliRejectionJsonModels.PeriodCloseFutureDateDetails(
              conflict.attemptedEffectiveDateTo().toString());
      case BookAdministrationRejection.PeriodCloseCrossesFiscalYearBoundary conflict ->
          new CliRejectionJsonModels.PeriodCloseFiscalYearDetails(
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
              blockingArtifacts.bookFilePath().toAbsolutePath().normalize().toString(),
              blockingArtifacts.blockingArtifactPaths().stream()
                  .map(path -> path.toAbsolutePath().normalize().toString())
                  .toList());
      case BookMaintenanceRejection.BackupSourceHasBlockingArtifacts blockingArtifacts ->
          new CliRejectionJsonModels.BlockingArtifactsDetails(
              blockingArtifacts.backupFilePath().toAbsolutePath().normalize().toString(),
              blockingArtifacts.blockingArtifactPaths().stream()
                  .map(path -> path.toAbsolutePath().normalize().toString())
                  .toList());
      case BookMaintenanceRejection.BackupDestinationAlreadyExists destinationAlreadyExists ->
          new CliRejectionJsonModels.BackupFileDetails(
              destinationAlreadyExists.backupFilePath().toAbsolutePath().normalize().toString());
      case BookMaintenanceRejection.BackupKeyFileAlreadyExists destinationAlreadyExists ->
          new CliRejectionJsonModels.BackupBookKeyFileDetails(
              destinationAlreadyExists
                  .backupBookKeyFilePath()
                  .toAbsolutePath()
                  .normalize()
                  .toString());
      case BookMaintenanceRejection.NoRollbackArtifactsFound noRollbackArtifactsFound ->
          new CliRejectionJsonModels.BookFileDetails(
              noRollbackArtifactsFound.bookFilePath().toAbsolutePath().normalize().toString());
      case BookMaintenanceRejection.RollbackArtifactSelectionRequired selectionRequired ->
          new CliRejectionJsonModels.RollbackArtifactSelectionDetails(
              selectionRequired.bookFilePath().toAbsolutePath().normalize().toString(),
              selectionRequired.rollbackArtifactPaths().stream()
                  .map(path -> path.toAbsolutePath().normalize().toString())
                  .toList());
      case BookMaintenanceRejection.RollbackArtifactNotFound rollbackArtifactNotFound ->
          new CliRejectionJsonModels.RollbackArtifactDetails(
              rollbackArtifactNotFound
                  .rollbackArtifactPath()
                  .toAbsolutePath()
                  .normalize()
                  .toString());
      case BookMaintenanceRejection.RollbackArtifactNotForBook rollbackArtifactNotForBook ->
          new CliRejectionJsonModels.RollbackArtifactMismatchDetails(
              rollbackArtifactNotForBook.bookFilePath().toAbsolutePath().normalize().toString(),
              rollbackArtifactNotForBook
                  .rollbackArtifactPath()
                  .toAbsolutePath()
                  .normalize()
                  .toString());
    };
  }

  private static CliRejectionJsonModels.AccountTaxonomyDetails taxonomyDetails(
      AccountTaxonomy accountTaxonomy) {
    return new CliRejectionJsonModels.AccountTaxonomyDetails(
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
