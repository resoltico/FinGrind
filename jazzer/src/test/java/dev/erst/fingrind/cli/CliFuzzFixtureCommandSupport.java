package dev.erst.fingrind.cli;

import static java.nio.charset.StandardCharsets.UTF_8;

import dev.erst.fingrind.contract.bookkeeping.BookkeepingEntry;
import dev.erst.fingrind.contract.bookkeeping.MonetaryAmount;
import dev.erst.fingrind.contract.bookkeeping.PostEntryCommand;
import dev.erst.fingrind.core.AccountCode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

final class CliFuzzFixtureCommandSupport {
  private CliFuzzFixtureCommandSupport() {}

  static String basicValidRequest() {
    return SqliteRoundTripWorkflowTestSupport.basicValidRequest();
  }

  static PostEntryCommand withEntry(PostEntryCommand template, BookkeepingEntry entry) {
    return new PostEntryCommand(
        entry, template.evidence(), template.requestProvenance(), template.sourceChannel());
  }

  static PostEntryCommand openAccountingPositionCommand(String... accountCodes) {
    if (accountCodes.length == 0 || accountCodes.length % 2 != 0) {
      throw new IllegalArgumentException(
          "Open-accounting-position fixture requires an even positive number of account codes.");
    }
    List<BookkeepingEntry.OpeningPosition.OpeningAccountBalance> balances = new ArrayList<>();
    int splitIndex = accountCodes.length / 2;
    for (int index = 0; index < accountCodes.length; index++) {
      balances.add(
          new BookkeepingEntry.OpeningPosition.OpeningAccountBalance(
              new AccountCode(accountCodes[index]),
              index < splitIndex
                  ? dev.erst.fingrind.core.JournalLine.EntrySide.DEBIT
                  : dev.erst.fingrind.core.JournalLine.EntrySide.CREDIT,
              new MonetaryAmount("EUR", "100")));
    }
    return withEntry(
        CliFuzzFixtures.readPostEntryCommand(basicValidRequest().getBytes(UTF_8)),
        new BookkeepingEntry.OpeningPosition(LocalDate.parse("2026-04-14"), balances));
  }

  static PostEntryCommand reversalAdjustmentCommand(String... accountCodes) {
    if (accountCodes.length == 0 || accountCodes.length % 2 != 0) {
      throw new IllegalArgumentException(
          "Reversal-adjustment fixture requires an even positive number of account codes.");
    }
    List<dev.erst.fingrind.core.JournalLine> lines = new ArrayList<>();
    int splitIndex = accountCodes.length / 2;
    for (int index = 0; index < accountCodes.length; index++) {
      lines.add(
          new dev.erst.fingrind.core.JournalLine(
              new AccountCode(accountCodes[index]),
              index < splitIndex
                  ? dev.erst.fingrind.core.JournalLine.EntrySide.DEBIT
                  : dev.erst.fingrind.core.JournalLine.EntrySide.CREDIT,
              dev.erst.fingrind.core.Money.parse("EUR", "1.00")));
    }
    dev.erst.fingrind.core.JournalEntry journalEntry =
        new dev.erst.fingrind.core.JournalEntry(LocalDate.parse("2026-04-14"), lines);
    return withEntry(
        CliFuzzFixtures.readPostEntryCommand(basicValidRequest().getBytes(UTF_8)),
        new BookkeepingEntry.Reversal(
            journalEntry.effectiveDate(),
            new dev.erst.fingrind.contract.bookkeeping.PostingLineage.Reversal(
                new dev.erst.fingrind.core.ReversalReference(
                    new dev.erst.fingrind.core.PostingId("posting-admin-test")),
                new dev.erst.fingrind.core.ReversalReason("administrative fixture")),
            null,
            journalEntry));
  }
}
