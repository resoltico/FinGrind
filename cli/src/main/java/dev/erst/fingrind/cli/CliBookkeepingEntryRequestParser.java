package dev.erst.fingrind.cli;

import static dev.erst.fingrind.cli.CliJsonFieldAccess.optionalText;
import static dev.erst.fingrind.cli.CliJsonFieldAccess.requiredText;
import static dev.erst.fingrind.cli.CliJsonScalarParsers.parseWireValue;
import static dev.erst.fingrind.cli.CliJsonStructureAccess.rejectForbiddenField;
import static dev.erst.fingrind.cli.CliJsonStructureAccess.rejectUnexpectedFields;
import static dev.erst.fingrind.cli.CliJsonStructureAccess.requireObjectNode;
import static dev.erst.fingrind.cli.CliJsonStructureAccess.requiredArray;
import static dev.erst.fingrind.cli.CliJsonStructureAccess.requiredObject;

import dev.erst.fingrind.contract.bookkeeping.BookkeepingEntry;
import dev.erst.fingrind.contract.bookkeeping.JournalRecipe;
import dev.erst.fingrind.contract.bookkeeping.JournalRecipeKind;
import dev.erst.fingrind.contract.bookkeeping.MonetaryAmount;
import dev.erst.fingrind.contract.bookkeeping.PostingLineage;
import dev.erst.fingrind.contract.discovery.ScaffoldPlaceholders;
import dev.erst.fingrind.contract.protocol.ProtocolPostEntryFields;
import dev.erst.fingrind.contract.protocol.ProtocolPostingRequestFieldSets;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.BookkeepingEntryKind;
import dev.erst.fingrind.core.CanonicalTemporalText;
import dev.erst.fingrind.core.JournalEntry;
import dev.erst.fingrind.core.JournalLine;
import dev.erst.fingrind.core.PostingId;
import dev.erst.fingrind.core.ReversalReason;
import dev.erst.fingrind.core.ReversalReference;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import tools.jackson.databind.JsonNode;
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
      case JOURNAL -> readJournalEntry(rootNode);
      case OPEN_ACCOUNTING_POSITION -> readOpenAccountingPositionEntry(rootNode);
      case REVERSAL_ADJUSTMENT -> readReversalAdjustmentEntry(rootNode);
    };
  }

  private static BookkeepingEntry.Journal readJournalEntry(ObjectNode rootNode) {
    return optionalText(rootNode, ProtocolPostEntryFields.TopLevel.RECIPE_KIND)
        .map(
            recipeKindText ->
                readRecipeBackedJournalEntry(
                    rootNode,
                    parseWireValue(
                        recipeKindText,
                        ProtocolPostEntryFields.TopLevel.RECIPE_KIND,
                        JournalRecipeKind.wireValues(),
                        JournalRecipeKind::fromWireValue)))
        .orElseGet(
            () -> {
              rejectUnexpectedFields(
                  rootNode, null, ProtocolPostingRequestFieldSets.journalDirectFields());
              return new BookkeepingEntry.Journal(readAdministrativeJournalEntry(rootNode), null);
            });
  }

  private static BookkeepingEntry.Journal readRecipeBackedJournalEntry(
      ObjectNode rootNode, JournalRecipeKind recipeKind) {
    return switch (recipeKind) {
      case CASH_REVENUE -> {
        rejectUnexpectedFields(
            rootNode, null, ProtocolPostingRequestFieldSets.cashRevenueRecipeFields());
        yield new BookkeepingEntry.Journal(
            requiredEffectiveDate(rootNode),
            new JournalRecipe.CashRevenue(
                new AccountCode(
                    requiredText(rootNode, ProtocolPostEntryFields.TopLevel.CASH_ACCOUNT_CODE)),
                new AccountCode(
                    requiredText(rootNode, ProtocolPostEntryFields.TopLevel.REVENUE_ACCOUNT_CODE)),
                requiredPositiveAmount(rootNode)));
      }
      case CASH_EXPENSE -> {
        rejectUnexpectedFields(
            rootNode, null, ProtocolPostingRequestFieldSets.cashExpenseRecipeFields());
        yield new BookkeepingEntry.Journal(
            requiredEffectiveDate(rootNode),
            new JournalRecipe.CashExpense(
                new AccountCode(
                    requiredText(rootNode, ProtocolPostEntryFields.TopLevel.EXPENSE_ACCOUNT_CODE)),
                new AccountCode(
                    requiredText(rootNode, ProtocolPostEntryFields.TopLevel.CASH_ACCOUNT_CODE)),
                requiredPositiveAmount(rootNode)));
      }
      case EQUITY_CONTRIBUTION -> {
        rejectUnexpectedFields(
            rootNode, null, ProtocolPostingRequestFieldSets.equityContributionRecipeFields());
        yield new BookkeepingEntry.Journal(
            requiredEffectiveDate(rootNode),
            new JournalRecipe.EquityContribution(
                new AccountCode(
                    requiredText(rootNode, ProtocolPostEntryFields.TopLevel.CASH_ACCOUNT_CODE)),
                new AccountCode(
                    requiredText(rootNode, ProtocolPostEntryFields.TopLevel.EQUITY_ACCOUNT_CODE)),
                requiredPositiveAmount(rootNode)));
      }
      case EQUITY_WITHDRAWAL -> {
        rejectUnexpectedFields(
            rootNode, null, ProtocolPostingRequestFieldSets.equityWithdrawalRecipeFields());
        yield new BookkeepingEntry.Journal(
            requiredEffectiveDate(rootNode),
            new JournalRecipe.EquityWithdrawal(
                new AccountCode(
                    requiredText(rootNode, ProtocolPostEntryFields.TopLevel.EQUITY_ACCOUNT_CODE)),
                new AccountCode(
                    requiredText(rootNode, ProtocolPostEntryFields.TopLevel.CASH_ACCOUNT_CODE)),
                requiredPositiveAmount(rootNode)));
      }
    };
  }

  private static BookkeepingEntry.OpenAccountingPosition readOpenAccountingPositionEntry(
      ObjectNode rootNode) {
    rejectUnexpectedFields(
        rootNode, null, ProtocolPostingRequestFieldSets.openAccountingPositionFields());
    return new BookkeepingEntry.OpenAccountingPosition(
        requiredEffectiveDate(rootNode),
        readOpeningBalances(
            requiredArray(rootNode, ProtocolPostEntryFields.TopLevel.OPENING_BALANCES)));
  }

  private static BookkeepingEntry.ReversalAdjustment readReversalAdjustmentEntry(
      ObjectNode rootNode) {
    rejectUnexpectedFields(
        rootNode, null, ProtocolPostingRequestFieldSets.reversalAdjustmentFields());
    return new BookkeepingEntry.ReversalAdjustment(
        readAdministrativeJournalEntry(rootNode), readRequiredReversal(rootNode));
  }

  private static JournalEntry readAdministrativeJournalEntry(ObjectNode rootNode) {
    return new JournalEntry(
        requiredEffectiveDate(rootNode),
        readLines(requiredArray(rootNode, ProtocolPostEntryFields.TopLevel.LINES)));
  }

  private static List<BookkeepingEntry.OpenAccountingPosition.OpeningAccountBalance>
      readOpeningBalances(JsonNode openingBalancesNode) {
    List<BookkeepingEntry.OpenAccountingPosition.OpeningAccountBalance> openingBalances =
        new ArrayList<>();
    int index = 0;
    for (JsonNode openingBalanceNode : openingBalancesNode) {
      ObjectNode openingBalanceObject =
          requireObjectNode(openingBalanceNode, "openingBalances[%d]".formatted(index));
      rejectUnexpectedFields(
          openingBalanceObject,
          "openingBalances[%d]".formatted(index),
          ProtocolPostingRequestFieldSets.openingBalanceFields());
      openingBalances.add(
          new BookkeepingEntry.OpenAccountingPosition.OpeningAccountBalance(
              new AccountCode(
                  requiredText(
                      openingBalanceObject, ProtocolPostEntryFields.OpeningBalance.ACCOUNT_CODE)),
              parseWireValue(
                  requiredText(openingBalanceObject, ProtocolPostEntryFields.OpeningBalance.SIDE),
                  ProtocolPostEntryFields.OpeningBalance.SIDE,
                  JournalLine.EntrySide.wireValues(),
                  JournalLine.EntrySide::fromWireValue),
              MonetaryAmount.of(
                  CliJsonMoneyParser.requiredPositiveMoney(
                          openingBalanceObject, ProtocolPostEntryFields.OpeningBalance.AMOUNT)
                      .money())));
      index++;
    }
    return openingBalances;
  }

  private static List<JournalLine> readLines(JsonNode linesNode) {
    List<JournalLine> lines = new ArrayList<>();
    int index = 0;
    for (JsonNode lineNode : linesNode) {
      ObjectNode lineObject = requireObjectNode(lineNode, "lines[%d]".formatted(index));
      rejectUnexpectedFields(
          lineObject,
          "lines[%d]".formatted(index),
          ProtocolPostingRequestFieldSets.journalLineFields());
      lines.add(
          new JournalLine(
              new AccountCode(
                  requiredText(lineObject, ProtocolPostEntryFields.JournalLine.ACCOUNT_CODE)),
              parseWireValue(
                  requiredText(lineObject, ProtocolPostEntryFields.JournalLine.SIDE),
                  ProtocolPostEntryFields.JournalLine.SIDE,
                  JournalLine.EntrySide.wireValues(),
                  JournalLine.EntrySide::fromWireValue),
              CliJsonMoneyParser.requiredPositiveMoney(
                  lineObject, ProtocolPostEntryFields.JournalLine.AMOUNT)));
      index++;
    }
    return lines;
  }

  private static LocalDate requiredEffectiveDate(ObjectNode rootNode) {
    return CanonicalTemporalText.parseLocalDate(
        CliRequestPlaceholderValues.requiredRealText(
            rootNode,
            ProtocolPostEntryFields.TopLevel.EFFECTIVE_DATE,
            ScaffoldPlaceholders.EFFECTIVE_DATE,
            null),
        ProtocolPostEntryFields.TopLevel.EFFECTIVE_DATE);
  }

  private static MonetaryAmount requiredPositiveAmount(ObjectNode rootNode) {
    return MonetaryAmount.of(
        CliJsonMoneyParser.requiredPositiveMoney(rootNode, ProtocolPostEntryFields.TopLevel.AMOUNT)
            .money());
  }

  private static PostingLineage.Reversal readRequiredReversal(ObjectNode rootNode) {
    ObjectNode reversalObject = requiredObject(rootNode, ProtocolPostEntryFields.TopLevel.REVERSAL);
    return readReversalObject(reversalObject);
  }

  private static PostingLineage.Reversal readReversalObject(ObjectNode reversalObject) {
    rejectForbiddenField(reversalObject, ProtocolPostEntryFields.Reversal.KIND);
    rejectUnexpectedFields(
        reversalObject,
        ProtocolPostEntryFields.TopLevel.REVERSAL,
        ProtocolPostingRequestFieldSets.reversalFields());
    return new PostingLineage.Reversal(
        new ReversalReference(
            new PostingId(
                requiredText(reversalObject, ProtocolPostEntryFields.Reversal.PRIOR_POSTING_ID))),
        new ReversalReason(requiredText(reversalObject, ProtocolPostEntryFields.Reversal.REASON)));
  }
}
