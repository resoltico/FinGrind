package dev.erst.fingrind.contract.discovery;

import dev.erst.fingrind.contract.discovery.ContractFinancingTemplates.FinancingTemplateDescriptor;
import dev.erst.fingrind.contract.discovery.ContractPostingRequestTemplateFieldSupport.TemplateTextField;
import dev.erst.fingrind.contract.discovery.ContractPostingRequestTemplateValidators.PostingTemplateFields;
import dev.erst.fingrind.contract.discovery.ContractReversalTemplates.ReversalTemplateDescriptor;
import dev.erst.fingrind.core.BookkeepingEntryKind;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** Validates discovery templates owned by the Financing context. */
final class ContractFinancingPostingRequestTemplateValidators {
  private ContractFinancingPostingRequestTemplateValidators() {}

  static Map<
          BookkeepingEntryKind, ContractPostingRequestTemplateValidators.PostingTemplateValidator>
      validators() {
    return Map.of(
        BookkeepingEntryKind.FINANCING_BORROWING,
        ContractFinancingPostingRequestTemplateValidators::validateBorrowing,
        BookkeepingEntryKind.FINANCING_PRINCIPAL_REPAYMENT,
        ContractFinancingPostingRequestTemplateValidators::validatePrincipalRepayment,
        BookkeepingEntryKind.FINANCING_INTEREST_ACCRUAL,
        ContractFinancingPostingRequestTemplateValidators::validateInterestAccrual,
        BookkeepingEntryKind.FINANCING_INTEREST_PAYMENT,
        ContractFinancingPostingRequestTemplateValidators::validateInterestPayment);
  }

  private static void validateBorrowing(
      PostingTemplateFields fields, @Nullable ReversalTemplateDescriptor reversal) {
    FinancingTemplateDescriptor financing = requiredFinancing(fields, "financing borrowing");
    ContractPostingTemplateFieldRules.requirePresent(
        financing.financingArrangementId(), "financingArrangementId");
    ContractPostingTemplateFieldRules.requirePresent(
        financing.principalLiabilityAccountCode(), "principalLiabilityAccountCode");
    ContractPostingTemplateFieldRules.requirePresent(
        financing.interestPayableAccountCode(), "interestPayableAccountCode");
    ContractPostingTemplateFieldRules.requirePresent(
        financing.principalAmount(), "principalAmount");
    ContractPostingTemplateFieldRules.requireAbsent(
        financing.interestExpenseAccountCode(), "interestExpenseAccountCode");
    ContractPostingTemplateFieldRules.requireAbsent(financing.interestAmount(), "interestAmount");
    ContractPostingTemplateFieldRules.requirePresent(fields.cashAccountCode(), "cashAccountCode");
    ContractPostingTemplateNonTextFieldPolicy.requireOnlyOperationalFields(
        fields,
        List.of(TemplateTextField.CASH),
        ContractPostingTemplateNonTextFieldPolicy.ContextField.FINANCING);
    ContractPostingTemplateFieldRules.forbidReversal(reversal);
  }

  private static void validatePrincipalRepayment(
      PostingTemplateFields fields, @Nullable ReversalTemplateDescriptor reversal) {
    FinancingTemplateDescriptor financing =
        requiredFinancing(fields, "financing principal repayment");
    ContractPostingTemplateFieldRules.requirePresent(
        financing.financingArrangementId(), "financingArrangementId");
    ContractPostingTemplateFieldRules.requirePresent(
        financing.principalAmount(), "principalAmount");
    ContractPostingTemplateFieldRules.requireAbsent(
        financing.principalLiabilityAccountCode(), "principalLiabilityAccountCode");
    ContractPostingTemplateFieldRules.requireAbsent(
        financing.interestPayableAccountCode(), "interestPayableAccountCode");
    ContractPostingTemplateFieldRules.requireAbsent(
        financing.interestExpenseAccountCode(), "interestExpenseAccountCode");
    ContractPostingTemplateFieldRules.requireAbsent(financing.interestAmount(), "interestAmount");
    ContractPostingTemplateFieldRules.requirePresent(fields.cashAccountCode(), "cashAccountCode");
    ContractPostingTemplateNonTextFieldPolicy.requireOnlyOperationalFields(
        fields,
        List.of(TemplateTextField.CASH),
        ContractPostingTemplateNonTextFieldPolicy.ContextField.FINANCING);
    ContractPostingTemplateFieldRules.forbidReversal(reversal);
  }

  private static void validateInterestAccrual(
      PostingTemplateFields fields, @Nullable ReversalTemplateDescriptor reversal) {
    FinancingTemplateDescriptor financing = requiredFinancing(fields, "financing interest accrual");
    ContractPostingTemplateFieldRules.requirePresent(
        financing.financingArrangementId(), "financingArrangementId");
    ContractPostingTemplateFieldRules.requirePresent(
        financing.interestExpenseAccountCode(), "interestExpenseAccountCode");
    ContractPostingTemplateFieldRules.requirePresent(financing.interestAmount(), "interestAmount");
    ContractPostingTemplateFieldRules.requireAbsent(
        financing.principalLiabilityAccountCode(), "principalLiabilityAccountCode");
    ContractPostingTemplateFieldRules.requireAbsent(
        financing.interestPayableAccountCode(), "interestPayableAccountCode");
    ContractPostingTemplateFieldRules.requireAbsent(financing.principalAmount(), "principalAmount");
    ContractPostingTemplateFieldRules.requireAbsent(fields.cashAccountCode(), "cashAccountCode");
    ContractPostingTemplateNonTextFieldPolicy.requireOnlyOperationalFields(
        fields, List.of(), ContractPostingTemplateNonTextFieldPolicy.ContextField.FINANCING);
    ContractPostingTemplateFieldRules.forbidReversal(reversal);
  }

  private static void validateInterestPayment(
      PostingTemplateFields fields, @Nullable ReversalTemplateDescriptor reversal) {
    FinancingTemplateDescriptor financing = requiredFinancing(fields, "financing interest payment");
    ContractPostingTemplateFieldRules.requirePresent(
        financing.financingArrangementId(), "financingArrangementId");
    ContractPostingTemplateFieldRules.requirePresent(financing.interestAmount(), "interestAmount");
    ContractPostingTemplateFieldRules.requireAbsent(
        financing.principalLiabilityAccountCode(), "principalLiabilityAccountCode");
    ContractPostingTemplateFieldRules.requireAbsent(
        financing.interestPayableAccountCode(), "interestPayableAccountCode");
    ContractPostingTemplateFieldRules.requireAbsent(
        financing.interestExpenseAccountCode(), "interestExpenseAccountCode");
    ContractPostingTemplateFieldRules.requireAbsent(financing.principalAmount(), "principalAmount");
    ContractPostingTemplateFieldRules.requirePresent(fields.cashAccountCode(), "cashAccountCode");
    ContractPostingTemplateNonTextFieldPolicy.requireOnlyOperationalFields(
        fields,
        List.of(TemplateTextField.CASH),
        ContractPostingTemplateNonTextFieldPolicy.ContextField.FINANCING);
    ContractPostingTemplateFieldRules.forbidReversal(reversal);
  }

  private static FinancingTemplateDescriptor requiredFinancing(
      PostingTemplateFields fields, String owner) {
    return Objects.requireNonNull(
        fields.financing(), () -> "financing is required for " + owner + ".");
  }
}
