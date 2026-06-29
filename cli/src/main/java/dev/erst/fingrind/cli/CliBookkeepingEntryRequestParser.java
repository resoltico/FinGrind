package dev.erst.fingrind.cli;

import static dev.erst.fingrind.cli.CliJsonFieldAccess.requiredText;
import static dev.erst.fingrind.cli.CliJsonScalarParsers.parseWireValue;
import static dev.erst.fingrind.cli.CliJsonStructureAccess.rejectUnexpectedFields;
import static dev.erst.fingrind.cli.CliJsonStructureAccess.requiredArray;

import dev.erst.fingrind.contract.bookkeeping.BookkeepingEntry;
import dev.erst.fingrind.contract.protocol.ProtocolPostEntryFields;
import dev.erst.fingrind.contract.protocol.ProtocolPostingRequestFieldSets;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.BookkeepingEntryKind;
import tools.jackson.databind.node.ObjectNode;

/** Parses bookkeeping-entry payloads for posting commands. */
final class CliBookkeepingEntryRequestParser {
  private CliBookkeepingEntryRequestParser() {}

  static BookkeepingEntry readEntry(ObjectNode rootNode) {
    BookkeepingEntryKind entryKind =
        parseWireValue(
            requiredText(rootNode, ProtocolPostEntryFields.TopLevel.ENTRY_KIND),
            ProtocolPostEntryFields.TopLevel.ENTRY_KIND,
            BookkeepingEntryKind.wireValues(),
            BookkeepingEntryKind::fromWireValue);
    return switch (entryKind) {
      case DIRECT_JOURNAL -> readDirectJournalEntry(rootNode);
      case SALE -> readSaleEntry(rootNode);
      case EXPENSE -> readExpenseEntry(rootNode);
      case OWNER_CONTRIBUTION -> readOwnerContributionEntry(rootNode);
      case OWNER_WITHDRAWAL -> readOwnerWithdrawalEntry(rootNode);
      case OPENING_POSITION -> readOpeningPositionEntry(rootNode);
      case REVERSAL -> readReversalEntry(rootNode);
    };
  }

  private static BookkeepingEntry.DirectJournal readDirectJournalEntry(ObjectNode rootNode) {
    rejectUnexpectedFields(rootNode, null, ProtocolPostingRequestFieldSets.journalDirectFields());
    return new BookkeepingEntry.DirectJournal(
        CliBookkeepingEntryStructureParser.readAdministrativeJournalEntry(rootNode),
        CliBookkeepingEntryNestedParser.optionalForeignExchange(rootNode));
  }

  private static BookkeepingEntry.Sale readSaleEntry(ObjectNode rootNode) {
    rejectUnexpectedFields(rootNode, null, ProtocolPostingRequestFieldSets.saleFields());
    return new BookkeepingEntry.Sale(
        CliBookkeepingEntryStructureParser.requiredEffectiveDate(rootNode),
        new AccountCode(requiredText(rootNode, ProtocolPostEntryFields.TopLevel.CASH_ACCOUNT_CODE)),
        new AccountCode(
            requiredText(rootNode, ProtocolPostEntryFields.TopLevel.REVENUE_ACCOUNT_CODE)),
        CliBookkeepingEntryStructureParser.requiredPositiveAmount(rootNode),
        CliBookkeepingEntryNestedParser.optionalForeignExchange(rootNode),
        CliBookkeepingEntryNestedParser.optionalTaxSelection(rootNode),
        null);
  }

  private static BookkeepingEntry.Expense readExpenseEntry(ObjectNode rootNode) {
    rejectUnexpectedFields(rootNode, null, ProtocolPostingRequestFieldSets.expenseFields());
    return new BookkeepingEntry.Expense(
        CliBookkeepingEntryStructureParser.requiredEffectiveDate(rootNode),
        new AccountCode(
            requiredText(rootNode, ProtocolPostEntryFields.TopLevel.EXPENSE_ACCOUNT_CODE)),
        new AccountCode(requiredText(rootNode, ProtocolPostEntryFields.TopLevel.CASH_ACCOUNT_CODE)),
        CliBookkeepingEntryStructureParser.requiredPositiveAmount(rootNode),
        CliBookkeepingEntryNestedParser.optionalForeignExchange(rootNode),
        CliBookkeepingEntryNestedParser.optionalTaxSelection(rootNode),
        null);
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
        CliBookkeepingEntryStructureParser.readAdministrativeJournalEntry(rootNode),
        CliBookkeepingEntryNestedParser.readRequiredReversal(rootNode),
        CliBookkeepingEntryNestedParser.optionalForeignExchange(rootNode));
  }
}
