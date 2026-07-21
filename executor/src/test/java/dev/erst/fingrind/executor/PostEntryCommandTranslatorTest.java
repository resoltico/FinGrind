package dev.erst.fingrind.executor;

import static dev.erst.fingrind.executor.ExecutorAccountingTestSupport.accountingEvidence;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
import dev.erst.fingrind.executor.bookkeeping.PostingValidationStore;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Covers application-boundary translation from published entry commands to internal postings. */
class PostEntryCommandTranslatorTest {
  private static final PostingValidationStore EMPTY_VALIDATION_STORE =
      new PostEntrySemanticsPolicyTestSupport.PostingValidationStoreDouble(Map.of());

  @Test
  void toPostingCommand_translatesSalesIntoCanonicalPostingCommand() {
    PostEntryCommand command =
        new PostEntryCommand(
            new BookkeepingEntry.SaleSettled(
                LocalDate.parse("2026-04-07"),
                new AccountCode("1000"),
                new AccountCode("4000"),
                new MonetaryAmount("EUR", "1250"),
                null,
                null,
                null,
                null,
                null),
            accountingEvidence("recipe-cash-revenue"),
            PostingApplicationServiceTestSupport.requestProvenance("idem-recipe-cash-revenue"),
            SourceChannel.CLI);

    assertEquals(
        new PostingCommand(
            PostingKind.STANDARD,
            dev.erst.fingrind.core.PostingOriginKind.SALE_SETTLED,
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
            SourceChannel.CLI,
            command.entry(),
            command.entry()),
        PostEntryCommandTranslator.toPostingCommand(command, EMPTY_VALIDATION_STORE));
  }

  @Test
  void toPostingCommand_translatesExpensesIntoCanonicalPostingCommand() {
    PostEntryCommand command =
        new PostEntryCommand(
            new BookkeepingEntry.ExpenseSettled(
                LocalDate.parse("2026-04-07"),
                new AccountCode("5000"),
                new AccountCode("1000"),
                new MonetaryAmount("EUR", "1250"),
                null,
                null,
                null),
            accountingEvidence("recipe-cash-expense"),
            PostingApplicationServiceTestSupport.requestProvenance("idem-recipe-cash-expense"),
            SourceChannel.CLI);

    assertEquals(
        new PostingCommand(
            PostingKind.STANDARD,
            dev.erst.fingrind.core.PostingOriginKind.EXPENSE_SETTLED,
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
            SourceChannel.CLI,
            command.entry(),
            command.entry()),
        PostEntryCommandTranslator.toPostingCommand(command, EMPTY_VALIDATION_STORE));
  }

  @Test
  void toPostingCommand_translatesOwnerContributionsIntoCanonicalPostingCommand() {
    PostEntryCommand command =
        new PostEntryCommand(
            new BookkeepingEntry.OwnerContribution(
                LocalDate.parse("2026-04-07"),
                new AccountCode("1000"),
                new AccountCode("3000"),
                new MonetaryAmount("EUR", "1250"),
                null),
            accountingEvidence("recipe-equity-contribution"),
            PostingApplicationServiceTestSupport.requestProvenance(
                "idem-recipe-equity-contribution"),
            SourceChannel.CLI);

    assertEquals(
        new PostingCommand(
            PostingKind.STANDARD,
            dev.erst.fingrind.core.PostingOriginKind.OWNER_CONTRIBUTION,
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
            SourceChannel.CLI,
            command.entry(),
            command.entry()),
        PostEntryCommandTranslator.toPostingCommand(command, EMPTY_VALIDATION_STORE));
  }

  @Test
  void toPostingCommand_translatesOwnerWithdrawalsIntoCanonicalPostingCommand() {
    PostEntryCommand command =
        new PostEntryCommand(
            new BookkeepingEntry.OwnerWithdrawal(
                LocalDate.parse("2026-04-07"),
                new AccountCode("3000"),
                new AccountCode("1000"),
                new MonetaryAmount("EUR", "1250"),
                null),
            accountingEvidence("recipe-equity-withdrawal"),
            PostingApplicationServiceTestSupport.requestProvenance("idem-recipe-equity-withdrawal"),
            SourceChannel.CLI);

    assertEquals(
        new PostingCommand(
            PostingKind.STANDARD,
            dev.erst.fingrind.core.PostingOriginKind.OWNER_WITHDRAWAL,
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
            SourceChannel.CLI,
            command.entry(),
            command.entry()),
        PostEntryCommandTranslator.toPostingCommand(command, EMPTY_VALIDATION_STORE));
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
    JournalEntry priorJournalEntry =
        new JournalEntry(
            LocalDate.parse("2026-04-06"),
            List.of(
                new JournalLine(
                    new AccountCode("1000"),
                    JournalLine.EntrySide.DEBIT,
                    Money.parse("EUR", "12.50")),
                new JournalLine(
                    new AccountCode("2000"),
                    JournalLine.EntrySide.CREDIT,
                    Money.parse("EUR", "12.50"))));
    PostingValidationStore validationStore =
        new PostEntrySemanticsPolicyTestSupport.PostingValidationStoreDouble(
            ExecutorAccountingTestSupport.bookIdentity(),
            Map.of(),
            Map.of(
                new PostingId("bdc03c47-a16c-3688-a18f-2445894bbc69"),
                PostingApplicationServiceTestSupport.existingPosting(
                    "posting-1", "prior-reversal", priorJournalEntry)));
    PostEntryCommand command =
        new PostEntryCommand(
            new BookkeepingEntry.Reversal(
                journalEntry.effectiveDate(),
                new dev.erst.fingrind.contract.bookkeeping.PostingLineage.Reversal(
                    new dev.erst.fingrind.core.ReversalReference(new PostingId("bdc03c47-a16c-3688-a18f-2445894bbc69")),
                    new dev.erst.fingrind.core.ReversalReason("reverse erroneous entry")),
                null,
                null),
            accountingEvidence("reversal-adjustment"),
            PostingApplicationServiceTestSupport.requestProvenance("idem-reversal-adjustment"),
            SourceChannel.CLI);

    assertEquals(
        new PostingCommand(
            PostingKind.STANDARD,
            dev.erst.fingrind.core.PostingOriginKind.REVERSAL,
            journalEntry,
            PostingLineageModel.reversal(
                new dev.erst.fingrind.core.ReversalReference(new PostingId("bdc03c47-a16c-3688-a18f-2445894bbc69")),
                new dev.erst.fingrind.core.ReversalReason("reverse erroneous entry")),
            accountingEvidence("reversal-adjustment"),
            PostingApplicationServiceTestSupport.requestProvenance("idem-reversal-adjustment"),
            SourceChannel.CLI,
            command.entry(),
            PostingApplicationServiceTestSupport.resolvedReversalEntry(
                "posting-1", "reverse erroneous entry", journalEntry)),
        PostEntryCommandTranslator.toPostingCommand(command, validationStore));
  }

  @Test
  void toPostingCommand_rejectsEntriesThatRemainUnresolvedAfterApplicationChecks() {
    PostEntryCommand command =
        new PostEntryCommand(
            new BookkeepingEntry.Reversal(
                LocalDate.parse("2026-04-07"),
                new dev.erst.fingrind.contract.bookkeeping.PostingLineage.Reversal(
                    new dev.erst.fingrind.core.ReversalReference(new PostingId("6045a122-24d5-3839-bfbe-fd3f0590e5b6")),
                    new dev.erst.fingrind.core.ReversalReason("operator reversal")),
                null,
                null),
            accountingEvidence("reversal-missing-target"),
            PostingApplicationServiceTestSupport.requestProvenance("idem-reversal-missing-target"),
            SourceChannel.CLI);

    IllegalStateException failure =
        assertThrows(
            IllegalStateException.class,
            () -> PostEntryCommandTranslator.toPostingCommand(command, EMPTY_VALIDATION_STORE));

    assertEquals(
        "Posting translation requires a resolvable entry after application rejection checks.",
        failure.getMessage());
  }

  @Test
  void toPostingCommand_translatesOpeningPositionWithDirectLineage() {
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
            new BookkeepingEntry.OpeningPosition(
                journalEntry.effectiveDate(), openingBalances(journalEntry)),
            accountingEvidence("opening-balance-adjustment"),
            PostingApplicationServiceTestSupport.requestProvenance(
                "idem-opening-balance-adjustment"),
            SourceChannel.CLI);

    assertEquals(
        new PostingCommand(
            PostingKind.OPENING_BALANCE,
            dev.erst.fingrind.core.PostingOriginKind.OPENING_POSITION,
            journalEntry,
            PostingLineageModel.direct(),
            accountingEvidence("opening-balance-adjustment"),
            PostingApplicationServiceTestSupport.requestProvenance(
                "idem-opening-balance-adjustment"),
            SourceChannel.CLI,
            command.entry(),
            command.entry()),
        PostEntryCommandTranslator.toPostingCommand(command, EMPTY_VALIDATION_STORE));
  }

  @Test
  void toPostingCommand_translatesOpeningPositionIntoOpeningBalancePosting() {
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
            new BookkeepingEntry.OpeningPosition(
                journalEntry.effectiveDate(), openingBalances(journalEntry)),
            accountingEvidence("correction-adjustment"),
            PostingApplicationServiceTestSupport.requestProvenance("idem-correction-adjustment"),
            SourceChannel.CLI);

    assertEquals(
        new PostingCommand(
            PostingKind.OPENING_BALANCE,
            dev.erst.fingrind.core.PostingOriginKind.OPENING_POSITION,
            journalEntry,
            PostingLineageModel.direct(),
            accountingEvidence("correction-adjustment"),
            PostingApplicationServiceTestSupport.requestProvenance("idem-correction-adjustment"),
            SourceChannel.CLI,
            command.entry(),
            command.entry()),
        PostEntryCommandTranslator.toPostingCommand(command, EMPTY_VALIDATION_STORE));
  }

  @Test
  void toPostingCommand_translatesDirectJournalsIntoJournalOriginPostings() {
    JournalEntry journalEntry =
        new JournalEntry(
            LocalDate.parse("2026-04-07"),
            List.of(
                new JournalLine(
                    new AccountCode("1000"),
                    JournalLine.EntrySide.DEBIT,
                    Money.parse("EUR", "12.50")),
                new JournalLine(
                    new AccountCode("2100"),
                    JournalLine.EntrySide.CREDIT,
                    Money.parse("EUR", "12.50"))));
    PostEntryCommand command =
        new PostEntryCommand(
            new BookkeepingEntry.DirectJournal(journalEntry, null),
            accountingEvidence("direct-journal"),
            PostingApplicationServiceTestSupport.requestProvenance("idem-direct-journal"),
            SourceChannel.CLI);

    assertEquals(
        new PostingCommand(
            PostingKind.STANDARD,
            dev.erst.fingrind.core.PostingOriginKind.DIRECT_JOURNAL,
            journalEntry,
            PostingLineageModel.direct(),
            accountingEvidence("direct-journal"),
            PostingApplicationServiceTestSupport.requestProvenance("idem-direct-journal"),
            SourceChannel.CLI,
            command.entry(),
            command.entry()),
        PostEntryCommandTranslator.toPostingCommand(command, EMPTY_VALIDATION_STORE));
  }

  private static List<BookkeepingEntry.OpeningPosition.OpeningAccountBalance> openingBalances(
      JournalEntry journalEntry) {
    List<BookkeepingEntry.OpeningPosition.OpeningAccountBalance> balances =
        new ArrayList<>(journalEntry.lines().size());
    for (JournalLine line : journalEntry.lines()) {
      balances.add(
          new BookkeepingEntry.OpeningPosition.OpeningAccountBalance(
              line.accountCode(), line.side(), MonetaryAmount.of(line.amount().money()), null));
    }
    return List.copyOf(balances);
  }
}
