package dev.erst.fingrind.executor;

import dev.erst.fingrind.contract.bookkeeping.BookkeepingEntry;
import dev.erst.fingrind.contract.bookkeeping.JournalRecipe;
import dev.erst.fingrind.contract.bookkeeping.JournalRecipeKind;
import dev.erst.fingrind.contract.bookkeeping.PostEntryCommand;
import dev.erst.fingrind.core.JournalEntry;
import dev.erst.fingrind.core.PostingKind;
import dev.erst.fingrind.core.PostingOriginKind;
import dev.erst.fingrind.executor.bookkeeping.PostingCommand;
import dev.erst.fingrind.executor.bookkeeping.PostingLineageModel;
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
    PostingOriginKind postingOriginKind = postingOriginKind(command.entry());
    return switch (command.entry()) {
      case BookkeepingEntry.Journal journal ->
          new PostingCommand(
              PostingKind.STANDARD,
              postingOriginKind,
              journal.journalEntry(),
              PostingLineageModel.direct(),
              command.evidence(),
              command.requestProvenance(),
              command.sourceChannel());
      case BookkeepingEntry.OpenAccountingPosition openingPosition ->
          new PostingCommand(
              PostingKind.OPENING_BALANCE,
              postingOriginKind,
              new JournalEntry(openingPosition.effectiveDate(), openingPosition.lines()),
              PostingLineageModel.direct(),
              command.evidence(),
              command.requestProvenance(),
              command.sourceChannel());
      case BookkeepingEntry.ReversalAdjustment reversalAdjustment ->
          new PostingCommand(
              PostingKind.STANDARD,
              postingOriginKind,
              reversalAdjustment.journalEntry(),
              toReversalPostingLineageModel(reversalAdjustment.reversal()),
              command.evidence(),
              command.requestProvenance(),
              command.sourceChannel());
    };
  }

  private static PostingOriginKind postingOriginKind(BookkeepingEntry entry) {
    return switch (Objects.requireNonNull(entry, "entry")) {
      case BookkeepingEntry.Journal journal -> postingOriginKind(journal);
      case BookkeepingEntry.OpenAccountingPosition _ -> PostingOriginKind.OPEN_ACCOUNTING_POSITION;
      case BookkeepingEntry.ReversalAdjustment _ -> PostingOriginKind.REVERSAL_ADJUSTMENT;
    };
  }

  private static PostingOriginKind postingOriginKind(BookkeepingEntry.Journal journal) {
    JournalRecipe recipe = journal.recipe();
    return switch (recipe == null ? null : recipe.recipeKind()) {
      case null -> PostingOriginKind.JOURNAL;
      case JournalRecipeKind.CASH_REVENUE -> PostingOriginKind.CASH_REVENUE;
      case JournalRecipeKind.CASH_EXPENSE -> PostingOriginKind.CASH_EXPENSE;
      case JournalRecipeKind.EQUITY_CONTRIBUTION -> PostingOriginKind.EQUITY_CONTRIBUTION;
      case JournalRecipeKind.EQUITY_WITHDRAWAL -> PostingOriginKind.EQUITY_WITHDRAWAL;
    };
  }

  private static PostingLineageModel toReversalPostingLineageModel(
      dev.erst.fingrind.contract.bookkeeping.PostingLineage.Reversal reversal) {
    Objects.requireNonNull(reversal, "reversal");
    return PostingLineageModel.reversal(reversal.reference(), reversal.reason());
  }
}
