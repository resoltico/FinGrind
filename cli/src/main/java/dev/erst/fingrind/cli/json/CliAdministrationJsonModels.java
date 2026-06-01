package dev.erst.fingrind.cli.json;

import static dev.erst.fingrind.cli.json.CliJsonModelValidation.requireNonNegative;
import static dev.erst.fingrind.cli.json.CliJsonModelValidation.requirePositive;
import static dev.erst.fingrind.cli.json.CliJsonModelValidation.requireText;

import org.jspecify.annotations.Nullable;

/** Administration and inspection JSON records emitted by the CLI transport layer. */
public interface CliAdministrationJsonModels {
  record MigrationPolicyPayload(
      String mode,
      boolean inPlaceUpgradeSupported,
      boolean olderFormatsAccepted,
      boolean newerFormatsAccepted,
      int supportedBookFormatVersion) {
    public MigrationPolicyPayload {
      mode = requireText(mode, "mode");
      requirePositive(supportedBookFormatVersion, "supportedBookFormatVersion");
    }
  }

  record BookIdentityPayload(
      String entityName,
      String accountingKernelProfile,
      java.util.List<String> businessActivityTags,
      String functionalCurrency,
      String fiscalYearStart) {
    public BookIdentityPayload {
      entityName = requireText(entityName, "entityName");
      accountingKernelProfile = requireText(accountingKernelProfile, "accountingKernelProfile");
      businessActivityTags =
          CliJsonModelValidation.copyList(businessActivityTags, "businessActivityTags");
      functionalCurrency = requireText(functionalCurrency, "functionalCurrency");
      fiscalYearStart = requireText(fiscalYearStart, "fiscalYearStart");
    }
  }

  record ResultTransferReadinessPayload(
      boolean ready,
      String requiredFinancialPositionLineClassification,
      @Nullable String resultHoldingAccountCode,
      @Nullable String blockingCode,
      @Nullable String blockingMessage,
      java.util.List<String> candidateAccountCodes) {
    public ResultTransferReadinessPayload {
      requiredFinancialPositionLineClassification =
          requireText(
              requiredFinancialPositionLineClassification,
              "requiredFinancialPositionLineClassification");
      resultHoldingAccountCode =
          CliJsonModelValidation.requireOptionalText(
              resultHoldingAccountCode, "resultHoldingAccountCode");
      blockingCode = CliJsonModelValidation.requireOptionalText(blockingCode, "blockingCode");
      blockingMessage =
          CliJsonModelValidation.requireOptionalText(blockingMessage, "blockingMessage");
      candidateAccountCodes =
          CliJsonModelValidation.copyList(candidateAccountCodes, "candidateAccountCodes");
    }
  }

  record OpenBookPayload(String bookFile, String initializedAt, BookIdentityPayload bookIdentity)
      implements CliSuccessPayload {
    public OpenBookPayload {
      bookFile = requireText(bookFile, "bookFile");
      initializedAt = requireText(initializedAt, "initializedAt");
      java.util.Objects.requireNonNull(bookIdentity, "bookIdentity");
    }
  }

  record GeneratedBookKeyFilePayload(
      String bookKeyFile, String encoding, int entropyBits, String permissions)
      implements CliSuccessPayload {
    public GeneratedBookKeyFilePayload {
      bookKeyFile = requireText(bookKeyFile, "bookKeyFile");
      encoding = requireText(encoding, "encoding");
      requirePositive(entropyBits, "entropyBits");
      permissions = requireText(permissions, "permissions");
    }
  }

  record RekeyBookPayload(
      String bookFile, String replacementPassphraseSource, @Nullable String replacementBookKeyFile)
      implements CliSuccessPayload {
    public RekeyBookPayload {
      bookFile = requireText(bookFile, "bookFile");
      replacementPassphraseSource =
          requireText(replacementPassphraseSource, "replacementPassphraseSource");
      if (replacementBookKeyFile != null) {
        replacementBookKeyFile = requireText(replacementBookKeyFile, "replacementBookKeyFile");
      }
    }
  }

  record BackupBookPayload(String bookFile, String backupFile, String backupBookKeyFile)
      implements CliSuccessPayload {
    public BackupBookPayload {
      bookFile = requireText(bookFile, "bookFile");
      backupFile = requireText(backupFile, "backupFile");
      backupBookKeyFile = requireText(backupBookKeyFile, "backupBookKeyFile");
    }
  }

  record RestoreBookPayload(String bookFile, String backupFile, String backupBookKeyFile)
      implements CliSuccessPayload {
    public RestoreBookPayload {
      bookFile = requireText(bookFile, "bookFile");
      backupFile = requireText(backupFile, "backupFile");
      backupBookKeyFile = requireText(backupBookKeyFile, "backupBookKeyFile");
    }
  }

  record InspectRekeyRollbackPayload(String bookFile, java.util.List<String> rollbackArtifacts)
      implements CliSuccessPayload {
    public InspectRekeyRollbackPayload {
      bookFile = requireText(bookFile, "bookFile");
      rollbackArtifacts = CliJsonModelValidation.copyList(rollbackArtifacts, "rollbackArtifacts");
    }
  }

  record RestoreRekeyRollbackPayload(String bookFile, String rollbackArtifact)
      implements CliSuccessPayload {
    public RestoreRekeyRollbackPayload {
      bookFile = requireText(bookFile, "bookFile");
      rollbackArtifact = requireText(rollbackArtifact, "rollbackArtifact");
    }
  }

  record DeleteRekeyRollbackPayload(String bookFile, String rollbackArtifact)
      implements CliSuccessPayload {
    public DeleteRekeyRollbackPayload {
      bookFile = requireText(bookFile, "bookFile");
      rollbackArtifact = requireText(rollbackArtifact, "rollbackArtifact");
    }
  }

  record TransferredPeriodResultPayload(
      int transferOrder,
      String effectiveDateFrom,
      String effectiveDateTo,
      String resultHoldingAccountCode,
      java.util.List<CliBookQueryJsonModels.BalanceBucketPayload> transferredTotals,
      String transferredAt,
      java.util.List<String> transferPostingIds)
      implements CliSuccessPayload {
    public TransferredPeriodResultPayload {
      requirePositive(transferOrder, "transferOrder");
      effectiveDateFrom = requireText(effectiveDateFrom, "effectiveDateFrom");
      effectiveDateTo = requireText(effectiveDateTo, "effectiveDateTo");
      resultHoldingAccountCode = requireText(resultHoldingAccountCode, "resultHoldingAccountCode");
      transferredTotals = CliJsonModelValidation.copyList(transferredTotals, "transferredTotals");
      transferredAt = requireText(transferredAt, "transferredAt");
      transferPostingIds =
          CliJsonModelValidation.copyList(transferPostingIds, "transferPostingIds");
    }
  }

  record MissingBookInspectionPayload(
      String bookFile,
      String state,
      boolean compatibleWithCurrentBinary,
      boolean canInitializeWithOpenBook,
      int supportedBookFormatVersion,
      MigrationPolicyPayload migrationPolicy)
      implements CliSuccessPayload {
    public MissingBookInspectionPayload {
      bookFile = requireText(bookFile, "bookFile");
      state = requireText(state, "state");
      requirePositive(supportedBookFormatVersion, "supportedBookFormatVersion");
      java.util.Objects.requireNonNull(migrationPolicy, "migrationPolicy");
    }
  }

  record ExistingBookInspectionPayload(
      String bookFile,
      String state,
      boolean compatibleWithCurrentBinary,
      boolean canInitializeWithOpenBook,
      int applicationId,
      int detectedBookFormatVersion,
      int supportedBookFormatVersion,
      MigrationPolicyPayload migrationPolicy)
      implements CliSuccessPayload {
    public ExistingBookInspectionPayload {
      bookFile = requireText(bookFile, "bookFile");
      state = requireText(state, "state");
      requireNonNegative(detectedBookFormatVersion, "detectedBookFormatVersion");
      requirePositive(supportedBookFormatVersion, "supportedBookFormatVersion");
      java.util.Objects.requireNonNull(migrationPolicy, "migrationPolicy");
    }
  }

  record InitializedBookInspectionPayload(
      String bookFile,
      String state,
      boolean compatibleWithCurrentBinary,
      boolean canInitializeWithOpenBook,
      int applicationId,
      int detectedBookFormatVersion,
      int supportedBookFormatVersion,
      MigrationPolicyPayload migrationPolicy,
      String initializedAt,
      BookIdentityPayload bookIdentity,
      ResultTransferReadinessPayload resultTransferReadiness)
      implements CliSuccessPayload {
    public InitializedBookInspectionPayload {
      bookFile = requireText(bookFile, "bookFile");
      state = requireText(state, "state");
      requireNonNegative(detectedBookFormatVersion, "detectedBookFormatVersion");
      requirePositive(supportedBookFormatVersion, "supportedBookFormatVersion");
      java.util.Objects.requireNonNull(migrationPolicy, "migrationPolicy");
      initializedAt = requireText(initializedAt, "initializedAt");
      java.util.Objects.requireNonNull(bookIdentity, "bookIdentity");
      java.util.Objects.requireNonNull(resultTransferReadiness, "resultTransferReadiness");
    }
  }
}
