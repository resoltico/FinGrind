package dev.erst.fingrind.contract.discovery;

import dev.erst.fingrind.contract.discovery.ContractPostingRequestTemplateFieldSupport.TemplateTextField;
import dev.erst.fingrind.contract.discovery.ContractPostingRequestTemplateValidators.PostingTemplateFields;
import dev.erst.fingrind.contract.discovery.ContractRealizedForeignExchangeTemplates.RealizedForeignExchangeTemplateDescriptor;
import dev.erst.fingrind.contract.discovery.ContractReversalTemplates.ReversalTemplateDescriptor;
import dev.erst.fingrind.core.BookkeepingEntryKind;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** Validates discovery templates owned by the Realized Foreign Exchange context. */
final class ContractRealizedForeignExchangePostingRequestTemplateValidators {
  private ContractRealizedForeignExchangePostingRequestTemplateValidators() {}

  static Map<
          BookkeepingEntryKind, ContractPostingRequestTemplateValidators.PostingTemplateValidator>
      validators() {
    return Map.of(
        BookkeepingEntryKind.FOREIGN_CURRENCY_OBLIGATION,
        ContractRealizedForeignExchangePostingRequestTemplateValidators::validateObligation,
        BookkeepingEntryKind.REALIZED_FOREIGN_EXCHANGE_SETTLEMENT,
        ContractRealizedForeignExchangePostingRequestTemplateValidators::validateSettlement);
  }

  private static void validateObligation(
      PostingTemplateFields fields, @Nullable ReversalTemplateDescriptor reversal) {
    RealizedForeignExchangeTemplateDescriptor context =
        requiredContext(fields, "foreign-currency obligation");
    ContractPostingTemplateFieldRules.requirePresent(
        context.foreignCurrencyObligationId(), "foreignCurrencyObligationId");
    ContractPostingTemplateFieldRules.requirePresent(
        context.realizedGainAccountCode(), "realizedGainAccountCode");
    ContractPostingTemplateFieldRules.requirePresent(
        context.realizedLossAccountCode(), "realizedLossAccountCode");
    ContractPostingTemplateFieldRules.requirePresent(
        fields.receivableAccountCode(), "receivableAccountCode");
    ContractPostingTemplateFieldRules.requirePresent(
        fields.revenueAccountCode(), "revenueAccountCode");
    ContractPostingTemplateFieldRules.requirePresent(fields.foreignExchange(), "foreignExchange");
    ContractPostingTemplateFieldRules.requireAbsent(fields.cashAccountCode(), "cashAccountCode");
    ContractPostingTemplateNonTextFieldPolicy.requireOnlyOperationalFields(
        fields,
        List.of(TemplateTextField.RECEIVABLE, TemplateTextField.REVENUE),
        ContractPostingTemplateNonTextFieldPolicy.CoreField.FOREIGN_EXCHANGE,
        ContractPostingTemplateNonTextFieldPolicy.ContextField.REALIZED_FOREIGN_EXCHANGE);
    ContractPostingTemplateFieldRules.forbidReversal(reversal);
  }

  private static void validateSettlement(
      PostingTemplateFields fields, @Nullable ReversalTemplateDescriptor reversal) {
    RealizedForeignExchangeTemplateDescriptor context =
        requiredContext(fields, "realized foreign-exchange settlement");
    ContractPostingTemplateFieldRules.requirePresent(
        context.foreignCurrencyObligationId(), "foreignCurrencyObligationId");
    ContractPostingTemplateFieldRules.requireAbsent(
        context.realizedGainAccountCode(), "realizedGainAccountCode");
    ContractPostingTemplateFieldRules.requireAbsent(
        context.realizedLossAccountCode(), "realizedLossAccountCode");
    ContractPostingTemplateFieldRules.requirePresent(fields.cashAccountCode(), "cashAccountCode");
    ContractPostingTemplateFieldRules.requirePresent(fields.foreignExchange(), "foreignExchange");
    ContractPostingTemplateFieldRules.requireAbsent(
        fields.receivableAccountCode(), "receivableAccountCode");
    ContractPostingTemplateFieldRules.requireAbsent(
        fields.revenueAccountCode(), "revenueAccountCode");
    ContractPostingTemplateNonTextFieldPolicy.requireOnlyOperationalFields(
        fields,
        List.of(TemplateTextField.CASH),
        ContractPostingTemplateNonTextFieldPolicy.CoreField.FOREIGN_EXCHANGE,
        ContractPostingTemplateNonTextFieldPolicy.ContextField.REALIZED_FOREIGN_EXCHANGE);
    ContractPostingTemplateFieldRules.forbidReversal(reversal);
  }

  private static RealizedForeignExchangeTemplateDescriptor requiredContext(
      PostingTemplateFields fields, String owner) {
    return Objects.requireNonNull(
        fields.realizedForeignExchange(),
        () -> "realizedForeignExchange is required for " + owner + ".");
  }
}
