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
          ProtocolBusinessEventFields.Core.ENTRY_KIND,
          ProtocolBusinessEventFields.Core.EFFECTIVE_DATE,
          ProtocolBusinessEventFields.Core.LINES,
          ProtocolBusinessEventFields.Core.FOREIGN_EXCHANGE,
          ProtocolBusinessEventFields.Core.EVIDENCE,
          ProtocolBusinessEventFields.Core.PROVENANCE);
  private static final Set<String> SALE_SETTLED_FIELDS =
      Set.of(
          ProtocolBusinessEventFields.Core.ENTRY_KIND,
          ProtocolBusinessEventFields.Core.EFFECTIVE_DATE,
          ProtocolBusinessEventFields.Core.CASH_ACCOUNT_CODE,
          ProtocolBusinessEventFields.Core.REVENUE_ACCOUNT_CODE,
          ProtocolBusinessEventFields.Core.AMOUNT,
          ProtocolBusinessEventFields.Inventory.INVENTORY_RELIEF,
          ProtocolBusinessEventFields.Core.FOREIGN_EXCHANGE,
          ProtocolBusinessEventFields.Core.TAX,
          ProtocolBusinessEventFields.Core.EVIDENCE,
          ProtocolBusinessEventFields.Core.PROVENANCE);
  private static final Set<String> SALE_ON_CREDIT_FIELDS =
      Set.of(
          ProtocolBusinessEventFields.Core.ENTRY_KIND,
          ProtocolBusinessEventFields.Core.EFFECTIVE_DATE,
          ProtocolBusinessEventFields.Core.RECEIVABLE_ACCOUNT_CODE,
          ProtocolBusinessEventFields.Core.REVENUE_ACCOUNT_CODE,
          ProtocolBusinessEventFields.Core.AMOUNT,
          ProtocolBusinessEventFields.Inventory.INVENTORY_RELIEF,
          ProtocolBusinessEventFields.Core.FOREIGN_EXCHANGE,
          ProtocolBusinessEventFields.Core.TAX,
          ProtocolBusinessEventFields.Core.EVIDENCE,
          ProtocolBusinessEventFields.Core.PROVENANCE);
  private static final Set<String> FOREIGN_CURRENCY_OBLIGATION_FIELDS =
      realizedForeignExchangeFields(
          ProtocolBusinessEventFields.RealizedForeignExchange.FOREIGN_CURRENCY_OBLIGATION_ID,
          ProtocolBusinessEventFields.Core.RECEIVABLE_ACCOUNT_CODE,
          ProtocolBusinessEventFields.Core.REVENUE_ACCOUNT_CODE,
          ProtocolBusinessEventFields.RealizedForeignExchange.REALIZED_GAIN_ACCOUNT_CODE,
          ProtocolBusinessEventFields.RealizedForeignExchange.REALIZED_LOSS_ACCOUNT_CODE,
          ProtocolBusinessEventFields.Core.FOREIGN_EXCHANGE);
  private static final Set<String> REALIZED_FOREIGN_EXCHANGE_SETTLEMENT_FIELDS =
      realizedForeignExchangeFields(
          ProtocolBusinessEventFields.RealizedForeignExchange.FOREIGN_CURRENCY_OBLIGATION_ID,
          ProtocolBusinessEventFields.Core.CASH_ACCOUNT_CODE,
          ProtocolBusinessEventFields.Core.FOREIGN_EXCHANGE);
  private static final Set<String> LATVIAN_MONTHLY_PAYROLL_FIELDS =
      Set.of(
          ProtocolBusinessEventFields.Core.ENTRY_KIND,
          ProtocolBusinessEventFields.Core.EFFECTIVE_DATE,
          ProtocolBusinessEventFields.LatvianPayroll.PAYROLL_RUN_ID,
          ProtocolBusinessEventFields.LatvianPayroll.EMPLOYEE_REFERENCE,
          ProtocolBusinessEventFields.LatvianPayroll.PAYROLL_MONTH,
          ProtocolBusinessEventFields.LatvianPayroll.TAX_BOOK_HELD_AT_EMPLOYER,
          ProtocolBusinessEventFields.LatvianPayroll.DEPENDANT_COUNT,
          ProtocolBusinessEventFields.LatvianPayroll.WAGE_EXPENSE_ACCOUNT_CODE,
          ProtocolBusinessEventFields.LatvianPayroll
              .EMPLOYER_SOCIAL_CONTRIBUTION_EXPENSE_ACCOUNT_CODE,
          ProtocolBusinessEventFields.LatvianPayroll.NET_WAGES_PAYABLE_ACCOUNT_CODE,
          ProtocolBusinessEventFields.LatvianPayroll
              .EMPLOYEE_SOCIAL_CONTRIBUTION_PAYABLE_ACCOUNT_CODE,
          ProtocolBusinessEventFields.LatvianPayroll
              .EMPLOYER_SOCIAL_CONTRIBUTION_PAYABLE_ACCOUNT_CODE,
          ProtocolBusinessEventFields.LatvianPayroll.PERSONAL_INCOME_TAX_PAYABLE_ACCOUNT_CODE,
          ProtocolBusinessEventFields.LatvianPayroll.GROSS_WAGES,
          ProtocolBusinessEventFields.Core.EVIDENCE,
          ProtocolBusinessEventFields.Core.PROVENANCE);
  private static final Set<String> LATVIAN_PAYROLL_SETTLEMENT_FIELDS =
      Set.of(
          ProtocolBusinessEventFields.Core.ENTRY_KIND,
          ProtocolBusinessEventFields.Core.EFFECTIVE_DATE,
          ProtocolBusinessEventFields.LatvianPayroll.PAYROLL_RUN_ID,
          ProtocolBusinessEventFields.Core.CASH_ACCOUNT_CODE,
          ProtocolBusinessEventFields.Core.EVIDENCE,
          ProtocolBusinessEventFields.Core.PROVENANCE);
  private static final Set<String> EXPENSE_SETTLED_FIELDS =
      Set.of(
          ProtocolBusinessEventFields.Core.ENTRY_KIND,
          ProtocolBusinessEventFields.Core.EFFECTIVE_DATE,
          ProtocolBusinessEventFields.Inventory.EXPENSE_ACCOUNT_CODE,
          ProtocolBusinessEventFields.Core.CASH_ACCOUNT_CODE,
          ProtocolBusinessEventFields.Core.AMOUNT,
          ProtocolBusinessEventFields.Core.FOREIGN_EXCHANGE,
          ProtocolBusinessEventFields.Core.TAX,
          ProtocolBusinessEventFields.Core.EVIDENCE,
          ProtocolBusinessEventFields.Core.PROVENANCE);
  private static final Set<String> EXPENSE_ON_CREDIT_FIELDS =
      Set.of(
          ProtocolBusinessEventFields.Core.ENTRY_KIND,
          ProtocolBusinessEventFields.Core.EFFECTIVE_DATE,
          ProtocolBusinessEventFields.Inventory.EXPENSE_ACCOUNT_CODE,
          ProtocolBusinessEventFields.Core.PAYABLE_ACCOUNT_CODE,
          ProtocolBusinessEventFields.Core.AMOUNT,
          ProtocolBusinessEventFields.Core.FOREIGN_EXCHANGE,
          ProtocolBusinessEventFields.Core.TAX,
          ProtocolBusinessEventFields.Core.EVIDENCE,
          ProtocolBusinessEventFields.Core.PROVENANCE);
  private static final Set<String> RECEIPT_FIELDS =
      Set.of(
          ProtocolBusinessEventFields.Core.ENTRY_KIND,
          ProtocolBusinessEventFields.Core.EFFECTIVE_DATE,
          ProtocolBusinessEventFields.Core.CASH_ACCOUNT_CODE,
          ProtocolBusinessEventFields.Core.RECEIVABLE_ACCOUNT_CODE,
          ProtocolBusinessEventFields.Core.AMOUNT,
          ProtocolBusinessEventFields.Core.SETTLEMENT_ADJUNCT,
          ProtocolBusinessEventFields.Core.EVIDENCE,
          ProtocolBusinessEventFields.Core.PROVENANCE);
  private static final Set<String> PAYMENT_FIELDS =
      Set.of(
          ProtocolBusinessEventFields.Core.ENTRY_KIND,
          ProtocolBusinessEventFields.Core.EFFECTIVE_DATE,
          ProtocolBusinessEventFields.Core.PAYABLE_ACCOUNT_CODE,
          ProtocolBusinessEventFields.Core.CASH_ACCOUNT_CODE,
          ProtocolBusinessEventFields.Core.AMOUNT,
          ProtocolBusinessEventFields.Core.SETTLEMENT_ADJUNCT,
          ProtocolBusinessEventFields.Core.EVIDENCE,
          ProtocolBusinessEventFields.Core.PROVENANCE);
  private static final Set<String> OWNER_CONTRIBUTION_FIELDS =
      Set.of(
          ProtocolBusinessEventFields.Core.ENTRY_KIND,
          ProtocolBusinessEventFields.Core.EFFECTIVE_DATE,
          ProtocolBusinessEventFields.Core.CASH_ACCOUNT_CODE,
          ProtocolBusinessEventFields.Core.EQUITY_ACCOUNT_CODE,
          ProtocolBusinessEventFields.Core.AMOUNT,
          ProtocolBusinessEventFields.Core.FOREIGN_EXCHANGE,
          ProtocolBusinessEventFields.Core.EVIDENCE,
          ProtocolBusinessEventFields.Core.PROVENANCE);
  private static final Set<String> OWNER_WITHDRAWAL_FIELDS =
      Set.of(
          ProtocolBusinessEventFields.Core.ENTRY_KIND,
          ProtocolBusinessEventFields.Core.EFFECTIVE_DATE,
          ProtocolBusinessEventFields.Core.EQUITY_ACCOUNT_CODE,
          ProtocolBusinessEventFields.Core.CASH_ACCOUNT_CODE,
          ProtocolBusinessEventFields.Core.AMOUNT,
          ProtocolBusinessEventFields.Core.FOREIGN_EXCHANGE,
          ProtocolBusinessEventFields.Core.EVIDENCE,
          ProtocolBusinessEventFields.Core.PROVENANCE);
  private static final Set<String> OPENING_POSITION_FIELDS =
      Set.of(
          ProtocolBusinessEventFields.Core.ENTRY_KIND,
          ProtocolBusinessEventFields.Core.EFFECTIVE_DATE,
          ProtocolBusinessEventFields.Core.OPENING_BALANCES,
          ProtocolBusinessEventFields.Core.EVIDENCE,
          ProtocolBusinessEventFields.Core.PROVENANCE);
  private static final Set<String> REVERSAL_ENTRY_FIELDS =
      Set.of(
          ProtocolBusinessEventFields.Core.ENTRY_KIND,
          ProtocolBusinessEventFields.Core.EFFECTIVE_DATE,
          ProtocolBusinessEventFields.Core.FOREIGN_EXCHANGE,
          ProtocolBusinessEventFields.Core.EVIDENCE,
          ProtocolBusinessEventFields.Core.PROVENANCE,
          ProtocolBusinessEventFields.Core.REVERSAL);
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
