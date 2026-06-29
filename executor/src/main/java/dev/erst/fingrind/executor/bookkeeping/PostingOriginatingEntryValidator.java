package dev.erst.fingrind.executor.bookkeeping;

import dev.erst.fingrind.contract.bookkeeping.BookkeepingEntry;
import dev.erst.fingrind.core.JournalEntry;
import dev.erst.fingrind.core.PostingKind;
import dev.erst.fingrind.core.PostingOriginKind;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/** Validates retained caller-authored entry facts against one executor-owned posting shape. */
public final class PostingOriginatingEntryValidator {
  private PostingOriginatingEntryValidator() {}

  /** Rejects caller-authored entry facts that drift from the executor posting they annotate. */
  public static void requireMatches(
      @Nullable BookkeepingEntry originatingEntry,
      PostingKind postingKind,
      PostingOriginKind postingOriginKind,
      JournalEntry journalEntry,
      PostingLineageModel postingLineage,
      String subjectName) {
    Objects.requireNonNull(postingKind, "postingKind");
    Objects.requireNonNull(postingOriginKind, "postingOriginKind");
    Objects.requireNonNull(journalEntry, "journalEntry");
    Objects.requireNonNull(postingLineage, "postingLineage");
    Objects.requireNonNull(subjectName, "subjectName");
    if (originatingEntry == null) {
      return;
    }
    if (originatingEntry.postingKind() != postingKind) {
      throw mismatch("postingKind", subjectName);
    }
    if (originatingEntry.postingOriginKind() != postingOriginKind) {
      throw mismatch("postingOriginKind", subjectName);
    }
    if (!originatingEntry.journalEntry().equals(journalEntry)) {
      throw new IllegalArgumentException(
          "originatingEntry journalEntry must match the " + subjectName + " journalEntry.");
    }
    if (!lineageEquals(originatingEntry, postingLineage)) {
      throw new IllegalArgumentException(
          "originatingEntry postingLineage must match the " + subjectName + " lineage.");
    }
  }

  private static IllegalArgumentException mismatch(String fieldName, String subjectName) {
    return new IllegalArgumentException(
        "originatingEntry " + fieldName + " must match the " + subjectName + ".");
  }

  private static boolean lineageEquals(
      BookkeepingEntry originatingEntry, PostingLineageModel postingLineage) {
    return switch (originatingEntry.postingLineage()) {
      case dev.erst.fingrind.contract.bookkeeping.PostingLineage.Direct _ ->
          postingLineage instanceof PostingLineageModel.Direct;
      case dev.erst.fingrind.contract.bookkeeping.PostingLineage.Reversal reversal ->
          postingLineage instanceof PostingLineageModel.Reversal model
              && model.reference().equals(reversal.reference())
              && model.reason().equals(reversal.reason());
    };
  }
}
