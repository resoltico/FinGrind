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
import tools.jackson.databind.node.ObjectNode;

/** Shared typed-entry reader ownership for the posting-request parser. */
final class CliTypedBookkeepingEntryReaders {
  private static final Map<BookkeepingEntryKind, TypedEntryReader> READERS =
      Map.ofEntries(
          Map.entry(
              BookkeepingEntryKind.SALE_SETTLED,
              CliTypedBookkeepingEntryReaders::readSaleSettledEntry),
          Map.entry(
              BookkeepingEntryKind.SALE_ON_CREDIT,
              CliTypedBookkeepingEntryReaders::readSaleOnCreditEntry),
          Map.entry(
              BookkeepingEntryKind.PURCHASE_SETTLED,
              CliTypedBookkeepingEntryReaders::readPurchaseSettledEntry),
          Map.entry(
              BookkeepingEntryKind.PURCHASE_ON_CREDIT,
              CliTypedBookkeepingEntryReaders::readPurchaseOnCreditEntry),
          Map.entry(
              BookkeepingEntryKind.EXPENSE_SETTLED,
              CliTypedBookkeepingEntryReaders::readExpenseSettledEntry),
          Map.entry(
              BookkeepingEntryKind.EXPENSE_ON_CREDIT,
              CliTypedBookkeepingEntryReaders::readExpenseOnCreditEntry),
          Map.entry(
              BookkeepingEntryKind.RECEIPT, CliTypedBookkeepingEntryReaders::readReceiptEntry),
          Map.entry(
              BookkeepingEntryKind.PAYMENT, CliTypedBookkeepingEntryReaders::readPaymentEntry),
          Map.entry(
              BookkeepingEntryKind.OWNER_CONTRIBUTION,
              CliTypedBookkeepingEntryReaders::readOwnerContributionEntry),
          Map.entry(
              BookkeepingEntryKind.OWNER_WITHDRAWAL,
              CliTypedBookkeepingEntryReaders::readOwnerWithdrawalEntry),
          Map.entry(
              BookkeepingEntryKind.OPENING_POSITION,
              CliTypedBookkeepingEntryReaders::readOpeningPositionEntry),
          Map.entry(
              BookkeepingEntryKind.REVERSAL, CliTypedBookkeepingEntryReaders::readReversalEntry));

  private CliTypedBookkeepingEntryReaders() {}

  static BookkeepingEntry read(ObjectNode rootNode, BookkeepingEntryKind entryKind) {
    if (entryKind == BookkeepingEntryKind.DIRECT_JOURNAL) {
      throw new IllegalStateException("Direct journal entries are handled separately.");
    }
    return Objects.requireNonNull(READERS.get(entryKind), "entryKind").read(rootNode);
  }

  private static BookkeepingEntry.SaleSettled readSaleSettledEntry(ObjectNode rootNode) {
    rejectUnexpectedFields(rootNode, null, ProtocolPostingRequestFieldSets.saleSettledFields());
    return new BookkeepingEntry.SaleSettled(
        CliBookkeepingEntryStructureParser.requiredEffectiveDate(rootNode),
        new AccountCode(requiredText(rootNode, ProtocolPostEntryFields.TopLevel.CASH_ACCOUNT_CODE)),
        new AccountCode(
            requiredText(rootNode, ProtocolPostEntryFields.TopLevel.REVENUE_ACCOUNT_CODE)),
        CliBookkeepingEntryStructureParser.requiredPositiveAmount(rootNode),
        CliBookkeepingEntryNestedParser.optionalInventoryRelief(rootNode),
        CliBookkeepingEntryNestedParser.optionalForeignExchange(rootNode),
        CliBookkeepingEntryNestedParser.optionalTaxSelection(rootNode),
        null);
  }

  private static BookkeepingEntry.SaleOnCredit readSaleOnCreditEntry(ObjectNode rootNode) {
    rejectUnexpectedFields(rootNode, null, ProtocolPostingRequestFieldSets.saleOnCreditFields());
    return new BookkeepingEntry.SaleOnCredit(
        CliBookkeepingEntryStructureParser.requiredEffectiveDate(rootNode),
        new AccountCode(
            requiredText(rootNode, ProtocolPostEntryFields.TopLevel.RECEIVABLE_ACCOUNT_CODE)),
        new AccountCode(
            requiredText(rootNode, ProtocolPostEntryFields.TopLevel.REVENUE_ACCOUNT_CODE)),
        CliBookkeepingEntryStructureParser.requiredPositiveAmount(rootNode),
        CliBookkeepingEntryNestedParser.optionalInventoryRelief(rootNode),
        CliBookkeepingEntryNestedParser.optionalTaxSelection(rootNode),
        null);
  }

  private static BookkeepingEntry.PurchaseSettled readPurchaseSettledEntry(ObjectNode rootNode) {
    rejectUnexpectedFields(rootNode, null, ProtocolPostingRequestFieldSets.purchaseSettledFields());
    return new BookkeepingEntry.PurchaseSettled(
        CliBookkeepingEntryStructureParser.requiredEffectiveDate(rootNode),
        new AccountCode(
            requiredText(rootNode, ProtocolPostEntryFields.TopLevel.INVENTORY_ACCOUNT_CODE)),
        new AccountCode(requiredText(rootNode, ProtocolPostEntryFields.TopLevel.CASH_ACCOUNT_CODE)),
        CliBookkeepingEntryStructureParser.requiredPositiveAmount(rootNode),
        CliBookkeepingEntryNestedParser.optionalForeignExchange(rootNode));
  }

  private static BookkeepingEntry.PurchaseOnCredit readPurchaseOnCreditEntry(ObjectNode rootNode) {
    rejectUnexpectedFields(
        rootNode, null, ProtocolPostingRequestFieldSets.purchaseOnCreditFields());
    return new BookkeepingEntry.PurchaseOnCredit(
        CliBookkeepingEntryStructureParser.requiredEffectiveDate(rootNode),
        new AccountCode(
            requiredText(rootNode, ProtocolPostEntryFields.TopLevel.INVENTORY_ACCOUNT_CODE)),
        new AccountCode(
            requiredText(rootNode, ProtocolPostEntryFields.TopLevel.PAYABLE_ACCOUNT_CODE)),
        CliBookkeepingEntryStructureParser.requiredPositiveAmount(rootNode));
  }

  private static BookkeepingEntry.ExpenseSettled readExpenseSettledEntry(ObjectNode rootNode) {
    rejectUnexpectedFields(rootNode, null, ProtocolPostingRequestFieldSets.expenseSettledFields());
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
    rejectUnexpectedFields(rootNode, null, ProtocolPostingRequestFieldSets.expenseOnCreditFields());
    return new BookkeepingEntry.ExpenseOnCredit(
        CliBookkeepingEntryStructureParser.requiredEffectiveDate(rootNode),
        new AccountCode(
            requiredText(rootNode, ProtocolPostEntryFields.TopLevel.EXPENSE_ACCOUNT_CODE)),
        new AccountCode(
            requiredText(rootNode, ProtocolPostEntryFields.TopLevel.PAYABLE_ACCOUNT_CODE)),
        CliBookkeepingEntryStructureParser.requiredPositiveAmount(rootNode),
        CliBookkeepingEntryNestedParser.optionalTaxSelection(rootNode),
        null);
  }

  private static BookkeepingEntry.Receipt readReceiptEntry(ObjectNode rootNode) {
    rejectUnexpectedFields(rootNode, null, ProtocolPostingRequestFieldSets.receiptFields());
    return new BookkeepingEntry.Receipt(
        CliBookkeepingEntryStructureParser.requiredEffectiveDate(rootNode),
        new AccountCode(requiredText(rootNode, ProtocolPostEntryFields.TopLevel.CASH_ACCOUNT_CODE)),
        new AccountCode(
            requiredText(rootNode, ProtocolPostEntryFields.TopLevel.RECEIVABLE_ACCOUNT_CODE)),
        CliBookkeepingEntryStructureParser.requiredPositiveAmount(rootNode),
        CliBookkeepingEntryNestedParser.optionalSettlementAdjunct(rootNode));
  }

  private static BookkeepingEntry.Payment readPaymentEntry(ObjectNode rootNode) {
    rejectUnexpectedFields(rootNode, null, ProtocolPostingRequestFieldSets.paymentFields());
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
        rootNode, null, ProtocolPostingRequestFieldSets.ownerContributionFields());
    return new BookkeepingEntry.OwnerContribution(
        CliBookkeepingEntryStructureParser.requiredEffectiveDate(rootNode),
        new AccountCode(requiredText(rootNode, ProtocolPostEntryFields.TopLevel.CASH_ACCOUNT_CODE)),
        new AccountCode(
            requiredText(rootNode, ProtocolPostEntryFields.TopLevel.EQUITY_ACCOUNT_CODE)),
        CliBookkeepingEntryStructureParser.requiredPositiveAmount(rootNode),
        CliBookkeepingEntryNestedParser.optionalForeignExchange(rootNode));
  }

  private static BookkeepingEntry.OwnerWithdrawal readOwnerWithdrawalEntry(ObjectNode rootNode) {
    rejectUnexpectedFields(rootNode, null, ProtocolPostingRequestFieldSets.ownerWithdrawalFields());
    return new BookkeepingEntry.OwnerWithdrawal(
        CliBookkeepingEntryStructureParser.requiredEffectiveDate(rootNode),
        new AccountCode(
            requiredText(rootNode, ProtocolPostEntryFields.TopLevel.EQUITY_ACCOUNT_CODE)),
        new AccountCode(requiredText(rootNode, ProtocolPostEntryFields.TopLevel.CASH_ACCOUNT_CODE)),
        CliBookkeepingEntryStructureParser.requiredPositiveAmount(rootNode),
        CliBookkeepingEntryNestedParser.optionalForeignExchange(rootNode));
  }

  private static BookkeepingEntry.OpeningPosition readOpeningPositionEntry(ObjectNode rootNode) {
    rejectUnexpectedFields(rootNode, null, ProtocolPostingRequestFieldSets.openingPositionFields());
    return new BookkeepingEntry.OpeningPosition(
        CliBookkeepingEntryStructureParser.requiredEffectiveDate(rootNode),
        CliBookkeepingEntryStructureParser.readOpeningBalances(
            requiredArray(rootNode, ProtocolPostEntryFields.TopLevel.OPENING_BALANCES)));
  }

  private static BookkeepingEntry.Reversal readReversalEntry(ObjectNode rootNode) {
    rejectUnexpectedFields(rootNode, null, ProtocolPostingRequestFieldSets.reversalEntryFields());
    return new BookkeepingEntry.Reversal(
        CliBookkeepingEntryStructureParser.requiredEffectiveDate(rootNode),
        CliBookkeepingEntryNestedParser.readRequiredReversal(rootNode),
        CliBookkeepingEntryNestedParser.optionalForeignExchange(rootNode),
        null);
  }

  /** Variant-specific typed-entry reader used by the posting-request parser. */
  @FunctionalInterface
  private interface TypedEntryReader {
    /** Parses one typed bookkeeping entry payload. */
    BookkeepingEntry read(ObjectNode rootNode);
  }
}
