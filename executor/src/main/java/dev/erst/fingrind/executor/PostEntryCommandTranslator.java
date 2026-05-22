package dev.erst.fingrind.executor;

import dev.erst.fingrind.contract.bookkeeping.BookkeepingEntry;
import dev.erst.fingrind.contract.bookkeeping.PostEntryCommand;
import dev.erst.fingrind.core.BookIdentity;
import dev.erst.fingrind.core.JournalEntry;
import dev.erst.fingrind.core.JournalLine;
import dev.erst.fingrind.core.PostingKind;
import dev.erst.fingrind.executor.bookkeeping.PostingCommand;
import dev.erst.fingrind.executor.bookkeeping.PostingLineageModel;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/** Application-boundary translator from published entry commands into internal posting commands. */
public final class PostEntryCommandTranslator {
  private PostEntryCommandTranslator() {}

  /** Translates one published post-entry command using the selected book policy profile. */
  public static PostingCommand toPostingCommand(
      BookIdentity bookIdentity, PostEntryCommand command) {
    Objects.requireNonNull(bookIdentity, "bookIdentity");
    Objects.requireNonNull(command, "command");
    return switch (bookIdentity.policyProfile()) {
      case INTERNAL_MANAGEMENT_SINGLE_ENTITY_V1 -> toInternalManagementSingleEntity(command);
    };
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
      case BookkeepingEntry.OwnerContribution event ->
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
      case BookkeepingEntry.OwnerDraw event ->
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
      case BookkeepingEntry.OpeningBalanceAdjustment openingBalanceAdjustment ->
          new PostingCommand(
              PostingKind.OPENING_BALANCE,
              openingBalanceAdjustment.journalEntry(),
              PostingLineageModel.direct(),
              command.evidence(),
              command.requestProvenance(),
              command.sourceChannel());
      case BookkeepingEntry.CorrectionAdjustment correctionAdjustment ->
          new PostingCommand(
              PostingKind.STANDARD,
              correctionAdjustment.journalEntry(),
              PostingLineageModel.direct(),
              command.evidence(),
              command.requestProvenance(),
              command.sourceChannel());
      case BookkeepingEntry.ReversalAdjustment reversalAdjustment ->
          new PostingCommand(
              PostingKind.STANDARD,
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
        new JournalEntry(effectiveDate, lines),
        PostingLineageModel.direct(),
        command.evidence(),
        command.requestProvenance(),
        command.sourceChannel());
  }

  private static PostingLineageModel toReversalPostingLineageModel(
      dev.erst.fingrind.contract.bookkeeping.PostingLineage.Reversal reversal) {
    Objects.requireNonNull(reversal, "reversal");
    return PostingLineageModel.reversal(reversal.reference(), reversal.reason());
  }
}
