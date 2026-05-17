package dev.erst.fingrind.cli.json;

import static dev.erst.fingrind.cli.json.CliJsonModelValidation.requireNonNegative;
import static dev.erst.fingrind.cli.json.CliJsonModelValidation.requirePositive;
import static dev.erst.fingrind.cli.json.CliJsonModelValidation.requireText;

import org.jspecify.annotations.Nullable;

/** Administration and inspection JSON records emitted by the CLI transport layer. */
public interface CliAdministrationJsonModels {

  record TaxRegistrationPayload(
      String jurisdictionCode, String registrationId, String filingFrequency) {
    public TaxRegistrationPayload {
      jurisdictionCode = requireText(jurisdictionCode, "jurisdictionCode");
      registrationId = requireText(registrationId, "registrationId");
      filingFrequency = requireText(filingFrequency, "filingFrequency");
    }
  }

  record TaxCodeDefinitionPayload(
      String taxCode,
      String displayName,
      String jurisdictionCode,
      int rateBasisPoints,
      String pricingMode,
      String recoverability,
      String liabilityAccountCode,
      @Nullable String receivableAccountCode) {
    public TaxCodeDefinitionPayload {
      taxCode = requireText(taxCode, "taxCode");
      displayName = requireText(displayName, "displayName");
      jurisdictionCode = requireText(jurisdictionCode, "jurisdictionCode");
      requireNonNegative(rateBasisPoints, "rateBasisPoints");
      pricingMode = requireText(pricingMode, "pricingMode");
      recoverability = requireText(recoverability, "recoverability");
      liabilityAccountCode = requireText(liabilityAccountCode, "liabilityAccountCode");
      if (receivableAccountCode != null) {
        receivableAccountCode = requireText(receivableAccountCode, "receivableAccountCode");
      }
    }
  }

  record TaxProfilePayload(
      java.util.List<TaxRegistrationPayload> registrations,
      java.util.List<TaxCodeDefinitionPayload> taxCodeDefinitions) {
    public TaxProfilePayload {
      registrations = CliJsonModelValidation.copyList(registrations, "registrations");
      taxCodeDefinitions =
          CliJsonModelValidation.copyList(taxCodeDefinitions, "taxCodeDefinitions");
    }
  }

  record BookIdentityPayload(
      String entityName,
      String entityForm,
      String ownerModel,
      String reportingObligationStatus,
      String taxRegistrationStatus,
      TaxProfilePayload taxProfile,
      java.util.List<String> businessActivityTags,
      String functionalCurrency,
      String fiscalYearStart,
      String accountingBasis) {
    public BookIdentityPayload {
      entityName = requireText(entityName, "entityName");
      entityForm = requireText(entityForm, "entityForm");
      ownerModel = requireText(ownerModel, "ownerModel");
      reportingObligationStatus =
          requireText(reportingObligationStatus, "reportingObligationStatus");
      taxRegistrationStatus = requireText(taxRegistrationStatus, "taxRegistrationStatus");
      java.util.Objects.requireNonNull(taxProfile, "taxProfile");
      businessActivityTags =
          CliJsonModelValidation.copyList(businessActivityTags, "businessActivityTags");
      functionalCurrency = requireText(functionalCurrency, "functionalCurrency");
      fiscalYearStart = requireText(fiscalYearStart, "fiscalYearStart");
      accountingBasis = requireText(accountingBasis, "accountingBasis");
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

  record ClosedPeriodPayload(
      int closeOrder,
      String effectiveDateFrom,
      String effectiveDateTo,
      String closingEquityAccountCode,
      java.util.List<CliBookQueryJsonModels.BalanceBucketPayload> closedTotals,
      String closedAt,
      java.util.List<String> closingPostingIds)
      implements CliSuccessPayload {
    public ClosedPeriodPayload {
      requirePositive(closeOrder, "closeOrder");
      effectiveDateFrom = requireText(effectiveDateFrom, "effectiveDateFrom");
      effectiveDateTo = requireText(effectiveDateTo, "effectiveDateTo");
      closingEquityAccountCode = requireText(closingEquityAccountCode, "closingEquityAccountCode");
      closedTotals = CliJsonModelValidation.copyList(closedTotals, "closedTotals");
      closedAt = requireText(closedAt, "closedAt");
      closingPostingIds = CliJsonModelValidation.copyList(closingPostingIds, "closingPostingIds");
    }
  }

  record MissingBookInspectionPayload(
      String bookFile,
      String state,
      boolean compatibleWithCurrentBinary,
      boolean canInitializeWithOpenBook,
      int supportedBookFormatVersion)
      implements CliSuccessPayload {
    public MissingBookInspectionPayload {
      bookFile = requireText(bookFile, "bookFile");
      state = requireText(state, "state");
      requirePositive(supportedBookFormatVersion, "supportedBookFormatVersion");
    }
  }

  record ExistingBookInspectionPayload(
      String bookFile,
      String state,
      boolean compatibleWithCurrentBinary,
      boolean canInitializeWithOpenBook,
      int applicationId,
      int detectedBookFormatVersion,
      int supportedBookFormatVersion)
      implements CliSuccessPayload {
    public ExistingBookInspectionPayload {
      bookFile = requireText(bookFile, "bookFile");
      state = requireText(state, "state");
      requireNonNegative(detectedBookFormatVersion, "detectedBookFormatVersion");
      requirePositive(supportedBookFormatVersion, "supportedBookFormatVersion");
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
      String initializedAt,
      BookIdentityPayload bookIdentity)
      implements CliSuccessPayload {
    public InitializedBookInspectionPayload {
      bookFile = requireText(bookFile, "bookFile");
      state = requireText(state, "state");
      requireNonNegative(detectedBookFormatVersion, "detectedBookFormatVersion");
      requirePositive(supportedBookFormatVersion, "supportedBookFormatVersion");
      initializedAt = requireText(initializedAt, "initializedAt");
      java.util.Objects.requireNonNull(bookIdentity, "bookIdentity");
    }
  }
}
