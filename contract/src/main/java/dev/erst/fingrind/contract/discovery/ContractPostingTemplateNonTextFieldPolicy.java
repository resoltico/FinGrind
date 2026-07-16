package dev.erst.fingrind.contract.discovery;

import dev.erst.fingrind.contract.discovery.ContractPostingRequestTemplateFieldSupport.TemplateTextField;
import dev.erst.fingrind.contract.discovery.ContractPostingRequestTemplateValidators.PostingTemplateFields;
import java.util.List;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/** Owns absence checks for non-text posting-template fields across typed contexts. */
final class ContractPostingTemplateNonTextFieldPolicy {
  private ContractPostingTemplateNonTextFieldPolicy() {}

  static void requireOnlyOperationalFields(
      PostingTemplateFields fields,
      List<TemplateTextField> admittedTextFields,
      NonTextField... admittedNonTextFields) {
    ContractPostingRequestTemplateFieldSupport.forbidTextFieldsExcept(fields, admittedTextFields);
    Set<NonTextField> admitted = Set.of(admittedNonTextFields);
    for (NonTextField field : NonTextField.all()) {
      if (!admitted.contains(field)) {
        ContractPostingTemplateFieldRules.requireAbsent(field.value(fields), field.fieldName());
      }
    }
  }

  /** Describes one non-text field that a posting-template variant may admit. */
  sealed interface NonTextField permits CoreField, ContextField {
    /** Returns the request-field name used in deterministic validation messages. */
    String fieldName();

    /** Returns the field value from one complete posting-template field bundle. */
    @Nullable Object value(PostingTemplateFields fields);

    /** Returns every non-text field that must be considered for an admission check. */
    static List<NonTextField> all() {
      return List.of(
          CoreField.AMOUNT,
          CoreField.QUANTITY,
          CoreField.UNIT_COST,
          CoreField.INVENTORY_RELIEF,
          CoreField.SETTLEMENT_ADJUNCT,
          CoreField.FOREIGN_EXCHANGE,
          CoreField.TAX,
          CoreField.LINES,
          CoreField.OPENING_BALANCES,
          CoreField.ACCRUAL_CUTOFF_ID,
          ContextField.PREPAYMENT_ASSET_ACCOUNT_CODE,
          ContextField.DEFERRED_REVENUE_ACCOUNT_CODE,
          ContextField.ACCRUED_EXPENSE_LIABILITY_ACCOUNT_CODE,
          ContextField.RECOGNITION_INTERVAL,
          ContextField.LATVIAN_MONTHLY_PAYROLL,
          ContextField.LATVIAN_PAYROLL_SETTLEMENT,
          ContextField.FIXED_ASSET,
          ContextField.FINANCING,
          ContextField.REALIZED_FOREIGN_EXCHANGE);
    }
  }

  /** Enumerates non-text fields shared by the core posting-request contract. */
  enum CoreField implements NonTextField {
    AMOUNT("amount"),
    QUANTITY("quantity"),
    UNIT_COST("unitCost"),
    INVENTORY_RELIEF("inventoryRelief"),
    SETTLEMENT_ADJUNCT("settlementAdjunct"),
    FOREIGN_EXCHANGE("foreignExchange"),
    TAX("tax"),
    LINES("lines"),
    OPENING_BALANCES("openingBalances"),
    ACCRUAL_CUTOFF_ID("accrualCutoffId");

    private final String fieldName;

    CoreField(String fieldName) {
      this.fieldName = fieldName;
    }

    @Override
    public String fieldName() {
      return fieldName;
    }

    @Override
    public @Nullable Object value(PostingTemplateFields fields) {
      return switch (this) {
        case AMOUNT -> fields.amount();
        case QUANTITY -> fields.quantity();
        case UNIT_COST -> fields.unitCost();
        case INVENTORY_RELIEF -> fields.inventoryRelief();
        case SETTLEMENT_ADJUNCT -> fields.settlementAdjunct();
        case FOREIGN_EXCHANGE -> fields.foreignExchange();
        case TAX -> fields.tax();
        case LINES -> fields.lines();
        case OPENING_BALANCES -> fields.openingBalances();
        case ACCRUAL_CUTOFF_ID -> fields.accrualCutoffId();
      };
    }
  }

  /** Enumerates non-text fields owned by a bounded business context. */
  enum ContextField implements NonTextField {
    PREPAYMENT_ASSET_ACCOUNT_CODE("prepaymentAssetAccountCode"),
    DEFERRED_REVENUE_ACCOUNT_CODE("deferredRevenueAccountCode"),
    ACCRUED_EXPENSE_LIABILITY_ACCOUNT_CODE("accruedExpenseLiabilityAccountCode"),
    RECOGNITION_INTERVAL("recognitionInterval"),
    LATVIAN_MONTHLY_PAYROLL("latvianMonthlyPayroll"),
    LATVIAN_PAYROLL_SETTLEMENT("latvianPayrollSettlement"),
    FIXED_ASSET("fixedAsset"),
    FINANCING("financing"),
    REALIZED_FOREIGN_EXCHANGE("realizedForeignExchange");

    private final String fieldName;

    ContextField(String fieldName) {
      this.fieldName = fieldName;
    }

    @Override
    public String fieldName() {
      return fieldName;
    }

    @Override
    public @Nullable Object value(PostingTemplateFields fields) {
      return switch (this) {
        case PREPAYMENT_ASSET_ACCOUNT_CODE -> fields.prepaymentAssetAccountCode();
        case DEFERRED_REVENUE_ACCOUNT_CODE -> fields.deferredRevenueAccountCode();
        case ACCRUED_EXPENSE_LIABILITY_ACCOUNT_CODE -> fields.accruedExpenseLiabilityAccountCode();
        case RECOGNITION_INTERVAL -> fields.recognitionInterval();
        case LATVIAN_MONTHLY_PAYROLL -> fields.latvianMonthlyPayroll();
        case LATVIAN_PAYROLL_SETTLEMENT -> fields.latvianPayrollSettlement();
        case FIXED_ASSET -> fields.fixedAsset();
        case FINANCING -> fields.financing();
        case REALIZED_FOREIGN_EXCHANGE -> fields.realizedForeignExchange();
      };
    }
  }
}
