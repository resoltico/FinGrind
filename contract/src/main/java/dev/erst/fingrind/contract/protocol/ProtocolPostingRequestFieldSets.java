package dev.erst.fingrind.contract.protocol;

import dev.erst.fingrind.core.BookkeepingEntryKind;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Canonical post-entry request-field sets for direct journals and typed business entries. */
public final class ProtocolPostingRequestFieldSets {
  private static final Set<String> POST_ENTRY_TOP_LEVEL_FIELDS =
      Set.copyOf(ProtocolPostEntryFields.topLevelFields());
  private static final Set<String> JOURNAL_DIRECT_FIELDS =
      Set.of(
          ProtocolPostEntryFields.TopLevel.ENTRY_KIND,
          ProtocolPostEntryFields.TopLevel.EFFECTIVE_DATE,
          ProtocolPostEntryFields.TopLevel.LINES,
          ProtocolPostEntryFields.TopLevel.FOREIGN_EXCHANGE,
          ProtocolPostEntryFields.TopLevel.EVIDENCE,
          ProtocolPostEntryFields.TopLevel.PROVENANCE);
  private static final Set<String> SALE_SETTLED_FIELDS =
      Set.of(
          ProtocolPostEntryFields.TopLevel.ENTRY_KIND,
          ProtocolPostEntryFields.TopLevel.EFFECTIVE_DATE,
          ProtocolPostEntryFields.TopLevel.CASH_ACCOUNT_CODE,
          ProtocolPostEntryFields.TopLevel.REVENUE_ACCOUNT_CODE,
          ProtocolPostEntryFields.TopLevel.AMOUNT,
          ProtocolPostEntryFields.TopLevel.INVENTORY_RELIEF,
          ProtocolPostEntryFields.TopLevel.FOREIGN_EXCHANGE,
          ProtocolPostEntryFields.TopLevel.TAX,
          ProtocolPostEntryFields.TopLevel.EVIDENCE,
          ProtocolPostEntryFields.TopLevel.PROVENANCE);
  private static final Set<String> SALE_ON_CREDIT_FIELDS =
      Set.of(
          ProtocolPostEntryFields.TopLevel.ENTRY_KIND,
          ProtocolPostEntryFields.TopLevel.EFFECTIVE_DATE,
          ProtocolPostEntryFields.TopLevel.RECEIVABLE_ACCOUNT_CODE,
          ProtocolPostEntryFields.TopLevel.REVENUE_ACCOUNT_CODE,
          ProtocolPostEntryFields.TopLevel.AMOUNT,
          ProtocolPostEntryFields.TopLevel.INVENTORY_RELIEF,
          ProtocolPostEntryFields.TopLevel.FOREIGN_EXCHANGE,
          ProtocolPostEntryFields.TopLevel.TAX,
          ProtocolPostEntryFields.TopLevel.EVIDENCE,
          ProtocolPostEntryFields.TopLevel.PROVENANCE);
  private static final Set<String> FOREIGN_CURRENCY_OBLIGATION_FIELDS =
      realizedForeignExchangeFields(
          ProtocolPostEntryFields.TopLevel.FOREIGN_CURRENCY_OBLIGATION_ID,
          ProtocolPostEntryFields.TopLevel.RECEIVABLE_ACCOUNT_CODE,
          ProtocolPostEntryFields.TopLevel.REVENUE_ACCOUNT_CODE,
          ProtocolPostEntryFields.TopLevel.REALIZED_GAIN_ACCOUNT_CODE,
          ProtocolPostEntryFields.TopLevel.REALIZED_LOSS_ACCOUNT_CODE,
          ProtocolPostEntryFields.TopLevel.FOREIGN_EXCHANGE);
  private static final Set<String> REALIZED_FOREIGN_EXCHANGE_SETTLEMENT_FIELDS =
      realizedForeignExchangeFields(
          ProtocolPostEntryFields.TopLevel.FOREIGN_CURRENCY_OBLIGATION_ID,
          ProtocolPostEntryFields.TopLevel.CASH_ACCOUNT_CODE,
          ProtocolPostEntryFields.TopLevel.FOREIGN_EXCHANGE);
  private static final Set<String> LATVIAN_MONTHLY_PAYROLL_FIELDS =
      Set.of(
          ProtocolPostEntryFields.TopLevel.ENTRY_KIND,
          ProtocolPostEntryFields.TopLevel.EFFECTIVE_DATE,
          ProtocolPostEntryFields.TopLevel.PAYROLL_RUN_ID,
          ProtocolPostEntryFields.TopLevel.EMPLOYEE_REFERENCE,
          ProtocolPostEntryFields.TopLevel.PAYROLL_MONTH,
          ProtocolPostEntryFields.TopLevel.WAGE_EXPENSE_ACCOUNT_CODE,
          ProtocolPostEntryFields.TopLevel.EMPLOYER_SOCIAL_CONTRIBUTION_EXPENSE_ACCOUNT_CODE,
          ProtocolPostEntryFields.TopLevel.NET_WAGES_PAYABLE_ACCOUNT_CODE,
          ProtocolPostEntryFields.TopLevel.EMPLOYEE_SOCIAL_CONTRIBUTION_PAYABLE_ACCOUNT_CODE,
          ProtocolPostEntryFields.TopLevel.EMPLOYER_SOCIAL_CONTRIBUTION_PAYABLE_ACCOUNT_CODE,
          ProtocolPostEntryFields.TopLevel.PERSONAL_INCOME_TAX_PAYABLE_ACCOUNT_CODE,
          ProtocolPostEntryFields.TopLevel.GROSS_WAGES,
          ProtocolPostEntryFields.TopLevel.EVIDENCE,
          ProtocolPostEntryFields.TopLevel.PROVENANCE);
  private static final Set<String> LATVIAN_PAYROLL_SETTLEMENT_FIELDS =
      Set.of(
          ProtocolPostEntryFields.TopLevel.ENTRY_KIND,
          ProtocolPostEntryFields.TopLevel.EFFECTIVE_DATE,
          ProtocolPostEntryFields.TopLevel.PAYROLL_RUN_ID,
          ProtocolPostEntryFields.TopLevel.CASH_ACCOUNT_CODE,
          ProtocolPostEntryFields.TopLevel.EVIDENCE,
          ProtocolPostEntryFields.TopLevel.PROVENANCE);
  private static final Set<String> EXPENSE_SETTLED_FIELDS =
      Set.of(
          ProtocolPostEntryFields.TopLevel.ENTRY_KIND,
          ProtocolPostEntryFields.TopLevel.EFFECTIVE_DATE,
          ProtocolPostEntryFields.TopLevel.EXPENSE_ACCOUNT_CODE,
          ProtocolPostEntryFields.TopLevel.CASH_ACCOUNT_CODE,
          ProtocolPostEntryFields.TopLevel.AMOUNT,
          ProtocolPostEntryFields.TopLevel.FOREIGN_EXCHANGE,
          ProtocolPostEntryFields.TopLevel.TAX,
          ProtocolPostEntryFields.TopLevel.EVIDENCE,
          ProtocolPostEntryFields.TopLevel.PROVENANCE);
  private static final Set<String> EXPENSE_ON_CREDIT_FIELDS =
      Set.of(
          ProtocolPostEntryFields.TopLevel.ENTRY_KIND,
          ProtocolPostEntryFields.TopLevel.EFFECTIVE_DATE,
          ProtocolPostEntryFields.TopLevel.EXPENSE_ACCOUNT_CODE,
          ProtocolPostEntryFields.TopLevel.PAYABLE_ACCOUNT_CODE,
          ProtocolPostEntryFields.TopLevel.AMOUNT,
          ProtocolPostEntryFields.TopLevel.FOREIGN_EXCHANGE,
          ProtocolPostEntryFields.TopLevel.TAX,
          ProtocolPostEntryFields.TopLevel.EVIDENCE,
          ProtocolPostEntryFields.TopLevel.PROVENANCE);
  private static final Set<String> RECEIPT_FIELDS =
      Set.of(
          ProtocolPostEntryFields.TopLevel.ENTRY_KIND,
          ProtocolPostEntryFields.TopLevel.EFFECTIVE_DATE,
          ProtocolPostEntryFields.TopLevel.CASH_ACCOUNT_CODE,
          ProtocolPostEntryFields.TopLevel.RECEIVABLE_ACCOUNT_CODE,
          ProtocolPostEntryFields.TopLevel.AMOUNT,
          ProtocolPostEntryFields.TopLevel.SETTLEMENT_ADJUNCT,
          ProtocolPostEntryFields.TopLevel.EVIDENCE,
          ProtocolPostEntryFields.TopLevel.PROVENANCE);
  private static final Set<String> PAYMENT_FIELDS =
      Set.of(
          ProtocolPostEntryFields.TopLevel.ENTRY_KIND,
          ProtocolPostEntryFields.TopLevel.EFFECTIVE_DATE,
          ProtocolPostEntryFields.TopLevel.PAYABLE_ACCOUNT_CODE,
          ProtocolPostEntryFields.TopLevel.CASH_ACCOUNT_CODE,
          ProtocolPostEntryFields.TopLevel.AMOUNT,
          ProtocolPostEntryFields.TopLevel.SETTLEMENT_ADJUNCT,
          ProtocolPostEntryFields.TopLevel.EVIDENCE,
          ProtocolPostEntryFields.TopLevel.PROVENANCE);
  private static final Set<String> OWNER_CONTRIBUTION_FIELDS =
      Set.of(
          ProtocolPostEntryFields.TopLevel.ENTRY_KIND,
          ProtocolPostEntryFields.TopLevel.EFFECTIVE_DATE,
          ProtocolPostEntryFields.TopLevel.CASH_ACCOUNT_CODE,
          ProtocolPostEntryFields.TopLevel.EQUITY_ACCOUNT_CODE,
          ProtocolPostEntryFields.TopLevel.AMOUNT,
          ProtocolPostEntryFields.TopLevel.FOREIGN_EXCHANGE,
          ProtocolPostEntryFields.TopLevel.EVIDENCE,
          ProtocolPostEntryFields.TopLevel.PROVENANCE);
  private static final Set<String> OWNER_WITHDRAWAL_FIELDS =
      Set.of(
          ProtocolPostEntryFields.TopLevel.ENTRY_KIND,
          ProtocolPostEntryFields.TopLevel.EFFECTIVE_DATE,
          ProtocolPostEntryFields.TopLevel.EQUITY_ACCOUNT_CODE,
          ProtocolPostEntryFields.TopLevel.CASH_ACCOUNT_CODE,
          ProtocolPostEntryFields.TopLevel.AMOUNT,
          ProtocolPostEntryFields.TopLevel.FOREIGN_EXCHANGE,
          ProtocolPostEntryFields.TopLevel.EVIDENCE,
          ProtocolPostEntryFields.TopLevel.PROVENANCE);
  private static final Set<String> OPENING_POSITION_FIELDS =
      Set.of(
          ProtocolPostEntryFields.TopLevel.ENTRY_KIND,
          ProtocolPostEntryFields.TopLevel.EFFECTIVE_DATE,
          ProtocolPostEntryFields.TopLevel.OPENING_BALANCES,
          ProtocolPostEntryFields.TopLevel.EVIDENCE,
          ProtocolPostEntryFields.TopLevel.PROVENANCE);
  private static final Set<String> REVERSAL_ENTRY_FIELDS =
      Set.of(
          ProtocolPostEntryFields.TopLevel.ENTRY_KIND,
          ProtocolPostEntryFields.TopLevel.EFFECTIVE_DATE,
          ProtocolPostEntryFields.TopLevel.FOREIGN_EXCHANGE,
          ProtocolPostEntryFields.TopLevel.EVIDENCE,
          ProtocolPostEntryFields.TopLevel.PROVENANCE,
          ProtocolPostEntryFields.TopLevel.REVERSAL);
  private static final Map<BookkeepingEntryKind, Set<String>> FIELDS_BY_ENTRY_KIND =
      fieldsByEntryKind();

  private ProtocolPostingRequestFieldSets() {}

  private static Set<String> realizedForeignExchangeFields(String... variantFields) {
    return ProtocolPostingRequestFieldSetSupport.typedEntryFields(variantFields);
  }

  private static Map<BookkeepingEntryKind, Set<String>> fieldsByEntryKind() {
    var fields = new EnumMap<BookkeepingEntryKind, Set<String>>(BookkeepingEntryKind.class);
    fields.put(BookkeepingEntryKind.DIRECT_JOURNAL, JOURNAL_DIRECT_FIELDS);
    fields.put(BookkeepingEntryKind.SALE_SETTLED, SALE_SETTLED_FIELDS);
    fields.put(BookkeepingEntryKind.SALE_ON_CREDIT, SALE_ON_CREDIT_FIELDS);
    fields.put(BookkeepingEntryKind.EXPENSE_SETTLED, EXPENSE_SETTLED_FIELDS);
    fields.put(BookkeepingEntryKind.EXPENSE_ON_CREDIT, EXPENSE_ON_CREDIT_FIELDS);
    fields.put(BookkeepingEntryKind.RECEIPT, RECEIPT_FIELDS);
    fields.put(BookkeepingEntryKind.PAYMENT, PAYMENT_FIELDS);
    fields.put(BookkeepingEntryKind.OWNER_CONTRIBUTION, OWNER_CONTRIBUTION_FIELDS);
    fields.put(BookkeepingEntryKind.OWNER_WITHDRAWAL, OWNER_WITHDRAWAL_FIELDS);
    fields.put(BookkeepingEntryKind.OPENING_POSITION, OPENING_POSITION_FIELDS);
    fields.put(BookkeepingEntryKind.REVERSAL, REVERSAL_ENTRY_FIELDS);
    fields.put(BookkeepingEntryKind.LATVIAN_MONTHLY_PAYROLL, LATVIAN_MONTHLY_PAYROLL_FIELDS);
    fields.put(
        BookkeepingEntryKind.LATVIAN_PAYROLL_NET_WAGE_SETTLEMENT,
        LATVIAN_PAYROLL_SETTLEMENT_FIELDS);
    fields.put(
        BookkeepingEntryKind.LATVIAN_PAYROLL_STATE_REMITTANCE, LATVIAN_PAYROLL_SETTLEMENT_FIELDS);
    fields.put(
        BookkeepingEntryKind.FOREIGN_CURRENCY_OBLIGATION, FOREIGN_CURRENCY_OBLIGATION_FIELDS);
    fields.put(
        BookkeepingEntryKind.REALIZED_FOREIGN_EXCHANGE_SETTLEMENT,
        REALIZED_FOREIGN_EXCHANGE_SETTLEMENT_FIELDS);
    return Map.copyOf(fields);
  }

  /** Returns the accepted top-level fields shared by post-entry requests. */
  public static Set<String> postEntryTopLevelFields() {
    return POST_ENTRY_TOP_LEVEL_FIELDS;
  }

  /** Returns the canonical accepted top-level field set for one owned entry kind. */
  public static Set<String> fieldsFor(BookkeepingEntryKind entryKind) {
    BookkeepingEntryKind requiredEntryKind = Objects.requireNonNull(entryKind, "entryKind");
    Set<String> fields = FIELDS_BY_ENTRY_KIND.get(requiredEntryKind);
    if (fields == null) {
      throw new IllegalArgumentException(
          "No posting request-field set is owned for " + requiredEntryKind.wireValue() + ".");
    }
    return fields;
  }
}
