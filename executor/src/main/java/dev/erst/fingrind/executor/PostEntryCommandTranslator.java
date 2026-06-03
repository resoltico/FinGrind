package dev.erst.fingrind.executor;

import dev.erst.fingrind.contract.bookkeeping.BookkeepingEntry;
import dev.erst.fingrind.contract.bookkeeping.PostEntryCommand;
import dev.erst.fingrind.core.JournalEntry;
import dev.erst.fingrind.core.JournalLine;
import dev.erst.fingrind.core.PostingKind;
import dev.erst.fingrind.core.PostingOriginKind;
import dev.erst.fingrind.executor.bookkeeping.PostingCommand;
import dev.erst.fingrind.executor.bookkeeping.PostingLineageModel;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/** Application-boundary translator from published entry commands into internal posting commands. */
public final class PostEntryCommandTranslator {
  private PostEntryCommandTranslator() {}

  /** Translates one published post-entry command into one internal posting command. */
  public static PostingCommand toPostingCommand(PostEntryCommand command) {
    Objects.requireNonNull(command, "command");
    return toInternalManagementSingleEntity(command);
  }

  private static PostingCommand toInternalManagementSingleEntity(PostEntryCommand command) {
    return switch (command.entry()) {
      case BookkeepingEntry.CashRevenue event ->
          standardPosting(
              event.effectiveDate(),
              List.of(
                  new JournalLine(
                      event.cashAccountCode(),
                      JournalLine.EntrySide.DEBIT,
                      event.amount().toMoney()),
                  new JournalLine(
                      event.revenueAccountCode(),
                      JournalLine.EntrySide.CREDIT,
                      event.amount().toMoney())),
              command);
      case BookkeepingEntry.CashExpense event ->
          standardPosting(
              event.effectiveDate(),
              List.of(
                  new JournalLine(
                      event.expenseAccountCode(),
                      JournalLine.EntrySide.DEBIT,
                      event.amount().toMoney()),
                  new JournalLine(
                      event.cashAccountCode(),
                      JournalLine.EntrySide.CREDIT,
                      event.amount().toMoney())),
              command);
      case BookkeepingEntry.EquityContribution event ->
          standardPosting(
              event.effectiveDate(),
              List.of(
                  new JournalLine(
                      event.cashAccountCode(),
                      JournalLine.EntrySide.DEBIT,
                      event.amount().toMoney()),
                  new JournalLine(
                      event.equityAccountCode(),
                      JournalLine.EntrySide.CREDIT,
                      event.amount().toMoney())),
              command);
      case BookkeepingEntry.EquityWithdrawal event ->
          standardPosting(
              event.effectiveDate(),
              List.of(
                  new JournalLine(
                      event.equityAccountCode(),
                      JournalLine.EntrySide.DEBIT,
                      event.amount().toMoney()),
                  new JournalLine(
                      event.cashAccountCode(),
                      JournalLine.EntrySide.CREDIT,
                      event.amount().toMoney())),
              command);
      case BookkeepingEntry.OpenAccountingPosition openingPosition ->
          new PostingCommand(
              PostingKind.OPENING_BALANCE,
              postingOriginKind(openingPosition),
              new JournalEntry(openingPosition.effectiveDate(), openingPosition.lines()),
              PostingLineageModel.direct(),
              command.evidence(),
              command.requestProvenance(),
              command.sourceChannel());
      case BookkeepingEntry.ReversalAdjustment reversalAdjustment ->
          new PostingCommand(
              PostingKind.STANDARD,
              postingOriginKind(reversalAdjustment),
              reversalAdjustment.journalEntry(),
              toReversalPostingLineageModel(reversalAdjustment.reversal()),
              command.evidence(),
              command.requestProvenance(),
              command.sourceChannel());
    };
  }

  private static PostingCommand standardPosting(
      LocalDate effectiveDate, List<JournalLine> lines, PostEntryCommand command) {
    return new PostingCommand(
        PostingKind.STANDARD,
        postingOriginKind(command.entry()),
        new JournalEntry(effectiveDate, lines),
        PostingLineageModel.direct(),
        command.evidence(),
        command.requestProvenance(),
        command.sourceChannel());
  }

  private static PostingOriginKind postingOriginKind(BookkeepingEntry entry) {
    return switch (Objects.requireNonNull(entry, "entry")) {
      case BookkeepingEntry.CashRevenue _ -> PostingOriginKind.CASH_REVENUE;
      case BookkeepingEntry.CashExpense _ -> PostingOriginKind.CASH_EXPENSE;
      case BookkeepingEntry.EquityContribution _ -> PostingOriginKind.EQUITY_CONTRIBUTION;
      case BookkeepingEntry.EquityWithdrawal _ -> PostingOriginKind.EQUITY_WITHDRAWAL;
      case BookkeepingEntry.OpenAccountingPosition _ -> PostingOriginKind.OPEN_ACCOUNTING_POSITION;
      case BookkeepingEntry.ReversalAdjustment _ -> PostingOriginKind.REVERSAL_ADJUSTMENT;
    };
  }

  private static PostingLineageModel toReversalPostingLineageModel(
      dev.erst.fingrind.contract.bookkeeping.PostingLineage.Reversal reversal) {
    Objects.requireNonNull(reversal, "reversal");
    return PostingLineageModel.reversal(reversal.reference(), reversal.reason());
  }
}
