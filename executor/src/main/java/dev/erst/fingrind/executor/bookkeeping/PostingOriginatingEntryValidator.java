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

  /** Rejects resolved entry facts that drift from the executor posting they annotate. */
  public static void requireResolvedMatches(
      @Nullable BookkeepingEntry resolvedOriginatingEntry,
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
    if (resolvedOriginatingEntry == null) {
      return;
    }
    if (resolvedOriginatingEntry.postingKind() != postingKind) {
      throw mismatch("postingKind", subjectName);
    }
    if (resolvedOriginatingEntry.postingOriginKind() != postingOriginKind) {
      throw mismatch("postingOriginKind", subjectName);
    }
    if (!resolvedOriginatingEntry.journalEntry().equals(journalEntry)) {
      throw new IllegalArgumentException(
          "resolvedOriginatingEntry journalEntry must match the " + subjectName + " journalEntry.");
    }
    if (!lineageEquals(resolvedOriginatingEntry, postingLineage)) {
      throw new IllegalArgumentException(
          "resolvedOriginatingEntry postingLineage must match the " + subjectName + " lineage.");
    }
  }

  /** Rejects caller-authored entry facts whose durable identity drifts from the posting shape. */
  public static void requireCallerAuthoredMatches(
      @Nullable BookkeepingEntry callerAuthoredEntry,
      PostingKind postingKind,
      PostingOriginKind postingOriginKind,
      PostingLineageModel postingLineage,
      String subjectName) {
    Objects.requireNonNull(postingKind, "postingKind");
    Objects.requireNonNull(postingOriginKind, "postingOriginKind");
    Objects.requireNonNull(postingLineage, "postingLineage");
    Objects.requireNonNull(subjectName, "subjectName");
    if (callerAuthoredEntry == null) {
      return;
    }
    if (callerAuthoredEntry.postingKind() != postingKind) {
      throw new IllegalArgumentException(
          "callerAuthoredEntry postingKind must match the " + subjectName + ".");
    }
    if (callerAuthoredEntry.postingOriginKind() != postingOriginKind) {
      throw new IllegalArgumentException(
          "callerAuthoredEntry postingOriginKind must match the " + subjectName + ".");
    }
    if (!lineageEquals(callerAuthoredEntry, postingLineage)) {
      throw new IllegalArgumentException(
          "callerAuthoredEntry postingLineage must match the " + subjectName + " lineage.");
    }
  }

  private static IllegalArgumentException mismatch(String fieldName, String subjectName) {
    return new IllegalArgumentException(
        "resolvedOriginatingEntry " + fieldName + " must match the " + subjectName + ".");
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
