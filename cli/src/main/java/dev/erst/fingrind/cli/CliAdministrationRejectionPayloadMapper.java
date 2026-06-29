package dev.erst.fingrind.cli;

import dev.erst.fingrind.cli.json.CliEnvelopeJsonModels;
import dev.erst.fingrind.cli.json.CliRejectionJsonModels;
import dev.erst.fingrind.contract.bookkeeping.BookAdministrationRejection;
import dev.erst.fingrind.contract.bookkeeping.CloseTargetAccountCandidateAmbiguous;
import dev.erst.fingrind.contract.bookkeeping.RejectionNarrative;
import dev.erst.fingrind.contract.protocol.OperationId;
import dev.erst.fingrind.contract.protocol.ProtocolCatalog;
import dev.erst.fingrind.contract.protocol.ProtocolEnvelopeStatus;
import dev.erst.fingrind.core.AccountTaxonomy;
import java.util.Locale;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** Maps administrative bookkeeping rejections into CLI rejected envelopes. */
final class CliAdministrationRejectionPayloadMapper {
  private static final String DECLARE_ACCOUNT_OPERATION =
      ProtocolCatalog.operationName(OperationId.DECLARE_ACCOUNT);
  private static final String OPEN_BOOK_OPERATION =
      ProtocolCatalog.operationName(OperationId.OPEN_BOOK);
  private static final String INTERIM_RESULT_SWEEP_OPERATION =
      ProtocolCatalog.operationName(OperationId.INTERIM_RESULT_SWEEP);
  private static final String FISCAL_YEAR_CLOSE_OPERATION =
      ProtocolCatalog.operationName(OperationId.FISCAL_YEAR_CLOSE);

  private CliAdministrationRejectionPayloadMapper() {}

  static CliEnvelopeJsonModels.Envelope<?> rejectedEnvelope(
      OperationId operationId, BookAdministrationRejection rejection) {
    return new CliEnvelopeJsonModels.Envelope<>(
        ProtocolEnvelopeStatus.REJECTED,
        null,
        BookAdministrationRejection.wireCode(rejection),
        RejectionNarrative.message(rejection),
        rejectionHint(operationId, rejection),
        null,
        null,
        rejectionDetails(rejection),
        null);
  }

  private static String rejectionHint(
      OperationId operationId, BookAdministrationRejection rejection) {
    String lifecycleHint = lifecycleHint(rejection);
    if (lifecycleHint != null) {
      return lifecycleHint;
    }
    String accountHint = accountHint(rejection);
    if (accountHint != null) {
      return accountHint;
    }
    return closeWindowHint(operationId, rejection);
  }

  private static @Nullable String lifecycleHint(BookAdministrationRejection rejection) {
    if (rejection instanceof BookAdministrationRejection.BookAlreadyInitialized) {
      return "Use "
          + ProtocolCatalog.operationName(OperationId.INSPECT_BOOK)
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
    return null;
  }

  private static @Nullable String accountHint(BookAdministrationRejection rejection) {
    if (rejection instanceof BookAdministrationRejection.AccountTypeConflict) {
      return "Keep the existing account identity as declared, or choose a different accountCode for a differently classified account.";
    }
    if (rejection instanceof BookAdministrationRejection.AccountTaxonomyConflict) {
      return "Keep the existing taxonomy for this account, or choose a different accountCode for an account with different hierarchy or statement classification.";
    }
    if (rejection instanceof BookAdministrationRejection.ParentAccountMissing) {
      return "Declare the requested parent account first, or remove parentAccountCode and rerun "
          + DECLARE_ACCOUNT_OPERATION
          + ".";
    }
    if (rejection instanceof BookAdministrationRejection.ParentAccountInactive) {
      return "Reactivate the requested parent account by redeclaring it, or choose an active parentAccountCode before rerunning "
          + DECLARE_ACCOUNT_OPERATION
          + ".";
    }
    if (rejection instanceof BookAdministrationRejection.ParentAccountTypeConflict) {
      return "Choose a parentAccountCode with the same accountType as the child account, or declare the child under the correct accountType before rerunning "
          + DECLARE_ACCOUNT_OPERATION
          + ".";
    }
    if (rejection instanceof BookAdministrationRejection.ParentAccountNotHeader) {
      return "Choose a parentAccountCode declared as HEADER, or remove parentAccountCode and rerun "
          + DECLARE_ACCOUNT_OPERATION
          + ".";
    }
    if (rejection instanceof BookAdministrationRejection.ParentAccountTaxonomyConflict) {
      return "Choose a parentAccountCode in the same statement-classification family as the child account, or adjust the child taxonomy before rerunning "
          + DECLARE_ACCOUNT_OPERATION
          + ".";
    }
    if (rejection instanceof BookAdministrationRejection.AccountHierarchyCycle) {
      return "Choose a parentAccountCode that is not the account itself and not one of its descendants, then rerun "
          + DECLARE_ACCOUNT_OPERATION
          + ".";
    }
    return null;
  }

  private static String closeWindowHint(
      OperationId operationId, BookAdministrationRejection rejection) {
    if (rejection
        instanceof BookAdministrationRejection.CloseTargetAccountCandidateMissing missing) {
      String operation = closeOperation(operationId);
      return missing.inactiveCandidateAccountCodes().isEmpty()
          ? "Declare one active equity account whose financialPositionLineClassification is "
              + missing.requiredFinancialPositionLineClassification().wireValue()
              + ", then rerun "
              + operation
              + "."
          : "Reactivate one of the matching equity accounts or declare exactly one active replacement with financialPositionLineClassification "
              + missing.requiredFinancialPositionLineClassification().wireValue()
              + ", then rerun "
              + operation
              + ".";
    }
    if (rejection instanceof CloseTargetAccountCandidateAmbiguous) {
      return "Leave exactly one active equity account with the required closing classification for this book, then retry the declaration or rerun "
          + closeOperation(operationId)
          + ".";
    }
    if (rejection instanceof BookAdministrationRejection.InterimResultSweepMustStartAt) {
      return "Rerun "
          + INTERIM_RESULT_SWEEP_OPERATION
          + " with the required "
          + CliTemporalScopeText.firstOption(OperationId.INTERIM_RESULT_SWEEP)
          + " value and the next contiguous unclosed "
          + CliTemporalScopeText.upperLabel(OperationId.INTERIM_RESULT_SWEEP)
              .toLowerCase(Locale.ROOT)
          + ".";
    }
    if (rejection instanceof BookAdministrationRejection.InterimResultSweepFutureDate) {
      return "Choose a "
          + CliTemporalScopeText.secondOption(OperationId.INTERIM_RESULT_SWEEP)
          + " on or before the current UTC date, then rerun "
          + INTERIM_RESULT_SWEEP_OPERATION
          + ".";
    }
    if (rejection
        instanceof BookAdministrationRejection.InterimResultSweepCrossesFiscalYearBoundary) {
      return "Choose "
          + CliTemporalScopeText.firstOption(OperationId.INTERIM_RESULT_SWEEP)
          + " and "
          + CliTemporalScopeText.secondOption(OperationId.INTERIM_RESULT_SWEEP)
          + " values that remain inside one fiscal year for this book, then rerun "
          + INTERIM_RESULT_SWEEP_OPERATION
          + ".";
    }
    if (rejection instanceof BookAdministrationRejection.FiscalYearCloseMustStartAt) {
      return "Choose the fiscal year start for "
          + CliTemporalScopeText.firstOption(OperationId.FISCAL_YEAR_CLOSE)
          + ", then rerun "
          + FISCAL_YEAR_CLOSE_OPERATION
          + ".";
    }
    if (rejection instanceof BookAdministrationRejection.FiscalYearCloseMustEndAt) {
      return "Choose the fiscal year end for "
          + CliTemporalScopeText.secondOption(OperationId.FISCAL_YEAR_CLOSE)
          + ", then rerun "
          + FISCAL_YEAR_CLOSE_OPERATION
          + ".";
    }
    return "Choose a "
        + CliTemporalScopeText.secondOption(OperationId.FISCAL_YEAR_CLOSE)
        + " on or before the current UTC date, then rerun "
        + FISCAL_YEAR_CLOSE_OPERATION
        + ".";
  }

  private static CliRejectionJsonModels.@org.jspecify.annotations.Nullable RejectionDetails
      rejectionDetails(BookAdministrationRejection rejection) {
    CliRejectionJsonModels.@Nullable RejectionDetails accountDetails = accountDetails(rejection);
    if (accountDetails != null) {
      return accountDetails;
    }
    return periodTransferDetails(rejection);
  }

  private static CliRejectionJsonModels.@org.jspecify.annotations.Nullable RejectionDetails
      accountDetails(BookAdministrationRejection rejection) {
    return switch (rejection) {
      case BookAdministrationRejection.AccountTypeConflict conflict ->
          new CliRejectionJsonModels.AccountTypeConflictDetails(
              conflict.accountCode().value(),
              conflict.existingAccountType().wireValue(),
              conflict.requestedAccountType().wireValue());
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
      default -> null;
    };
  }

  private static CliRejectionJsonModels.@org.jspecify.annotations.Nullable RejectionDetails
      periodTransferDetails(BookAdministrationRejection rejection) {
    return switch (rejection) {
      case BookAdministrationRejection.CloseTargetAccountCandidateMissing conflict ->
          new CliRejectionJsonModels.CloseTargetAccountCandidateMissingDetails(
              conflict.requiredFinancialPositionLineClassification().wireValue(),
              conflict.inactiveCandidateAccountCodes().stream().map(code -> code.value()).toList());
      case CloseTargetAccountCandidateAmbiguous conflict ->
          new CliRejectionJsonModels.CloseTargetAccountCandidateAmbiguousDetails(
              conflict.requiredFinancialPositionLineClassification().wireValue(),
              conflict.candidateAccountCodes().stream().map(code -> code.value()).toList());
      case BookAdministrationRejection.InterimResultSweepMustStartAt conflict ->
          new CliRejectionJsonModels.InterimResultSweepStartDetails(
              conflict.requiredEffectiveDateFrom().toString());
      case BookAdministrationRejection.InterimResultSweepFutureDate conflict ->
          new CliRejectionJsonModels.InterimResultSweepFutureDateDetails(
              conflict.attemptedEffectiveDateTo().toString());
      case BookAdministrationRejection.InterimResultSweepCrossesFiscalYearBoundary conflict ->
          new CliRejectionJsonModels.InterimResultSweepFiscalYearDetails(
              conflict.attemptedEffectiveDateFrom().toString(),
              conflict.attemptedEffectiveDateTo().toString(),
              conflict.fiscalYearStart().wireValue());
      case BookAdministrationRejection.FiscalYearCloseMustStartAt conflict ->
          new CliRejectionJsonModels.FiscalYearCloseStartDetails(
              conflict.requiredEffectiveDateFrom().toString());
      case BookAdministrationRejection.FiscalYearCloseMustEndAt conflict ->
          new CliRejectionJsonModels.FiscalYearCloseEndDetails(
              conflict.requiredEffectiveDateTo().toString());
      case BookAdministrationRejection.FiscalYearCloseFutureDate conflict ->
          new CliRejectionJsonModels.FiscalYearCloseFutureDateDetails(
              conflict.attemptedEffectiveDateTo().toString());
      default -> null;
    };
  }

  private static String closeOperation(OperationId operationId) {
    return switch (Objects.requireNonNull(operationId, "operationId")) {
      case INTERIM_RESULT_SWEEP -> INTERIM_RESULT_SWEEP_OPERATION;
      case FISCAL_YEAR_CLOSE -> FISCAL_YEAR_CLOSE_OPERATION;
      default -> throw new IllegalArgumentException("Unsupported close operation: " + operationId);
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
