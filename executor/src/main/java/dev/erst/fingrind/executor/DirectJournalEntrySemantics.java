package dev.erst.fingrind.executor;

import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.JournalLine;
import dev.erst.fingrind.executor.bookkeeping.BookkeepingEntrySemanticsViolationFactory;
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
    requireCashBasisAccountMovement(violations, accounts, selectorField, selectorValue, lines);
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
        BookkeepingEntrySemanticsViolationFactory.economicNullJournal(
            selectorField, selectorValue));
  }

  private static void requireCashBasisAccountMovement(
      List<BookkeepingPostingRejection.EntrySemanticsViolation> violations,
      Map<AccountCode, RegisteredAccount> accounts,
      String selectorField,
      String selectorValue,
      List<JournalLine> lines) {
    List<AccountCode> referencedAccountCodes =
        lines.stream().map(JournalLine::accountCode).distinct().toList();
    if (accounts.size() != referencedAccountCodes.size()) {
      return;
    }
    boolean hasCashAccount =
        referencedAccountCodes.stream()
            .map(accounts::get)
            .anyMatch(RegisteredAccount::cashAndCashEquivalent);
    if (hasCashAccount) {
      return;
    }
    violations.add(
        BookkeepingEntrySemanticsViolationFactory.cashBasisAccountRequired(
            selectorField, selectorValue, referencedAccountCodes));
  }
}
