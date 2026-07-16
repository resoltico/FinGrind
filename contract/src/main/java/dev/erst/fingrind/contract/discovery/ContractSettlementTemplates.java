package dev.erst.fingrind.contract.discovery;

import dev.erst.fingrind.contract.bookkeeping.MonetaryAmount;
import dev.erst.fingrind.contract.internal.ContractDescriptorValidation;
import dev.erst.fingrind.contract.tax.TaxCode;
import dev.erst.fingrind.contract.tax.TaxRegistrationId;
import dev.erst.fingrind.core.AccountCode;

/** Settlement and tax-selector request template descriptors. */
public interface ContractSettlementTemplates {
  record SettlementAdjunctTemplateDescriptor(String accountCode, MonetaryAmount amount)
      implements TemplateDescriptorType {
    public SettlementAdjunctTemplateDescriptor {
      accountCode = ContractDescriptorValidation.requireText(accountCode, "accountCode");
      new AccountCode(accountCode);
      amount = ContractDescriptorValidation.requireValue(amount, "amount");
      if (!amount.toMoney().isPositive()) {
        throw new IllegalArgumentException("amount must carry one positive minor-unit value.");
      }
    }
  }

  record TaxSelectionTemplateDescriptor(String taxRegistrationId, String taxCode)
      implements TemplateDescriptorType {
    public TaxSelectionTemplateDescriptor {
      taxRegistrationId =
          ContractDescriptorValidation.requireText(taxRegistrationId, "taxRegistrationId");
      taxCode = ContractDescriptorValidation.requireText(taxCode, "taxCode");
      ContractTemplateValidationSupport.validateLiveTextUnlessPlaceholder(
          taxRegistrationId, TaxRegistrationId::new);
      ContractTemplateValidationSupport.validateLiveTextUnlessPlaceholder(taxCode, TaxCode::new);
    }
  }
}
