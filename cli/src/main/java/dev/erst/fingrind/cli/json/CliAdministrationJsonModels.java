package dev.erst.fingrind.cli.json;

import static dev.erst.fingrind.cli.json.CliJsonModelValidation.requireNonNegative;
import static dev.erst.fingrind.cli.json.CliJsonModelValidation.requireOptionalText;
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
      String accountingBasis,
      String accountingFrameworkPosition,
      String entityForm,
      String bookTemplateId,
      @Nullable String inventoryCostingDoctrine,
      String functionalCurrency,
      String fiscalYearStart,
      String bookStartEffectiveDate) {
    public BookIdentityPayload {
      entityName = requireText(entityName, "entityName");
      accountingKernelProfile = requireText(accountingKernelProfile, "accountingKernelProfile");
      accountingBasis = requireText(accountingBasis, "accountingBasis");
      accountingFrameworkPosition =
          requireText(accountingFrameworkPosition, "accountingFrameworkPosition");
      entityForm = requireText(entityForm, "entityForm");
      bookTemplateId = requireText(bookTemplateId, "bookTemplateId");
      inventoryCostingDoctrine =
          requireOptionalText(inventoryCostingDoctrine, "inventoryCostingDoctrine");
      functionalCurrency = requireText(functionalCurrency, "functionalCurrency");
      fiscalYearStart = requireText(fiscalYearStart, "fiscalYearStart");
      bookStartEffectiveDate = requireText(bookStartEffectiveDate, "bookStartEffectiveDate");
    }
  }

  record CloseReadinessPayload(
      CliCloseTargetReadinessPayload interimResultTarget,
      CliCloseTargetReadinessPayload retainedAccumulatedTarget) {
    public CloseReadinessPayload {
      java.util.Objects.requireNonNull(interimResultTarget, "interimResultTarget");
      java.util.Objects.requireNonNull(retainedAccumulatedTarget, "retainedAccumulatedTarget");
    }
  }

  record OpenBookPayload(
      String bookFile,
      String initializedAt,
      java.util.List<String> tightenedParentDirectories,
      BookIdentityPayload bookIdentity)
      implements CliSuccessPayload {
    public OpenBookPayload {
      bookFile = requireText(bookFile, "bookFile");
      initializedAt = requireText(initializedAt, "initializedAt");
      tightenedParentDirectories =
          CliJsonModelValidation.copyList(tightenedParentDirectories, "tightenedParentDirectories");
      java.util.Objects.requireNonNull(bookIdentity, "bookIdentity");
    }
  }

  record GeneratedBookKeyFilePayload(
      String encoding,
      int entropyBits,
      String permissions,
      java.util.List<String> tightenedParentDirectories)
      implements CliSuccessPayload {
    public GeneratedBookKeyFilePayload {
      encoding = requireText(encoding, "encoding");
      requirePositive(entropyBits, "entropyBits");
      permissions = requireText(permissions, "permissions");
      tightenedParentDirectories =
          CliJsonModelValidation.copyList(tightenedParentDirectories, "tightenedParentDirectories");
    }
  }

  record RekeyBookPayload(String bookFile, String newBookKeyFile) implements CliSuccessPayload {
    public RekeyBookPayload {
      bookFile = requireText(bookFile, "bookFile");
      newBookKeyFile = requireText(newBookKeyFile, "newBookKeyFile");
    }
  }

  record BackupBookPayload(String bookFile, String backupId, String acknowledgementState)
      implements CliSuccessPayload {
    public BackupBookPayload {
      bookFile = requireText(bookFile, "bookFile");
      backupId = requireText(backupId, "backupId");
      acknowledgementState = requireText(acknowledgementState, "acknowledgementState");
    }
  }

  record RestoreBookPayload(String bookFile, String bookKeyFilePath) implements CliSuccessPayload {
    public RestoreBookPayload {
      bookFile = requireText(bookFile, "bookFile");
      bookKeyFilePath = requireText(bookKeyFilePath, "bookKeyFilePath");
    }
  }

  record SweptInterimResultPayload(
      int sweepOrder,
      String effectiveDateFrom,
      String effectiveDateTo,
      String resultHoldingAccountCode,
      java.util.List<CliBookQueryJsonModels.BalanceBucketPayload> sweptTotals,
      String sweptAt,
      java.util.List<String> sweepPostingIds)
      implements CliSuccessPayload {
    public SweptInterimResultPayload {
      requirePositive(sweepOrder, "sweepOrder");
      effectiveDateFrom = requireText(effectiveDateFrom, "effectiveDateFrom");
      effectiveDateTo = requireText(effectiveDateTo, "effectiveDateTo");
      resultHoldingAccountCode = requireText(resultHoldingAccountCode, "resultHoldingAccountCode");
      sweptTotals = CliJsonModelValidation.copyList(sweptTotals, "sweptTotals");
      sweptAt = requireText(sweptAt, "sweptAt");
      sweepPostingIds = CliJsonModelValidation.copyList(sweepPostingIds, "sweepPostingIds");
    }
  }

  record ClosedFiscalYearPayload(
      int closeOrder,
      String effectiveDateFrom,
      String effectiveDateTo,
      String capitalAccountCode,
      String resultHoldingAccountCode,
      String retainedAccumulatedAccountCode,
      String closedAt,
      boolean idempotentReplay,
      java.util.List<String> closePostingIds)
      implements CliSuccessPayload {
    public ClosedFiscalYearPayload {
      requirePositive(closeOrder, "closeOrder");
      effectiveDateFrom = requireText(effectiveDateFrom, "effectiveDateFrom");
      effectiveDateTo = requireText(effectiveDateTo, "effectiveDateTo");
      capitalAccountCode = requireText(capitalAccountCode, "capitalAccountCode");
      resultHoldingAccountCode = requireText(resultHoldingAccountCode, "resultHoldingAccountCode");
      retainedAccumulatedAccountCode =
          requireText(retainedAccumulatedAccountCode, "retainedAccumulatedAccountCode");
      closedAt = requireText(closedAt, "closedAt");
      closePostingIds = CliJsonModelValidation.copyList(closePostingIds, "closePostingIds");
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
      CloseReadinessPayload closeReadiness)
      implements CliSuccessPayload {
    public InitializedBookInspectionPayload {
      bookFile = requireText(bookFile, "bookFile");
      state = requireText(state, "state");
      requireNonNegative(detectedBookFormatVersion, "detectedBookFormatVersion");
      requirePositive(supportedBookFormatVersion, "supportedBookFormatVersion");
      java.util.Objects.requireNonNull(migrationPolicy, "migrationPolicy");
      initializedAt = requireText(initializedAt, "initializedAt");
      java.util.Objects.requireNonNull(bookIdentity, "bookIdentity");
      java.util.Objects.requireNonNull(closeReadiness, "closeReadiness");
    }
  }
}
