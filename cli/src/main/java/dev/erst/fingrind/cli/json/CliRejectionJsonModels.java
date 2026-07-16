package dev.erst.fingrind.cli.json;

import static dev.erst.fingrind.cli.json.CliJsonModelValidation.copyList;
import static dev.erst.fingrind.cli.json.CliJsonModelValidation.requireOptionalText;
import static dev.erst.fingrind.cli.json.CliJsonModelValidation.requireText;
import static dev.erst.fingrind.cli.json.CliJsonModelValidation.requireValue;

import java.util.List;
import org.jspecify.annotations.Nullable;

/** Rejection-detail JSON records emitted by the CLI transport layer. */
public interface CliRejectionJsonModels extends CliPlanJsonModels, CliTaxRejectionJsonModels {

  /** Sealed marker for machine-readable CLI rejection detail payloads. */
  sealed interface RejectionDetails extends CliEnvelopeJsonModels.EnvelopeDetails
      permits PostingRejectionDetails,
          AccountRejectionDetails,
          CloseWindowRejectionDetails,
          QueryOrPlanRejectionDetails,
          CliTaxRejectionJsonModels.TaxRejectionDetails,
          MaintenanceRejectionDetails {}

  /** Sealed category for posting lifecycle rejection payloads. */
  sealed interface PostingRejectionDetails extends RejectionDetails
      permits AccountStateViolationsDetails,
          EntrySemanticsViolationsDetails,
          PriorPostingDetails,
          PostingEffectiveDateInFutureDetails,
          FunctionalCurrencyMismatchDetails,
          OpeningPositionWindowClosedDetails,
          OpeningPositionNominalAccountDetails,
          SweptInterimResultViolationDetails {}

  /** Sealed category for account-registry rejection payloads. */
  sealed interface AccountRejectionDetails extends RejectionDetails
      permits AccountTypeConflictDetails,
          AccountTaxonomyConflictDetails,
          AccountCodeDetails,
          AccountDependenciesDetails,
          ParentAccountDetails,
          ParentAccountTypeConflictDetails,
          ParentAccountNodeKindDetails,
          ParentAccountTaxonomyConflictDetails,
          ReservedResultClassificationDetails,
          CloseTargetAccountCandidateMissingDetails,
          CloseTargetAccountCandidateAmbiguousDetails {}

  /** Sealed category for close-window rejection payloads. */
  sealed interface CloseWindowRejectionDetails extends RejectionDetails
      permits InterimResultSweepStartDetails,
          InterimResultSweepFutureDateDetails,
          InterimResultSweepFiscalYearDetails,
          FiscalYearCloseStartDetails,
          FiscalYearCloseEndDetails,
          FiscalYearCloseTransferredThroughDetails,
          FiscalYearCloseFutureDateDetails {}

  /** Sealed category for query and workflow rejection payloads. */
  sealed interface QueryOrPlanRejectionDetails extends RejectionDetails
      permits UnknownAccountDetails, PostingNotFoundDetails, PlanRejectionDetails {}

  /** Sealed category for protected-book maintenance rejection payloads. */
  sealed interface MaintenanceRejectionDetails extends RejectionDetails
      permits BookFileDetails,
          BookAndBackupFileDetails,
          BlockingArtifactsDetails,
          CliArtifactPathFailureDetails,
          ArtifactBusyDetails,
          ArtifactVerificationFailureDetails,
          BackupFileDetails,
          SecretTargetDetails,
          RollbackArtifactDetails,
          RollbackArtifactMismatchDetails,
          RollbackArtifactSelectionDetails {}

  record AccountStateViolationsDetails(List<CliAccountStateViolationPayload> violations)
      implements PostingRejectionDetails {
    public AccountStateViolationsDetails {
      violations = copyList(violations, "violations");
      if (violations.isEmpty()) {
        throw new IllegalArgumentException("violations must not be empty.");
      }
    }
  }

  record EntrySemanticsViolationsDetails(List<CliEntrySemanticsViolationPayload> violations)
      implements PostingRejectionDetails {
    public EntrySemanticsViolationsDetails {
      violations = copyList(violations, "violations");
      if (violations.isEmpty()) {
        throw new IllegalArgumentException("violations must not be empty.");
      }
    }
  }

  record PriorPostingDetails(String priorPostingId) implements PostingRejectionDetails {
    public PriorPostingDetails {
      priorPostingId = requireText(priorPostingId, "priorPostingId");
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

  record AccountCodeDetails(String accountCode) implements AccountRejectionDetails {
    public AccountCodeDetails {
      accountCode = requireText(accountCode, "accountCode");
    }
  }

  record AccountDependenciesDetails(String accountCode, List<String> dependencies)
      implements AccountRejectionDetails {
    public AccountDependenciesDetails {
      accountCode = requireText(accountCode, "accountCode");
      dependencies = copyList(dependencies, "dependencies");
      if (dependencies.isEmpty()) {
        throw new IllegalArgumentException("dependencies must not be empty.");
      }
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

  record ParentAccountNodeKindDetails(
      String accountCode, String parentAccountCode, String parentAccountNodeKind)
      implements AccountRejectionDetails {
    public ParentAccountNodeKindDetails {
      accountCode = requireText(accountCode, "accountCode");
      parentAccountCode = requireText(parentAccountCode, "parentAccountCode");
      parentAccountNodeKind = requireText(parentAccountNodeKind, "parentAccountNodeKind");
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
      String accountNodeKind,
      @Nullable String parentAccountCode,
      @Nullable String financialPositionLineClassification,
      @Nullable String profitAndLossLineClassification) {
    public AccountTaxonomyDetails {
      accountNodeKind = requireText(accountNodeKind, "accountNodeKind");
      parentAccountCode = requireOptionalText(parentAccountCode, "parentAccountCode");
      financialPositionLineClassification =
          requireOptionalText(
              financialPositionLineClassification, "financialPositionLineClassification");
      profitAndLossLineClassification =
          requireOptionalText(profitAndLossLineClassification, "profitAndLossLineClassification");
    }
  }

  record FunctionalCurrencyMismatchDetails(String functionalCurrency, String attemptedCurrency)
      implements PostingRejectionDetails {
    public FunctionalCurrencyMismatchDetails {
      functionalCurrency = requireText(functionalCurrency, "functionalCurrency");
      attemptedCurrency = requireText(attemptedCurrency, "attemptedCurrency");
    }
  }

  record OpeningPositionNominalAccountDetails(String accountCode, String accountType)
      implements PostingRejectionDetails {
    public OpeningPositionNominalAccountDetails {
      accountCode = requireText(accountCode, "accountCode");
      accountType = requireText(accountType, "accountType");
    }
  }

  record OpeningPositionWindowClosedDetails(
      String firstBlockingPostingKind, String firstBlockingEffectiveDate)
      implements PostingRejectionDetails {
    public OpeningPositionWindowClosedDetails {
      firstBlockingPostingKind = requireText(firstBlockingPostingKind, "firstBlockingPostingKind");
      firstBlockingEffectiveDate =
          requireText(firstBlockingEffectiveDate, "firstBlockingEffectiveDate");
    }
  }

  record ReservedResultClassificationDetails(
      String accountCode, String financialPositionLineClassification)
      implements AccountRejectionDetails {
    public ReservedResultClassificationDetails {
      accountCode = requireText(accountCode, "accountCode");
      financialPositionLineClassification =
          requireText(financialPositionLineClassification, "financialPositionLineClassification");
    }
  }

  record CloseTargetAccountCandidateMissingDetails(
      String requiredFinancialPositionLineClassification,
      List<String> inactiveCandidateAccountCodes)
      implements AccountRejectionDetails {
    public CloseTargetAccountCandidateMissingDetails {
      requiredFinancialPositionLineClassification =
          requireText(
              requiredFinancialPositionLineClassification,
              "requiredFinancialPositionLineClassification");
      inactiveCandidateAccountCodes =
          copyList(inactiveCandidateAccountCodes, "inactiveCandidateAccountCodes");
    }
  }

  record CloseTargetAccountCandidateAmbiguousDetails(
      String requiredFinancialPositionLineClassification, List<String> candidateAccountCodes)
      implements AccountRejectionDetails {
    public CloseTargetAccountCandidateAmbiguousDetails {
      requiredFinancialPositionLineClassification =
          requireText(
              requiredFinancialPositionLineClassification,
              "requiredFinancialPositionLineClassification");
      candidateAccountCodes = copyList(candidateAccountCodes, "candidateAccountCodes");
    }
  }

  record InterimResultSweepStartDetails(String requiredEffectiveDateFrom)
      implements CloseWindowRejectionDetails {
    public InterimResultSweepStartDetails {
      requiredEffectiveDateFrom =
          requireText(requiredEffectiveDateFrom, "requiredEffectiveDateFrom");
    }
  }

  record InterimResultSweepFutureDateDetails(String attemptedEffectiveDateTo)
      implements CloseWindowRejectionDetails {
    public InterimResultSweepFutureDateDetails {
      attemptedEffectiveDateTo = requireText(attemptedEffectiveDateTo, "attemptedEffectiveDateTo");
    }
  }

  record InterimResultSweepFiscalYearDetails(
      String attemptedEffectiveDateFrom, String attemptedEffectiveDateTo, String fiscalYearStart)
      implements CloseWindowRejectionDetails {
    public InterimResultSweepFiscalYearDetails {
      attemptedEffectiveDateFrom =
          requireText(attemptedEffectiveDateFrom, "attemptedEffectiveDateFrom");
      attemptedEffectiveDateTo = requireText(attemptedEffectiveDateTo, "attemptedEffectiveDateTo");
      fiscalYearStart = requireText(fiscalYearStart, "fiscalYearStart");
    }
  }

  record FiscalYearCloseStartDetails(String requiredEffectiveDateFrom)
      implements CloseWindowRejectionDetails {
    public FiscalYearCloseStartDetails {
      requiredEffectiveDateFrom =
          requireText(requiredEffectiveDateFrom, "requiredEffectiveDateFrom");
    }
  }

  record FiscalYearCloseEndDetails(String requiredEffectiveDateTo)
      implements CloseWindowRejectionDetails {
    public FiscalYearCloseEndDetails {
      requiredEffectiveDateTo = requireText(requiredEffectiveDateTo, "requiredEffectiveDateTo");
    }
  }

  record FiscalYearCloseTransferredThroughDetails(
      String attemptedEffectiveDateTo, String transferredThroughEffectiveDate)
      implements CloseWindowRejectionDetails {
    public FiscalYearCloseTransferredThroughDetails {
      attemptedEffectiveDateTo = requireText(attemptedEffectiveDateTo, "attemptedEffectiveDateTo");
      transferredThroughEffectiveDate =
          requireText(transferredThroughEffectiveDate, "transferredThroughEffectiveDate");
    }
  }

  record FiscalYearCloseFutureDateDetails(String attemptedEffectiveDateTo)
      implements CloseWindowRejectionDetails {
    public FiscalYearCloseFutureDateDetails {
      attemptedEffectiveDateTo = requireText(attemptedEffectiveDateTo, "attemptedEffectiveDateTo");
    }
  }

  record SweptInterimResultViolationDetails(
      String transferredThroughEffectiveDate, String attemptedEffectiveDate)
      implements PostingRejectionDetails {
    public SweptInterimResultViolationDetails {
      transferredThroughEffectiveDate =
          requireText(transferredThroughEffectiveDate, "transferredThroughEffectiveDate");
      attemptedEffectiveDate = requireText(attemptedEffectiveDate, "attemptedEffectiveDate");
    }
  }

  record PostingEffectiveDateInFutureDetails(String attemptedEffectiveDate, String currentUtcDate)
      implements PostingRejectionDetails {
    public PostingEffectiveDateInFutureDetails {
      attemptedEffectiveDate = requireText(attemptedEffectiveDate, "attemptedEffectiveDate");
      currentUtcDate = requireText(currentUtcDate, "currentUtcDate");
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

  record SecretTargetDetails(String secretTarget) implements MaintenanceRejectionDetails {
    public SecretTargetDetails {
      secretTarget = requireText(secretTarget, "secretTarget");
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
