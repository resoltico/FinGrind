package dev.erst.fingrind.cli;

import static dev.erst.fingrind.cli.CliJsonFieldAccess.requiredText;
import static dev.erst.fingrind.cli.CliJsonScalarParsers.parseWireValue;
import static dev.erst.fingrind.cli.CliJsonStructureAccess.rejectUnexpectedFields;

import dev.erst.fingrind.contract.bookkeeping.BookkeepingEntry;
import dev.erst.fingrind.contract.protocol.ProtocolPostEntryFields;
import dev.erst.fingrind.contract.protocol.ProtocolPostingRequestFieldSets;
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
    if (entryKind == BookkeepingEntryKind.DIRECT_JOURNAL) {
      return readDirectJournalEntry(rootNode);
    }
    return readTypedEntry(rootNode, entryKind);
  }

  private static BookkeepingEntry readTypedEntry(
      ObjectNode rootNode, BookkeepingEntryKind entryKind) {
    return CliTypedBookkeepingEntryReaders.read(rootNode, entryKind);
  }

  private static BookkeepingEntry.DirectJournal readDirectJournalEntry(ObjectNode rootNode) {
    rejectUnexpectedFields(
        rootNode,
        null,
        ProtocolPostingRequestFieldSets.fieldsFor(BookkeepingEntryKind.DIRECT_JOURNAL));
    return new BookkeepingEntry.DirectJournal(
        CliBookkeepingEntryStructureParser.readAdministrativeJournalEntry(rootNode),
        CliBookkeepingEntryNestedParser.optionalForeignExchange(rootNode));
  }
}
