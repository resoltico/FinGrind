package dev.erst.fingrind.contract.discovery;

import dev.erst.fingrind.contract.discovery.ContractTemplates.DeclareTaxCodeTemplateDescriptor;
import dev.erst.fingrind.contract.internal.ContractDescriptorValidation;
import dev.erst.fingrind.contract.tax.DeclareTaxRegistrationCommand;
import dev.erst.fingrind.contract.tax.TaxApplicationKind;
import dev.erst.fingrind.contract.tax.TaxCode;
import dev.erst.fingrind.contract.tax.TaxCodeDefinition;
import dev.erst.fingrind.contract.tax.TaxCodeName;
import dev.erst.fingrind.contract.tax.TaxInclusionMode;
import dev.erst.fingrind.contract.tax.TaxJurisdiction;
import dev.erst.fingrind.contract.tax.TaxObligationFrequency;
import dev.erst.fingrind.contract.tax.TaxRate;
import dev.erst.fingrind.contract.tax.TaxRegistrationId;
import dev.erst.fingrind.contract.tax.TaxRegistrationName;
import dev.erst.fingrind.contract.tax.TaxRegistrationNumber;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.AccountName;
import dev.erst.fingrind.core.AccountNodeKind;
import dev.erst.fingrind.core.AccountType;
import dev.erst.fingrind.core.CashFlowAssetClassification;
import dev.erst.fingrind.core.FinancialPositionLineClassification;
import dev.erst.fingrind.core.ProfitAndLossLineClassification;
import dev.erst.fingrind.core.UnitOfMeasure;
import java.util.List;
import org.jspecify.annotations.Nullable;

/** Shared validation and normalization owner for declaration-oriented template descriptors. */
final class ContractDeclarationTemplateValidationSupport {
  private ContractDeclarationTemplateValidationSupport() {}

  static DeclareAccountTemplateValues validateDeclareAccountTemplate(
      DeclareAccountTemplateValues requested) {
    String validatedAccountCode =
        ContractDescriptorValidation.requireText(requested.accountCode(), "accountCode");
    String validatedAccountName =
        ContractDescriptorValidation.requireText(requested.accountName(), "accountName");
    AccountType validatedAccountType =
        ContractDescriptorValidation.requireValue(requested.accountType(), "accountType");
    AccountNodeKind validatedAccountNodeKind =
        ContractDescriptorValidation.requireValue(requested.accountNodeKind(), "accountNodeKind");
    @Nullable String validatedParentAccountCode =
        ContractDescriptorValidation.requireOptionalText(
            requested.parentAccountCode(), "parentAccountCode");
    @Nullable String validatedContraOfAccountCode =
        ContractDescriptorValidation.requireOptionalText(
            requested.contraOfAccountCode(), "contraOfAccountCode");
    @Nullable FinancialPositionLineClassification validatedFinancialPositionLineClassification =
        ContractDescriptorValidation.requireOptionalValue(
            requested.financialPositionLineClassification(), "financialPositionLineClassification");
    @Nullable ProfitAndLossLineClassification validatedProfitAndLossLineClassification =
        ContractDescriptorValidation.requireOptionalValue(
            requested.profitAndLossLineClassification(), "profitAndLossLineClassification");
    @Nullable CashFlowAssetClassification validatedCashFlowAssetClassification =
        ContractDescriptorValidation.requireOptionalValue(
            requested.cashFlowAssetClassification(), "cashFlowAssetClassification");
    @Nullable UnitOfMeasure validatedUnitOfMeasure =
        ContractDescriptorValidation.requireOptionalValue(
            requested.unitOfMeasure(), "unitOfMeasure");

    new AccountCode(validatedAccountCode);
    new AccountName(validatedAccountName);
    if (validatedParentAccountCode != null) {
      new AccountCode(validatedParentAccountCode);
    }
    if (validatedContraOfAccountCode != null) {
      new AccountCode(validatedContraOfAccountCode);
    }
    if (validatedUnitOfMeasure != null) {
      new UnitOfMeasure(validatedUnitOfMeasure.token(), validatedUnitOfMeasure.quantityScale());
    }
    return new DeclareAccountTemplateValues(
        validatedAccountCode,
        validatedAccountName,
        validatedAccountType,
        validatedAccountNodeKind,
        validatedParentAccountCode,
        validatedContraOfAccountCode,
        validatedFinancialPositionLineClassification,
        validatedProfitAndLossLineClassification,
        validatedCashFlowAssetClassification,
        validatedUnitOfMeasure);
  }

  static DeclareTaxRegistrationTemplateValues validateDeclareTaxRegistrationTemplate(
      String taxRegistrationId,
      String taxRegistrationName,
      TaxJurisdiction jurisdiction,
      @Nullable String registrationNumber,
      String payableAccountCode,
      String recoverableAccountCode,
      TaxObligationFrequency obligationFrequency,
      int dueDaysAfterPeriodEnd,
      List<DeclareTaxCodeTemplateDescriptor> taxCodes) {
    String validatedTaxRegistrationId =
        ContractDescriptorValidation.requireText(taxRegistrationId, "taxRegistrationId");
    String validatedTaxRegistrationName =
        ContractDescriptorValidation.requireText(taxRegistrationName, "taxRegistrationName");
    TaxJurisdiction validatedJurisdiction =
        ContractDescriptorValidation.requireValue(jurisdiction, "jurisdiction");
    @Nullable String validatedRegistrationNumber =
        ContractDescriptorValidation.requireOptionalText(registrationNumber, "registrationNumber");
    String validatedPayableAccountCode =
        ContractDescriptorValidation.requireText(payableAccountCode, "payableAccountCode");
    String validatedRecoverableAccountCode =
        ContractDescriptorValidation.requireText(recoverableAccountCode, "recoverableAccountCode");
    TaxObligationFrequency validatedObligationFrequency =
        ContractDescriptorValidation.requireValue(obligationFrequency, "obligationFrequency");
    List<DeclareTaxCodeTemplateDescriptor> validatedTaxCodes =
        ContractDescriptorValidation.copyList(taxCodes, "taxCodes");

    new DeclareTaxRegistrationCommand(
        new TaxRegistrationId(validatedTaxRegistrationId),
        new TaxRegistrationName(validatedTaxRegistrationName),
        validatedJurisdiction,
        validatedRegistrationNumber == null
            ? null
            : new TaxRegistrationNumber(validatedRegistrationNumber),
        new AccountCode(validatedPayableAccountCode),
        new AccountCode(validatedRecoverableAccountCode),
        validatedObligationFrequency,
        dueDaysAfterPeriodEnd,
        validatedTaxCodes.stream()
            .map(ContractDeclarationTemplateValidationSupport::toTaxCodeDefinition)
            .toList());
    return new DeclareTaxRegistrationTemplateValues(
        validatedTaxRegistrationId,
        validatedTaxRegistrationName,
        validatedJurisdiction,
        validatedRegistrationNumber,
        validatedPayableAccountCode,
        validatedRecoverableAccountCode,
        validatedObligationFrequency,
        dueDaysAfterPeriodEnd,
        validatedTaxCodes);
  }

  static String validateRetireAccountTemplate(String accountCode) {
    String validatedAccountCode =
        ContractDescriptorValidation.requireText(accountCode, "accountCode");
    new AccountCode(validatedAccountCode);
    return validatedAccountCode;
  }

  static DeclareTaxCodeTemplateValues validateDeclareTaxCodeTemplate(
      String taxCode,
      String taxCodeName,
      int ratePartsPerMillion,
      TaxInclusionMode inclusionMode,
      TaxApplicationKind applicationKind) {
    String validatedTaxCode = ContractDescriptorValidation.requireText(taxCode, "taxCode");
    String validatedTaxCodeName =
        ContractDescriptorValidation.requireText(taxCodeName, "taxCodeName");
    TaxInclusionMode validatedInclusionMode =
        ContractDescriptorValidation.requireValue(inclusionMode, "inclusionMode");
    TaxApplicationKind validatedApplicationKind =
        ContractDescriptorValidation.requireValue(applicationKind, "applicationKind");

    new TaxCodeDefinition(
        new TaxCode(validatedTaxCode),
        new TaxCodeName(validatedTaxCodeName),
        new TaxRate(ratePartsPerMillion),
        validatedInclusionMode,
        validatedApplicationKind);
    return new DeclareTaxCodeTemplateValues(
        validatedTaxCode,
        validatedTaxCodeName,
        ratePartsPerMillion,
        validatedInclusionMode,
        validatedApplicationKind);
  }

  private static TaxCodeDefinition toTaxCodeDefinition(
      DeclareTaxCodeTemplateDescriptor taxCodeTemplate) {
    return new TaxCodeDefinition(
        new TaxCode(taxCodeTemplate.taxCode()),
        new TaxCodeName(taxCodeTemplate.taxCodeName()),
        new TaxRate(taxCodeTemplate.ratePartsPerMillion()),
        taxCodeTemplate.inclusionMode(),
        taxCodeTemplate.applicationKind());
  }

  /** Validated values for one declare-account template descriptor. */
  record DeclareAccountTemplateValues(
      String accountCode,
      String accountName,
      AccountType accountType,
      AccountNodeKind accountNodeKind,
      @Nullable String parentAccountCode,
      @Nullable String contraOfAccountCode,
      @Nullable FinancialPositionLineClassification financialPositionLineClassification,
      @Nullable ProfitAndLossLineClassification profitAndLossLineClassification,
      @Nullable CashFlowAssetClassification cashFlowAssetClassification,
      @Nullable UnitOfMeasure unitOfMeasure) {}

  /** Validated values for one declare-tax-registration template descriptor. */
  record DeclareTaxRegistrationTemplateValues(
      String taxRegistrationId,
      String taxRegistrationName,
      TaxJurisdiction jurisdiction,
      @Nullable String registrationNumber,
      String payableAccountCode,
      String recoverableAccountCode,
      TaxObligationFrequency obligationFrequency,
      int dueDaysAfterPeriodEnd,
      List<DeclareTaxCodeTemplateDescriptor> taxCodes) {}

  /** Validated values for one declare-tax-code template descriptor. */
  record DeclareTaxCodeTemplateValues(
      String taxCode,
      String taxCodeName,
      int ratePartsPerMillion,
      TaxInclusionMode inclusionMode,
      TaxApplicationKind applicationKind) {}
}
