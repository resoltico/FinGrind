package dev.erst.fingrind.contract.tax;

import dev.erst.fingrind.contract.internal.ContractDescriptorValidation;
import dev.erst.fingrind.core.AccountCode;
import java.time.Instant;
import java.util.List;
import org.jspecify.annotations.Nullable;

/** Current declared snapshot for one owned tax registration. */
public record DeclaredTaxRegistration(
    TaxRegistrationId taxRegistrationId,
    TaxRegistrationName taxRegistrationName,
    TaxJurisdiction jurisdiction,
    @Nullable TaxRegistrationNumber registrationNumber,
    AccountCode payableAccountCode,
    AccountCode recoverableAccountCode,
    TaxObligationFrequency obligationFrequency,
    int dueDaysAfterPeriodEnd,
    List<TaxCodeDefinition> taxCodes,
    Instant declaredAt) {
  /** Validates one declared tax-registration snapshot. */
  public DeclaredTaxRegistration {
    taxRegistrationId =
        ContractDescriptorValidation.requireValue(taxRegistrationId, "taxRegistrationId");
    taxRegistrationName =
        ContractDescriptorValidation.requireValue(taxRegistrationName, "taxRegistrationName");
    jurisdiction = ContractDescriptorValidation.requireValue(jurisdiction, "jurisdiction");
    payableAccountCode =
        ContractDescriptorValidation.requireValue(payableAccountCode, "payableAccountCode");
    recoverableAccountCode =
        ContractDescriptorValidation.requireValue(recoverableAccountCode, "recoverableAccountCode");
    obligationFrequency =
        ContractDescriptorValidation.requireValue(obligationFrequency, "obligationFrequency");
    taxCodes = ContractDescriptorValidation.copyList(taxCodes, "taxCodes");
    declaredAt = ContractDescriptorValidation.requireValue(declaredAt, "declaredAt");
    if (dueDaysAfterPeriodEnd < 0 || dueDaysAfterPeriodEnd > 366) {
      throw new IllegalArgumentException("dueDaysAfterPeriodEnd must be between 0 and 366.");
    }
    if (taxCodes.isEmpty()) {
      throw new IllegalArgumentException("taxCodes must contain at least one declared tax code.");
    }
  }
}
