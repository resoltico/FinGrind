package dev.erst.fingrind.executor;

import dev.erst.fingrind.contract.bookkeeping.BookkeepingEntry;
import dev.erst.fingrind.contract.bookkeeping.PostEntryCommand;
import dev.erst.fingrind.executor.bookkeeping.PostingCommand;
import dev.erst.fingrind.executor.bookkeeping.PostingLineageModel;
import dev.erst.fingrind.executor.spi.TaxRegistrationLookupStore;
import java.util.Objects;

/** Application-boundary translator from published entry commands into internal posting commands. */
public final class PostEntryCommandTranslator {
  private PostEntryCommandTranslator() {}

  /** Translates one published post-entry command into one internal posting command. */
  public static PostingCommand toPostingCommand(
      PostEntryCommand command, TaxRegistrationLookupStore taxRegistrationLookupStore) {
    Objects.requireNonNull(command, "command");
    Objects.requireNonNull(taxRegistrationLookupStore, "taxRegistrationLookupStore");
    return toInternalManagementSingleEntity(
        command, TaxPostingResolution.resolve(command.entry(), taxRegistrationLookupStore));
  }

  private static PostingCommand toInternalManagementSingleEntity(
      PostEntryCommand command, BookkeepingEntry resolvedEntry) {
    return switch (resolvedEntry) {
      case BookkeepingEntry.DirectJournal journal ->
          new PostingCommand(
              journal.postingKind(),
              journal.postingOriginKind(),
              journal.journalEntry(),
              PostingLineageModel.direct(),
              command.evidence(),
              command.requestProvenance(),
              command.sourceChannel(),
              resolvedEntry);
      case BookkeepingEntry.Sale sale ->
          new PostingCommand(
              sale.postingKind(),
              sale.postingOriginKind(),
              sale.journalEntry(),
              PostingLineageModel.direct(),
              command.evidence(),
              command.requestProvenance(),
              command.sourceChannel(),
              resolvedEntry);
      case BookkeepingEntry.Expense expense ->
          new PostingCommand(
              expense.postingKind(),
              expense.postingOriginKind(),
              expense.journalEntry(),
              PostingLineageModel.direct(),
              command.evidence(),
              command.requestProvenance(),
              command.sourceChannel(),
              resolvedEntry);
      case BookkeepingEntry.OwnerContribution ownerContribution ->
          new PostingCommand(
              ownerContribution.postingKind(),
              ownerContribution.postingOriginKind(),
              ownerContribution.journalEntry(),
              PostingLineageModel.direct(),
              command.evidence(),
              command.requestProvenance(),
              command.sourceChannel(),
              resolvedEntry);
      case BookkeepingEntry.OwnerWithdrawal ownerWithdrawal ->
          new PostingCommand(
              ownerWithdrawal.postingKind(),
              ownerWithdrawal.postingOriginKind(),
              ownerWithdrawal.journalEntry(),
              PostingLineageModel.direct(),
              command.evidence(),
              command.requestProvenance(),
              command.sourceChannel(),
              resolvedEntry);
      case BookkeepingEntry.OpeningPosition openingPosition ->
          new PostingCommand(
              openingPosition.postingKind(),
              openingPosition.postingOriginKind(),
              openingPosition.journalEntry(),
              PostingLineageModel.direct(),
              command.evidence(),
              command.requestProvenance(),
              command.sourceChannel(),
              resolvedEntry);
      case BookkeepingEntry.Reversal reversal ->
          new PostingCommand(
              reversal.postingKind(),
              reversal.postingOriginKind(),
              reversal.journalEntry(),
              toReversalPostingLineageModel(reversal.reversal()),
              command.evidence(),
              command.requestProvenance(),
              command.sourceChannel(),
              resolvedEntry);
    };
  }

  private static PostingLineageModel toReversalPostingLineageModel(
      dev.erst.fingrind.contract.bookkeeping.PostingLineage.Reversal reversal) {
    Objects.requireNonNull(reversal, "reversal");
    return PostingLineageModel.reversal(reversal.reference(), reversal.reason());
  }
}
