package dev.erst.fingrind.cli;

import static dev.erst.fingrind.cli.CliJsonFieldAccess.requiredText;
import static dev.erst.fingrind.cli.CliJsonScalarParsers.parseWireValue;
import static dev.erst.fingrind.cli.CliJsonStructureAccess.rejectUnexpectedFields;
import static dev.erst.fingrind.cli.CliJsonStructureAccess.requireObjectNode;
import static dev.erst.fingrind.cli.CliJsonStructureAccess.requiredArray;

import dev.erst.fingrind.contract.bookkeeping.BookkeepingEntry;
import dev.erst.fingrind.contract.bookkeeping.MonetaryAmount;
import dev.erst.fingrind.contract.discovery.ScaffoldPlaceholders;
import dev.erst.fingrind.contract.protocol.ProtocolPostEntryFields;
import dev.erst.fingrind.contract.protocol.ProtocolPostingNestedFieldSets;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.CanonicalTemporalText;
import dev.erst.fingrind.core.JournalEntry;
import dev.erst.fingrind.core.JournalLine;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

/** Parses the structural bookkeeping-entry request fields shared across entry variants. */
final class CliBookkeepingEntryStructureParser {
  private CliBookkeepingEntryStructureParser() {}

  static JournalEntry readAdministrativeJournalEntry(ObjectNode rootNode) {
    return new JournalEntry(
        requiredEffectiveDate(rootNode),
        readLines(requiredArray(rootNode, ProtocolPostEntryFields.TopLevel.LINES)));
  }

  static List<BookkeepingEntry.OpeningPosition.OpeningAccountBalance> readOpeningBalances(
      JsonNode openingBalancesNode) {
    List<BookkeepingEntry.OpeningPosition.OpeningAccountBalance> openingBalances =
        new ArrayList<>();
    int index = 0;
    for (JsonNode openingBalanceNode : openingBalancesNode) {
      ObjectNode openingBalanceObject =
          requireObjectNode(openingBalanceNode, "openingBalances[%d]".formatted(index));
      rejectUnexpectedFields(
          openingBalanceObject,
          "openingBalances[%d]".formatted(index),
          ProtocolPostingNestedFieldSets.openingBalanceFields());
      openingBalances.add(
          new BookkeepingEntry.OpeningPosition.OpeningAccountBalance(
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

  static LocalDate requiredEffectiveDate(ObjectNode rootNode) {
    return CanonicalTemporalText.parseLocalDate(
        CliRequestPlaceholderValues.requiredRealText(
            rootNode,
            ProtocolPostEntryFields.TopLevel.EFFECTIVE_DATE,
            ScaffoldPlaceholders.EFFECTIVE_DATE,
            null),
        ProtocolPostEntryFields.TopLevel.EFFECTIVE_DATE);
  }

  static MonetaryAmount requiredPositiveAmount(ObjectNode rootNode) {
    return MonetaryAmount.of(
        CliJsonMoneyParser.requiredPositiveMoney(rootNode, ProtocolPostEntryFields.TopLevel.AMOUNT)
            .money());
  }

  private static List<JournalLine> readLines(JsonNode linesNode) {
    List<JournalLine> lines = new ArrayList<>();
    int index = 0;
    for (JsonNode lineNode : linesNode) {
      ObjectNode lineObject = requireObjectNode(lineNode, "lines[%d]".formatted(index));
      rejectUnexpectedFields(
          lineObject,
          "lines[%d]".formatted(index),
          ProtocolPostingNestedFieldSets.journalLineFields());
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
}
