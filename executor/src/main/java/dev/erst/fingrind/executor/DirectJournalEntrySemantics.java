package dev.erst.fingrind.executor;

import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.JournalLine;
import dev.erst.fingrind.executor.bookkeeping.BookkeepingEntryModeSemanticsViolations;
import dev.erst.fingrind.executor.bookkeeping.BookkeepingPostingRejection;
import dev.erst.fingrind.executor.bookkeeping.RegisteredAccount;
import java.util.List;
import java.util.Map;

/** Raw direct-journal semantics for economically durable cash-basis movement. */
final class DirectJournalEntrySemantics {
  private DirectJournalEntrySemantics() {}

  static void validate(
      List<BookkeepingPostingRejection.EntrySemanticsViolation> violations,
      Map<AccountCode, RegisteredAccount> accounts,
      String selectorField,
      String selectorValue,
      List<JournalLine> lines) {
    requireEconomicAccountMovement(violations, selectorField, selectorValue, lines);
  }

  private static void requireEconomicAccountMovement(
      List<BookkeepingPostingRejection.EntrySemanticsViolation> violations,
      String selectorField,
      String selectorValue,
      List<JournalLine> lines) {
    if (!JournalEconomicMovement.isEconomicallyNull(lines)) {
      return;
    }
    violations.add(
        BookkeepingEntryModeSemanticsViolations.economicNullJournal(selectorField, selectorValue));
  }
}
