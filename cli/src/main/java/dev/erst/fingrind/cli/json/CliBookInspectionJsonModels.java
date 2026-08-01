package dev.erst.fingrind.cli.json;

import static dev.erst.fingrind.cli.json.CliJsonModelValidation.requireNonNegative;
import static dev.erst.fingrind.cli.json.CliJsonModelValidation.requireOptionalText;
import static dev.erst.fingrind.cli.json.CliJsonModelValidation.requirePositive;
import static dev.erst.fingrind.cli.json.CliJsonModelValidation.requireText;

import org.jspecify.annotations.Nullable;

/** Book identity and compatibility-inspection JSON records emitted by the CLI transport layer. */
public interface CliBookInspectionJsonModels {
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
