package dev.erst.fingrind.cli;

import static dev.erst.fingrind.cli.CliJsonFieldAccess.requiredText;
import static dev.erst.fingrind.cli.CliJsonStructureAccess.rejectUnexpectedFields;
import static dev.erst.fingrind.cli.CliJsonStructureAccess.requiredArray;

import dev.erst.fingrind.contract.bookkeeping.BookkeepingEntry;
import dev.erst.fingrind.contract.protocol.ProtocolPostEntryFields;
import dev.erst.fingrind.contract.protocol.ProtocolPostingRequestFieldSets;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.BookkeepingEntryKind;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.node.ObjectNode;

/** Shared typed-entry reader ownership for the posting-request parser. */
final class CliTypedBookkeepingEntryReaders {
  private static final Map<BookkeepingEntryKind, EntryReader> READERS =
      Map.ofEntries(
          Map.entry(
              BookkeepingEntryKind.PURCHASE_SETTLED, CliInventoryBookkeepingEntryReaders::read),
          Map.entry(
              BookkeepingEntryKind.PURCHASE_ON_CREDIT, CliInventoryBookkeepingEntryReaders::read),
          Map.entry(
              BookkeepingEntryKind.INVENTORY_CAPITALIZATION_SETTLED,
              CliInventoryBookkeepingEntryReaders::read),
          Map.entry(
              BookkeepingEntryKind.INVENTORY_CAPITALIZATION_ON_CREDIT,
              CliInventoryBookkeepingEntryReaders::read),
          Map.entry(
              BookkeepingEntryKind.INVENTORY_WRITE_DOWN, CliInventoryBookkeepingEntryReaders::read),
          Map.entry(
              BookkeepingEntryKind.INVENTORY_SHRINKAGE, CliInventoryBookkeepingEntryReaders::read),
          Map.entry(
              BookkeepingEntryKind.INVENTORY_COUNT_INCREASE,
              CliInventoryBookkeepingEntryReaders::read),
          Map.entry(BookkeepingEntryKind.PREPAYMENT, CliAccrualCutoffBookkeepingEntryReaders::read),
          Map.entry(
              BookkeepingEntryKind.DEFERRED_REVENUE, CliAccrualCutoffBookkeepingEntryReaders::read),
          Map.entry(
              BookkeepingEntryKind.ACCRUED_EXPENSE, CliAccrualCutoffBookkeepingEntryReaders::read),
          Map.entry(
              BookkeepingEntryKind.ACCRUAL_CUTOFF_RECOGNITION,
              CliAccrualCutoffBookkeepingEntryReaders::read),
          Map.entry(
              BookkeepingEntryKind.ACCRUED_EXPENSE_SETTLEMENT,
              CliAccrualCutoffBookkeepingEntryReaders::read),
          Map.entry(
              BookkeepingEntryKind.FIXED_ASSET_CAPITALIZATION,
              CliFixedAssetBookkeepingEntryReaders::read),
          Map.entry(
              BookkeepingEntryKind.FIXED_ASSET_DEPRECIATION,
              CliFixedAssetBookkeepingEntryReaders::read),
          Map.entry(
              BookkeepingEntryKind.FIXED_ASSET_DISPOSAL,
              CliFixedAssetBookkeepingEntryReaders::read),
          Map.entry(
              BookkeepingEntryKind.FINANCING_BORROWING, CliFinancingBookkeepingEntryReaders::read),
          Map.entry(
              BookkeepingEntryKind.FINANCING_PRINCIPAL_REPAYMENT,
              CliFinancingBookkeepingEntryReaders::read),
          Map.entry(
              BookkeepingEntryKind.FINANCING_INTEREST_ACCRUAL,
              CliFinancingBookkeepingEntryReaders::read),
          Map.entry(
              BookkeepingEntryKind.FINANCING_INTEREST_PAYMENT,
              CliFinancingBookkeepingEntryReaders::read),
          Map.entry(
              BookkeepingEntryKind.FOREIGN_CURRENCY_OBLIGATION,
              CliRealizedForeignExchangeBookkeepingEntryReaders::read),
          Map.entry(
              BookkeepingEntryKind.REALIZED_FOREIGN_EXCHANGE_SETTLEMENT,
              CliRealizedForeignExchangeBookkeepingEntryReaders::read),
          Map.entry(
              BookkeepingEntryKind.LATVIAN_MONTHLY_PAYROLL,
              CliLatvianPayrollBookkeepingEntryReaders::read),
          Map.entry(
              BookkeepingEntryKind.LATVIAN_PAYROLL_NET_WAGE_SETTLEMENT,
              CliLatvianPayrollBookkeepingEntryReaders::read),
          Map.entry(
              BookkeepingEntryKind.LATVIAN_PAYROLL_STATE_REMITTANCE,
              CliLatvianPayrollBookkeepingEntryReaders::read),
          Map.entry(
              BookkeepingEntryKind.SALE_SETTLED,
              (rootNode, ignored) -> readSaleSettledEntry(rootNode)),
          Map.entry(
              BookkeepingEntryKind.SALE_ON_CREDIT,
              (rootNode, ignored) -> readSaleOnCreditEntry(rootNode)),
          Map.entry(
              BookkeepingEntryKind.EXPENSE_SETTLED,
              (rootNode, ignored) -> readExpenseSettledEntry(rootNode)),
          Map.entry(
              BookkeepingEntryKind.EXPENSE_ON_CREDIT,
              (rootNode, ignored) -> readExpenseOnCreditEntry(rootNode)),
          Map.entry(
              BookkeepingEntryKind.RECEIPT, (rootNode, ignored) -> readReceiptEntry(rootNode)),
          Map.entry(
              BookkeepingEntryKind.PAYMENT, (rootNode, ignored) -> readPaymentEntry(rootNode)),
          Map.entry(
              BookkeepingEntryKind.OWNER_CONTRIBUTION,
              (rootNode, ignored) -> readOwnerContributionEntry(rootNode)),
          Map.entry(
              BookkeepingEntryKind.OWNER_WITHDRAWAL,
              (rootNode, ignored) -> readOwnerWithdrawalEntry(rootNode)),
          Map.entry(
              BookkeepingEntryKind.OPENING_POSITION,
              (rootNode, ignored) -> readOpeningPositionEntry(rootNode)),
          Map.entry(
              BookkeepingEntryKind.REVERSAL, (rootNode, ignored) -> readReversalEntry(rootNode)));

  private CliTypedBookkeepingEntryReaders() {}

  static BookkeepingEntry read(ObjectNode rootNode, @Nullable BookkeepingEntryKind entryKind) {
    if (entryKind == null) {
      throw new IllegalArgumentException("A typed bookkeeping entry kind is required.");
    }
    if (entryKind == BookkeepingEntryKind.DIRECT_JOURNAL) {
      throw new IllegalStateException("Direct journal entries are handled separately.");
    }
    return Objects.requireNonNull(
            READERS.get(entryKind), "No typed entry reader is owned for " + entryKind + ".")
        .read(rootNode, entryKind);
  }

  /** Parses one request object into the typed bookkeeping entry owned by its entry kind. */
  @FunctionalInterface
  private interface EntryReader {
    /** Parses one request object after the dispatcher selected this entry-kind reader. */
    BookkeepingEntry read(ObjectNode rootNode, BookkeepingEntryKind entryKind);
  }

  private static BookkeepingEntry.SaleSettled readSaleSettledEntry(ObjectNode rootNode) {
    rejectUnexpectedFields(
        rootNode,
        null,
        ProtocolPostingRequestFieldSets.fieldsFor(BookkeepingEntryKind.SALE_SETTLED));
    return new BookkeepingEntry.SaleSettled(
        CliBookkeepingEntryStructureParser.requiredEffectiveDate(rootNode),
        new AccountCode(requiredText(rootNode, ProtocolPostEntryFields.TopLevel.CASH_ACCOUNT_CODE)),
        new AccountCode(
            requiredText(rootNode, ProtocolPostEntryFields.TopLevel.REVENUE_ACCOUNT_CODE)),
        CliBookkeepingEntryStructureParser.requiredPositiveAmount(rootNode),
        CliBookkeepingEntryNestedParser.optionalInventoryRelief(rootNode),
        null,
        CliBookkeepingEntryNestedParser.optionalForeignExchange(rootNode),
        CliBookkeepingEntryNestedParser.optionalTaxSelection(rootNode),
        null);
  }

  private static BookkeepingEntry.SaleOnCredit readSaleOnCreditEntry(ObjectNode rootNode) {
    rejectUnexpectedFields(
        rootNode,
        null,
        ProtocolPostingRequestFieldSets.fieldsFor(BookkeepingEntryKind.SALE_ON_CREDIT));
    return new BookkeepingEntry.SaleOnCredit(
        CliBookkeepingEntryStructureParser.requiredEffectiveDate(rootNode),
        new AccountCode(
            requiredText(rootNode, ProtocolPostEntryFields.TopLevel.RECEIVABLE_ACCOUNT_CODE)),
        new AccountCode(
            requiredText(rootNode, ProtocolPostEntryFields.TopLevel.REVENUE_ACCOUNT_CODE)),
        CliBookkeepingEntryStructureParser.requiredPositiveAmount(rootNode),
        CliBookkeepingEntryNestedParser.optionalInventoryRelief(rootNode),
        null,
        CliBookkeepingEntryNestedParser.optionalForeignExchange(rootNode),
        CliBookkeepingEntryNestedParser.optionalTaxSelection(rootNode),
        null);
  }

  private static BookkeepingEntry.ExpenseSettled readExpenseSettledEntry(ObjectNode rootNode) {
    rejectUnexpectedFields(
        rootNode,
        null,
        ProtocolPostingRequestFieldSets.fieldsFor(BookkeepingEntryKind.EXPENSE_SETTLED));
    return new BookkeepingEntry.ExpenseSettled(
        CliBookkeepingEntryStructureParser.requiredEffectiveDate(rootNode),
        new AccountCode(
            requiredText(rootNode, ProtocolPostEntryFields.TopLevel.EXPENSE_ACCOUNT_CODE)),
        new AccountCode(requiredText(rootNode, ProtocolPostEntryFields.TopLevel.CASH_ACCOUNT_CODE)),
        CliBookkeepingEntryStructureParser.requiredPositiveAmount(rootNode),
        CliBookkeepingEntryNestedParser.optionalForeignExchange(rootNode),
        CliBookkeepingEntryNestedParser.optionalTaxSelection(rootNode),
        null);
  }

  private static BookkeepingEntry.ExpenseOnCredit readExpenseOnCreditEntry(ObjectNode rootNode) {
    rejectUnexpectedFields(
        rootNode,
        null,
        ProtocolPostingRequestFieldSets.fieldsFor(BookkeepingEntryKind.EXPENSE_ON_CREDIT));
    return new BookkeepingEntry.ExpenseOnCredit(
        CliBookkeepingEntryStructureParser.requiredEffectiveDate(rootNode),
        new AccountCode(
            requiredText(rootNode, ProtocolPostEntryFields.TopLevel.EXPENSE_ACCOUNT_CODE)),
        new AccountCode(
            requiredText(rootNode, ProtocolPostEntryFields.TopLevel.PAYABLE_ACCOUNT_CODE)),
        CliBookkeepingEntryStructureParser.requiredPositiveAmount(rootNode),
        CliBookkeepingEntryNestedParser.optionalForeignExchange(rootNode),
        CliBookkeepingEntryNestedParser.optionalTaxSelection(rootNode),
        null);
  }

  private static BookkeepingEntry.Receipt readReceiptEntry(ObjectNode rootNode) {
    rejectUnexpectedFields(
        rootNode, null, ProtocolPostingRequestFieldSets.fieldsFor(BookkeepingEntryKind.RECEIPT));
    return new BookkeepingEntry.Receipt(
        CliBookkeepingEntryStructureParser.requiredEffectiveDate(rootNode),
        new AccountCode(requiredText(rootNode, ProtocolPostEntryFields.TopLevel.CASH_ACCOUNT_CODE)),
        new AccountCode(
            requiredText(rootNode, ProtocolPostEntryFields.TopLevel.RECEIVABLE_ACCOUNT_CODE)),
        CliBookkeepingEntryStructureParser.requiredPositiveAmount(rootNode),
        CliBookkeepingEntryNestedParser.optionalSettlementAdjunct(rootNode));
  }

  private static BookkeepingEntry.Payment readPaymentEntry(ObjectNode rootNode) {
    rejectUnexpectedFields(
        rootNode, null, ProtocolPostingRequestFieldSets.fieldsFor(BookkeepingEntryKind.PAYMENT));
    return new BookkeepingEntry.Payment(
        CliBookkeepingEntryStructureParser.requiredEffectiveDate(rootNode),
        new AccountCode(
            requiredText(rootNode, ProtocolPostEntryFields.TopLevel.PAYABLE_ACCOUNT_CODE)),
        new AccountCode(requiredText(rootNode, ProtocolPostEntryFields.TopLevel.CASH_ACCOUNT_CODE)),
        CliBookkeepingEntryStructureParser.requiredPositiveAmount(rootNode),
        CliBookkeepingEntryNestedParser.optionalSettlementAdjunct(rootNode));
  }

  private static BookkeepingEntry.OwnerContribution readOwnerContributionEntry(
      ObjectNode rootNode) {
    rejectUnexpectedFields(
        rootNode,
        null,
        ProtocolPostingRequestFieldSets.fieldsFor(BookkeepingEntryKind.OWNER_CONTRIBUTION));
    return new BookkeepingEntry.OwnerContribution(
        CliBookkeepingEntryStructureParser.requiredEffectiveDate(rootNode),
        new AccountCode(requiredText(rootNode, ProtocolPostEntryFields.TopLevel.CASH_ACCOUNT_CODE)),
        new AccountCode(
            requiredText(rootNode, ProtocolPostEntryFields.TopLevel.EQUITY_ACCOUNT_CODE)),
        CliBookkeepingEntryStructureParser.requiredPositiveAmount(rootNode),
        CliBookkeepingEntryNestedParser.optionalForeignExchange(rootNode));
  }

  private static BookkeepingEntry.OwnerWithdrawal readOwnerWithdrawalEntry(ObjectNode rootNode) {
    rejectUnexpectedFields(
        rootNode,
        null,
        ProtocolPostingRequestFieldSets.fieldsFor(BookkeepingEntryKind.OWNER_WITHDRAWAL));
    return new BookkeepingEntry.OwnerWithdrawal(
        CliBookkeepingEntryStructureParser.requiredEffectiveDate(rootNode),
        new AccountCode(
            requiredText(rootNode, ProtocolPostEntryFields.TopLevel.EQUITY_ACCOUNT_CODE)),
        new AccountCode(requiredText(rootNode, ProtocolPostEntryFields.TopLevel.CASH_ACCOUNT_CODE)),
        CliBookkeepingEntryStructureParser.requiredPositiveAmount(rootNode),
        CliBookkeepingEntryNestedParser.optionalForeignExchange(rootNode));
  }

  private static BookkeepingEntry.OpeningPosition readOpeningPositionEntry(ObjectNode rootNode) {
    rejectUnexpectedFields(
        rootNode,
        null,
        ProtocolPostingRequestFieldSets.fieldsFor(BookkeepingEntryKind.OPENING_POSITION));
    return new BookkeepingEntry.OpeningPosition(
        CliBookkeepingEntryStructureParser.requiredEffectiveDate(rootNode),
        CliBookkeepingEntryStructureParser.readOpeningBalances(
            requiredArray(rootNode, ProtocolPostEntryFields.TopLevel.OPENING_BALANCES)));
  }

  private static BookkeepingEntry.Reversal readReversalEntry(ObjectNode rootNode) {
    rejectUnexpectedFields(
        rootNode, null, ProtocolPostingRequestFieldSets.fieldsFor(BookkeepingEntryKind.REVERSAL));
    return new BookkeepingEntry.Reversal(
        CliBookkeepingEntryStructureParser.requiredEffectiveDate(rootNode),
        CliBookkeepingEntryNestedParser.readRequiredReversal(rootNode),
        CliBookkeepingEntryNestedParser.optionalForeignExchange(rootNode),
        null);
  }
}
