package dev.erst.fingrind.cli.json;

import static dev.erst.fingrind.cli.json.CliJsonModelValidation.copyList;
import static dev.erst.fingrind.cli.json.CliJsonModelValidation.requireOptionalText;
import static dev.erst.fingrind.cli.json.CliJsonModelValidation.requireText;
import static dev.erst.fingrind.cli.json.CliJsonModelValidation.requireValue;

import java.util.List;
import org.jspecify.annotations.Nullable;

/** Rejection-detail JSON records emitted by the CLI transport layer. */
public interface CliRejectionJsonModels extends CliPlanJsonModels {

  /** Sealed marker for machine-readable CLI rejection detail payloads. */
  sealed interface RejectionDetails
      permits PostingRejectionDetails,
          AccountRejectionDetails,
          PeriodCloseRejectionDetails,
          QueryOrPlanRejectionDetails,
          MaintenanceRejectionDetails {}

  /** Sealed category for posting lifecycle rejection payloads. */
  sealed interface PostingRejectionDetails extends RejectionDetails
      permits AccountStateViolationsDetails,
          PriorPostingDetails,
          PostingKindDetails,
          FunctionalCurrencyMismatchDetails,
          OpeningBalanceWindowClosedDetails,
          OpeningBalanceNominalAccountDetails,
          ClosedPeriodViolationDetails {}

  /** Sealed category for account-registry rejection payloads. */
  sealed interface AccountRejectionDetails extends RejectionDetails
      permits AccountRoleConflictDetails,
          AccountTypeConflictDetails,
          AccountTaxonomyConflictDetails,
          ParentAccountDetails,
          ParentAccountTypeConflictDetails,
          ParentAccountTaxonomyConflictDetails,
          ClosingEquityAccountDetails,
          ClosingEquityAccountCandidateMissingDetails,
          ClosingEquityAccountCandidateAmbiguousDetails {}

  /** Sealed category for close-window rejection payloads. */
  sealed interface PeriodCloseRejectionDetails extends RejectionDetails
      permits PeriodCloseStartDetails, PeriodCloseFutureDateDetails, PeriodCloseFiscalYearDetails {}

  /** Sealed category for query and workflow rejection payloads. */
  sealed interface QueryOrPlanRejectionDetails extends RejectionDetails
      permits UnknownAccountDetails, PostingNotFoundDetails, PlanRejectionDetails {}

  /** Sealed category for protected-book maintenance rejection payloads. */
  sealed interface MaintenanceRejectionDetails extends RejectionDetails
      permits BookFileDetails,
          BookAndBackupFileDetails,
          BlockingArtifactsDetails,
          ArtifactBusyDetails,
          ArtifactVerificationFailureDetails,
          BackupFileDetails,
          BackupBookKeyFileDetails,
          RollbackArtifactDetails,
          RollbackArtifactMismatchDetails,
          RollbackArtifactSelectionDetails {}

  record AccountStateViolationsDetails(List<AccountStateViolationPayload> violations)
      implements PostingRejectionDetails {
    public AccountStateViolationsDetails {
      violations = copyList(violations, "violations");
      if (violations.isEmpty()) {
        throw new IllegalArgumentException("violations must not be empty.");
      }
    }
  }

  record AccountStateViolationPayload(String code, String accountCode) {
    public AccountStateViolationPayload {
      code = requireText(code, "code");
      accountCode = requireText(accountCode, "accountCode");
    }
  }

  record PriorPostingDetails(String priorPostingId) implements PostingRejectionDetails {
    public PriorPostingDetails {
      priorPostingId = requireText(priorPostingId, "priorPostingId");
    }
  }

  record AccountRoleConflictDetails(
      String accountCode, String existingAccountRole, String requestedAccountRole)
      implements AccountRejectionDetails {
    public AccountRoleConflictDetails {
      accountCode = requireText(accountCode, "accountCode");
      existingAccountRole = requireText(existingAccountRole, "existingAccountRole");
      requestedAccountRole = requireText(requestedAccountRole, "requestedAccountRole");
    }
  }

  record AccountTypeConflictDetails(
      String accountCode, String existingAccountType, String requestedAccountType)
      implements AccountRejectionDetails {
    public AccountTypeConflictDetails {
      accountCode = requireText(accountCode, "accountCode");
      existingAccountType = requireText(existingAccountType, "existingAccountType");
      requestedAccountType = requireText(requestedAccountType, "requestedAccountType");
    }
  }

  record AccountTaxonomyConflictDetails(
      String accountCode,
      AccountTaxonomyDetails existingAccountTaxonomy,
      AccountTaxonomyDetails requestedAccountTaxonomy)
      implements AccountRejectionDetails {
    public AccountTaxonomyConflictDetails {
      accountCode = requireText(accountCode, "accountCode");
      existingAccountTaxonomy = requireValue(existingAccountTaxonomy, "existingAccountTaxonomy");
      requestedAccountTaxonomy = requireValue(requestedAccountTaxonomy, "requestedAccountTaxonomy");
    }
  }

  record ParentAccountDetails(String accountCode, String parentAccountCode)
      implements AccountRejectionDetails {
    public ParentAccountDetails {
      accountCode = requireText(accountCode, "accountCode");
      parentAccountCode = requireText(parentAccountCode, "parentAccountCode");
    }
  }

  record ParentAccountTypeConflictDetails(
      String accountCode,
      String requestedAccountType,
      String parentAccountCode,
      String parentAccountType)
      implements AccountRejectionDetails {
    public ParentAccountTypeConflictDetails {
      accountCode = requireText(accountCode, "accountCode");
      requestedAccountType = requireText(requestedAccountType, "requestedAccountType");
      parentAccountCode = requireText(parentAccountCode, "parentAccountCode");
      parentAccountType = requireText(parentAccountType, "parentAccountType");
    }
  }

  record ParentAccountTaxonomyConflictDetails(
      String accountCode,
      AccountTaxonomyDetails requestedAccountTaxonomy,
      String parentAccountCode,
      AccountTaxonomyDetails parentAccountTaxonomy)
      implements AccountRejectionDetails {
    public ParentAccountTaxonomyConflictDetails {
      accountCode = requireText(accountCode, "accountCode");
      requestedAccountTaxonomy = requireValue(requestedAccountTaxonomy, "requestedAccountTaxonomy");
      parentAccountCode = requireText(parentAccountCode, "parentAccountCode");
      parentAccountTaxonomy = requireValue(parentAccountTaxonomy, "parentAccountTaxonomy");
    }
  }

  record AccountTaxonomyDetails(
      @Nullable String parentAccountCode,
      @Nullable String financialPositionLineClassification,
      @Nullable String profitAndLossLineClassification) {
    public AccountTaxonomyDetails {
      parentAccountCode = requireOptionalText(parentAccountCode, "parentAccountCode");
      financialPositionLineClassification =
          requireOptionalText(
              financialPositionLineClassification, "financialPositionLineClassification");
      profitAndLossLineClassification =
          requireOptionalText(profitAndLossLineClassification, "profitAndLossLineClassification");
    }
  }

  record PostingKindDetails(String postingKind) implements PostingRejectionDetails {
    public PostingKindDetails {
      postingKind = requireText(postingKind, "postingKind");
    }
  }

  record FunctionalCurrencyMismatchDetails(String functionalCurrency, String attemptedCurrency)
      implements PostingRejectionDetails {
    public FunctionalCurrencyMismatchDetails {
      functionalCurrency = requireText(functionalCurrency, "functionalCurrency");
      attemptedCurrency = requireText(attemptedCurrency, "attemptedCurrency");
    }
  }

  record OpeningBalanceNominalAccountDetails(String accountCode, String accountType)
      implements PostingRejectionDetails {
    public OpeningBalanceNominalAccountDetails {
      accountCode = requireText(accountCode, "accountCode");
      accountType = requireText(accountType, "accountType");
    }
  }

  record OpeningBalanceWindowClosedDetails(
      String firstBlockingPostingKind, String firstBlockingEffectiveDate)
      implements PostingRejectionDetails {
    public OpeningBalanceWindowClosedDetails {
      firstBlockingPostingKind = requireText(firstBlockingPostingKind, "firstBlockingPostingKind");
      firstBlockingEffectiveDate =
          requireText(firstBlockingEffectiveDate, "firstBlockingEffectiveDate");
    }
  }

  record ClosingEquityAccountDetails(String accountCode) implements AccountRejectionDetails {
    public ClosingEquityAccountDetails {
      accountCode = requireText(accountCode, "accountCode");
    }
  }

  record ClosingEquityAccountCandidateMissingDetails(
      String requiredFinancialPositionLineClassification,
      List<String> inactiveCandidateAccountCodes)
      implements AccountRejectionDetails {
    public ClosingEquityAccountCandidateMissingDetails {
      requiredFinancialPositionLineClassification =
          requireText(
              requiredFinancialPositionLineClassification,
              "requiredFinancialPositionLineClassification");
      inactiveCandidateAccountCodes =
          copyList(inactiveCandidateAccountCodes, "inactiveCandidateAccountCodes");
    }
  }

  record ClosingEquityAccountCandidateAmbiguousDetails(
      String requiredFinancialPositionLineClassification, List<String> candidateAccountCodes)
      implements AccountRejectionDetails {
    public ClosingEquityAccountCandidateAmbiguousDetails {
      requiredFinancialPositionLineClassification =
          requireText(
              requiredFinancialPositionLineClassification,
              "requiredFinancialPositionLineClassification");
      candidateAccountCodes = copyList(candidateAccountCodes, "candidateAccountCodes");
    }
  }

  record PeriodCloseStartDetails(String requiredEffectiveDateFrom)
      implements PeriodCloseRejectionDetails {
    public PeriodCloseStartDetails {
      requiredEffectiveDateFrom =
          requireText(requiredEffectiveDateFrom, "requiredEffectiveDateFrom");
    }
  }

  record PeriodCloseFutureDateDetails(String attemptedEffectiveDateTo)
      implements PeriodCloseRejectionDetails {
    public PeriodCloseFutureDateDetails {
      attemptedEffectiveDateTo = requireText(attemptedEffectiveDateTo, "attemptedEffectiveDateTo");
    }
  }

  record PeriodCloseFiscalYearDetails(
      String attemptedEffectiveDateFrom, String attemptedEffectiveDateTo, String fiscalYearStart)
      implements PeriodCloseRejectionDetails {
    public PeriodCloseFiscalYearDetails {
      attemptedEffectiveDateFrom =
          requireText(attemptedEffectiveDateFrom, "attemptedEffectiveDateFrom");
      attemptedEffectiveDateTo = requireText(attemptedEffectiveDateTo, "attemptedEffectiveDateTo");
      fiscalYearStart = requireText(fiscalYearStart, "fiscalYearStart");
    }
  }

  record ClosedPeriodViolationDetails(
      String closedThroughEffectiveDate, String attemptedEffectiveDate)
      implements PostingRejectionDetails {
    public ClosedPeriodViolationDetails {
      closedThroughEffectiveDate =
          requireText(closedThroughEffectiveDate, "closedThroughEffectiveDate");
      attemptedEffectiveDate = requireText(attemptedEffectiveDate, "attemptedEffectiveDate");
    }
  }

  record UnknownAccountDetails(String accountCode) implements QueryOrPlanRejectionDetails {
    public UnknownAccountDetails {
      accountCode = requireText(accountCode, "accountCode");
    }
  }

  record PostingNotFoundDetails(String postingId) implements QueryOrPlanRejectionDetails {
    public PostingNotFoundDetails {
      postingId = requireText(postingId, "postingId");
    }
  }

  record BookFileDetails(String bookFile) implements MaintenanceRejectionDetails {
    public BookFileDetails {
      bookFile = requireText(bookFile, "bookFile");
    }
  }

  record BookAndBackupFileDetails(String bookFile, String backupFile)
      implements MaintenanceRejectionDetails {
    public BookAndBackupFileDetails {
      bookFile = requireText(bookFile, "bookFile");
      backupFile = requireText(backupFile, "backupFile");
    }
  }

  record BlockingArtifactsDetails(String bookFile, List<String> blockingArtifacts)
      implements MaintenanceRejectionDetails {
    public BlockingArtifactsDetails {
      bookFile = requireText(bookFile, "bookFile");
      blockingArtifacts = copyList(blockingArtifacts, "blockingArtifacts");
      if (blockingArtifacts.isEmpty()) {
        throw new IllegalArgumentException("blockingArtifacts must not be empty.");
      }
    }
  }

  record BackupFileDetails(String backupFile) implements MaintenanceRejectionDetails {
    public BackupFileDetails {
      backupFile = requireText(backupFile, "backupFile");
    }
  }

  record BackupBookKeyFileDetails(String backupBookKeyFile) implements MaintenanceRejectionDetails {
    public BackupBookKeyFileDetails {
      backupBookKeyFile = requireText(backupBookKeyFile, "backupBookKeyFile");
    }
  }

  record ArtifactBusyDetails(String artifactRole, String artifactPath)
      implements MaintenanceRejectionDetails {
    public ArtifactBusyDetails {
      artifactRole = requireText(artifactRole, "artifactRole");
      artifactPath = requireText(artifactPath, "artifactPath");
    }
  }

  record ArtifactVerificationFailureDetails(
      String artifactRole, String artifactPath, String verificationFailure)
      implements MaintenanceRejectionDetails {
    public ArtifactVerificationFailureDetails {
      artifactRole = requireText(artifactRole, "artifactRole");
      artifactPath = requireText(artifactPath, "artifactPath");
      verificationFailure = requireText(verificationFailure, "verificationFailure");
    }
  }

  record RollbackArtifactDetails(String rollbackArtifact) implements MaintenanceRejectionDetails {
    public RollbackArtifactDetails {
      rollbackArtifact = requireText(rollbackArtifact, "rollbackArtifact");
    }
  }

  record RollbackArtifactMismatchDetails(String bookFile, String rollbackArtifact)
      implements MaintenanceRejectionDetails {
    public RollbackArtifactMismatchDetails {
      bookFile = requireText(bookFile, "bookFile");
      rollbackArtifact = requireText(rollbackArtifact, "rollbackArtifact");
    }
  }

  record RollbackArtifactSelectionDetails(String bookFile, List<String> rollbackArtifacts)
      implements MaintenanceRejectionDetails {
    public RollbackArtifactSelectionDetails {
      bookFile = requireText(bookFile, "bookFile");
      rollbackArtifacts = copyList(rollbackArtifacts, "rollbackArtifacts");
      if (rollbackArtifacts.size() < 2) {
        throw new IllegalArgumentException(
            "rollbackArtifacts must contain at least two entries when explicit selection is required.");
      }
    }
  }

  record PlanRejectionDetails(LedgerPlanPayload plan) implements QueryOrPlanRejectionDetails {
    public PlanRejectionDetails {
      plan = requireValue(plan, "plan");
    }
  }
}
