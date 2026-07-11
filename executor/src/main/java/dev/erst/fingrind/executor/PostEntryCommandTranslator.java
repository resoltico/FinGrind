package dev.erst.fingrind.executor;

import dev.erst.fingrind.contract.bookkeeping.BookkeepingEntry;
import dev.erst.fingrind.contract.bookkeeping.PostEntryCommand;
import dev.erst.fingrind.executor.bookkeeping.PostingCommand;
import dev.erst.fingrind.executor.bookkeeping.PostingLineageModel;
import dev.erst.fingrind.executor.bookkeeping.PostingValidationStore;
import java.util.Objects;

/** Application-boundary translator from published entry commands into internal posting commands. */
public final class PostEntryCommandTranslator {
  private PostEntryCommandTranslator() {}

  /** Translates one published post-entry command into one internal posting command. */
  public static PostingCommand toPostingCommand(
      PostEntryCommand command, PostingValidationStore validationStore) {
    Objects.requireNonNull(command, "command");
    Objects.requireNonNull(validationStore, "validationStore");
    PostEntryResolutionSupport.ResolutionOutcome resolutionOutcome =
        PostEntryResolutionSupport.resolve(command.entry(), validationStore);
    if (resolutionOutcome.rejection().isPresent()) {
      throw new IllegalStateException(
          "Posting translation requires a resolvable entry after application rejection checks.");
    }
    return toInternalManagementSingleEntity(command, resolutionOutcome.entry());
  }

  private static PostingCommand toInternalManagementSingleEntity(
      PostEntryCommand command, BookkeepingEntry resolvedEntry) {
    PostingLineageModel postingLineage =
        resolvedEntry instanceof BookkeepingEntry.Reversal reversal
            ? toReversalPostingLineageModel(reversal.reversal())
            : PostingLineageModel.direct();
    return new PostingCommand(
        resolvedEntry.postingKind(),
        resolvedEntry.postingOriginKind(),
        resolvedEntry.journalEntry(),
        postingLineage,
        command.evidence(),
        command.requestProvenance(),
        command.sourceChannel(),
        command.entry(),
        resolvedEntry);
  }

  private static PostingLineageModel toReversalPostingLineageModel(
      dev.erst.fingrind.contract.bookkeeping.PostingLineage.Reversal reversal) {
    Objects.requireNonNull(reversal, "reversal");
    return PostingLineageModel.reversal(reversal.reference(), reversal.reason());
  }
}
