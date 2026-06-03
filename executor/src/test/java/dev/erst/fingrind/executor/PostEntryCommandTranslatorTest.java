package dev.erst.fingrind.executor;

import static dev.erst.fingrind.executor.ExecutorAccountingTestSupport.accountingEvidence;
import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.erst.fingrind.contract.bookkeeping.BookkeepingEntry;
import dev.erst.fingrind.contract.bookkeeping.MonetaryAmount;
import dev.erst.fingrind.contract.bookkeeping.PostEntryCommand;
import dev.erst.fingrind.core.AccountCode;
import dev.erst.fingrind.core.JournalEntry;
import dev.erst.fingrind.core.JournalLine;
import dev.erst.fingrind.core.Money;
import dev.erst.fingrind.core.PostingId;
import dev.erst.fingrind.core.PostingKind;
import dev.erst.fingrind.core.SourceChannel;
import dev.erst.fingrind.executor.bookkeeping.PostingCommand;
import dev.erst.fingrind.executor.bookkeeping.PostingLineageModel;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Covers application-boundary translation from published entry commands to internal postings. */
class PostEntryCommandTranslatorTest {
  @Test
  void toPostingCommand_translatesTypedCashRevenueIntoCanonicalPostingCommand() {
    PostEntryCommand command =
        new PostEntryCommand(
            new BookkeepingEntry.CashRevenue(
                LocalDate.parse("2026-04-07"),
                new AccountCode("1000"),
                new AccountCode("4000"),
                new MonetaryAmount("EUR", "1250")),
            accountingEvidence("recipe-cash-revenue"),
            PostingApplicationServiceTestSupport.requestProvenance("idem-recipe-cash-revenue"),
            SourceChannel.CLI);

    assertEquals(
        new PostingCommand(
            PostingKind.STANDARD,
            dev.erst.fingrind.core.PostingOriginKind.CASH_REVENUE,
            new JournalEntry(
                LocalDate.parse("2026-04-07"),
                List.of(
                    new JournalLine(
                        new AccountCode("1000"),
                        JournalLine.EntrySide.DEBIT,
                        Money.ofMinorUnits(dev.erst.fingrind.core.CurrencyUnit.of("EUR"), 1250)),
                    new JournalLine(
                        new AccountCode("4000"),
                        JournalLine.EntrySide.CREDIT,
                        Money.ofMinorUnits(dev.erst.fingrind.core.CurrencyUnit.of("EUR"), 1250)))),
            PostingLineageModel.direct(),
            accountingEvidence("recipe-cash-revenue"),
            PostingApplicationServiceTestSupport.requestProvenance("idem-recipe-cash-revenue"),
            SourceChannel.CLI),
        PostEntryCommandTranslator.toPostingCommand(command));
  }

  @Test
  void toPostingCommand_translatesTypedCashExpenseIntoCanonicalPostingCommand() {
    PostEntryCommand command =
        new PostEntryCommand(
            new BookkeepingEntry.CashExpense(
                LocalDate.parse("2026-04-07"),
                new AccountCode("5000"),
                new AccountCode("1000"),
                new MonetaryAmount("EUR", "1250")),
            accountingEvidence("recipe-cash-expense"),
            PostingApplicationServiceTestSupport.requestProvenance("idem-recipe-cash-expense"),
            SourceChannel.CLI);

    assertEquals(
        new PostingCommand(
            PostingKind.STANDARD,
            dev.erst.fingrind.core.PostingOriginKind.CASH_EXPENSE,
            new JournalEntry(
                LocalDate.parse("2026-04-07"),
                List.of(
                    new JournalLine(
                        new AccountCode("5000"),
                        JournalLine.EntrySide.DEBIT,
                        Money.ofMinorUnits(dev.erst.fingrind.core.CurrencyUnit.of("EUR"), 1250)),
                    new JournalLine(
                        new AccountCode("1000"),
                        JournalLine.EntrySide.CREDIT,
                        Money.ofMinorUnits(dev.erst.fingrind.core.CurrencyUnit.of("EUR"), 1250)))),
            PostingLineageModel.direct(),
            accountingEvidence("recipe-cash-expense"),
            PostingApplicationServiceTestSupport.requestProvenance("idem-recipe-cash-expense"),
            SourceChannel.CLI),
        PostEntryCommandTranslator.toPostingCommand(command));
  }

  @Test
  void toPostingCommand_translatesEquityContributionIntoCanonicalPostingCommand() {
    PostEntryCommand command =
        new PostEntryCommand(
            new BookkeepingEntry.EquityContribution(
                LocalDate.parse("2026-04-07"),
                new AccountCode("1000"),
                new AccountCode("3000"),
                new MonetaryAmount("EUR", "1250")),
            accountingEvidence("recipe-equity-contribution"),
            PostingApplicationServiceTestSupport.requestProvenance(
                "idem-recipe-equity-contribution"),
            SourceChannel.CLI);

    assertEquals(
        new PostingCommand(
            PostingKind.STANDARD,
            dev.erst.fingrind.core.PostingOriginKind.EQUITY_CONTRIBUTION,
            new JournalEntry(
                LocalDate.parse("2026-04-07"),
                List.of(
                    new JournalLine(
                        new AccountCode("1000"),
                        JournalLine.EntrySide.DEBIT,
                        Money.ofMinorUnits(dev.erst.fingrind.core.CurrencyUnit.of("EUR"), 1250)),
                    new JournalLine(
                        new AccountCode("3000"),
                        JournalLine.EntrySide.CREDIT,
                        Money.ofMinorUnits(dev.erst.fingrind.core.CurrencyUnit.of("EUR"), 1250)))),
            PostingLineageModel.direct(),
            accountingEvidence("recipe-equity-contribution"),
            PostingApplicationServiceTestSupport.requestProvenance(
                "idem-recipe-equity-contribution"),
            SourceChannel.CLI),
        PostEntryCommandTranslator.toPostingCommand(command));
  }

  @Test
  void toPostingCommand_translatesEquityWithdrawalIntoCanonicalPostingCommand() {
    PostEntryCommand command =
        new PostEntryCommand(
            new BookkeepingEntry.EquityWithdrawal(
                LocalDate.parse("2026-04-07"),
                new AccountCode("3000"),
                new AccountCode("1000"),
                new MonetaryAmount("EUR", "1250")),
            accountingEvidence("recipe-equity-withdrawal"),
            PostingApplicationServiceTestSupport.requestProvenance("idem-recipe-equity-withdrawal"),
            SourceChannel.CLI);

    assertEquals(
        new PostingCommand(
            PostingKind.STANDARD,
            dev.erst.fingrind.core.PostingOriginKind.EQUITY_WITHDRAWAL,
            new JournalEntry(
                LocalDate.parse("2026-04-07"),
                List.of(
                    new JournalLine(
                        new AccountCode("3000"),
                        JournalLine.EntrySide.DEBIT,
                        Money.ofMinorUnits(dev.erst.fingrind.core.CurrencyUnit.of("EUR"), 1250)),
                    new JournalLine(
                        new AccountCode("1000"),
                        JournalLine.EntrySide.CREDIT,
                        Money.ofMinorUnits(dev.erst.fingrind.core.CurrencyUnit.of("EUR"), 1250)))),
            PostingLineageModel.direct(),
            accountingEvidence("recipe-equity-withdrawal"),
            PostingApplicationServiceTestSupport.requestProvenance("idem-recipe-equity-withdrawal"),
            SourceChannel.CLI),
        PostEntryCommandTranslator.toPostingCommand(command));
  }

  @Test
  void toPostingCommand_translatesReversalAdjustmentsAndReversalLineage() {
    JournalEntry journalEntry =
        new JournalEntry(
            LocalDate.parse("2026-04-07"),
            List.of(
                new JournalLine(
                    new AccountCode("1000"),
                    JournalLine.EntrySide.CREDIT,
                    Money.parse("EUR", "12.50")),
                new JournalLine(
                    new AccountCode("2000"),
                    JournalLine.EntrySide.DEBIT,
                    Money.parse("EUR", "12.50"))));
    PostEntryCommand command =
        new PostEntryCommand(
            new BookkeepingEntry.ReversalAdjustment(
                journalEntry,
                new dev.erst.fingrind.contract.bookkeeping.PostingLineage.Reversal(
                    new dev.erst.fingrind.core.ReversalReference(new PostingId("posting-1")),
                    new dev.erst.fingrind.core.ReversalReason("reverse erroneous entry"))),
            accountingEvidence("reversal-adjustment"),
            PostingApplicationServiceTestSupport.requestProvenance("idem-reversal-adjustment"),
            SourceChannel.CLI);

    assertEquals(
        new PostingCommand(
            PostingKind.STANDARD,
            dev.erst.fingrind.core.PostingOriginKind.REVERSAL_ADJUSTMENT,
            journalEntry,
            PostingLineageModel.reversal(
                new dev.erst.fingrind.core.ReversalReference(new PostingId("posting-1")),
                new dev.erst.fingrind.core.ReversalReason("reverse erroneous entry")),
            accountingEvidence("reversal-adjustment"),
            PostingApplicationServiceTestSupport.requestProvenance("idem-reversal-adjustment"),
            SourceChannel.CLI),
        PostEntryCommandTranslator.toPostingCommand(command));
  }

  @Test
  void toPostingCommand_translatesOpenAccountingPositionWithDirectLineage() {
    JournalEntry journalEntry =
        new JournalEntry(
            LocalDate.parse("2026-04-07"),
            List.of(
                new JournalLine(
                    new AccountCode("1000"),
                    JournalLine.EntrySide.DEBIT,
                    Money.parse("EUR", "12.50")),
                new JournalLine(
                    new AccountCode("2000"),
                    JournalLine.EntrySide.CREDIT,
                    Money.parse("EUR", "12.50"))));
    PostEntryCommand command =
        new PostEntryCommand(
            new BookkeepingEntry.OpenAccountingPosition(
                journalEntry.effectiveDate(), openingBalances(journalEntry)),
            accountingEvidence("opening-balance-adjustment"),
            PostingApplicationServiceTestSupport.requestProvenance(
                "idem-opening-balance-adjustment"),
            SourceChannel.CLI);

    assertEquals(
        new PostingCommand(
            PostingKind.OPENING_BALANCE,
            dev.erst.fingrind.core.PostingOriginKind.OPEN_ACCOUNTING_POSITION,
            journalEntry,
            PostingLineageModel.direct(),
            accountingEvidence("opening-balance-adjustment"),
            PostingApplicationServiceTestSupport.requestProvenance(
                "idem-opening-balance-adjustment"),
            SourceChannel.CLI),
        PostEntryCommandTranslator.toPostingCommand(command));
  }

  @Test
  void toPostingCommand_translatesOpenAccountingPositionIntoOpeningBalancePosting() {
    JournalEntry journalEntry =
        new JournalEntry(
            LocalDate.parse("2026-04-07"),
            List.of(
                new JournalLine(
                    new AccountCode("6100"),
                    JournalLine.EntrySide.DEBIT,
                    Money.parse("EUR", "12.50")),
                new JournalLine(
                    new AccountCode("1000"),
                    JournalLine.EntrySide.CREDIT,
                    Money.parse("EUR", "12.50"))));
    PostEntryCommand command =
        new PostEntryCommand(
            new BookkeepingEntry.OpenAccountingPosition(
                journalEntry.effectiveDate(), openingBalances(journalEntry)),
            accountingEvidence("correction-adjustment"),
            PostingApplicationServiceTestSupport.requestProvenance("idem-correction-adjustment"),
            SourceChannel.CLI);

    assertEquals(
        new PostingCommand(
            PostingKind.OPENING_BALANCE,
            dev.erst.fingrind.core.PostingOriginKind.OPEN_ACCOUNTING_POSITION,
            journalEntry,
            PostingLineageModel.direct(),
            accountingEvidence("correction-adjustment"),
            PostingApplicationServiceTestSupport.requestProvenance("idem-correction-adjustment"),
            SourceChannel.CLI),
        PostEntryCommandTranslator.toPostingCommand(command));
  }

  private static List<BookkeepingEntry.OpenAccountingPosition.OpeningAccountBalance>
      openingBalances(JournalEntry journalEntry) {
    List<BookkeepingEntry.OpenAccountingPosition.OpeningAccountBalance> balances =
        new ArrayList<>(journalEntry.lines().size());
    for (JournalLine line : journalEntry.lines()) {
      balances.add(
          new BookkeepingEntry.OpenAccountingPosition.OpeningAccountBalance(
              line.accountCode(), line.side(), MonetaryAmount.of(line.amount().money())));
    }
    return List.copyOf(balances);
  }
}
